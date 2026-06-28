// Main Application Coordinator (UltraShop Dashboard)
// Orchestrates state management, network calls, event listeners, and UI updates

import { state, saveFilterState, restoreFilterState } from './state.js';
import { apiFetch, getToken, setToken, clearToken, showLogin, hideLogin, showToast } from './api.js';
import { renderDailyChart, renderShopChart } from './charts.js';
import {
  esc,
  fmt,
  renderCards,
  renderTopProducts,
  populateShopFilter,
  renderGlobalProductCards,
  renderProductsTable,
  renderPlayersTable,
  renderPagination,
  renderPlayerDetail
} from './ui.js';

// ── Drawer Management ──
function openPlayerDrawer() {
  const drawer = document.getElementById('playerDrawer');
  const backdrop = document.getElementById('drawerBackdrop');
  if (drawer && backdrop) {
    backdrop.classList.remove('hidden');
    // Trigger transition
    setTimeout(() => {
      drawer.classList.remove('translate-x-full');
      backdrop.classList.remove('opacity-0');
    }, 20);
  }
}

function closePlayerDrawer() {
  const drawer = document.getElementById('playerDrawer');
  const backdrop = document.getElementById('drawerBackdrop');
  if (drawer && backdrop) {
    drawer.classList.add('translate-x-full');
    backdrop.classList.add('opacity-0');
    setTimeout(() => {
      backdrop.classList.add('hidden');
    }, 300);
  }
}

// ── Auth Handling ──
async function handleLoginSubmit(e) {
  e.preventDefault();
  const input = document.getElementById('loginPassword');
  if (!input) return;
  const pw = input.value.trim();
  if (!pw) return;

  setToken(pw);
  try {
    const res = await apiFetch('/api/stats?days=1');
    if (res.ok) {
      hideLogin();
      await loadData();
      startAutoRefresh();
      showToast('Authenticated successfully', 'success');
    } else {
      clearToken();
      document.getElementById('loginError')?.classList.remove('hidden');
    }
  } catch {
    clearToken();
    document.getElementById('loginError')?.classList.remove('hidden');
  }
}

// ── Load Data & Render ──
async function loadData() {
  const fs = saveFilterState();
  const daysSelect = document.getElementById('daysSelect');
  if (!daysSelect) return;
  const days = daysSelect.value;

  // Flash the status light to green
  const syncLight = document.getElementById('syncIndicator');
  if (syncLight) {
    syncLight.classList.remove('bg-neon-green');
    syncLight.classList.add('bg-accent', 'animate-pulse');
  }

  try {
    // 1. Fetch Server/Global Stats
    const res = await apiFetch(`/api/stats?days=${days}`);
    if (!res.ok) {
      showToast(`Failed to load stats: ${res.status}`, 'error');
      return;
    }
    const data = await res.json();

    state.allProducts = data.products || [];
    state.allShops = data.shops || [];

    // Render Stats Overview Components
    renderCards(data.server, data.daily);
    state.dailyChart = renderDailyChart(data.daily, 'dailyChart', state.dailyChart);
    state.shopChart = renderShopChart(data.shops, 'shopChart', state.shopChart);
    renderTopProducts(state.allProducts.slice(0, 10));

    // Render Products Components
    populateShopFilter(data.shops);
    restoreFilterState(fs);
    renderFilteredProducts();
    renderGlobalProductCards(state.allProducts);

    // 2. Fetch Player Rankings
    const pRes = await apiFetch(`/api/players?days=${days}`);
    if (pRes.ok) {
      state.allPlayers = await pRes.json();
      renderPlayerPage();
    }
  } catch (err) {
    if (err.message !== 'Unauthorized' && err.message !== 'Rate limited') {
      showToast('Connection error — server unreachable', 'error');
    }
  } finally {
    if (syncLight) {
      syncLight.classList.remove('bg-accent', 'animate-pulse');
      syncLight.classList.add('bg-neon-green');
    }
  }
}

// ── Products Rendering & Filter Orchestration ──
function filterProducts() {
  state.productPage = 0;
  renderFilteredProducts();
}

function renderFilteredProducts() {
  const search = document.getElementById('productSearch')?.value.toLowerCase() || '';
  const sf = document.getElementById('shopFilter')?.value || '';
  const sort = document.getElementById('sortField')?.value || 'revenue';
  const min = parseInt(document.getElementById('minTxns')?.value || '0') || 0;

  let filtered = state.allProducts.filter(p => {
    if (search && !p.productId.toLowerCase().includes(search)) return false;
    if (sf && p.shopId !== sf) return false;
    return (p.totalBought + p.totalSold) >= min;
  });

  if (sort === 'productId') {
    filtered.sort((a, b) => a.productId.localeCompare(b.productId));
  } else {
    filtered.sort((a, b) => +b[sort] - +a[sort]);
  }

  const total = filtered.length;
  const pages = Math.max(1, Math.ceil(total / state.PAGE_SIZE));
  state.productPage = Math.min(state.productPage, pages - 1);
  const start = state.productPage * state.PAGE_SIZE;
  const page = filtered.slice(start, start + state.PAGE_SIZE);

  const countEl = document.getElementById('productCount');
  if (countEl) {
    countEl.textContent = total > 0
      ? `${start + 1}–${Math.min(start + state.PAGE_SIZE, total)} of ${total} products` 
      : '0 products';
  }

  renderProductsTable(page, start);
  renderPagination('productPagination', state.productPage, pages, p => {
    state.productPage = p;
    renderFilteredProducts();
  });
}

