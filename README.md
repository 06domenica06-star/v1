# HEY TONY — proyecto Android inicial

Aplicación nativa Android en Kotlin y Jetpack Compose. Permite iniciar el reconocimiento de voz tocando el botón, interpretar un conjunto inicial de comandos, abrir algunas aplicaciones y responder mediante la voz del sistema.

## Requisitos

- Android Studio reciente
- JDK 17
- Android SDK 35
- Teléfono Android 8.0 (API 26) o posterior para instalar

Abre esta carpeta en Android Studio, espera la sincronización de Gradle y ejecuta la configuración `app` en un teléfono. Al tocar **Hablar con Tony**, acepta el permiso de micrófono y habla en español.

## Comandos implementados

- Abrir Spotify, YouTube, WhatsApp, Instagram o Chrome (si están instalados)
- Abrir Ajustes o Cámara
- Consultar hora, fecha y nivel de batería
- Subir o bajar volumen multimedia

Los demás comandos muestran una respuesta orientativa y no ejecutan todavía llamadas, control multimedia o linterna.

## Alcance y límites

Esta versión no incluye detección de “Hey Tony” en segundo plano, pantalla bloqueada ni micrófono permanente. Android reserva la detección DSP y la integración completa de asistentes para servicios y hardware compatibles; una app normal no puede garantizar esa función en todos los teléfonos. La escucha actual comienza únicamente tras tocar el botón y usa el servicio de reconocimiento que provea el dispositivo, que puede requerir conexión a Internet. Las respuestas habladas usan TextToSpeech del sistema. No se guardan grabaciones.

No se pudo generar un APK en este entorno porque no hay Android SDK ni Gradle instalados. El proyecto se puede abrir y compilar desde Android Studio con los requisitos anteriores.

## Compilar el APK con GitHub Actions

1. Crea un repositorio en GitHub.
2. Descomprime el ZIP y sube todo el contenido de esta carpeta a la raíz del repositorio (incluida la carpeta oculta `.github`).
3. Al subir los archivos a `main` o `master`, GitHub ejecutará **Build Android APK**. También puedes iniciarlo desde **Actions → Build Android APK → Run workflow**.
4. Cuando termine, abre la ejecución y descarga el artefacto **HEY_TONY-debug-APK**. Dentro encontrarás `app-debug.apk`.

El APK es de depuración para instalar y probar; no es un paquete firmado para publicar en Google Play.
