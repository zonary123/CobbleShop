// Network API & Authentication for UltraShop Dashboard

const TOKEN_KEY = 'ultrashop_token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export function showLogin() {
  const o = document.getElementById('loginOverlay');
  if (o) {
    o.style.display = 'flex';
    document.getElementById('loginError')?.classList.add('hidden');
    const input = document.getElementById('loginPassword');
    if (input) {
      input.value = '';
      input.focus();
    }
  }
}

export function hideLogin() {
  const o = document.getElementById('loginOverlay');
  if (o) o.style.display = 'none';
}

export async function apiFetch(url, opts = {}) {
  const token = getToken();
  const headers = { ...(opts.headers || {}) };
  if (token) {
    headers['Authorization'] = 'Bearer ' + token;
  }

  const res = await fetch(url, { ...opts, headers });

  if (res.status === 401) {
    clearToken();
    showLogin();
    throw new Error('Unauthorized');
  }
  if (res.status === 429) {
    showToast('Rate limit exceeded — wait a moment', 'error');
    throw new Error('Rate limited');
  }

  return res;
}

// ── Toasts ──
export function showToast(msg, type = 'info') {
  const c = document.getElementById('toastContainer');
  if (!c) return;

  const t = document.createElement('div');
  t.className = `toast ${type} flex items-center gap-2 border bg-base-800/90 backdrop-blur-md rounded-xl px-5 py-3 shadow-xl transition-all duration-300 transform scale-95 opacity-0 translate-y-2`;
  
  // Icon based on type
  let icon = '';
  if (type === 'error') {
    icon = `<svg class="text-neon-red animate-pulse" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>`;
  } else if (type === 'success') {
    icon = `<svg class="text-neon-green" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`;
  } else {
    icon = `<svg class="text-neon-blue" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>`;
  }

  t.innerHTML = `${icon}<span>${msg}</span>`;
  c.appendChild(t);
  
  // Trigger animation entry
  setTimeout(() => {
    t.classList.remove('scale-95', 'opacity-0', 'translate-y-2');
    t.classList.add('scale-100', 'opacity-100', 'translate-y-0');
  }, 10);

  // Trigger animation exit and remove
  setTimeout(() => {
    t.classList.add('opacity-0', 'translate-x-10');
    setTimeout(() => t.remove(), 300);
  }, 3700);
}
