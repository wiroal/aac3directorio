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
    - `spreadsheetId`: `1d-r6f9cJGUHwJJE_83q8a8F-UBMuiYkNATSeQ5vSwmA`
    - `gid`: `45989746`
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
const API_URL = 'https://script.google.com/macros/s/AKfycbzanBe_LdN8Kyg7WeWW-613wh1J79VmWApP3pshmRZ8UOJs7YtcF-BIM_yCwBvab4ts/exec';
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
/** ========= CONFIG ========= **/
const MIN_CHARS     = 2;
const MAX_RESULTS   = 100;
const CACHE_MINUTES = 0; // 0 = sin caché mientras depuras

// <<< TU HOJA CORRECTA >>>
const SHEETS_SOURCE = {
  spreadsheetId: '1d-r6f9cJGUHwJJE_83q8a8F-UBMuiYkNATSeQ5vSwmA', // ID de la URL
  gid: 45989746                                            // GID de la URL
};
/** ========================== **/

// Normalización
const nkey = s => (s||'').toString()
  .normalize('NFD').replace(/\p{Diacritic}/gu,'')
  .toLowerCase().replace(/[^a-z0-9\s]/g,' ').replace(/\s+/g,' ').trim();
const ntxt = s => (s||'').toString().normalize('NFD').replace(/\p{Diacritic}/gu,'').toLowerCase().trim();

// Cabeceras
function findHeaderIndex(headers, variants){
  const H = headers.map(nkey);
  for (const v of variants){
    const idx = H.indexOf(nkey(v));
    if (idx >= 0) return idx;
  }
  return -1;
}

// Abrir pestaña por GID
function getSheetByGid_(ss, gid){
  const target = Number(gid);
  for (const sh of ss.getSheets()){
    if (sh.getSheetId() === target) return sh;
  }
  return null;
}

// Leer valores (sin CSV)
function readSheetValues_(){
  const ss = SpreadsheetApp.openById(SHEETS_SOURCE.spreadsheetId);
  const sh = getSheetByGid_(ss, SHEETS_SOURCE.gid);
  if (!sh) throw new Error('No se encontró la pestaña GID=' + SHEETS_SOURCE.gid);
  return sh.getDataRange().getDisplayValues();
}

// Cache simple
function getCache_(k){ if (!CACHE_MINUTES) return null;
  const r = CacheService.getScriptCache().get(k); return r ? JSON.parse(r) : null; }
function putCache_(k,o){ if (!CACHE_MINUTES) return;
  CacheService.getScriptCache().put(k, JSON.stringify(o), CACHE_MINUTES*60); }

// Helpers distrito
function normDistString(s){ const t=ntxt(s||'').replace(/\s+/g,''); return t.replace(/^distrito/,'d'); }
function isDistrictQuery(q){ return /^d\d+$/i.test(normDistString(q)); }