// ── Players Rendering & Navigation ──
function renderPlayerPage() {
  const sf = document.getElementById('playerSort')?.value || 'spent';
  const map = { spent: 'totalSpent', earned: 'totalEarned', bought: 'totalBought', sold: 'totalSold', transactions: 'transactions' };
  let sorted = [...state.allPlayers].sort((a, b) => +b[map[sf] || 'totalSpent'] - +a[map[sf] || 'totalSpent']);

  const total = sorted.length;
  const pages = Math.max(1, Math.ceil(total / state.PAGE_SIZE));
  state.playerPage = Math.min(state.playerPage, pages - 1);
  const start = state.playerPage * state.PAGE_SIZE;
  const page = sorted.slice(start, start + state.PAGE_SIZE);

  renderPlayersTable(page, start, lookupPlayerByUuid);
  renderPagination('playerPagination', state.playerPage, pages, p => {
    state.playerPage = p;
    renderPlayerPage();
  });
}

// ── Player Lookup Orchestration ──
async function lookupPlayer() {
  const inputEl = document.getElementById('playerInput');
  if (!inputEl) return;
  const input = inputEl.value.trim();
  if (!input) return;

  if (input.length > 36 || !/^[a-zA-Z0-9_-]+$/.test(input)) {
    showToast('Invalid input — alphanumeric and dashes only', 'error');
    return;
  }

  try {
    const days = document.getElementById('daysSelect')?.value || '30';
    const res = await apiFetch(`/api/player?q=${encodeURIComponent(input)}&days=${days}`);
    if (!res.ok) {
      showToast('Player profile not found', 'error');
      return;
    }
    const data = await res.json();
    renderPlayerDetail(data);
    openPlayerDrawer();
  } catch (err) {
    if (err.message !== 'Unauthorized' && err.message !== 'Rate limited') {
      showToast('Error looking up player profile', 'error');
    }
  }
}

async function lookupPlayerByUuid(uuid) {
  const inputEl = document.getElementById('playerInput');
  if (inputEl) inputEl.value = uuid;

  // Wait a millisecond and run lookup
  await lookupPlayer();
}

// ── Days Synced Inputs ──
function syncDaysTimeframe(e) {
  const val = e.target.value;
  const desktop = document.getElementById('daysSelect');
  const mobile = document.getElementById('daysSelectMobile');
  if (desktop) desktop.value = val;
  if (mobile) mobile.value = val;
  
  loadData();
}

// ── Initialize Event Listeners ──
function bindEvents() {
  // Tabs click binding
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
      
      btn.classList.add('active');
      const content = document.getElementById('tab-' + btn.dataset.tab);
      if (content) content.classList.add('active');

      // Update active title in page header
      const titleEl = document.getElementById('pageTitle');
      const descEl = document.getElementById('pageDescription');
      if (titleEl) {
        titleEl.textContent = btn.querySelector('span')?.textContent || 'Overview';
      }
      if (descEl) {
        if (btn.dataset.tab === 'overview') {
          descEl.textContent = 'Detailed Minecraft shop analytics, trends, and key performance indicators';
        } else if (btn.dataset.tab === 'products') {
          descEl.textContent = 'Explore product catalog interactions, margins, and purchase statistics';
        } else if (btn.dataset.tab === 'players') {
          descEl.textContent = 'Browse customer database rankings, balances, and history profiles';
        }
      }
    });
  });

  // Filter bindings
  document.getElementById('productSearch')?.addEventListener('input', filterProducts);
  document.getElementById('shopFilter')?.addEventListener('change', filterProducts);
  document.getElementById('sortField')?.addEventListener('change', filterProducts);
  document.getElementById('minTxns')?.addEventListener('input', filterProducts);
  document.getElementById('playerSort')?.addEventListener('change', renderPlayerPage);

  // Synced Days Selectors
  document.getElementById('daysSelect')?.addEventListener('change', syncDaysTimeframe);
  document.getElementById('daysSelectMobile')?.addEventListener('change', syncDaysTimeframe);

  // Player search binding
  document.getElementById('playerSearchBtn')?.addEventListener('click', lookupPlayer);
  document.getElementById('playerInput')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') lookupPlayer();
  });

  // Drawer events
  document.getElementById('closeDrawerBtn')?.addEventListener('click', closePlayerDrawer);
  document.getElementById('drawerBackdrop')?.addEventListener('click', closePlayerDrawer);
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closePlayerDrawer();
  });

  // Login form submission
  document.getElementById('loginForm')?.addEventListener('submit', handleLoginSubmit);
}

// ── Autorefresh Engine ──
function startAutoRefresh() {
  if (state.autoRefreshTimer) clearInterval(state.autoRefreshTimer);
  state.autoRefreshTimer = setInterval(loadData, 60000);
}

// ── Application Bootstrapping ──
async function init() {
  bindEvents();

  const token = getToken();
  if (token) {
    try {
      const r = await fetch('/api/stats?days=1', { headers: { 'Authorization': 'Bearer ' + token } });
      if (r.ok) {
        hideLogin();
        await loadData();
        startAutoRefresh();
        return;
      }
    } catch {
      /* invalid token */
    }
    clearToken();
  }

  try {
    const r = await fetch('/api/stats?days=1');
    if (r.ok) {
      hideLogin();
      await loadData();
      startAutoRefresh();
      return;
    }
    if (r.status === 401) {
      showLogin();
      return;
    }
  } catch {
    /* unreachable */
  }

  hideLogin();
  await loadData();
  startAutoRefresh();
}

init();
