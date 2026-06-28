// State Manager for UltraShop Dashboard

export const state = {
  allProducts: [],
  allPlayers: [],
  allShops: [],
  productPage: 0,
  playerPage: 0,
  PAGE_SIZE: 25,
  autoRefreshTimer: null,
  dailyChart: null,
  shopChart: null,
};

export function saveFilterState() {
  return {
    productSearch: document.getElementById('productSearch')?.value || '',
    shopFilter: document.getElementById('shopFilter')?.value || '',
    sortField: document.getElementById('sortField')?.value || 'revenue',
    minTxns: document.getElementById('minTxns')?.value || '0',
    playerSort: document.getElementById('playerSort')?.value || 'spent',
    productPage: state.productPage,
    playerPage: state.playerPage,
  };
}

export function restoreFilterState(s) {
  const el = id => document.getElementById(id);
  if (el('productSearch')) el('productSearch').value = s.productSearch;
  if (el('shopFilter'))    el('shopFilter').value = s.shopFilter;
  if (el('sortField'))     el('sortField').value = s.sortField;
  if (el('minTxns'))       el('minTxns').value = s.minTxns;
  if (el('playerSort'))    el('playerSort').value = s.playerSort;
  state.productPage = s.productPage;
  state.playerPage = s.playerPage;
}