// API
function doGet(e){
  try{
    const q        = ntxt(e?.parameter?.q || '');
    const minChars = parseInt(e?.parameter?.min || String(MIN_CHARS), 10);
    const nocache  = String(e?.parameter?.nocache||'') === '1';

    if (String(e?.parameter?.clearcache||'')==='1'){
      CacheService.getScriptCache().remove('sheet_cache_v1');
      return json({ok:true, cleared:true});
    }

    let values = !nocache ? getCache_('sheet_cache_v1') : null;
    if (!values){ values = readSheetValues_(); putCache_('sheet_cache_v1', values); }
    if (!values || values.length < 2) return json({ok:true, total:0, results:[]});

    const headers = values[0].map(v => (v||'').toString().trim());
    const iDistrito = findHeaderIndex(headers, ['distrito','dist']);
    const iGrupo    = findHeaderIndex(headers, ['grupo','nombre','nombre del grupo']);
    const iDir      = findHeaderIndex(headers, ['direccion','dirección','dir']);
    const iReun     = findHeaderIndex(headers, ['reuniones','reunion','reunión','horarios','calendario','dias','días']);
    const iCont     = findHeaderIndex(headers, ['numero de contacto','número de contacto','contacto','telefono','teléfono','celular','whatsapp','wa']);
    const iUbi      = findHeaderIndex(headers, ['ubicacion','ubicación','mapa','link','url']);

    if (e?.parameter?.debug === 'headers')
      return json({ok:true, headers, map:{iDistrito,iGrupo,iDir,iReun,iCont,iUbi}});
    if (e?.parameter?.ping) return json({ok:true, ping:true});

    const rows = [];
    for (let r=1; r<values.length; r++){
      const row = values[r]||[];
      const distrito  = iDistrito>=0 ? (row[iDistrito]||'').toString().trim() : '';
      const grupo     = iGrupo   >=0 ? (row[iGrupo]   ||'').toString().trim() : '';
      const direccion = iDir     >=0 ? (row[iDir]     ||'').toString().trim() : '';
      const reuniones = iReun    >=0 ? (row[iReun]    ||'').toString().trim() : '';
      const contacto  = iCont    >=0 ? (row[iCont]    ||'').toString().trim() : '';
      let   ubicacion = iUbi     >=0 ? (row[iUbi]     ||'').toString().trim() : '';

      if (!ubicacion && direccion){
        ubicacion = 'https://www.google.com/maps/search/?api=1&query=' +
                    encodeURIComponent(direccion + ', Bogotá, Colombia');
      }
      if(!(distrito||grupo||direccion||reuniones||contacto||ubicacion)) continue;
      rows.push({ distrito, grupo, direccion, reuniones, contacto, ubicacion });
      if (rows.length > 5000) break;
    }

    if (!q || q.length < minChars) return json({ok:true, total:rows.length, results:[]});

    const results = [];
    if (isDistrictQuery(q)){
      const qd = normDistString(q), qnum = qd.replace(/^d/,'');
      for (const o of rows){
        const d = normDistString(o.distrito||''); const dnum = d.replace(/^d/,'');
        if (d===qd || dnum===qnum){ results.push(o); if (results.length>=MAX_RESULTS) break; }
      }
    } else {
      for (const o of rows){
        const hay = [o.grupo,o.direccion,o.distrito,o.reuniones,o.contacto,o.ubicacion]
          .some(v => ntxt(v).includes(q));
        if (hay){ results.push(o); if (results.length>=MAX_RESULTS) break; }
      }
    }

    return json({ok:true, total:rows.length, results});
  }catch(err){
    return json({ok:false, error:'Error: '+err.message});
  }
}

