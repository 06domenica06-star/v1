package com.heytony.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF071426)
private val Blue = Color(0xFF2387FF)
private val Pale = Color(0xFFEAF3FF)

data class LogEntry(val heard: String, val reply: String)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var autoListening = false
    private var activityVisible = false
    private var onListeningChanged: ((Boolean) -> Unit)? = null
    private var onStatusChanged: ((String) -> Unit)? = null
    private var onCommandHeard: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val restartRecognizer = Runnable { beginRecognition() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { HeyTonyScreen(this) }
    }

    override fun onStart() {
        super.onStart()
        activityVisible = true
        if (autoListening) beginRecognition()
    }

    override fun onStop() {
        activityVisible = false
        mainHandler.removeCallbacks(restartRecognizer)
        recognizer?.cancel()
        onListeningChanged?.invoke(false)
        super.onStop()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale("es", "MX")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    mainHandler.post { scheduleRecognition(350) }
                }
                @Deprecated("Deprecated by Android")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { scheduleRecognition(350) }
                }
            })
        }
    }

    fun startAutomaticListening(
        onListening: (Boolean) -> Unit,
        onStatus: (String) -> Unit,
        onCommand: (String) -> Unit
    ) {
        onListeningChanged = onListening
        onStatusChanged = onStatus
        onCommandHeard = onCommand
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onStatus("Permite el micrófono para que Tony empiece a escuchar al abrir la app.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onStatus("Este teléfono no tiene un servicio de reconocimiento de voz disponible.")
            return
        }
        autoListening = true
        onStatus("Escucha automática activa mientras HEY TONY está abierta.")
        createRecognizerIfNeeded()
        beginRecognition()
    }

    fun stopAutomaticListening() {
        autoListening = false
        mainHandler.removeCallbacks(restartRecognizer)
        recognizer?.cancel()
        onListeningChanged?.invoke(false)
        onStatusChanged?.invoke("Escucha pausada.")
    }

    fun resumeAutomaticListening() {
        if (onCommandHeard == null) return
        autoListening = true
        onStatusChanged?.invoke("Escucha automática activa mientras HEY TONY está abierta.")
        beginRecognition()
    }

    private fun createRecognizerIfNeeded() {
        if (recognizer != null) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onListeningChanged?.invoke(true)
                    onStatusChanged?.invoke("Te escucho; di tu comando.")
                }
                override fun onBeginningOfSpeech() { onListeningChanged?.invoke(true) }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    onListeningChanged?.invoke(false)
                    onStatusChanged?.invoke("Entendiendo…")
                }
                override fun onError(error: Int) {
                    onListeningChanged?.invoke(false)
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        autoListening = false
                        onStatusChanged?.invoke("Activa el permiso de micrófono en los ajustes de Android.")
                    } else {
                        onStatusChanged?.invoke("No te escuché; sigo intentando.")
                        scheduleRecognition(700)
                    }
                }
                override fun onResults(results: Bundle?) {
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    onListeningChanged?.invoke(false)
                    if (heard.isNullOrBlank()) {
                        scheduleRecognition(500)
                    } else {
                        onCommandHeard?.invoke(heard)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun beginRecognition() {
        if (!autoListening || !activityVisible || isFinishing || isDestroyed) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        createRecognizerIfNeeded()
        mainHandler.removeCallbacks(restartRecognizer)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        }
        try {
            recognizer?.startListening(intent)
        } catch (_: RuntimeException) {
            scheduleRecognition(1000)
        }
    }

    private fun scheduleRecognition(delayMillis: Long) {
        mainHandler.removeCallbacks(restartRecognizer)
        mainHandler.postDelayed(restartRecognizer, delayMillis)
    }

    fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hey-tony-reply")
        } else {
            scheduleRecognition(1500)
        }
    }

    fun runCommand(raw: String): String {
        val command = raw.lowercase(Locale("es", "PE")).replace("hey tony", "").replace("oye tony", "").replace("hola tony", "").trim()
        val pm = packageManager
        val appNames = listOf(
            Triple("spotify", "Spotify", "com.spotify.music"),
            Triple("youtube", "YouTube", "com.google.android.youtube"),
            Triple("whatsapp", "WhatsApp", "com.whatsapp"),
            Triple("instagram", "Instagram", "com.instagram.android"),
            Triple("chrome", "Chrome", "com.android.chrome")
        )
        val requestedApp = appNames.firstOrNull { command.contains(it.first) }
        if (requestedApp != null && (command.contains("abre") || command.contains("abrir") || command.contains("inicia") || command.contains("lanza"))) {
            val launch = pm.getLaunchIntentForPackage(requestedApp.third)
            if (launch != null) { startActivity(launch); return "Abriendo ${requestedApp.second}." }
            return "No encontré ${requestedApp.second} instalado en este teléfono."
        }
        if (command.contains("ajustes") || command.contains("configuración")) {
            startActivity(Intent(Settings.ACTION_SETTINGS)); return "Abriendo los ajustes."
        }
        if (command.contains("cámara") || command.contains("camara")) {
            val camera = pm.getLaunchIntentForPackage("com.android.camera2") ?: pm.getLaunchIntentForPackage("com.google.android.GoogleCamera")
            if (camera != null) { startActivity(camera); return "Abriendo la cámara." }
            return "No pude encontrar la aplicación de cámara."
        }
        if (command.contains("qué hora") || command.contains("que hora")) {
            return "Son las ${SimpleDateFormat("h:mm a", Locale("es", "PE")).format(Date())}."
        }
        if (command.contains("qué fecha") || command.contains("que fecha") || command.contains("qué día") || command.contains("que dia")) {
            return "Hoy es ${SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "PE")).format(Date())}."
        }
        if (command.contains("batería") || command.contains("bateria")) {
            val level = (getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return "La batería está al $level por ciento."
        }
        if (command.contains("sube el volumen") || command.contains("aumenta el volumen")) {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            return "Subí el volumen."
        }
        if (command.contains("baja el volumen") || command.contains("disminuye el volumen")) {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            return "Bajé el volumen."
        }
        if (command.contains("wifi") || command.contains("wi-fi")) {
            return "Puedes revisar el estado del Wi-Fi en los ajustes de conexión."
        }
        if (command.contains("linterna") || command.contains("linterna")) {
            return "Para controlar la linterna, concede el permiso de cámara desde los ajustes. Esta acción se habilitará en la siguiente versión."
        }
        if (command.contains("pausa") || command.contains("pausa la música") || command.contains("siguiente canción")) {
            return "Abre tu reproductor de música y usa sus controles multimedia."
        }
        if (command.startsWith("llama") || command.contains("llama a ")) {
            return "Para llamar, abre Contactos y elige a la persona."
        }
        return "Todavía no tengo ese comando. Prueba: abre Spotify, qué hora es o cuánta batería tengo."
    }

    override fun onDestroy() {
        autoListening = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy(); recognizer = null
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }
}

