// ── Auth ──
const TOKEN_KEY = 'ultrashop_token';
function getToken() { return localStorage.getItem(TOKEN_KEY); }
function setToken(t) { localStorage.setItem(TOKEN_KEY, t); }
function clearToken() { localStorage.removeItem(TOKEN_KEY); }

function showLogin() {
  const o = document.getElementById('loginOverlay');
  o.style.display = 'flex';
  document.getElementById('loginError').classList.add('hidden');
  document.getElementById('loginPassword').value = '';
  document.getElementById('loginPassword').focus();
}
function hideLogin() { document.getElementById('loginOverlay').style.display = 'none'; }

async function handleLogin(e) {
  e.preventDefault();
  const pw = document.getElementById('loginPassword').value.trim();
  if (!pw) return;
  setToken(pw);
  try {
    const r = await apiFetch('/api/stats?days=1');
    if (r.ok) { hideLogin(); loadData(); startAutoRefresh(); }
    else { clearToken(); document.getElementById('loginError').classList.remove('hidden'); }
  } catch { clearToken(); document.getElementById('loginError').classList.remove('hidden'); }
}

async function apiFetch(url, opts = {}) {
  const token = getToken();
  const headers = { ...(opts.headers || {}) };
  if (token) headers['Authorization'] = 'Bearer ' + token;
  const res = await fetch(url, { ...opts, headers });
  if (res.status === 401) { clearToken(); showLogin(); throw new Error('Unauthorized'); }
  if (res.status === 429) { showToast('Rate limit exceeded — wait a moment', 'error'); throw new Error('Rate limited'); }
  return res;
}

// ── Toasts ──
function showToast(msg, type = 'info') {
  const c = document.getElementById('toastContainer');
  const t = document.createElement('div');
  t.className = 'toast ' + type;
  t.textContent = msg;
  c.appendChild(t);
  setTimeout(() => t.remove(), 4000);
}

// ── State ──
let allProducts = [], allPlayers = [], allShops = [];
let dailyChart, shopChart;
let productPage = 0, playerPage = 0;
const PAGE_SIZE = 25;
let autoRefreshTimer = null;

// ── Tabs ──
document.querySelectorAll('.tab-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById('tab-' + btn.dataset.tab).classList.add('active');
  });
});

// ── Load Data ──
async function loadData() {
  const fs = saveFilterState();
  try {
    const days = document.getElementById('daysSelect').value;
    const res = await apiFetch('/api/stats?days=' + days);
    if (!res.ok) { showToast('Failed to load stats: ' + res.status, 'error'); return; }
    const data = await res.json();

    allProducts = data.products || [];
    allShops = data.shops || [];

    renderCards(data.server);
    renderDailyChart(data.daily);
    renderShopChart(data.shops);
    renderTopProducts(allProducts.slice(0, 10));
    populateShopFilter(data.shops);
    restoreFilterState(fs);
    filterProducts();
    renderGlobalProductCards();

    const pRes = await apiFetch('/api/players?days=' + days);
    if (pRes.ok) { allPlayers = await pRes.json(); renderPlayerList(); }
  } catch (err) {
    if (err.message !== 'Unauthorized' && err.message !== 'Rate limited')
      showToast('Connection error — server unreachable', 'error');
  }
}

function saveFilterState() {
  return {
    productSearch: document.getElementById('productSearch')?.value || '',
    shopFilter: document.getElementById('shopFilter')?.value || '',
    sortField: document.getElementById('sortField')?.value || 'revenue',
    minTxns: document.getElementById('minTxns')?.value || '0',
    playerSort: document.getElementById('playerSort')?.value || 'spent',
    productPage, playerPage,
  };
}
function restoreFilterState(s) {
  const el = id => document.getElementById(id);
  if (el('productSearch')) el('productSearch').value = s.productSearch;
  if (el('shopFilter'))    el('shopFilter').value = s.shopFilter;
  if (el('sortField'))     el('sortField').value = s.sortField;
  if (el('minTxns'))       el('minTxns').value = s.minTxns;
  if (el('playerSort'))    el('playerSort').value = s.playerSort;
  productPage = s.productPage;
  playerPage = s.playerPage;
}