function json(obj){
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
```

# B) HTML del blog — código completo

Reemplaza el bloque actual por este. Ya incluye el arreglo del mapa previo y usa tu **API_URL** nueva.

```HTML
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

  <script>
  (function(){
    // URL de tu Web App
    const API_URL = 'https://script.google.com/macros/s/AKfycbzanBe_LdN8Kyg7WeWW-613wh1J79VmWApP3pshmRZ8UOJs7YtcF-BIM_yCwBvab4ts/exec';
    const MIN_CHARS = 2;
    const FORCE_NOCACHE = true;

    const $=s=>document.querySelector(s);
    const q=$('#aa-q'), clearBtn=$('#aa-clear'), cnt=$('#aa-count'), out=$('#aa-results'), err=$('#aa-err');
    let t=null, ctl=null;

    async function buscar(term){
      if(ctl) ctl.abort(); ctl=new AbortController();
      let url = API_URL + (API_URL.includes('?')?'&':'?')
              + 'q=' + encodeURIComponent(term)
              + '&min=' + MIN_CHARS
              + (FORCE_NOCACHE ? '&nocache=1' : '');
      const r = await fetch(url, {signal: ctl.signal, cache:'no-store'});
      if(!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    }

    const escapeHTML = s => (s||'').replace(/[&<>"']/g,m=>({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[m]));
    const escapeAttr = s => (s||'').replace(/"/g,'&quot;');

    function linkifyContact(s){
      if(!s) return '';
      if(/^https?:/i.test(s)) return `<a href="${escapeAttr(s)}" target="_blank" rel="noopener">${escapeHTML(s)}</a>`;
      const re=/(\+?\d[\d\s().\-]{6,}\d)/g;
      let out='', last=0, m;
      while((m=re.exec(s))!==null){
        out += escapeHTML(s.slice(last,m.index));
        const raw = m[1].trim();
        const tel = raw.replace(/[^\d+]/g,'');
        out += `<a href="tel:${escapeAttr(tel)}">${escapeHTML(raw)}</a>`;
        last = m.index + m[1].length;
      }
      out += escapeHTML(s.slice(last));
      return out;
    }

    function fieldRow(emoji,label,html){
      const val = (html && String(html).trim()) ? html : '-';
      return `<div class="aa-row"><span class="aa-label">${emoji} ${label}:</span> ${val}</div>`;
    }

    // --- IFRAME: usa 'query' o 'q' de la URL larga; si no, usa la dirección ---
    function buildEmbedURL(o){
      let q = '';
      const u = (o.ubicacion || '').trim();
      try{
        if (u) {
          const url = new URL(u);
          if (url.hostname.includes('google.com')) {
            q = url.searchParams.get('query') || url.searchParams.get('q') || '';
          }
        }
      }catch(e){/* links cortos no parsean; ignorar */}
      if (!q) {
        const dir = (o.direccion || '').trim();
        q = dir ? `${dir}, Bogotá, Colombia` : (o.grupo || 'AA Bogotá');
      }
      return 'https://www.google.com/maps?output=embed&q=' + encodeURIComponent(q);
    }

    // BOTÓN IR: respeta exactamente el link del CSV
    function mapClickURL(o){
      const u = (o.ubicacion||'').trim();
      if (u && /^https?:/i.test(u)) return u;
      const dir = (o.direccion||'').trim();
      const q = dir ? `${dir}, Bogotá, Colombia` : (o.grupo||'AA Bogotá');
      return 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(q);
    }

    function card(o){
      const contactoHTML = linkifyContact(o.contacto);
      const embed = buildEmbedURL(o);
      const irURL = mapClickURL(o);

      return `<article class="aa-card">
        <div class="aa-title">🏷️ ${escapeHTML(o.grupo || '(Sin nombre)')}</div>
        ${fieldRow('🗺️','Distrito', escapeHTML(o.distrito))}
        ${fieldRow('📍','Dirección', escapeHTML(o.direccion))}
        ${fieldRow('📅','Reuniones', escapeHTML(o.reuniones))}
        ${fieldRow('📞','Número de contacto', contactoHTML)}
        <div class="aa-mapwrap">
          <iframe class="aa-map" src="${escapeAttr(embed)}" loading="lazy"
            referrerpolicy="no-referrer-when-downgrade" aria-label="Mapa de ubicación"></iframe>
        </div>
        <div class="aa-cta">
          <a class="aa-ir" href="${escapeAttr(irURL)}" target="_blank" rel="noopener">🗺️ IR</a>
        </div>
      </article>`;
    }

    function render(term){
      clearTimeout(t);
      if(term.length < MIN_CHARS){ out.innerHTML=''; cnt.textContent='Escribe para buscar.'; err.style.display='none'; return; }
      cnt.textContent='Buscando…';
      t=setTimeout(async ()=>{
        try{
          const data = await buscar(term);
          if(!data.ok) throw new Error(data.error||'Error');
          const arr = data.results || [];
          out.innerHTML = arr.map(card).join('');
          cnt.textContent = `Resultados: ${arr.length}`;
          err.style.display='none';
        }catch(e){
          err.style.display='block';
          err.textContent='No se pudo buscar: ' + e.message;
          cnt.textContent='Error';
          out.innerHTML='';
        }
      }, 200);
    }

    q.addEventListener('input', ()=>render(q.value.trim()));
    clearBtn.addEventListener('click', ()=>{ q.value=''; render(''); q.focus(); });
  })();
  </script>

  <div class="aa-footer">
    <a class="aa-fullbtn" href="https://www.aabogota.com/p/reuniones-virtuales-grupos-aa-bogota.html" rel="noopener" target="_blank">
      📚 Ir al directorio completo
    </a>
  </div>
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
```

#done
