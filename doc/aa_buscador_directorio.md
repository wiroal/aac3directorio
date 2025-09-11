----
		Tema: Manual buscador directorio de grupos
		Fecha:	8 de septiembre de 2025
		Ciudad: Bogotà D.C.
		Autor: William Rozo Alvarez

----



# Buscador AA Bogotá — Guía de instalación y operación

Este documento resume **lo que ya quedó funcionando** y **cómo mantenerlo**.

---

## 1) Arquitectura (qué hace cada parte)

- **API (Apps Script Web App)**: lee la hoja de cálculo **directamente** por `spreadsheetId + gid` y devuelve JSON.  
  - URL de la Web App (tu endpoint): **`URL GOOGLE CSV`**
  - Lee la pestaña de Google Sheets:
    - `spreadsheetId`: `ID GOOGLE`
    - `gid`: `4598****`
- **Front (código del blog)**: caja de búsqueda, tarjetas, mapa previo embebido y botón **IR** (que abre el **link exacto** que pusiste en la columna **UBICACION**).

---

## 2) Requisitos de la hoja

Encabezados recomendados (la API detecta variantes):

- `DISTRITO` (o `DIST`)
- `GRUPO`
- `DIRECCION` / `DIRECCIÓN` / `DIR`
- `REUNIONES`
- `NUMERO DE CONTACTO` / `NÚMERO DE CONTACTO` / `CONTACTO` / `TELEFONO` / `WHATSAPP`
- `UBICACION` / `UBICACIÓN` / `MAPA` / `LINK` / `URL`

> **Sugerencia:** En **UBICACION** coloca el link **completo** de Google Maps (por ejemplo `https://maps.app.goo.gl/...`).  
> - El **iframe previo** del mapa usa un **query** (dirección) y **no** intenta embeber el link corto; por eso **siempre se ve**.  
> - El botón **IR** usa **exactamente tu link** (lleva al pin correcto).

---

## 3) Despliegue / Cambios futuros

### 3.1 Cambiar de hoja o pestaña
Edita en el código de la API (Apps Script) solo esta parte:

a) Actualizaciones del CSV

b) Sube cambios por Drive → Gestionar versiones → Subir versión nueva (así mantienes el mismo ID).

c) La API ya detecta charset, pero si puedes exporta como CSV (UTF-8)

d) Si no actualiza, abre la API y crea una nueva versión de la implementación, a veces se pega en la versión anterio y no se ven los cambios 


```js
const SHEETS_SOURCE = {
  spreadsheetId: 'ESTA EN EL CSV',
  gid: 459***
};
```

> Guarda y **Deploy → Manage deployments → Edit → Deploy**.  
> Si el URL `/exec` cambia, actualiza `API_URL` en el blog.

### 3.2 Actualizar el front (blog)
En el HTML deja:
```js
const API_URL = 'URL IMPLEMENTACION';
```

---

## 4) Endpoints útiles de la API (diagnóstico)

- **Headers y mapeo**  
  `.../exec?debug=headers&nocache=1`

- **Ping**  
  `.../exec?ping=1`

- **Buscar por distrito**  
  - Formato: `D1`, `D2`, `D10`, etc.  
  - Ejemplo: `.../exec?q=D3&min=1&nocache=1`

- **Limpiar caché** (por si lo vuelves a activar)  
  `.../exec?clearcache=1`

> El front ya envía `nocache=1` en las búsquedas para evitar resultados viejos.

---

## 5) Cómo filtra la API

- Si la consulta parece un **distrito** (`D1`, `d1`, `D 1`, `Distrito 1`), filtra por distrito.  
- Si no, busca coincidencias en **GRUPO, DIRECCION, DISTRITO, REUNIONES, CONTACTO, UBICACION** (insensible a tildes y mayúsculas).

---

## 6) Mapa previo vs Botón **IR**

- **Mapa previo (iframe)**: usa **query** (la dirección en texto) o `?query=`/`?q=` si viene en un link largo de Google Maps. **Nunca** intenta embeber `maps.app.goo.gl` directamente (eso rompe).  
- **IR**: abre **exactamente** el link de **UBICACION** que pusiste en el CSV/Sheet (ideal cuando ya validaste el pin correcto).