function fmt(n) {
  return parseFloat(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// ── KPI Cards ──
function renderCards(s) {
  const cards = [
    { label: 'Total Transactions', value: s.totalTransactions.toLocaleString(), cls: 'c-blue',   color: '#60a5fa', glow: 'rgba(96,165,250,0.06)' },
    { label: 'Unique Players',     value: s.uniquePlayers.toLocaleString(),      cls: 'c-purple', color: '#a78bfa', glow: 'rgba(167,139,250,0.06)' },
    { label: 'Revenue (Buys)',     value: '$' + fmt(s.totalRevenue),             cls: 'c-green',  color: '#34d399', glow: 'rgba(52,211,153,0.06)' },
    { label: 'Payouts (Sells)',    value: '$' + fmt(s.totalPayout),              cls: 'c-red',    color: '#fb7185', glow: 'rgba(251,113,133,0.06)' },
    { label: 'Net Profit',         value: '$' + fmt(s.netProfit),                cls: 'c-amber',  color: '#f59e0b', glow: 'rgba(245,158,11,0.06)' },
  ];

  document.getElementById('cards').innerHTML = cards.map((c, i) => `
    <div class="kpi-card" style="--kpi-color:${c.color};--kpi-glow:${c.glow};animation:cardIn 0.4s ${i * 70}ms both cubic-bezier(0.16,1,0.3,1)">
      <div class="kpi-label">${c.label}</div>
      <div class="kpi-value ${c.cls}">${c.value}</div>
    </div>
  `).join('');
}

// ── Charts ──
const chartFont = { family: 'Outfit, system-ui, sans-serif', size: 12 };
const monoFont = { family: 'JetBrains Mono, monospace', size: 11 };
const tooltipStyle = {
  backgroundColor: 'rgba(10,10,26,0.95)', titleColor: '#e2e8f0', bodyColor: '#94a3b8',
  borderColor: 'rgba(255,255,255,0.08)', borderWidth: 1, cornerRadius: 10, padding: 12,
  titleFont: chartFont, bodyFont: monoFont,
};

function grad(ctx, top, bot) {
  const g = ctx.createLinearGradient(0, 0, 0, 300);
  g.addColorStop(0, top); g.addColorStop(1, bot);
  return g;
}

function renderDailyChart(daily) {
  const ctx = document.getElementById('dailyChart').getContext('2d');
  if (dailyChart) dailyChart.destroy();
  dailyChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: daily.map(d => d.date),
      datasets: [
        {
          label: 'Revenue', data: daily.map(d => +d.revenue),
          borderColor: '#34d399', backgroundColor: grad(ctx, 'rgba(52,211,153,0.1)', 'rgba(52,211,153,0)'),
          fill: true, tension: 0.4, borderWidth: 2,
          pointRadius: 0, pointHoverRadius: 5, pointHoverBackgroundColor: '#34d399', pointHoverBorderColor: '#0a0a1a', pointHoverBorderWidth: 3,
        },
        {
          label: 'Payouts', data: daily.map(d => +d.payout),
          borderColor: '#fb7185', backgroundColor: grad(ctx, 'rgba(251,113,133,0.07)', 'rgba(251,113,133,0)'),
          fill: true, tension: 0.4, borderWidth: 2,
          pointRadius: 0, pointHoverRadius: 5, pointHoverBackgroundColor: '#fb7185', pointHoverBorderColor: '#0a0a1a', pointHoverBorderWidth: 3,
        }
      ]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      scales: {
        y: { beginAtZero: true, ticks: { color: '#4b5563', font: monoFont }, grid: { color: 'rgba(255,255,255,0.025)', drawBorder: false } },
        x: { ticks: { color: '#4b5563', maxRotation: 45, font: monoFont }, grid: { display: false } }
      },
      plugins: {
        legend: { labels: { color: '#94a3b8', font: chartFont, usePointStyle: true, pointStyleWidth: 8, padding: 14 } },
        tooltip: tooltipStyle,
      }
    }
  });
}

function renderShopChart(shops) {
  const ctx = document.getElementById('shopChart').getContext('2d');
  if (shopChart) shopChart.destroy();
  const colors = ['#f59e0b','#34d399','#60a5fa','#fb7185','#a78bfa','#fb923c','#22d3ee','#e879f9','#a3e635','#f472b6'];
  shopChart = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: shops.map(s => s.shopId),
      datasets: [{ data: shops.map(s => +s.revenue), backgroundColor: colors.slice(0, shops.length), borderColor: '#0a0a1a', borderWidth: 3, hoverOffset: 6 }]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      cutout: '68%',
      plugins: {
        legend: { position: 'bottom', labels: { color: '#94a3b8', padding: 16, font: chartFont, usePointStyle: true, pointStyleWidth: 8 } },
        tooltip: tooltipStyle,
      }
    }
  });
}

