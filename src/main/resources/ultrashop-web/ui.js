// UI Renderer for UltraShop Dashboard
// Handles rendering Tables, Cards, Sparklines, Pagination, and the interactive Player Profile Drawer

import { renderSparkline } from './charts.js';

// Helper to escape HTML characters
export function esc(s) {
  if (!s) return '';
  const d = document.createElement('div');
  d.textContent = s;
  return d.innerHTML;
}

// Helper to format currency
export function fmt(n) {
  return parseFloat(n || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// ── Render KPI Cards with Sparklines ──
export function renderCards(s, daily) {
  const cards = [
    { id: 'tx', label: 'Total Transactions', value: s.totalTransactions.toLocaleString(), cls: 'text-neon-blue', color: '#60a5fa', glow: 'rgba(96,165,250,0.08)', data: daily.map(d => d.transactions) },
    { id: 'players', label: 'Unique Players', value: s.uniquePlayers.toLocaleString(), cls: 'text-neon-purple', color: '#a78bfa', glow: 'rgba(167,139,250,0.08)', data: daily.map(d => d.transactions) }, // proxy
    { id: 'rev', label: 'Revenue (Buys)', value: '$' + fmt(s.totalRevenue), cls: 'text-neon-green', color: '#34d399', glow: 'rgba(52,211,153,0.08)', data: daily.map(d => +d.revenue) },
    { id: 'pay', label: 'Payouts (Sells)', value: '$' + fmt(s.totalPayout), cls: 'text-neon-red', color: '#fb7185', glow: 'rgba(251,113,133,0.08)', data: daily.map(d => +d.payout) },
    { id: 'net', label: 'Net Profit', value: '$' + fmt(s.netProfit), cls: 'text-accent', color: '#f59e0b', glow: 'rgba(245,158,11,0.08)', data: daily.map(d => +d.revenue - +d.payout) },
  ];

  const cardsContainer = document.getElementById('cards');
  if (!cardsContainer) return;

  cardsContainer.innerHTML = cards.map((c, i) => `
    <div class="kpi-card relative flex flex-col justify-between" style="--kpi-color:${c.color};--kpi-glow:${c.glow};animation:cardIn 0.4s ${i * 75}ms both cubic-bezier(0.16,1,0.3,1)">
      <div>
        <div class="kpi-label">${c.label}</div>
        <div class="kpi-value ${c.cls} tracking-tight">${c.value}</div>
      </div>
      <div class="w-full h-10 mt-5 opacity-80 hover:opacity-100 transition-opacity">
        <canvas id="spark-${c.id}" class="w-full h-full"></canvas>
      </div>
    </div>
  `).join('');

  // Wait a frame to ensure elements are mounted, then draw sparklines
  setTimeout(() => {
    cards.forEach(c => {
      const canvas = document.getElementById(`spark-${c.id}`);
      if (canvas) {
        renderSparkline(canvas, c.data, c.color);
      }
    });
  }, 50);
}

// ── Render Top Products Table ──
export function renderTopProducts(prods) {
  const tb = document.getElementById('topProductTable');
  if (!tb) return;

  if (!prods.length) {
    tb.innerHTML = '<tr><td colspan="9" class="text-center text-gray-500 py-12 font-display text-sm">No data available</td></tr>';
    return;
  }

  tb.innerHTML = prods.map((p, i) => `
    <tr class="hover:bg-white/[0.02] transition-colors border-b border-white/[0.03]">
      <td class="text-accent font-bold py-3.5 px-4">#${i+1}</td>
      <td class="text-white font-semibold font-mono px-4 text-xs select-all">${esc(p.productId)}</td>
      <td class="text-gray-400 font-display px-4">${esc(p.shopId)}</td>
      <td class="px-4"><span class="tag tag-buy font-mono px-2 py-0.5 rounded text-[11px] font-semibold">${p.totalBought}×</span></td>
      <td class="px-4"><span class="tag tag-sell font-mono px-2 py-0.5 rounded text-[11px] font-semibold">${p.totalSold}×</span></td>
      <td class="text-neon-green font-mono px-4">$${fmt(p.revenue)}</td>
      <td class="text-neon-red font-mono px-4">$${fmt(p.payout)}</td>
      <td class="text-accent font-mono font-semibold px-4">$${fmt(p.netProfit)}</td>
      <td class="text-gray-500 font-mono px-4">${p.uniquePlayers}</td>
    </tr>
  `).join('');
}

// ── Populate Shop Dropdown Filter ──
export function populateShopFilter(shops) {
  const sel = document.getElementById('shopFilter');
  if (!sel) return;
  const cur = sel.value;
  sel.innerHTML = '<option value="">All Shops</option>' + shops.map(s => `<option value="${esc(s.shopId)}">${esc(s.shopId)}</option>`).join('');
  sel.value = cur;
}

// ── Render Global Stats Cards (Products Tab Bottom) ──
export function renderGlobalProductCards(allProducts) {
  const el = document.getElementById('globalProductCards');
  if (!el) return;

  if (!allProducts.length) {
    el.innerHTML = '';
    return;
  }

  const n = allProducts.length;
  const mb = allProducts.reduce((a, b) => a.totalBought > b.totalBought ? a : b);
  const ms = allProducts.reduce((a, b) => a.totalSold > b.totalSold ? a : b);
  const hr = allProducts[0];
  const tRev = allProducts.reduce((s, p) => s + +p.revenue, 0);

  const stats = [
    { label: 'Unique Products', val: n, cls: 'text-neon-blue', color: '#60a5fa' },
    { label: 'Most Bought', val: `${esc(mb.productId)} <small class="text-gray-500 block text-[10px] font-mono">${mb.totalBought}× purchased</small>`, cls: 'text-neon-green', color: '#34d399', long: true },
    { label: 'Most Sold', val: `${esc(ms.productId)} <small class="text-gray-500 block text-[10px] font-mono">${ms.totalSold}× sold</small>`, cls: 'text-neon-red', color: '#fb7185', long: true },
    { label: 'Highest Revenue', val: `${esc(hr.productId)} <small class="text-gray-500 block text-[10px] font-mono">$${fmt(hr.revenue)} generated</small>`, cls: 'text-accent', color: '#f59e0b', long: true },
    { label: 'Avg Revenue', val: '$' + fmt(tRev / n), cls: 'text-neon-purple', color: '#a78bfa' },
    { label: 'Total Revenue', val: '$' + fmt(tRev), cls: 'text-neon-green', color: '#34d399' },
  ];

  el.innerHTML = stats.map(c => `
    <div class="kpi-card flex flex-col justify-between min-h-[92px]" style="--kpi-color:${c.color};--kpi-glow:rgba(0,0,0,0)">
      <div class="kpi-label">${c.label}</div>
      <div class="kpi-value ${c.cls} ${c.long ? 'text-xs' : 'text-sm'} tracking-tight leading-tight mt-1">${c.val}</div>
    </div>
  `).join('');
}

// ── Render Products Tab Table ──
export function renderProductsTable(pageItems, startIdx) {
  const tb = document.getElementById('allProductTable');
  if (!tb) return;

  if (!pageItems.length) {
    tb.innerHTML = '<tr><td colspan="11" class="text-center text-gray-500 py-12 font-display text-sm">No matching products found</td></tr>';
    return;
  }

  tb.innerHTML = pageItems.map((p, i) => `
    <tr class="hover:bg-white/[0.02] transition-colors border-b border-white/[0.03]">
      <td class="text-accent font-bold py-3 px-4">#${startIdx+i+1}</td>
      <td class="text-white font-semibold font-mono px-4 text-xs select-all">${esc(p.productId)}</td>
      <td class="text-gray-400 font-display px-4">${esc(p.shopId)}</td>
      <td class="px-4"><span class="tag tag-buy font-mono px-2 py-0.5 rounded text-[11px]">${p.totalBought}×</span></td>
      <td class="px-4"><span class="tag tag-sell font-mono px-2 py-0.5 rounded text-[11px]">${p.totalSold}×</span></td>
      <td class="text-neon-green font-mono px-4">$${fmt(p.revenue)}</td>
      <td class="text-neon-red font-mono px-4">$${fmt(p.payout)}</td>
      <td class="text-accent font-mono font-semibold px-4">$${fmt(p.netProfit)}</td>
      <td class="text-gray-400 font-mono px-4">${p.uniqueBuyers}</td>
      <td class="text-gray-400 font-mono px-4">${p.uniqueSellers}</td>
      <td class="text-gray-500 font-mono px-4">${p.uniquePlayers}</td>
    </tr>
  `).join('');
}

// ── Render Players Tab Table ──
export function renderPlayersTable(pageItems, startIdx, onRowClick) {
  const tb = document.getElementById('playerTable');
  if (!tb) return;

  if (!pageItems.length) {
    tb.innerHTML = '<tr><td colspan="7" class="text-center text-gray-500 py-12 font-display text-sm">No player data available</td></tr>';
    return;
  }

  tb.innerHTML = pageItems.map((p, i) => `
    <tr class="player-row hover:bg-white/[0.02] active:bg-white/[0.04] transition-colors border-b border-white/[0.03] cursor-pointer group" data-uuid="${esc(p.playerUuid)}">
      <td class="text-accent font-bold py-3.5 px-4">#${startIdx+i+1}</td>
      <td class="px-4 py-2.5">
        <div class="flex items-center gap-3">
          <img src="https://crafatar.com/avatars/${p.playerUuid}?size=32&overlay" 
               class="w-8 h-8 rounded-lg border border-white/[0.08] shadow-md group-hover:border-accent/40 group-hover:scale-105 transition-all" 
               onerror="this.onerror=null; this.src='https://mc-heads.net/avatar/steve/32'" 
               alt="${esc(p.playerName)}" />
          <div>
            <div class="text-white font-semibold text-[13px] group-hover:text-accent transition-colors">${esc(p.playerName)}</div>
            <div class="text-gray-500 font-mono text-[9px] select-all">${esc(p.playerUuid)}</div>
          </div>
        </div>
      </td>
      <td class="px-4"><span class="tag tag-buy font-mono px-2 py-0.5 rounded text-[11px]">${p.totalBought}×</span></td>
      <td class="px-4"><span class="tag tag-sell font-mono px-2 py-0.5 rounded text-[11px]">${p.totalSold}×</span></td>
      <td class="text-neon-green font-mono px-4">$${fmt(p.totalSpent)}</td>
      <td class="text-neon-red font-mono px-4">$${fmt(p.totalEarned)}</td>
      <td class="text-accent font-mono font-semibold px-4">$${fmt(+p.totalSpent - +p.totalEarned)}</td>
    </tr>
  `).join('');

  // Bind click handlers to table rows
  tb.querySelectorAll('.player-row').forEach(row => {
    row.addEventListener('click', () => {
      onRowClick(row.dataset.uuid);
    });
  });
}

// ── Render Pagination Controls ──
export function renderPagination(id, cur, total, onChange) {
  const el = document.getElementById(id);
  if (!el) return;

  if (total <= 1) {
    el.innerHTML = '';
    return;
  }

  el.innerHTML = `
    <button class="pg-btn flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-white/[0.06] bg-base-800 text-xs font-semibold text-gray-400 hover:text-white hover:border-accent/40 active:scale-95 disabled:opacity-20 disabled:pointer-events-none transition-all" ${cur === 0 ? 'disabled' : ''}>
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="m15 18-6-6 6-6"/></svg>
      Prev
    </button>
    <span class="text-xs text-gray-500 font-mono px-3 py-1 bg-white/[0.02] border border-white/[0.04] rounded-lg">${cur + 1} / ${total}</span>
    <button class="pg-btn flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-white/[0.06] bg-base-800 text-xs font-semibold text-gray-400 hover:text-white hover:border-accent/40 active:scale-95 disabled:opacity-20 disabled:pointer-events-none transition-all" ${cur >= total - 1 ? 'disabled' : ''}>
      Next
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="m9 18 6-6-6-6"/></svg>
    </button>
  `;

  el.children[0].onclick = () => { if (cur > 0) onChange(cur - 1); };
  el.children[2].onclick = () => { if (cur < total - 1) onChange(cur + 1); };
}

// ── Render Player Details (Drawer View) ──
export function renderPlayerDetail(data) {
  const container = document.getElementById('playerDrawerContent');
  if (!container) return;

  const p = data.aggregate;
  const prods = data.products || [];
  const net = +p.totalSpent - +p.totalEarned;

  const stats = [
    { label: 'Total Items Bought', val: `${p.totalBought}×`, cls: 'text-neon-green', color: '#34d399' },
    { label: 'Total Items Sold', val: `${p.totalSold}×`, cls: 'text-neon-red', color: '#fb7185' },
    { label: 'Total Money Spent', val: '$' + fmt(p.totalSpent), cls: 'text-neon-green', color: '#34d399' },
    { label: 'Total Money Earned', val: '$' + fmt(p.totalEarned), cls: 'text-neon-red', color: '#fb7185' },
    { label: 'Net Spend Balance', val: '$' + fmt(net), cls: 'text-accent', color: '#f59e0b' },
  ];

  let html = `
    <!-- Top Identity Card -->
    <div class="flex flex-col items-center bg-white/[0.02] border border-white/[0.06] rounded-2xl p-6 text-center relative overflow-hidden">
      <!-- Glow effect -->
      <div class="absolute -top-12 left-1/2 -translate-x-1/2 w-32 h-20 bg-accent/15 rounded-full blur-2xl"></div>
      
      <!-- 3D Minecraft Body Render -->
      <div class="relative w-24 h-48 mb-4 hover:scale-105 transition-transform duration-300 select-none">
        <img src="https://crafatar.com/renders/body/${p.playerUuid}?overlay" 
             class="w-full h-full object-contain filter drop-shadow-[0_8px_16px_rgba(0,0,0,0.5)]" 
             onerror="this.onerror=null; this.src='https://mc-heads.net/body/steve/120'"
             alt="${esc(p.playerName)}" />
      </div>
      
      <h2 class="text-xl font-bold text-white tracking-tight">${esc(p.playerName)}</h2>
      <p class="text-[10px] text-gray-500 font-mono mt-1 select-all">${esc(p.playerUuid)}</p>
    </div>

    <!-- Quick Stats Grid -->
    <div class="grid grid-cols-2 gap-3">
      ${stats.map(c => `
        <div class="bg-base-900 border border-white/[0.04] rounded-xl p-3.5 transition-colors hover:border-white/[0.08]" style="border-left: 2px solid ${c.color}">
          <div class="text-[9px] font-bold text-gray-500 uppercase tracking-wider">${c.label}</div>
          <div class="text-sm font-semibold font-mono ${c.cls} mt-1">${c.val}</div>
        </div>
      `).join('')}
    </div>
  `;

  if (prods.length) {
    html += `
      <!-- Product List -->
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <h4 class="text-xs font-semibold text-gray-300 uppercase tracking-wider">Product History</h4>
          <span class="text-[9px] font-bold font-mono text-accent bg-accent/10 border border-accent/15 px-2 py-0.5 rounded">${prods.length} items</span>
        </div>
        
        <div class="overflow-x-auto rounded-xl border border-white/[0.05] bg-white/[0.01]">
          <table class="data-table w-full">
            <thead>
              <tr class="bg-white/[0.02]">
                <th class="py-2.5 px-3">Product</th>
                <th class="py-2.5 px-3">Shop</th>
                <th class="py-2.5 px-3">Bought</th>
                <th class="py-2.5 px-3">Sold</th>
                <th class="py-2.5 px-3 text-right">Spent</th>
                <th class="py-2.5 px-3 text-right">Earned</th>
              </tr>
            </thead>
            <tbody>
              ${prods.map(pr => `
                <tr class="hover:bg-white/[0.02] transition-colors border-t border-white/[0.04]">
                  <td class="text-white font-semibold font-mono text-[11px] py-2 px-3 select-all">${esc(pr.productId)}</td>
                  <td class="text-gray-400 text-[11px] px-3">${esc(pr.shopId)}</td>
                  <td class="px-3"><span class="tag tag-buy font-mono px-1.5 py-0.2 rounded text-[10px]">${pr.bought}×</span></td>
                  <td class="px-3"><span class="tag tag-sell font-mono px-1.5 py-0.2 rounded text-[10px]">${pr.sold}×</span></td>
                  <td class="text-neon-green font-mono text-[11px] px-3 text-right">$${fmt(pr.spent)}</td>
                  <td class="text-neon-red font-mono text-[11px] px-3 text-right">$${fmt(pr.earned)}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      </div>
    `;
  } else {
    html += `
      <div class="text-center py-8 text-gray-500 border border-dashed border-white/[0.06] rounded-xl text-xs font-display">
        No transaction history recorded for this period
      </div>
    `;
  }

  container.innerHTML = html;
}