---

## 7) Errores comunes

1. **Abre otra hoja al “Abrir hoja” del proyecto** → Ese era un proyecto **vinculado** a otra spreadsheet. Solución: usar Web App **independiente** y leer por `spreadsheetId + gid` (lo que ya quedó).  
2. **No encuentra por distrito** → Asegúrate de escribir `D1` (no solo `1`).  
3. **El mapa previo sale “No se ha podido mostrar…”** → Se estaba intentando embeber una URL corta. Ya quedó resuelto: el iframe usa **query** de dirección, y **IR** usa tu link.

---

## 8) Parámetros importantes en el front

- `MIN_CHARS = 2`: mínimo de letras para empezar a buscar.  
- `FORCE_NOCACHE = true`: fuerza `nocache=1` en cada búsqueda.  
- Debounce de 200 ms para no saturar la API.

---

## 9) Seguridad / Privacidad

- La Web App corre **como tú** (Execute as: *Me*). El front accede solo al endpoint `/exec`.  
- No se exponen datos completos si el usuario no escribe (la API devuelve `results: []` cuando no se cumple `MIN_CHARS`).

---

## 10) Mantenimiento

- Puedes activar caché del Script cambiando `CACHE_MINUTES` en la API (recomendado cuando estabilices los datos).  
- Para ver qué columnas mapea, usa `?debug=headers`.  
- Para añadir nuevas columnas, solo agrega el **header** y extiende el mapeo si quieres devolverla en el JSON.
---

# A) API (Apps Script) — código completo

Pégalo entero en tu proyecto de Apps Script (Web App). Ya apunta a tu hoja correcta `(spreadsheetId + gid)`. Luego *Deploy → Web app*.