// ── Top Products ──
function renderTopProducts(prods) {
  const tb = document.getElementById('topProductTable');
  if (!prods.length) { tb.innerHTML = '<tr><td colspan="9" class="text-center text-gray-500 py-12 font-display text-sm">No data</td></tr>'; return; }
  tb.innerHTML = prods.map((p, i) => `<tr>
    <td class="c-amber fw-bold">#${i+1}</td>
    <td class="c-white fw-semi">${esc(p.productId)}</td>
    <td class="c-muted">${esc(p.shopId)}</td>
    <td><span class="tag tag-buy">${p.totalBought}×</span></td>
    <td><span class="tag tag-sell">${p.totalSold}×</span></td>
    <td class="c-green">$${fmt(p.revenue)}</td>
    <td class="c-red">$${fmt(p.payout)}</td>
    <td class="c-amber fw-bold">$${fmt(p.netProfit)}</td>
    <td class="c-muted">${p.uniquePlayers}</td>
  </tr>`).join('');
}

// ── Products Tab ──
function populateShopFilter(shops) {
  const sel = document.getElementById('shopFilter');
  const cur = sel.value;
  sel.innerHTML = '<option value="">All Shops</option>' + shops.map(s => `<option value="${esc(s.shopId)}">${esc(s.shopId)}</option>`).join('');
  sel.value = cur;
}

function filterProducts() { productPage = 0; renderFilteredProducts(); }

function renderFilteredProducts() {
  const search = document.getElementById('productSearch').value.toLowerCase();
  const sf = document.getElementById('shopFilter').value;
  const sort = document.getElementById('sortField').value;
  const min = parseInt(document.getElementById('minTxns').value) || 0;

  let filtered = allProducts.filter(p => {
    if (search && !p.productId.toLowerCase().includes(search)) return false;
    if (sf && p.shopId !== sf) return false;
    return (p.totalBought + p.totalSold) >= min;
  });

  sort === 'productId'
    ? filtered.sort((a, b) => a.productId.localeCompare(b.productId))
    : filtered.sort((a, b) => +b[sort] - +a[sort]);

  const total = filtered.length;
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  productPage = Math.min(productPage, pages - 1);
  const start = productPage * PAGE_SIZE;
  const page = filtered.slice(start, start + PAGE_SIZE);

  document.getElementById('productCount').textContent = total > 0
    ? `${start+1}–${Math.min(start+PAGE_SIZE, total)} of ${total} products` : '0 products';

  const tb = document.getElementById('allProductTable');
  if (!page.length) {
    tb.innerHTML = '<tr><td colspan="11" class="text-center text-gray-500 py-12 font-display text-sm">No matching products</td></tr>';
  } else {
    tb.innerHTML = page.map((p, i) => `<tr>
      <td class="c-amber fw-bold">#${start+i+1}</td>
      <td class="c-white fw-semi">${esc(p.productId)}</td>
      <td class="c-muted">${esc(p.shopId)}</td>
      <td><span class="tag tag-buy">${p.totalBought}×</span></td>
      <td><span class="tag tag-sell">${p.totalSold}×</span></td>
      <td class="c-green">$${fmt(p.revenue)}</td>
      <td class="c-red">$${fmt(p.payout)}</td>
      <td class="c-amber fw-bold">$${fmt(p.netProfit)}</td>
      <td class="c-muted">${p.uniqueBuyers}</td>
      <td class="c-muted">${p.uniqueSellers}</td>
      <td class="c-muted">${p.uniquePlayers}</td>
    </tr>`).join('');
  }
  renderPagination('productPagination', productPage, pages, p => { productPage = p; renderFilteredProducts(); });
}

