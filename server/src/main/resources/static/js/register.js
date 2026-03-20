const API_BASE = (window.location.port && window.location.port !== '8081') ? 'http://localhost:8081' : '';

function roleLabel(r) {
  switch (r) {
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

  const role = document.getElementById('role').value;
  const inviteCode = document.getElementById('inviteCode').value.trim();
  const password = document.getElementById('password').value;
  
  if (!role) { msg.textContent = '请先选择角色'; return; }
  if (!password || !inviteCode) { msg.textContent = '请完整填写密码与邀请码'; return; }

  const payload = {
    password, role, inviteCode,
    studentName: role === 'PARENT' ? document.getElementById('studentNameForParent').value.trim() : document.getElementById('studentNameForStudent').value.trim(),
    studentNo: role === 'STUDENT' ? document.getElementById('studentNo').value.trim() : undefined,
    relationship: role === 'PARENT' ? document.getElementById('relationship').value.trim() : undefined,
    phoneNumber: role === 'PARENT' ? document.getElementById('phoneNumber').value.trim() : undefined
  };

  if (role === 'PARENT') {
    if (!payload.studentName || !payload.relationship) { msg.textContent = '家长注册需填写学生姓名与关系'; return; }
    if (!payload.phoneNumber) { msg.textContent = '家长注册需填写联系电话'; return; }
    
    // 家长注册前先预检学生
    msg.textContent = '正在查找学生信息...';
    try {
      const checkRes = await fetch(`${API_BASE}/api/auth/check-student?inviteCode=${encodeURIComponent(inviteCode)}&studentName=${encodeURIComponent(payload.studentName)}`);
      const checkData = await checkRes.json();
      if (!checkRes.ok) {
        msg.classList.add('error');
        msg.textContent = checkData.error === 'student_not_found' ? '未找到该学生，请检查姓名或咨询老师' : (checkData.message || '查找学生失败');
        return;
      }
      if (checkData.isBound) {
        msg.classList.add('error');
        msg.textContent = '该学生已被其他家长账户绑定';
        return;
      }
      
      // 显示确认弹窗
      document.getElementById('modalStudentName').textContent = checkData.studentName;
      document.getElementById('modalStudentNo').textContent = checkData.studentNo || '（无学号）';
      const modal = document.getElementById('bindModal');
      modal.style.display = 'flex';
      
      // 绑定一次性事件
      const btnConfirm = document.getElementById('btnConfirmBind');
      const btnCancel = document.getElementById('btnCancelBind');
      
      // 清除旧监听（简单处理：克隆节点）
      const newBtnConfirm = btnConfirm.cloneNode(true);
      btnConfirm.parentNode.replaceChild(newBtnConfirm, btnConfirm);
      const newBtnCancel = btnCancel.cloneNode(true);
      btnCancel.parentNode.replaceChild(newBtnCancel, btnCancel);
      
      newBtnCancel.addEventListener('click', () => {
        modal.style.display = 'none';
        msg.textContent = '';
      });
      
      newBtnConfirm.addEventListener('click', async () => {
        modal.style.display = 'none';
        await doRegister(payload, msg);
      });
      
    } catch (err) {
      msg.classList.add('error');
      msg.textContent = '网络错误，无法验证学生信息';
    }
    return;
  } else if (role === 'STUDENT') {
    if (!payload.studentName || !payload.studentNo) { msg.textContent = '学生注册需填写姓名与学号'; return; }
  }

  await doRegister(payload, msg);
}

async function doRegister(payload, msg) {
  msg.textContent = '正在注册...';
  msg.className = 'msg';
  try {
    const res = await fetch(API_BASE + '/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();
    if (!res.ok) {
      msg.classList.add('error');
      msg.textContent = data.message || '注册失败';
      return;
    }
    msg.classList.add('ok');
    msg.textContent = '注册成功，正在跳转...';
    try {
      if (data.token) localStorage.setItem('token', data.token);
      if (data.role) localStorage.setItem('role', data.role);
    } catch (_){ }
    setTimeout(() => window.location.href = data.homepagePath, 400);
  } catch (err) {
    msg.classList.add('error');
    msg.textContent = '网络错误，请稍后再试';
  }
}

document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('registerForm').addEventListener('submit', onSubmit);
  const roleSelect = document.getElementById('role');
  const containers = Array.from(document.querySelectorAll('#roleFields [data-role]'));
  function updateRoleFields(){
    const v = roleSelect.value;
    containers.forEach(el => {
      el.style.display = (el.getAttribute('data-role') === v) ? 'flex' : 'none';
    });
  }
  roleSelect.addEventListener('change', updateRoleFields);
  updateRoleFields();
});