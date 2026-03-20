const API_BASE = (window.location.port && window.location.port !== '8081') ? 'http://localhost:8081' : '';

function getToken() {
  try { return localStorage.getItem('token'); } catch (_) { return null; }
}

function getRole() {
  try { return localStorage.getItem('role'); } catch (_) { return null; }
}

async function verifyToken() {
  const token = getToken();
  if (!token) return null;
  try {
    const res = await fetch(API_BASE + '/api/auth/verify', {
      headers: { 'Authorization': 'Bearer ' + token }
    });
    const data = await res.json();
    if (!res.ok) return null;
    return data;
  } catch (_) { return null; }
}

async function requireRole(expectedRole) {
  const info = await verifyToken();
  if (!info || !info.role || (expectedRole && info.role !== expectedRole)) {
    try { sessionStorage.setItem('msg', '请重新登录'); } catch (_) {}
    window.location.href = '/';
    return;
  }
}

function logout() {
  try {
    localStorage.removeItem('token');
    localStorage.removeItem('role');
  } catch (_) {}
  window.location.href = '/';
}