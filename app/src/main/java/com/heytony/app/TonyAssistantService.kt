package com.heytony.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale

class TonyAssistantService : Service(), TextToSpeech.OnInitListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var stopping = false
    private var listeningSession = false

    private val retryListening = Runnable { beginListening() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this, this)
        createRecognizer()
        saveState(running = true, listening = false, status = "Iniciando escucha en segundo plano…")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAssistant()
            return START_NOT_STICKY
        }

        stopping = false
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_USER_STOPPED, false).apply()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (_: Exception) {
            saveState(running = false, listening = false, status = "Android no permitió iniciar la escucha en segundo plano.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START || recognizer != null) {
            beginListening()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the foreground assistant alive when its activity is dismissed from Recents.
        super.onTaskRemoved(rootIntent)
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale("es", "MX")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    handler.post { scheduleListening(400) }
                }
                @Deprecated("Deprecated by Android")
                override fun onError(utteranceId: String?) {
                    handler.post { scheduleListening(400) }
                }
            })
        }
    }

    private fun createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            saveState(running = true, listening = false, status = "No hay un servicio de reconocimiento de voz disponible.")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listeningSession = true
                    saveState(running = true, listening = true, status = "Te escucho, incluso en segundo plano.")
                }
                override fun onBeginningOfSpeech() {
                    saveState(running = true, listening = true, status = "Te escucho…")
                }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    saveState(running = true, listening = false, status = "Entendiendo…")
                }
                override fun onError(error: Int) {
                    listeningSession = false
                    if (stopping) return
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        saveState(running = false, listening = false, status = "Falta el permiso de micrófono. Abre HEY TONY para activarlo.")
                        stopSelf()
                    } else {
                        saveState(running = true, listening = false, status = "Sigo atento; vuelve a decir el comando.")
                        scheduleListening(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 700)
                    }
                }
                override fun onResults(results: Bundle?) {
                    listeningSession = false
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (heard.isNullOrBlank()) {
                        scheduleListening(500)
                    } else {
                        handleCommand(heard)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun beginListening() {
        if (stopping || listeningSession) return
        if (recognizer == null) createRecognizer()
        if (recognizer == null) {
            scheduleListening(2500)
            return
        }
        handler.removeCallbacks(retryListening)
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        }
        try {
            listeningSession = true
            recognizer?.startListening(speechIntent)
        } catch (_: RuntimeException) {
            listeningSession = false
            scheduleListening(1200)
        }
    }

    private fun scheduleListening(delayMillis: Long) {
        if (stopping) return
        handler.removeCallbacks(retryListening)
        handler.postDelayed(retryListening, delayMillis)
    }

    private fun handleCommand(heard: String) {
        saveState(running = true, listening = false, status = "Comando: $heard", heard = heard)
        val response = VoiceCommandProcessor.execute(this, heard)
        saveState(running = true, listening = false, status = response, heard = heard, reply = response)
        if (response == VoiceCommandProcessor.STOP_SENTINEL) {
            speak("De acuerdo, detengo el asistente.")
            handler.postDelayed({ stopAssistant() }, 1300)
        } else {
            speak(response)
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tony-response")
        } else {
            scheduleListening(1400)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, TonyAssistantService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            1002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("HEY TONY está activo")
            .setContentText("Escuchando comandos por voz. Toca Detener para apagarlo.")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Detener", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Asistente de voz HEY TONY",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Indica cuándo HEY TONY mantiene activo el micrófono." }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun saveState(
        running: Boolean,
        listening: Boolean,
        status: String,
        heard: String? = null,
        reply: String? = null
    ) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().apply {
            putBoolean(KEY_RUNNING, running)
            putBoolean(KEY_LISTENING, listening)
            putString(KEY_STATUS, status)
            if (heard != null) putString(KEY_HEARD, heard)
            if (reply != null) putString(KEY_REPLY, reply)
        }.apply()
    }

    private fun stopAssistant() {
        if (stopping) return
        stopping = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        listeningSession = false
        tts?.stop()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_USER_STOPPED, true).apply()
        saveState(running = false, listening = false, status = "Asistente detenido.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopping = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        listeningSession = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, false)
            .putBoolean(KEY_LISTENING, false)
            .apply()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.heytony.app.action.START_ASSISTANT"
        const val ACTION_STOP = "com.heytony.app.action.STOP_ASSISTANT"
        const val PREFS = "hey_tony_assistant"
        const val KEY_RUNNING = "running"
        const val KEY_LISTENING = "listening"
        const val KEY_STATUS = "status"
        const val KEY_HEARD = "heard"
        const val KEY_REPLY = "reply"
        const val KEY_USER_STOPPED = "user_stopped"
        private const val CHANNEL_ID = "hey_tony_voice_service"
        private const val NOTIFICATION_ID = 4201
    }
}