```js
/************** CONFIG **************/
const CSV_FILE_ID = 'Extraer del archivo base csv'; // ID del CSV en Drive
const MAX_RESULTS = 100;
const SEARCH_IN   = ['grupo','direccion','distrito','reuniones','contacto']; // campos para buscar
const API_VERSION = '2025-09-10_aa_csv_utf_fix_v1';
/************ FIN CONFIG ************/

/* ========== Helpers de normalización ========== */
const normKey = s => (s||'').toString()
  .normalize('NFD').replace(/\p{Diacritic}/gu,'')
  .toLowerCase().replace(/[^a-z0-9\s]/g,' ').replace(/\s+/g,' ').trim();

const norm = s => (s||'').toString()
  .normalize('NFD').replace(/\p{Diacritic}/gu,'')
  .toLowerCase().trim();

/* ========== Lectura de CSV con detección de charset ========== */
// Intenta varios charsets y se queda con el que tenga menos caracteres “�”
function decodeBlobText_(blob){
  const charsets = ['UTF-8', 'windows-1252', 'ISO-8859-1'];
  let best = { text: '', charset: 'UTF-8', score: 1e9 };
  for (const cs of charsets){
    try{
      const t = blob.getDataAsString(cs);
      const bad = (t.match(/\uFFFD/g) || []).length; // “carácter de reemplazo”
      if (bad < best.score) best = { text: t, charset: cs, score: bad };
    }catch(err){ /* ignora charset no soportado */ }
  }
  return best; // -> {text, charset, score}
}

// Lee SIEMPRE la versión vigente del CSV (misma ID) y devuelve texto limpio
function csvMeta_(fileId){
  const file = DriveApp.getFileById(fileId);
  const blob = file.getBlob();
  const dec  = decodeBlobText_(blob);
  // quita posible BOM
  let text = dec.text.replace(/^\uFEFF/, '');
  return {
    file,
    blob,
    text,
    charset: dec.charset,
    lastUpdated: file.getLastUpdated(),
    size: blob.getBytes().length
  };
}

/* ========== Utilidades de CSV ========== */
// Detecta separador ; , o tab
function guessSep(text){
  const head = text.split('\n').slice(0,5).join('\n');
  const counts = {
    ';' : (head.match(/;/g)  || []).length,
    ',' : (head.match(/,/g)  || []).length,
    '\t': (head.match(/\t/g) || []).length
  };
  return Object.entries(counts).sort((a,b)=>b[1]-a[1])[0][0] || ',';
}

// Parser CSV simple (comillas y separador configurable)
function parseCSV(text, sep){
  const rows = [];
  let row = [], cur = '', inQuotes = false;
  for (let i=0; i<text.length; i++){
    const ch = text[i], next = text[i+1];
    if (inQuotes){
      if (ch === '"'){
        if (next === '"'){ cur += '"'; i++; } else { inQuotes = false; }
      } else { cur += ch; }
    } else {
      if (ch === '"'){ inQuotes = true; }
      else if (ch === sep){ row.push(cur); cur=''; }
      else if (ch === '\r'){ /* skip */ }
      else if (ch === '\n'){ row.push(cur); rows.push(row); row=[]; cur=''; }
      else { cur += ch; }
    }
  }
  row.push(cur); rows.push(row);
  while (rows.length && rows[rows.length-1].every(c => !String(c||'').trim())) rows.pop();
  // quita BOM en el primer campo de la primera fila
  if (rows.length && rows[0].length) rows[0][0] = String(rows[0][0]).replace(/^\uFEFF/, '');
  return rows;
}

// Mapea encabezados -> índices (acepta variantes/español)
function mapHeaders(headers){
  const H = headers.map(normKey);
  const pick = (...aliases) => H.findIndex(h => aliases.map(normKey).includes(h));
  return {
    distrito : pick('distrito','dist'),
    grupo    : pick('grupo','nombre','nombre del grupo'),
    direccion: pick('direccion','dirección','dir','direc','address'),
    reuniones: pick('reuniones','reunion','reunión','horarios','calendario','dias','días'),
    contacto : pick('numero de contacto','número de contacto','contacto','telefono','teléfono','celular','whatsapp','wa'),
    ubicacion: pick('maps','mapa','ubicacion','ubicación','google maps','url mapa','link mapa')
  };
}

function json(obj){
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

/* ========== Web App ========== */
function doGet(e){
  try{
    const q        = norm(e?.parameter?.q || '');
    const minChars = parseInt(e?.parameter?.min || '2', 10);

    // 1) Lee la versión actual del CSV
    const meta = csvMeta_(CSV_FILE_ID);

    // ---- Endpoints de diagnóstico ----
    if (e?.parameter?.debug === 'meta'){
      return json({
        ok: true,
        apiVersion: API_VERSION,
        fileLastUpdated: meta.lastUpdated,
        sizeBytes: meta.size,
        charsetUsed: meta.charset
      });
    }
    if (e?.parameter?.debug === 'sample'){
      const sepS  = guessSep(meta.text);
      const rowsS = parseCSV(meta.text, sepS);
      return json({
        ok: true,
        apiVersion: API_VERSION,
        sep: sepS,
        rows: rowsS.length,
        headers: rowsS[0],
        firstDataRow: rowsS[1] || null,
        charsetUsed: meta.charset
      });
    }

    // 2) Parsea CSV y mapea columnas
    const sep  = guessSep(meta.text);
    const rows = parseCSV(meta.text, sep);
    if (!rows.length) return json({ ok:true, apiVersion: API_VERSION, total:0, results:[] });

    const headers = rows[0].map(v => (v||'').toString());
    const map     = mapHeaders(headers);

    // 3) Convierte filas en objetos homogéneos
    const data = [];
    for (let i=1; i<rows.length; i++){
      const r = rows[i]; if (!r) continue;
      const val = (idx)=> idx>=0 && idx<r.length ? String(r[idx]||'').trim() : '';
      const o = {
        distrito : val(map.distrito),
        grupo    : val(map.grupo),
        direccion: val(map.direccion),
        reuniones: val(map.reuniones),
        contacto : val(map.contacto),
        ubicacion: val(map.ubicacion) // opcional
      };
      if (!Object.values(o).some(x => x)) continue; // descarta filas completamente vacías
      data.push(o);
    }

    // 4) Comportamiento cuando no hay término suficiente
    if (!q || q.length < minChars){
      return json({ ok:true, apiVersion: API_VERSION, total: data.length, results: [] });
    }

    // 5) Filtro insensible a tildes/mayúsculas
    const results = [];
    for (const o of data){
      const hit = SEARCH_IN.some(k => norm(o[k]).includes(q));
      if (hit){
        results.push(o);
        if (results.length >= MAX_RESULTS) break;
      }
    }

    return json({ ok:true, apiVersion: API_VERSION, total: data.length, results });

  }catch(err){
    return json({ ok:false, apiVersion: API_VERSION, error: 'Error: ' + err });
  }
}

```

