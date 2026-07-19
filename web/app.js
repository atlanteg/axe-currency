'use strict';
const APP_VERSION = '1.0';

/* ---------- Persistence ---------- */
const store = {
  get(k, d){ try{ const v = localStorage.getItem('fixe.'+k); return v===null?d:JSON.parse(v);}catch(e){return d;} },
  set(k, v){ try{ localStorage.setItem('fixe.'+k, JSON.stringify(v)); }catch(e){} }
};

/* ---------- State ---------- */
let displayCurrencies = (()=>{
  const raw = store.get('currencies', DEFAULT_CURRENCIES.slice());
  const uniq = [...new Set(raw)];
  if (uniq.length !== raw.length) store.set('currencies', uniq); // чистим сохранённые дубли
  return uniq;
})();
let decimalPlaces = store.get('decimals', 0);
let sourceMode = store.get('source', 0);          // 0=auto, 1..3 forced
let lang = store.get('lang', null);               // null = system
let allRates = {};
let activeCurrency = displayCurrencies[0] || 'EUR';
let activeAmount = 1;
let currentSource = '';
let lastUpdated = '';

/* ---------- i18n ---------- */
function resolveLang(){
  if (lang && I18N[lang]) return lang;
  const cands = (navigator.languages || [navigator.language || 'en']);
  for (const l of cands){
    const base = (l||'').toLowerCase().split('-')[0];
    if (I18N[base]) return base;
  }
  return 'en'; // неизвестный язык → English (не ru)
}
function t(key, ...args){
  const L = resolveLang();
  let s = (I18N[L] && I18N[L][key]) || I18N.en[key] || key;
  s = s.replace(/%(\d+)\$[sd]/g, (_,n)=> args[+n-1] !== undefined ? args[+n-1] : '');
  return s;
}
function applyDir(){
  const L = resolveLang();
  document.documentElement.lang = L;
  document.documentElement.dir = RTL_LANGS.includes(L) ? 'rtl' : 'ltr';
}

/* ---------- Currency helpers ---------- */
const cName = c => NAMES[c] || c;
const cFlag = c => FLAGS[c] || '🌐';
const cSym  = c => SYMBOLS[c] || c;

function fmtRate(r){
  if (r >= 10000) return r.toFixed(0);
  if (r >= 100)   return r.toFixed(2);
  if (r >= 1)     return r.toFixed(4);
  return r.toFixed(6);
}
function fmtAmount(v){
  const n = decimalPlaces;
  if (n === 0) return String(Math.ceil(v - 1e-9));
  const f = Math.pow(10, n);
  return (Math.ceil(v * f - 1e-9) / f).toFixed(n);
}
function parseAmount(s){
  const v = parseFloat(String(s).replace(',', '.'));
  return isNaN(v) ? 0 : v;
}

/* ---------- Rate sources (fallback chain) ---------- */
const SOURCES = [
  { name:'ExchangeRate-API', urls:['https://open.er-api.com/v6/latest/EUR'],
    parse:j => { if(j.result!=='success') throw 0; return j.rates; } },
  { name:'F.A.', urls:[
      'https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/eur.json',
      'https://latest.currency-api.pages.dev/v1/currencies/eur.json'],
    parse:j => { const o={}; for(const k in j.eur){ const v=+j.eur[k]; if(v>0) o[k.toUpperCase()]=v; } return o; } },
  { name:'Frankfurter (ECB)', urls:['https://api.frankfurter.dev/v1/latest?base=EUR'],
    parse:j => { const o=Object.assign({}, j.rates); o.EUR=1; return o; } },
];

async function fetchRates(){
  const ordered = (sourceMode>=1 && sourceMode<=SOURCES.length)
    ? [SOURCES[sourceMode-1], ...SOURCES.filter((_,i)=>i!==sourceMode-1)]
    : SOURCES;
  let lastErr;
  for (const src of ordered){
    for (const url of src.urls){
      try{
        const ctrl = new AbortController();
        const to = setTimeout(()=>ctrl.abort(), 12000);
        const r = await fetch(url, {signal:ctrl.signal, cache:'no-store'});
        clearTimeout(to);
        if(!r.ok) throw new Error('HTTP '+r.status);
        const rates = src.parse(await r.json());
        if (Object.keys(rates).length < 2) throw new Error('too few');
        return { rates, source:src.name };
      }catch(e){ lastErr = e; }
    }
  }
  throw lastErr || new Error('no sources');
}