// ── Global Product Stats ──
function renderGlobalProductCards() {
  const el = document.getElementById('globalProductCards');
  if (!allProducts.length) { el.innerHTML = ''; return; }

  const n = allProducts.length;
  const mb = allProducts.reduce((a, b) => a.totalBought > b.totalBought ? a : b);
  const ms = allProducts.reduce((a, b) => a.totalSold > b.totalSold ? a : b);
  const hr = allProducts[0];
  const tRev = allProducts.reduce((s, p) => s + +p.revenue, 0);

  const stats = [
    { label: 'Unique Products', val: n, cls: 'c-blue', color: '#60a5fa' },
    { label: 'Most Bought', val: `${esc(mb.productId)} <small class="c-muted">(${mb.totalBought}×)</small>`, cls: 'c-green', color: '#34d399' },
    { label: 'Most Sold', val: `${esc(ms.productId)} <small class="c-muted">(${ms.totalSold}×)</small>`, cls: 'c-red', color: '#fb7185' },
    { label: 'Highest Revenue', val: `${esc(hr.productId)} <small class="c-muted">($${fmt(hr.revenue)})</small>`, cls: 'c-amber', color: '#f59e0b' },
    { label: 'Avg Revenue', val: '$' + fmt(tRev / n), cls: 'c-purple', color: '#a78bfa' },
    { label: 'Total Revenue', val: '$' + fmt(tRev), cls: 'c-green', color: '#34d399' },
  ];

  el.innerHTML = stats.map(c => `
    <div class="kpi-card" style="--kpi-color:${c.color};--kpi-glow:rgba(0,0,0,0)">
      <div class="kpi-label">${c.label}</div>
      <div class="kpi-value ${c.cls}" style="font-size:14px">${c.val}</div>
    </div>
  `).join('');
}

// ── Players ──
function renderPlayerList() { playerPage = 0; renderPlayerPage(); }

function renderPlayerPage() {
  const sf = document.getElementById('playerSort').value;
  const map = { spent:'totalSpent', earned:'totalEarned', bought:'totalBought', sold:'totalSold', transactions:'transactions' };
  let sorted = [...allPlayers].sort((a, b) => +b[map[sf]||'totalSpent'] - +a[map[sf]||'totalSpent']);

  const total = sorted.length;
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  playerPage = Math.min(playerPage, pages - 1);
  const start = playerPage * PAGE_SIZE;
  const page = sorted.slice(start, start + PAGE_SIZE);

  const tb = document.getElementById('playerTable');
  if (!page.length) {
    tb.innerHTML = '<tr><td colspan="7" class="text-center text-gray-500 py-12 font-display text-sm">No player data</td></tr>';
  } else {
    tb.innerHTML = page.map((p, i) => `<tr class="player-row cursor-pointer" data-uuid="${esc(p.playerUuid)}" data-name="${esc(p.playerName)}">
      <td class="c-amber fw-bold">#${start+i+1}</td>
      <td>
        <div class="c-white fw-semi" style="font-family:Outfit,sans-serif;font-size:13px">${esc(p.playerName)}</div>
        <div class="c-muted" style="font-size:10px">${esc(p.playerUuid)}</div>
      </td>
      <td><span class="tag tag-buy">${p.totalBought}×</span></td>
      <td><span class="tag tag-sell">${p.totalSold}×</span></td>
      <td class="c-green">$${fmt(p.totalSpent)}</td>
      <td class="c-red">$${fmt(p.totalEarned)}</td>
      <td class="c-amber fw-bold">$${fmt(+p.totalSpent - +p.totalEarned)}</td>
    </tr>`).join('');

    tb.querySelectorAll('.player-row').forEach(r => {
      r.addEventListener('click', () => lookupPlayerByUuid(r.dataset.uuid));
    });
  }
  renderPagination('playerPagination', playerPage, pages, p => { playerPage = p; renderPlayerPage(); });
}

async function lookupPlayer() {
  const input = document.getElementById('playerInput').value.trim();
  if (!input) return;
  if (input.length > 36 || !/^[a-zA-Z0-9_-]+$/.test(input)) {
    showToast('Invalid input — alphanumeric only', 'error'); return;
  }
  try {
    const days = document.getElementById('daysSelect').value;
    const res = await apiFetch(`/api/player?q=${encodeURIComponent(input)}&days=${days}`);
    if (!res.ok) { document.getElementById('playerResult').innerHTML = '<p class="text-gray-500 text-center py-12 text-sm">Player not found</p>'; return; }
    renderPlayerDetail(await res.json());
  } catch (err) {
    if (err.message !== 'Unauthorized' && err.message !== 'Rate limited')
      document.getElementById('playerResult').innerHTML = '<p class="text-gray-500 text-center py-12 text-sm">Error looking up player</p>';
  }
}

