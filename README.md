# AA Directorio Bogotá (Android)

App Android **sencilla y liviana** que muestra únicamente el **directorio de grupos de AA Bogotá**
dentro de un WebView y abre fuera de la app los enlaces a **Google Maps, WhatsApp, Teléfono, Zoom/Meet y YouTube**.

- URL cargada: `https://www.aabogota.com/p/reuniones-virtuales-grupos-aa-bogota.html`
- Permite navegar solo dentro de `www.aabogota.com` (el resto abre en apps externas).
- Pull‑to‑refresh y barra de progreso.

## Requisitos
- Android Studio (Giraffe o superior).
- SDK Android 34 (se descarga automático al abrir).
- Min SDK 24 (Android 7.0).

## Ejecutar
1. Clona o descarga este repo y ábrelo en Android Studio (`File → Open…`).
2. Pulsa **Run ▶** para instalar en emulador o dispositivo.

## Generar APK
- `Build → Generate Signed Bundle / APK → APK` (elige **release** y tu keystore).
- El archivo queda en `app/build/outputs/apk/release/app-release.apk`.

## Personalización
- Cambia el nombre de la app en `app/src/main/res/values/strings.xml`.
- Cambia el color principal en `app/src/main/res/values/colors.xml`.
- Cambia la URL en `MainActivity.kt` (constante `HOME_URL`).

## Licencia
<img width="403" height="141" alt="by-nc-sa" src="https://github.com/user-attachments/assets/d496cf45-2674-44b6-8340-0af96f747d42" />
[Licencia](https://creativecommons.org/licenses/by-nc-sa/4.0/)