async function refresh(){
  setStatus(t('loading'));
  try{
    const { rates, source } = await fetchRates();
    allRates = rates; currentSource = source;
    const d = new Date();
    const pad = n => String(n).padStart(2,'0');
    const mon = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getMonth()];
    lastUpdated = `${pad(d.getDate())} ${mon} ${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
    render();
  }catch(e){
    setStatus('⚠ ' + t('network_error'));
  }
}

/* ---------- Rendering ---------- */
const $ = s => document.querySelector(s);
const listEl = () => $('#list');

function setStatus(txt){ $('#status').textContent = txt; }

function render(){
  applyDir();
  // Защита от дублей: чистим список и сохраняем, если что-то повторилось
  const uniq = [...new Set(displayCurrencies)];
  if (uniq.length !== displayCurrencies.length){ displayCurrencies = uniq; store.set('currencies', displayCurrencies); }
  // static labels
  $('#btnClear').textContent = t('clear');
  $('#btnAdd').textContent = t('add_currency');
  $('#btnInfo').textContent = t('mid_market');
  $('#attribution').textContent = t('attribution');
  $('#version').textContent = t('version', APP_VERSION);
  $('#badge').textContent = t('selected', displayCurrencies.length);
  if (lastUpdated) setStatus(t('updated', lastUpdated, currentSource));

  const list = listEl();
  list.innerHTML = '';
  if (Object.keys(allRates).length === 0) return;

  const activeRate = allRates[activeCurrency] || 1;
  const amountInEur = activeRate ? activeAmount/activeRate : 0;
  const pivot = allRates[activeCurrency] ? activeCurrency : 'EUR';
  const pivotRate = allRates[pivot] || 1;

  displayCurrencies.forEach(code => {
    const rInEur = allRates[code];
    if (rInEur === undefined) return;
    const converted = amountInEur * rInEur;
    const isActive = code === activeCurrency;

    const card = document.createElement('div');
    card.className = 'card' + (isActive?' active':'');
    card.dataset.code = code;

    const rateText = (code === pivot)
      ? t('base_currency')
      : `1 ${pivot} = ${fmtRate(rInEur/pivotRate)} ${code}`;

    const r1 = document.createElement('div'); r1.className='row1';
    r1.innerHTML =
      `<button class="del" data-code="${code}">✕</button>`+
      `<span class="flag">${cFlag(code)}</span>`+
      `<span class="code">${code}</span>`+
      `<span class="spacer"></span>`+
      `<span class="sym">${cSym(code)}</span>`+
      `<input class="amount" inputmode="decimal" value="${fmtAmount(converted)}" placeholder="0">`+
      `<button class="handle" data-code="${code}" title="Reorder">⠿</button>`;

    const r2 = document.createElement('div'); r2.className='row2';
    r2.innerHTML = `<span class="name">${cName(code)}</span><span class="rate">${rateText}</span>`;

    card.appendChild(r1); card.appendChild(r2);

    const input = r1.querySelector('.amount');
    if (!isActive) input.value = fmtAmount(converted);
    input.addEventListener('focus', ()=>{
      activeCurrency = code; activeAmount = converted; input.select();
      markActive(code);
    });
    input.addEventListener('input', ()=>{
      activeCurrency = code; activeAmount = parseAmount(input.value);
      recompute();
    });
    r1.querySelector('.del').addEventListener('click', e=>{ e.stopPropagation(); confirmDelete(code); });
    attachDrag(r1.querySelector('.handle'), card);

    list.appendChild(card);
  });
}

// Update only amounts (не трогаем поле в фокусе) — при вводе
function recompute(){
  const activeRate = allRates[activeCurrency] || 1;
  const amountInEur = activeRate ? activeAmount/activeRate : 0;
  const pivot = allRates[activeCurrency] ? activeCurrency : 'EUR';
  const pivotRate = allRates[pivot] || 1;
  document.querySelectorAll('.card').forEach(card=>{
    const code = card.dataset.code;
    const rInEur = allRates[code]; if(rInEur===undefined) return;
    const conv = amountInEur * rInEur;
    const input = card.querySelector('.amount');
    if (document.activeElement !== input) input.value = fmtAmount(conv);
    const rate = card.querySelector('.rate');
    rate.textContent = (code===pivot) ? t('base_currency') : `1 ${pivot} = ${fmtRate(rInEur/pivotRate)} ${code}`;
    card.classList.toggle('active', code===activeCurrency);
  });
}
function markActive(code){
  document.querySelectorAll('.card').forEach(c=> c.classList.toggle('active', c.dataset.code===code));
  recompute();
}

/* ---------- Drag reorder (pointer-based) ---------- */
function attachDrag(handle, card){
  handle.addEventListener('pointerdown', e=>{
    e.preventDefault();
    const list = listEl();
    card.classList.add('dragging');
    const move = ev=>{
      const cards = [...list.querySelectorAll('.card:not(.dragging)')];
      const after = cards.find(c=>{
        const r = c.getBoundingClientRect();
        return ev.clientY < r.top + r.height/2;
      });
      if (after) list.insertBefore(card, after); else list.appendChild(card);
    };
    const up = ()=>{
      card.classList.remove('dragging');
      document.removeEventListener('pointermove', move);
      document.removeEventListener('pointerup', up);
      displayCurrencies = [...list.querySelectorAll('.card')].map(c=>c.dataset.code);
      store.set('currencies', displayCurrencies);
    };
    document.addEventListener('pointermove', move);
    document.addEventListener('pointerup', up);
  });
}

/* ---------- Modals ---------- */
function closeModal(){ $('#modal-root').innerHTML=''; }
function modal(html){
  const root = $('#modal-root');
  root.innerHTML = `<div class="overlay">${html}</div>`;
  root.querySelector('.overlay').addEventListener('click', e=>{ if(e.target.classList.contains('overlay')) closeModal(); });
  return root.querySelector('.modal');
}

function confirmDelete(code){
  if (displayCurrencies.length <= 2) return;
  modal(`<div class="modal">
    <h3>${t('delete_currency_title')}</h3>
    <div class="msg">${t('delete_currency_msg', code, cName(code))}</div>
    <div class="actions"><button data-x>${t('cancel')}</button><button data-ok>${t('delete')}</button></div>
  </div>`);
  $('#modal-root [data-x]').onclick = closeModal;
  $('#modal-root [data-ok]').onclick = ()=>{
    displayCurrencies = displayCurrencies.filter(c=>c!==code);
    if (activeCurrency===code) activeCurrency = displayCurrencies[0];
    store.set('currencies', displayCurrencies); closeModal(); render();
  };
}

function clearAll(){
  activeAmount = 0;
  if(document.activeElement) document.activeElement.blur();
  document.querySelectorAll('.card .amount').forEach(i=> i.value = fmtAmount(0));
  document.querySelectorAll('.card').forEach(c=> c.classList.remove('active'));
}

function showAdd(){
  const avail = Object.keys(allRates).filter(c=>!displayCurrencies.includes(c)).sort();
  if (!avail.length) return;
  const m = modal(`<div class="modal">
    <div class="search-box">🔍<input id="psearch" placeholder="${t('search_hint')}"></div>
    <div class="pick-list" id="picks"></div>
    <div class="actions"><button data-x>${t('cancel')}</button></div>
  </div>`);
  m.querySelector('h3, .search-box'); // noop
  const picks = m.querySelector('#picks');
  const draw = q=>{
    const f = avail.filter(c=> c.toLowerCase().includes(q) || cName(c).toLowerCase().includes(q));
    picks.innerHTML = f.map(c=>`<div class="pick-row" data-code="${c}"><span class="pf">${cFlag(c)}</span><span class="pc">${c}</span><span class="pn">${cName(c)}</span></div>`).join('');
    picks.querySelectorAll('.pick-row').forEach(row=> row.onclick=()=>{
      const c = row.dataset.code;
      if (!displayCurrencies.includes(c)) displayCurrencies.push(c);
      store.set('currencies', displayCurrencies); closeModal(); render();
    });
  };
  draw('');
  const inp = m.querySelector('#psearch');
  inp.addEventListener('input', ()=> draw(inp.value.trim().toLowerCase()));
  m.querySelector('[data-x]').onclick = closeModal;
  setTimeout(()=>inp.focus(), 50);
}

function showInfo(){
  modal(`<div class="modal">
    <h3>${t('source_info_title')}</h3>
    <div class="msg">${t('source_info_message')}</div>
    <div class="actions"><button data-x>${t('ok')}</button></div>
  </div>`);
  $('#modal-root [data-x]').onclick = closeModal;
}

function sourceOptions(){ return [t('source_auto'),'ExchangeRate-API','F.A.','Frankfurter (ECB)']; }

function showSettings(){
  const dec = decimalPlaces===0 ? t('precision_value_int') : t('precision_value_n', decimalPlaces);
  const src = sourceOptions()[Math.min(Math.max(sourceMode,0),3)];
  const langLabel = lang ? (LANGUAGES.find(l=>l[0]===lang)?.[1]||lang) : t('language_system');
  const m = modal(`<div class="modal">
    <h3>${t('settings')}</h3>
    <div class="list-choice">
      <button class="choice" data-i="0">${t('setting_precision', dec)}</button>
      <button class="choice" data-i="1">${t('setting_source', src)}</button>
      <button class="choice" data-i="2">${t('setting_language', langLabel)}</button>
    </div>
    <div class="actions"><button data-x>${t('close')}</button></div>
  </div>`);
  m.querySelector('[data-x]').onclick = closeModal;
  m.querySelector('[data-i="0"]').onclick = showPrecision;
  m.querySelector('[data-i="1"]').onclick = showSource;
  m.querySelector('[data-i="2"]').onclick = showLanguage;
}

function choiceList(title, items, checkedIdx, onPick){
  const m = modal(`<div class="modal">
    <h3>${title}</h3>
    <div class="list-choice">${items.map((it,i)=>`<button class="choice ${i===checkedIdx?'sel':''}" data-i="${i}"><span class="radio">${i===checkedIdx?'◉':'○'}</span>${it}</button>`).join('')}</div>
    <div class="actions"><button data-x>${t('back')}</button></div>
  </div>`);
  m.querySelector('[data-x]').onclick = closeModal;
  m.querySelectorAll('.choice').forEach(b=> b.onclick=()=>{ onPick(+b.dataset.i); });
}

function showPrecision(){
  const opts=[t('prec_0'),t('prec_1'),t('prec_2'),t('prec_4')]; const vals=[0,1,2,4];
  choiceList(t('precision_title'), opts, vals.indexOf(decimalPlaces), i=>{
    decimalPlaces = vals[i]; store.set('decimals', decimalPlaces); closeModal(); render();
  });
}
function showSource(){
  choiceList(t('source_title'), sourceOptions(), sourceMode, i=>{
    sourceMode = i; store.set('source', sourceMode); closeModal(); refresh();
  });
}
function showLanguage(){
  const items = [t('language_system'), ...LANGUAGES.map(l=>l[1])];
  const checked = lang ? (LANGUAGES.findIndex(l=>l[0]===lang)+1) : 0;
  choiceList(t('language_title'), items, checked<0?0:checked, i=>{
    lang = i===0 ? null : LANGUAGES[i-1][0];
    store.set('lang', lang); closeModal(); render();
  });
}

/* ---------- Wire up ---------- */
$('#btnRefresh').onclick = refresh;
$('#btnClear').onclick = clearAll;
$('#btnAdd').onclick = showAdd;
$('#btnInfo').onclick = showInfo;
$('#btnSettings').onclick = showSettings;

render();
refresh();
setInterval(refresh, 30*60*1000);
document.addEventListener('visibilitychange', ()=>{ if(!document.hidden) refresh(); });

/* ---------- PWA ---------- */
if ('serviceWorker' in navigator){
  window.addEventListener('load', ()=> navigator.serviceWorker.register('sw.js').catch(()=>{}));
}