function lookupPlayerByUuid(uuid) {
  document.getElementById('playerInput').value = uuid;
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
  document.querySelector('[data-tab="players"]').classList.add('active');
  document.getElementById('tab-players').classList.add('active');
  lookupPlayer();
}

function renderPlayerDetail(data) {
  const p = data.aggregate, prods = data.products || [];
  const net = +p.totalSpent - +p.totalEarned;

  const stats = [
    { label: 'Player', val: esc(p.playerName || p.playerUuid), cls: 'c-blue', color: '#60a5fa', sm: true },
    { label: 'Items Bought', val: p.totalBought, cls: 'c-green', color: '#34d399' },
    { label: 'Items Sold', val: p.totalSold, cls: 'c-red', color: '#fb7185' },
    { label: 'Total Spent', val: '$'+fmt(p.totalSpent), cls: 'c-green', color: '#34d399' },
    { label: 'Total Earned', val: '$'+fmt(p.totalEarned), cls: 'c-red', color: '#fb7185' },
    { label: 'Net Spend', val: '$'+fmt(net), cls: 'c-amber', color: '#f59e0b' },
  ];

  let html = `<div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mb-6">
    ${stats.map(c => `<div class="kpi-card" style="--kpi-color:${c.color}">
      <div class="kpi-label">${c.label}</div>
      <div class="kpi-value ${c.cls}" style="font-size:${c.sm ? '13px' : '18px'}">${c.val}</div>
    </div>`).join('')}
  </div>`;

  if (prods.length) {
    html += `
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-sm font-semibold text-gray-200">Product Breakdown</h3>
        <span class="text-[10px] font-semibold uppercase tracking-wider text-accent bg-accent/10 border border-accent/15 px-2.5 py-1 rounded-md">${prods.length} items</span>
      </div>
      <div class="overflow-x-auto">
        <table class="data-table w-full">
          <thead><tr><th>Product</th><th>Shop</th><th>Bought</th><th>Sold</th><th>Spent</th><th>Earned</th></tr></thead>
          <tbody>${prods.map(pr => `<tr>
            <td class="c-white fw-semi">${esc(pr.productId)}</td>
            <td class="c-muted">${esc(pr.shopId)}</td>
            <td><span class="tag tag-buy">${pr.bought}×</span></td>
            <td><span class="tag tag-sell">${pr.sold}×</span></td>
            <td class="c-green">$${fmt(pr.spent)}</td>
            <td class="c-red">$${fmt(pr.earned)}</td>
          </tr>`).join('')}</tbody>
        </table>
      </div>`;
  }
  document.getElementById('playerResult').innerHTML = html;
}

// ── Pagination ──
function renderPagination(id, cur, total, onChange) {
  const el = document.getElementById(id);
  if (total <= 1) { el.innerHTML = ''; return; }
  el.innerHTML = `
    <button class="pg-btn" ${cur===0?'disabled':''}'>← Prev</button>
    <span class="text-xs text-gray-500 font-mono">${cur+1} / ${total}</span>
    <button class="pg-btn" ${cur>=total-1?'disabled':''}>Next →</button>`;
  el.children[0].onclick = () => { if (cur > 0) onChange(cur - 1); };
  el.children[2].onclick = () => { if (cur < total - 1) onChange(cur + 1); };
}

// ── Utils ──
function esc(s) { if (!s) return ''; const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

// ── Init ──
async function init() {
  const token = getToken();
  if (token) {
    try {
      const r = await fetch('/api/stats?days=1', { headers: { 'Authorization': 'Bearer ' + token } });
      if (r.ok) { hideLogin(); loadData(); startAutoRefresh(); return; }
    } catch { /* invalid */ }
    clearToken();
  }
  try {
    const r = await fetch('/api/stats?days=1');
    if (r.ok) { hideLogin(); loadData(); startAutoRefresh(); return; }
    if (r.status === 401) { showLogin(); return; }
  } catch { /* unreachable */ }
  hideLogin(); loadData(); startAutoRefresh();
}

function startAutoRefresh() {
  if (autoRefreshTimer) clearInterval(autoRefreshTimer);
  autoRefreshTimer = setInterval(loadData, 60000);
}

init();
