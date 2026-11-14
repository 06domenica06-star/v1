package com.heytony.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Locale

private val Navy = Color(0xFF071426)
private val Blue = Color(0xFF2387FF)
private val Pale = Color(0xFFEAF3FF)

data class LogEntry(val heard: String, val reply: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HeyTonyScreen(this) }
    }
}

@Composable
private fun HeyTonyScreen(activity: MainActivity) {
    val prefs = remember { activity.getSharedPreferences(TonyAssistantService.PREFS, android.content.Context.MODE_PRIVATE) }
    var running by remember { mutableStateOf(prefs.getBoolean(TonyAssistantService.KEY_RUNNING, false)) }
    var listening by remember { mutableStateOf(prefs.getBoolean(TonyAssistantService.KEY_LISTENING, false)) }
    var status by remember { mutableStateOf(prefs.getString(TonyAssistantService.KEY_STATUS, "Listo para iniciar") ?: "Listo para iniciar") }
    var lastHeard by remember { mutableStateOf(prefs.getString(TonyAssistantService.KEY_HEARD, "Abre HEY TONY y habla; el asistente seguirá activo en segundo plano.") ?: "Abre HEY TONY y habla; el asistente seguirá activo en segundo plano.") }
    val history = remember { mutableStateListOf<LogEntry>() }
    var lastHistoryPhrase by remember { mutableStateOf("") }

    fun startService() {
        prefs.edit().putBoolean(TonyAssistantService.KEY_USER_STOPPED, false).apply()
        val serviceIntent = Intent(activity, TonyAssistantService::class.java).setAction(TonyAssistantService.ACTION_START)
        ContextCompat.startForegroundService(activity, serviceIntent)
        status = "Iniciando asistente manos libres…"
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val audioAllowed = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (audioAllowed) startService()
        else status = "Permite el micrófono en los ajustes de Android para usar el asistente."
    }

    fun requestPermissionOrStart() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) startService() else permissionLauncher.launch(needed.toTypedArray())
    }

    LaunchedEffect(Unit) {
        if (!prefs.getBoolean(TonyAssistantService.KEY_USER_STOPPED, false)) {
            requestPermissionOrStart()
        }
        while (true) {
            running = prefs.getBoolean(TonyAssistantService.KEY_RUNNING, false)
            listening = prefs.getBoolean(TonyAssistantService.KEY_LISTENING, false)
            status = prefs.getString(TonyAssistantService.KEY_STATUS, status) ?: status
            val heard = prefs.getString(TonyAssistantService.KEY_HEARD, "") ?: ""
            val reply = prefs.getString(TonyAssistantService.KEY_REPLY, "") ?: ""
            if (heard.isNotBlank() && heard != lastHistoryPhrase) {
                lastHistoryPhrase = heard
                lastHeard = heard
                if (reply.isNotBlank()) history.add(0, LogEntry(heard, reply))
            }
            delay(450)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Navy, surface = Color(0xFF10233B))) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF08162A), Color(0xFF0B2341), Navy))).padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SettingsVoice, null, tint = Blue, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("HEY TONY", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text("v1.2", color = Color(0xFF9BB5D1), fontSize = 13.sp)
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
                    Text(if (listening) "Te escucho; puedes hablar" else status, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(7.dp))
                    Text(lastHeard, color = Color(0xFFAFC3DA), fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (listening) "MICRÓFONO ACTIVO EN SEGUNDO PLANO" else if (running) "ASISTENTE ACTIVO" else "ASISTENTE DETENIDO",
                        color = if (listening) Color(0xFF80C7FF) else Color(0xFF8EA8C5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (running) {
                                prefs.edit().putBoolean(TonyAssistantService.KEY_USER_STOPPED, true).apply()
                                activity.startService(Intent(activity, TonyAssistantService::class.java).setAction(TonyAssistantService.ACTION_STOP))
                                status = "Deteniendo asistente…"
                            } else requestPermissionOrStart()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (running) Color(0xFF25415E) else Blue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text(if (running) "Detener asistente" else "Iniciar manos libres", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp))
                    Text("Se mantiene activo con una notificación visible. Android o el ahorro de batería pueden limitarlo en algunos teléfonos.", color = Color(0xFF8EA8C5), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("COMANDOS DE VOZ", color = Pale, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Abre apps · hora, fecha y batería · volumen y música · alarmas y temporizadores · linterna · búsquedas y mapas", color = Color(0xFFB6C9DF), fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HISTORIAL", color = Pale, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.weight(1f)); Icon(Icons.Default.VolumeUp, null, tint = Color(0xFF8EA8C5), modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(7.dp))
            if (history.isEmpty()) Text("Tus comandos recientes aparecerán aquí.", color = Color(0xFF7892AF), fontSize = 13.sp)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history.take(5)) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10233B)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Tú: ${item.heard}", color = Color.White, fontSize = 13.sp)
                            Text("Tony: ${item.reply}", color = Color(0xFFAFC3DA), fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Tony escucha los comandos mientras está activo. Usa la notificación o el botón para detenerlo.", color = Color(0xFF67819D), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
        }
    }
}
