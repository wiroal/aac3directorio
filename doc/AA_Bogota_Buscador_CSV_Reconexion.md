# Buscador AA Bogotá — Guía rápida para reconectar la base (CSV/Sheets)

**Fecha:** 2025-11-29  
**Autor:** WIROAL  
**Propósito:** Paso a paso para cambiar el archivo de base de datos (CSV o Google Sheets) del buscador y verificar que todo siga funcionando.

---

## 1) Cómo obtener el `FILE_ID` (ejemplo real)

Link de Google Drive (tipo archivo):
```
https://drive.google.com/file/d/1xE_KQLO7hEDHDGhnYU0mnxG2tMA-Oo_R/view?usp=drive_link
```
**FILE_ID** = lo que está **entre** `/d/` y `/view`:

```
1xE_KQLO7hEDHDGhnYU0mnxG2tMA-Oo_R
```

Otros casos:
- **Google Sheets/Docs:** `/d/ID/edit` → el ID va entre `/d/` y `/edit`.
- **Atajo universal:** busca el primer `/d/` y toma todo lo que sigue hasta el próximo `/`.

Verificación rápida:
```
https://drive.google.com/file/d/FILE_ID/view
```

---

## 2) Opción A (recomendada): solo cambiar **front** (sin redeploy)

En tu HTML del blog, asegura que el fetch **incluye** `file=<FILE_ID>` y un rompe-caché.  
Cambia **solo** esta constante:

```html
<script>
(function(){
  const API_URL = 'https://script.google.com/macros/s/AKfycbx2RypJ1kwXfKMuX8_c0Xka5YHJQUPDQZ2_fX-stgZlmFzCtHAsJTFqw9o2kwZe6Zaf/exec';
  const CSV_ID  = '1xE_KQLO7hEDHDGhnYU0mnxG2tMA-Oo_R'; // <--- NUEVO
  const MIN_CHARS = 2;

  function buildURL(term){
    const p = new URLSearchParams({
      q: term,
      min: String(MIN_CHARS),
      file: CSV_ID,        // fuerza el archivo nuevo
      v: Date.now()+''     // rompe caché del navegador/CDN
    });
    return API_URL + '?' + p.toString();
  }

  // ... resto del script (buscar/render) sin cambios ...
})();
</script>
```

**Ventaja:** no necesitas tocar el Apps Script ni publicar nueva versión.

---

## 3) Opción B: actualizar **backend** (Apps Script)

En `Code.gs`, cambia el bloque de configuración y **sube la versión** (para identificar builds):

```js
/************** CONFIG **************/
const CSV_FILE_ID = '1xE_KQLO7hEDHDGhnYU0mnxG2tMA-Oo_R'; // <--- NUEVO
const MAX_RESULTS = 100;
const SEARCH_IN   = ['grupo','direccion','distrito','reuniones','contacto'];
const API_VERSION = '2025-11-29'; // <--- actualiza (fecha sugerida)
/************ FIN CONFIG ************/
```

Luego:
1. **Ejecutar → doGet** (acepta permisos si los pide).
2. **Implementar → Gestionar implementaciones → Nueva versión**.
3. Acceso: **Cualquiera** (público).
4. Copia la URL `.../exec` si cambió. (Si mantienes la misma implementación, no cambia).

---

## 4) Pruebas rápidas (desde el móvil o incógnito)

Reemplaza `<EXEC_URL>` por tu URL de Apps Script:

- **Meta (lee archivo y charset):**
```
<EXEC_URL>?debug=meta&file=1xE_KQLO7hEDHDGhnYU0mnxG2tMA-Oo_R
```
Deberías ver: `"ok":true`, `"sizeBytes">0`, `"apiVersion":"2025-11-29"` y `charsetUsed`.

- **Muestra (encabezados y 1ª fila):**
```
<EXEC_URL>?debug=sample&file=1xE_KQLO7hEDHDGhnYU0mnxG2tMA-Oo_R
```

- **Búsqueda controlada:**
```
<EXEC_URL>?q=decima&min=1&file=1xE_KQLO7hEDHDGhnYU0mnxG2tMA-Oo_R
```

> Si estos 3 funcionan, el backend **sí está leyendo** la base. Si la página no busca, es **caché del front** o un error de URL/ID en el HTML.

---

## 5) Checklist cuando “no conecta”

- **401/403** en consola: la implementación no es pública → en Apps Script, “Cualquiera”.
- **`ok:true` pero `results:[]`**: quizá `min` demasiado alto; prueba `?q=a&min=1&file=ID`.  
  Verifica encabezados: `DISTRITO|GRUPO|DIRECCION|REUNIONES|NUMERO DE CONTACTO|UBICACION`.
- **Caracteres raros (tildes/ñ)**: re-exporta el CSV en **UTF-8** (el backend intenta `UTF-8/win-1252/ISO-8859-1`).
- **Mapas**: si `UBICACION` está vacío, se construye con `DIRECCION`; si pones links de Google Maps tipo `/embed` o `/place/`, el backend los normaliza a `/search?query=...` (mejor en móviles).
- **Sigue viejo**: recarga en incógnito o usa Ctrl+F5. El front añade `v=Date.now()` en cada request.

---

## 6) Buenas prácticas para el CSV

- Encabezados limpios:  
  `DISTRITO, GRUPO, DIRECCION, REUNIONES, NUMERO DE CONTACTO, UBICACION`
- Evita filas completamente vacías al final.
- Exporta en **UTF-8** si puedes.
- No mezcles separadores; el backend autodetecta `; , o tab`.
- Mantén direcciones en formato corto para Maps (ej.: `Calle 63A # 24-81`).

---

## 7) Snippets útiles

**Extraer ID con JavaScript:**
```js
function getFileIdFromUrl(url){
  const m = String(url).match(/\/d\/([a-zA-Z0-9_-]+)/);
  return m ? m[1] : null;
}
```

**Armado directo de WhatsApp (por si quieres otro formato):**
```js
function waUrl(text){
  return 'https://wa.me/?text=' + encodeURIComponent(text);
}
```

**Abrir Maps con dirección si no hay `UBICACION`:**
```js
function mapsSearchUrl(dir){
  return 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(dir + ', Bogotá, Colombia');
}
```

---

## 8) Mini recordatorio pegable (comentado) para tu HTML

```html
<!--
[Checklist cambio CSV]
1) Sacar FILE_ID del nuevo Drive link (entre /d/ y /view).
2) Front: actualizar const CSV_ID = 'FILE_ID' y mantener v=Date.now().
3) (Opcional) Backend: cambiar CSV_FILE_ID y subir API_VERSION + nueva implementación pública.
4) Probar:
   ?debug=meta&file=FILE_ID
   ?debug=sample&file=FILE_ID
   ?q=decima&min=1&file=FILE_ID
5) Si no refresca: incógnito / Ctrl+F5.
-->
```

---

## 9) Notas de seguridad y permisos

- La Web App debe estar publicada con acceso **“Cualquiera”** para que los usuarios anónimos (página pública) puedan consultar.
- El archivo en Drive debe ser **accesible** por la cuenta bajo la cual corre el Apps Script (o ser público si así lo decides).
- Evita exponer datos sensibles en el CSV (números personales si no han dado consentimiento expreso).
