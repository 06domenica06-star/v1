package com.heytony.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    private var onRecognized: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { HeyTonyScreen(this) }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale("es", "MX")
    }

    fun listen(onResult: (String) -> Unit, onFailure: (String) -> Unit) {
        onRecognized = onResult
        onError = onFailure
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onFailure("Este teléfono no tiene un servicio de reconocimiento de voz disponible.")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Di un comando para HEY TONY")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try { startActivityForResult(intent, 4201) }
        catch (_: ActivityNotFoundException) { onFailure("No se encontró una aplicación de reconocimiento de voz.") }
    }

    @Deprecated("Uses the system speech UI for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 4201) return
        val phrase = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (resultCode == RESULT_OK && !phrase.isNullOrBlank()) onRecognized?.invoke(phrase)
        else onError?.invoke("No logré entender el comando. Inténtalo otra vez.")
    }

    fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hey-tony-reply")
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
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }
}

@Composable
private fun HeyTonyScreen(activity: MainActivity) {
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Listo para ayudarte") }
    var lastHeard by remember { mutableStateOf("Toca el micrófono y di un comando") }
    val entries = remember { mutableStateListOf<LogEntry>() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening(activity, { listening = it }, { status = it }, { heard, reply ->
            lastHeard = heard; entries.add(0, LogEntry(heard, reply)); status = reply; activity.speak(reply)
        }) else status = "Necesito permiso para usar el micrófono cuando tú inicies un comando."
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
                    Button(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }, enabled = !listening, colors = ButtonDefaults.buttonColors(containerColor = Blue), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Default.Mic, null); Spacer(Modifier.width(9.dp)); Text(if (listening) "Escuchando…" else "Hablar con Tony", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("La escucha se inicia solo cuando tocas el botón.", color = Color(0xFF8EA8C5), fontSize = 12.sp, textAlign = TextAlign.Center)
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
            Text("El reconocimiento puede usar el servicio de voz instalado en tu teléfono.", color = Color(0xFF67819D), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
        }
    }
}

private fun startListening(
    activity: MainActivity,
    setListening: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    onCommand: (String, String) -> Unit
) {
    setListening(true); setStatus("Te escucho…")
    activity.listen({ heard ->
        val reply = activity.runCommand(heard)
        setListening(false); onCommand(heard, reply)
    }, { error -> setListening(false); setStatus(error) })
}