@Composable
private fun HeyTonyScreen(activity: MainActivity) {
    var listening by remember { mutableStateOf(false) }
    var automaticMode by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Preparando escucha automática…") }
    var lastHeard by remember { mutableStateOf("Abre la app y habla; no tienes que tocar el micrófono.") }
    val entries = remember { mutableStateListOf<LogEntry>() }
    val startListening: () -> Unit = {
        activity.startAutomaticListening(
            onListening = { listening = it },
            onStatus = { status = it },
            onCommand = { heard ->
                lastHeard = heard
                val reply = activity.runCommand(heard)
                entries.add(0, LogEntry(heard, reply))
                status = reply
                activity.speak(reply)
            }
        )
        automaticMode = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
        else {
            automaticMode = false
            status = "Permite el micrófono en los ajustes de Android para usar la escucha automática."
        }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            status = "Autoriza el micrófono una vez para iniciar la escucha automática."
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Navy, surface = Color(0xFF10233B))) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF08162A), Color(0xFF0B2341), Navy))).padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SettingsVoice, null, tint = Blue, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("HEY TONY", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text("v1.0", color = Color(0xFF9BB5D1), fontSize = 13.sp)
            }
            Spacer(Modifier.height(30.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10233B)), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(174.dp).background(Color(0xFF132E4E), CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(137.dp).background(if (listening) Color(0xFF174F88) else Color(0xFF173A61), CircleShape), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(98.dp).background(Blue, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(42.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(17.dp))
                    Text(if (listening) "Te escucho…" else status, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(7.dp))
                    Text(lastHeard, color = Color(0xFFAFC3DA), fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (listening) "MICRÓFONO ACTIVO" else status.uppercase(Locale("es", "PE")),
                        color = if (listening) Color(0xFF80C7FF) else Color(0xFF8EA8C5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            if (automaticMode) {
                                activity.stopAutomaticListening()
                                listening = false
                                automaticMode = false
                            } else {
                                activity.resumeAutomaticListening()
                                automaticMode = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(if (automaticMode) "Pausar escucha" else "Reanudar escucha") }
                    Spacer(Modifier.height(4.dp))
                    Text("Escucha automáticamente mientras esta pantalla está abierta.", color = Color(0xFF8EA8C5), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("PRUEBA ESTOS COMANDOS", color = Pale, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(10.dp))
            Text("“Abre Spotify”   ·   “¿Qué hora es?”\n“¿Cuánta batería tengo?”   ·   “Sube el volumen”", color = Color(0xFFB6C9DF), fontSize = 14.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(21.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HISTORIAL", color = Pale, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.weight(1f)); Icon(Icons.Default.VolumeUp, null, tint = Color(0xFF8EA8C5), modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(7.dp))
            if (entries.isEmpty()) Text("Tus comandos recientes aparecerán aquí.", color = Color(0xFF7892AF), fontSize = 13.sp)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries.take(5)) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10233B)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Tú: ${item.heard}", color = Color.White, fontSize = 13.sp)
                            Text("Tony: ${item.reply}", color = Color(0xFFAFC3DA), fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("El reconocimiento usa el servicio de voz del teléfono. La app deja de escuchar al salir de ella.", color = Color(0xFF67819D), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
        }
    }
}
