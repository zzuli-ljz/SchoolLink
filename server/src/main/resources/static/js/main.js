const API_BASE = (window.location.port && window.location.port !== '8081') ? 'http://localhost:8081' : '';

async function fetchRoles() {
  const sel = document.getElementById('role');
  const fallback = ['SYSTEM_ADMIN','SCHOOL_ADMIN','TEACHER','PARENT','STUDENT'];
  async function tryFetch(url){
    try {
      const res = await fetch(url);
      if (!res.ok) throw new Error('bad status');
      return await res.json();
    } catch(_){ return null; }
  }
  let roles = await tryFetch('/api/auth/roles');
  if (!roles) roles = await tryFetch(API_BASE + '/api/auth/roles');
  if (!roles) roles = fallback;
  roles.forEach(r => {
    const opt = document.createElement('option');
    opt.value = r;
    opt.textContent = roleLabel(r);
    sel.appendChild(opt);
  });
}

function roleLabel(r) {
  switch (r) {
    case 'SYSTEM_ADMIN': return '系统管理员';
    case 'SCHOOL_ADMIN': return '学校管理员';
    case 'TEACHER': return '老师';
    case 'PARENT': return '家长';
    case 'STUDENT': return '学生';
    default: return r;
  }
}

async function onSubmit(e) {
  e.preventDefault();
  const msg = document.getElementById('msg');
  msg.textContent = '';
  msg.className = 'msg';
  const payload = {
    username: document.getElementById('username').value.trim(),
    password: document.getElementById('password').value,
    role: document.getElementById('role').value
  };

  if (!payload.role) { msg.textContent = '请先选择角色'; return; }

  try {
    const res = await fetch(API_BASE + '/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();
    if (!res.ok) {
      msg.classList.add('error');
      msg.textContent = data.message || '登录失败';
      return;
    }
    // 登录成功，给出轻微提示后跳转
    msg.classList.add('ok');
    msg.textContent = '登录成功，正在跳转...';
    try {
      if (data.token) localStorage.setItem('token', data.token);
      if (data.role) localStorage.setItem('role', data.role);
    } catch (_) {}
    setTimeout(() => window.location.href = data.homepagePath, 300);
  } catch (err) {
    msg.textContent = '网络错误，请稍后再试';
  }
}

document.addEventListener('DOMContentLoaded', () => {
  try {
    const m = sessionStorage.getItem('msg');
    if (m) {
      const msg = document.getElementById('msg');
      msg.classList.add('error');
      msg.textContent = m;
      sessionStorage.removeItem('msg');
    }
  } catch (_){ }
  fetchRoles();
  document.getElementById('loginForm').addEventListener('submit', onSubmit);
});