# B) HTML del blog — código completo

Reemplaza el bloque actual por este. Ya incluye el arreglo del mapa previo y usa tu **API_URL** nueva.

```HTML
<!--Empieza el buscador-->


<!--PANEL con borde azul-->
<div class="aa-panel">
  <!--Buscador AA Bogotá – versión “bonita” con emojis-->
  <div class="aa-box">
    <div class="aa-search">
      <input autocomplete="off" class="aa-input" id="aa-q" placeholder="🔎 escribe al menos 2 letras (D1, grupo, barrio, dirección…)" type="search" />
      <button class="aa-clear" id="aa-clear" title="Limpiar">×</button>
    </div>
    <div class="aa-count" id="aa-count">Escribe para buscar.</div>
    <div class="aa-err" id="aa-err"></div>
    <div id="aa-results"></div>
  </div>

  <style>
    .aa-box{max-width:980px;margin:0 auto}
    .aa-search{position:relative}
    .aa-input{width:100%;padding:14px 44px 14px 14px;border:1px solid #e5e7eb;border-radius:16px;font-size:16px;outline:none;transition:.2s}
    .aa-input:focus{box-shadow:0 0 0 3px rgba(99,102,241,.15);border-color:#a5b4fc}
    .aa-clear{position:absolute;right:8px;top:50%;transform:translateY(-50%);border:1px solid #e5e7eb;background:#fff;border-radius:12px;padding:6px 10px;cursor:pointer;font-size:18px;line-height:1}
    .aa-count{font-size:13px;color:#475569;margin:8px 4px}
    .aa-err{display:none;margin:10px 0;padding:12px;border-radius:12px;background:#ffe8e8;border:1px solid #f2b1b1;color:#8a1c1c}
    .aa-card{border:1px solid #eef2f7;background:#fff;border-radius:18px;padding:14px 16px;margin:12px 0;box-shadow:0 2px 12px rgba(15,23,42,.04)}
    .aa-title{font-weight:800;letter-spacing:.2px;margin-bottom:6px}
    .aa-row{margin:4px 0;color:#0f172a}
    .aa-label{font-weight:700;color:#334155;margin-right:4px}
    .aa-chip{display:inline-block;border-radius:999px;padding:2px 10px;border:1px solid #e2e8f0;background:#f8fafc;font-size:12px}
    .aa-mapwrap{margin-top:10px}
    .aa-map{width:100%;height:200px;border:0;border-radius:12px}
    .aa-cta{margin-top:10px}
    .aa-ir{
      display:inline-flex;align-items:center;gap:6px;
      padding:8px 12px;border-radius:10px;
      border:1px solid #dbeafe;background:#eef6ff;
      text-decoration:none;font-weight:700;color:#0f172a
    }
    .aa-ir:hover{box-shadow:0 0 0 3px rgba(10,87,167,.15)}
    @media (prefers-color-scheme: dark){
      .aa-input,.aa-clear{background:#0b0c10;color:#e5e7eb;border-color:#263041}
      .aa-card{background:#0b0c10;border-color:#1e293b;box-shadow:0 1px 10px rgba(0,0,0,.25)}
      .aa-count{color:#94a3b8}
      .aa-label{color:#cbd5e1}
      .aa-ir{background:#0b0c10;color:#e5e7eb;border-color:#263041}
    }
  </style>

  <!-- Estilos extras para alinear IR + Compartir y el botón de WhatsApp -->
  <style>
    .aa-cta{display:flex;gap:8px;flex-wrap:wrap} /* ahora muestra los dos botones en fila */
    .aa-share{
      display:inline-flex;align-items:center;gap:6px;
      padding:8px 12px;border-radius:10px;
      border:1px solid #d1fae5;background:#ecfdf5;
      text-decoration:none;font-weight:700;color:#065f46;
    }
    .aa-share:hover{box-shadow:0 0 0 3px rgba(16,185,129,.15)}
  </style>
  
  <style>
  /* Poner los botones en fila */
  .aa-cta{display:flex; gap:8px; flex-wrap:wrap}

  /* Botón Compartir (WhatsApp) */
  .aa-share{
    display:inline-flex; align-items:center; gap:6px;
    padding:8px 12px; border-radius:10px;
    border:1px solid #d1fae5; background:#ecfdf5;
    text-decoration:none; font-weight:700; color:#065f46;
  }
  .aa-share:hover{ box-shadow:0 0 0 3px rgba(16,185,129,.15) }
</style>


  <!-- Script -->
  
  <script>
(function(){
  const API_URL = 'https://script.google.com******/exec';
  const MIN_CHARS = 2; // 1 si quieres con una sola letra

  // Emojis seguros (sin variation selector) generados por Unicode
  const E = {
    compass:  '\u{1F9ED}', // 🧭
    pin:      '\u{1F4CD}', // 📍
    map:      '\u{1F5FA}', // 🗺
    cal:      '\u{1F4C5}', // 📅
    phone:    '\u{1F4DE}'  // 📞  (evita ☎ + VS-16)
  };
  const EN_DASH = '\u2013'; // –

  document.addEventListener('DOMContentLoaded', function(){
    const $=s=>document.querySelector(s);
    const q=$('#aa-q'), clearBtn=$('#aa-clear'), cnt=$('#aa-count'), out=$('#aa-results'), err=$('#aa-err');
    if(!q||!cnt||!out) return;

    let deb=null, ctl=null, reqId=0, anim=null;

    const esc=s=>(s||'').replace(/[&<>"']/g,m=>({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[m]));
    const escAttr=s=>(s||'').replace(/"/g,'&quot;');

    function startLoading(){ stopLoading(); err.style.display='none'; let i=0; anim=setInterval(()=>{ cnt.textContent='Buscando grupos' + '.'.repeat((i++%3)+1); },320); }
    function stopLoading(){ if(anim){ clearInterval(anim); anim=null; } }

    function buildURL(term){
      return API_URL + (API_URL.includes('?')?'&':'?') +
             'q=' + encodeURIComponent(term) +
             '&min=' + MIN_CHARS +
             '&ts=' + Date.now();
    }

    async function buscar(term, signal){
      const r = await fetch(buildURL(term), { signal, cache:'no-store' });
      if(!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    }

    // Convierte teléfonos a <a href="tel:"> y corrige el último slice
    function linkifyContact(s){
      if(!s) return '';
      if(/^https?:/i.test(s)) return `<a href="${escAttr(s)}" target="_blank" rel="noopener">${esc(s)}</a>`;
      const re=/(\+?\d[\d\s().\-]{6,}\d)/g; let h='', last=0, m;
      while((m=re.exec(s))!==null){
        h += esc(s.slice(last, m.index));
        const raw=m[1].trim();
        const tel=raw.replace(/[^\d+]/g,'');
        h += `<a href="tel:${escAttr(tel)}">${esc(raw)}</a>`;
        last=m.index+m[1].length;
      }
      h += esc(s.slice(last));
      return h;
    }

    // Limpia entidades HTML y variation selectors (evita &#65039; etc.)
    function cleanSharePiece(s){
      return (s||'')
        .replace(/\uFE0F/g,'')         // quita VS-16
        .replace(/&#\d+;?/g,'')         // quita entidades numéricas
        .replace(/&[a-z]+;?/gi,'');     // quita entidades nombradas
    }

    const row=(e,l,h)=>`<div class="aa-row"><span class="aa-label">${e} ${l}:</span> ${(h&&String(h).trim())?h:'-'}</div>`;

    // Mapa por iframe y enlace para abrir Maps
    function embedURL(o){
      let q=''; const u=(o.ubicacion||'').trim();
      try{ if(u){ const url=new URL(u); if(url.hostname.includes('google.com')) q=url.searchParams.get('query')||url.searchParams.get('q')||''; } }catch{}
      if(!q){ const d=(o.direccion||'').trim(); q=d?`${d}, Bogotá, Colombia`:(o.grupo||'AA Bogotá'); }
      return 'https://www.google.com/maps?output=embed&q='+encodeURIComponent(q);
    }
    function mapClickURL(o){
      const u=(o.ubicacion||'').trim(); if(u && /^https?:/i.test(u)) return u;
      const d=(o.direccion||'').trim(); const q=d?`${d}, Bogotá, Colombia`:(o.grupo||'AA Bogotá');
      return 'https://www.google.com/maps/search/?api=1&query='+encodeURIComponent(q);
    }

    // === Compartir por WhatsApp (bonito y sin entidades) ===
    function buildShareText(o, irURL){
      const L = [];
      L.push(`${E.compass} AA Bogotá ${EN_DASH} ${cleanSharePiece(o.grupo || '(Sin nombre)')}`);
      if (o.direccion) L.push(`${E.pin} Dirección: ${cleanSharePiece(o.direccion)}`);
      if (o.distrito)  L.push(`${E.map} Distrito: ${cleanSharePiece(o.distrito)}`);
      if (o.reuniones) L.push(`${E.cal} Reuniones: ${cleanSharePiece(o.reuniones)}`);
      if (o.contacto)  L.push(`${E.phone} Contacto: ${cleanSharePiece(o.contacto)}`);
      L.push(`${E.map} Mapa: ${irURL}`);
      return L.join('\n');
    }
    function buildWhatsAppURL(o, irURL){
      const txt = buildShareText(o, irURL);
      return 'https://wa.me/?text=' + encodeURIComponent(txt);
    }

    // Tarjeta + botones IR y Compartir
    function card(o){
      const contacto = linkifyContact(o.contacto);
      const embed = embedURL(o);
      const irURL = mapClickURL(o);
      const waURL = buildWhatsAppURL(o, irURL);

      return `<article class="aa-card">
        <div class="aa-title">🏷️ ${esc(o.grupo||'(Sin nombre)')}</div>
        ${row('🗺️','Distrito',  esc(o.distrito))}
        ${row('📍','Dirección', esc(o.direccion))}
        ${row('📅','Reuniones', esc(o.reuniones))}
        ${row('📞','Número de contacto', contacto)}
        <div class="aa-mapwrap"><iframe class="aa-map" src="${escAttr(embed)}" loading="lazy" referrerpolicy="no-referrer-when-downgrade" aria-label="Mapa de ubicación"></iframe></div>
        <div class="aa-cta">
          <a class="aa-ir" href="${escAttr(irURL)}" target="_blank" rel="noopener">🗺️ IR</a>
          <a class="aa-share" href="${escAttr(waURL)}" target="_blank" rel="noopener" aria-label="Compartir por WhatsApp">📤 Compartir</a>
        </div>
      </article>`;
    }

    function showNoResults(term){
      cnt.textContent = `No encontramos grupos para “${term}”`;
      err.style.display = 'none';
      out.innerHTML = '';
    }

    function render(term){
      clearTimeout(deb);
      if(term.length < MIN_CHARS){
        out.innerHTML=''; stopLoading(); cnt.textContent='Escribe para buscar.'; err.style.display='none'; return;
      }
      deb=setTimeout(async ()=>{
        const id=++reqId;
        if(ctl) ctl.abort();
        ctl = new AbortController();
        startLoading();
        try{
          const data = await buscar(term, ctl.signal);
          if(id!==reqId) return;
          if(!data || data.ok!==true) throw new Error('Respuesta inválida');
          const arr = data.results || [];
          stopLoading();
          if(!arr.length) { showNoResults(term); return; }
          out.innerHTML = arr.map(card).join('');
          cnt.textContent = `Resultados: ${arr.length}`;
          err.style.display='none';
        }catch(e){
          if (e.name === 'AbortError' || /aborted/i.test(e.message)) return;
          stopLoading();
          cnt.textContent = 'No pudimos consultar la base ahora. Toca para reintentar.';
          err.style.display='none';
          out.innerHTML='';
        }
      }, 220);
    }

    q.addEventListener('input', ()=>render(q.value.trim()));
    clearBtn.addEventListener('click', ()=>{ q.value=''; render(''); q.focus(); });
  });
})();
</script>

  
  
</div>

<style>
  :root{ --aa-blue:#0A57A7; }
  .aa-panel{
    max-width:1040px; margin:20px auto; padding:16px;
    border:3px solid var(--aa-blue); border-radius:20px;
    background: linear-gradient(180deg,#f6faff, #ffffff);
    box-shadow:0 10px 24px rgba(10,87,167,.08);
  }
  .aa-panel .aa-input:focus{ border-color:var(--aa-blue)!important; box-shadow:0 0 0 4px rgba(10,87,167,.15)!important; }
  .aa-panel .aa-card{ border-color:#dbeafe; }
  .aa-panel .aa-clear{ border-color:#dbeafe; }
  .aa-panel .aa-fullbtn{
    display:inline-flex; align-items:center; gap:8px;
    padding:12px 16px; border-radius:12px;
    border:1px solid #dbeafe; background:#f8fbff;
    text-decoration:none; font-weight:700; color:#0f172a;
    box-shadow:0 2px 10px rgba(15,23,42,.05)
  }
  .aa-panel .aa-fullbtn:hover{ box-shadow:0 0 0 3px rgba(10,87,167,.15); }
  .aa-footer{margin:16px 0 0; text-align:center}
  @media (prefers-color-scheme: dark){
    .aa-panel{ background:#0b0c10; border-color:#1e90ff; box-shadow:0 10px 24px rgba(0,0,0,.3); }
    .aa-panel .aa-fullbtn{ background:#0b0c10; color:#e5e7eb; border-color:#263041; }
  }
</style>

<!--estilo fondo blanco (modo claro forzado dentro del panel)-->
<style>
  .aa-panel, .aa-panel * { color-scheme: light !important; }
  .aa-panel,
  .aa-panel .aa-card,
  .aa-panel .aa-input,
  .aa-panel .aa-clear,
  .aa-panel .aa-fullbtn,
  .aa-panel #aa-results,
  .aa-panel #aa-err {
    background-color: #fff !important;
    color: #0f172a !important;
    border-color: #e5e7eb !important;
  }
  .aa-panel .aa-input::placeholder { color: #6b7280 !important; }
  .aa-panel input:-webkit-autofill {
    -webkit-text-fill-color: #0f172a !important;
    -webkit-box-shadow: 0 0 0px 1000px #fff inset !important;
    box-shadow: 0 0 0px 1000px #fff inset !important;
    caret-color: #0f172a !important;
  }
  .aa-panel .aa-clear { background:#fff !important; }
  .aa-panel .aa-card  { border:1px solid #e5e7eb !important; box-shadow: 0 2px 12px rgba(15,23,42,.04) !important; }
  .aa-panel #aa-err { background:#ffe8e8 !important; border-color:#f2b1b1 !important; color:#8a1c1c !important; }
  .aa-panel iframe { filter: none !important; }
</style>


  <!--Botón: Ir al directorio completo-->
  <div class="aa-footer">
    <a class="aa-fullbtn" href="https://www.aabogota.com/p/reuniones-virtuales-grupos-aa-bogota.html" rel="noopener" target="_blank">
      📚 Ir al directorio completo
    </a>
  </div>
</div>






```

#done
