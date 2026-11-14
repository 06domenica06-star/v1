package com.heytony.app

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.media.AudioManager
import android.media.AudioManager.*
import android.net.Uri
import android.provider.MediaStore
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VoiceCommandProcessor {
    const val STOP_SENTINEL = "__STOP_HEY_TONY__"

    private val apps = listOf(
        Triple("spotify", "Spotify", "com.spotify.music"),
        Triple("youtube", "YouTube", "com.google.android.youtube"),
        Triple("whatsapp", "WhatsApp", "com.whatsapp"),
        Triple("instagram", "Instagram", "com.instagram.android"),
        Triple("chrome", "Chrome", "com.android.chrome"),
        Triple("gmail", "Gmail", "com.google.android.gm"),
        Triple("google maps", "Google Maps", "com.google.android.apps.maps"),
        Triple("maps", "Google Maps", "com.google.android.apps.maps"),
        Triple("tiktok", "TikTok", "com.zhiliaoapp.musically"),
        Triple("telegram", "Telegram", "org.telegram.messenger"),
        Triple("facebook", "Facebook", "com.facebook.katana")
    )

    fun execute(context: Context, raw: String): String {
        val command = raw.lowercase(Locale("es", "PE"))
            .replace("hey tony", "")
            .replace("oye tony", "")
            .replace("hola tony", "")
            .trim()

        if (command.contains("detén el asistente") || command.contains("deten el asistente") ||
            command.contains("apaga el asistente") || command.contains("para de escuchar")) return STOP_SENTINEL

        if (command.contains("qué hora") || command.contains("que hora") || command == "la hora") {
            return "Son las ${SimpleDateFormat("h:mm a", Locale("es", "PE")).format(Date())}."
        }
        if (command.contains("qué fecha") || command.contains("que fecha") || command.contains("qué día es") || command.contains("que dia es")) {
            return "Hoy es ${SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "PE")).format(Date())}."
        }
        if (command.contains("batería") || command.contains("bateria")) {
            val level = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return "La batería está al $level por ciento."
        }
        if (command.contains("qué puedes hacer") || command.contains("que puedes hacer") || command.contains("ayuda")) {
            return "Puedo abrir aplicaciones, decirte la hora, fecha y batería, controlar el volumen y la música, encender la linterna, abrir cámara, ajustes, contactos y mensajes, poner alarmas y temporizadores, buscar en internet y abrir mapas."
        }
        if (command.contains("cómo te llamas") || command.contains("como te llamas") || command.contains("quién eres") || command.contains("quien eres")) {
            return "Soy Tony, tu asistente de voz."
        }

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when {
            command.contains("sube el volumen") || command.contains("aumenta el volumen") || command.contains("más volumen") -> {
                audio.adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE, FLAG_SHOW_UI)
                return "Subí el volumen."
            }
            command.contains("baja el volumen") || command.contains("disminuye el volumen") || command.contains("menos volumen") -> {
                audio.adjustStreamVolume(STREAM_MUSIC, ADJUST_LOWER, FLAG_SHOW_UI)
                return "Bajé el volumen."
            }
            command.contains("silencia") || command.contains("silencio") || command.contains("quita el sonido") -> {
                audio.adjustStreamVolume(STREAM_MUSIC, ADJUST_MUTE, FLAG_SHOW_UI)
                return "Silencié el audio multimedia."
            }
            command.contains("activa el sonido") || command.contains("quita el silencio") -> {
                audio.adjustStreamVolume(STREAM_MUSIC, ADJUST_UNMUTE, FLAG_SHOW_UI)
                return "Activé el sonido multimedia."
            }
        }

        if (command.contains("linterna") || command.contains("flash")) {
            val enabled = command.contains("enciende") || command.contains("activa") || command.contains("prende")
            val disabled = command.contains("apaga") || command.contains("desactiva")
            if (!enabled && !disabled) return "Di enciende la linterna o apaga la linterna."
            return try {
                val camera = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = camera.cameraIdList.firstOrNull { id ->
                    camera.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return "Este teléfono no tiene flash disponible."
                camera.setTorchMode(cameraId, enabled)
                if (enabled) "Linterna encendida." else "Linterna apagada."
            } catch (_: SecurityException) {
                "Para usar la linterna, permite el permiso de cámara en los ajustes de HEY TONY."
            } catch (_: Exception) {
                "No pude cambiar la linterna en este teléfono."
            }
        }

        if (command.contains("pausa la música") || command == "pausa" || command.contains("pausa la musica") ||
            command.contains("continúa la música") || command.contains("continua la musica") || command.contains("reanuda la música")) {
            sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return "Pausé o reanudé la música."
        }
        if (command.contains("siguiente canción") || command.contains("siguiente cancion")) {
            sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_NEXT)
            return "Pasando a la siguiente canción."
        }
        if (command.contains("canción anterior") || command.contains("cancion anterior")) {
            sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            return "Volviendo a la canción anterior."
        }

        if (command.contains("wifi") || command.contains("wi-fi")) {
            val wifiSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            return launch(context, wifiSettings, "Abriendo los controles de Wi-Fi.")
        }
        if (command.contains("pon una alarma") || command.contains("programa una alarma") || command.contains("despiértame") || command.contains("despiertame")) {
            val match = Regex("(?:a las|para las)\\s+(\\d{1,2})(?:[:.](\\d{2}))?").find(command)
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (match != null) {
                intent.putExtra(AlarmClock.EXTRA_HOUR, match.groupValues[1].toInt().coerceIn(0, 23))
                intent.putExtra(AlarmClock.EXTRA_MINUTES, match.groupValues[2].ifBlank { "0" }.toInt().coerceIn(0, 59))
                intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Alarma de HEY TONY")
            }
            return launch(context, intent, "Abriendo la alarma para confirmar los detalles.")
        }
        if (command.contains("temporizador") || command.contains("timer")) {
            val seconds = Regex("(\\d+)\\s*(segundos?|minutos?|horas?)").find(command)
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (seconds != null) {
                val amount = seconds.groupValues[1].toIntOrNull() ?: 1
                val unit = seconds.groupValues[2]
                val duration = when {
                    unit.startsWith("hora") -> amount * 3600
                    unit.startsWith("minuto") -> amount * 60
                    else -> amount
                }
                intent.putExtra(AlarmClock.EXTRA_LENGTH, duration)
                intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Temporizador de HEY TONY")
            }
            return launch(context, intent, "Abriendo el temporizador.")
        }

        val openVerbs = listOf("abre", "abrir", "inicia", "lanza", "muestra")
        if (openVerbs.any(command::contains)) {
            val app = apps.firstOrNull { command.contains(it.first) }
            if (app != null) {
                if ((context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) {
                    return "Desbloquea el teléfono para abrir ${app.second}."
                }
                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.third)
                    ?: return "No encontré ${app.second} instalado."
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return launch(context, launchIntent, "Abriendo ${app.second}.")
            }
            if (command.contains("cámara") || command.contains("camara")) {
                return launch(context, Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Abriendo la cámara.")
            }
            if (command.contains("ajustes") || command.contains("configuración") || command.contains("configuracion")) {
                return launch(context, Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Abriendo los ajustes.")
            }
            if (command.contains("contactos")) {
                return launch(context, Intent(Intent.ACTION_VIEW).setData(android.provider.ContactsContract.Contacts.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Abriendo Contactos.")
            }
            if (command.contains("mensajes")) {
                return launch(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Abriendo Mensajes.")
            }
        }

        if (command.contains("busca en internet") || command.contains("busca en google") || command.startsWith("busca ")) {
            val query = command.substringAfter("busca").replace("en internet", "").replace("en google", "").trim()
            if (query.isBlank()) return "Dime qué quieres buscar."
            val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launch(context, intent, "Buscando $query.")
        }
        if (command.contains("mapa") || command.contains("navega a") || command.contains("cómo llego a") || command.contains("como llego a")) {
            val place = command.replace("abre el mapa de", "").replace("abre mapas", "")
                .replace("navega a", "").replace("cómo llego a", "").replace("como llego a", "")
                .replace("mapa", "").replace("mapas", "").trim()
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(place.ifBlank { "" })}")
            return launch(context, Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Abriendo el mapa${if (place.isNotBlank()) " de $place" else ""}.")
        }

        if (command.contains("llama") || command.contains("marca")) {
            val number = Regex("\\+?[0-9][0-9\\s-]{5,}").find(command)?.value?.replace(" ", "")?.replace("-", "")
            if (number != null) {
                return launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Abriendo el marcador para llamar.")
            }
            return "Para proteger tus contactos, abre Contactos y selecciona a quién llamar. También puedes decirme un número."
        }
        if (command.contains("envía un mensaje") || command.contains("manda un mensaje") || command.contains("envia un mensaje")) {
            return "Dime el número y el texto del mensaje; por ahora abriré Mensajes para que lo confirmes en pantalla."
        }

        if (command.contains("cómo estás") || command.contains("como estas")) return "Estoy listo para ayudarte."
        return "Aún no reconozco ese comando. Di qué puedes hacer para escuchar algunos ejemplos."
    }

    private fun sendMediaKey(audio: AudioManager, keyCode: Int) {
        val downTime = System.currentTimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(downTime, System.currentTimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun launch(context: Context, intent: Intent, response: String): String {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            response
        } catch (_: ActivityNotFoundException) {
            "No encontré una aplicación compatible con ese comando."
        } catch (_: SecurityException) {
            "Android bloqueó la apertura mientras HEY TONY estaba en segundo plano. Desbloquea el teléfono e inténtalo de nuevo."
        }
    }
}
