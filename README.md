# HEY TONY — asistente Android de voz (v1.3)

Proyecto nativo en Kotlin y Jetpack Compose. Al abrir HEY TONY se solicita el permiso de micrófono y se inicia un servicio de Android en primer plano. La notificación permanente indica que el micrófono está activo y permite detenerlo. El servicio puede continuar cuando la app queda en segundo plano o se quita de Recientes, sujeto a las restricciones del teléfono.

## Requisitos

- Android Studio reciente
- JDK 17
- Android SDK 35
- Android 8.0 (API 26) o posterior

Abre la carpeta en Android Studio o compílala con el flujo de GitHub Actions incluido. Al instalarla, concede el permiso de micrófono y, en Android 13 o posterior, permite las notificaciones para ver el control persistente.

## Comandos de voz incluidos

Prueba frases como estas mientras el indicador diga que Tony está escuchando:

- “Abre WhatsApp”, “abre Spotify” o “abre Google Maps”
- “¿Qué hora es?”, “¿qué fecha es hoy?” o “¿cuánta batería tengo?”
- “Sube el volumen”, “pausa la música” o “siguiente canción”
- “Enciende la linterna” o “abre los ajustes”
- “Pon una alarma para las 7” o “temporizador de 5 minutos”
- “Busca restaurantes en Lima” o “cómo llego a Machu Picchu”
- “Abre contactos”, “abre mensajes” o “llama al 999123456”
- “¿Qué puedes hacer?”

No hace falta decir “Hey Tony”. Esta versión escucha comandos continuamente mientras el servicio está activo; el micrófono se muestra mediante una notificación permanente. No incorpora una detección local de palabra de activación.

- Abrir Spotify, YouTube, WhatsApp, Instagram, Chrome, Gmail, Google Maps, TikTok, Telegram y Facebook, si están instalados
- Decir la hora, fecha y nivel de batería
- Subir, bajar, silenciar o activar el volumen multimedia
- Pausar o reanudar música, pasar a la siguiente canción o volver a la anterior
- Abrir la cámara, ajustes, contactos, mensajes y controles de Wi-Fi
- Encender o apagar la linterna (requiere permiso de cámara)
- Programar una alarma o temporizador
- Buscar en internet y abrir mapas de lugares
- Abrir el marcador con un número dictado; la llamada se confirma en pantalla
- Preguntar “¿qué puedes hacer?” para oír ejemplos

## Límites de Android

La escucha se mantiene mediante un servicio con notificación visible. Se inicia cuando abres la app y das permiso; puede seguir en segundo plano, pero Android y las optimizaciones de batería de cada fabricante pueden detenerla. No se garantiza que sobreviva a **Forzar detención**, reinicios del teléfono o todas las políticas de Samsung, Xiaomi y otros fabricantes.

Esta versión no tiene un detector de palabra clave “Hey Tony”: mientras el servicio está activo, el reconocedor del teléfono escucha frases y procesa los comandos que entiende. El reconocimiento puede necesitar internet y consume más batería que un detector de palabra clave de bajo consumo. Android también puede bloquear la apertura de otras aplicaciones desde segundo plano o con la pantalla bloqueada; las consultas habladas y acciones de sistema compatibles sí pueden responder sin mostrar una pantalla. No se envían grabaciones a un servidor propio ni se guardan audios.

## Obtener el APK con GitHub Actions

1. Sube el contenido del proyecto a la raíz del repositorio, incluida la carpeta `.github`.
2. El flujo **Build Android APK** se ejecuta al subir cambios a `main` o `master`; también puedes iniciarlo desde **Actions → Build Android APK → Run workflow**.
3. Descarga el artefacto **HEY_TONY-debug-APK** de una ejecución completada. Incluye `app-debug.apk`.

El APK es una compilación de depuración para instalar y probar, no un paquete firmado para Google Play.
