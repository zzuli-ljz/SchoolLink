// Lightweight API helpers with token from localStorage
(function(){
  function getToken(){
    try { return localStorage.getItem('token') || ''; } catch(e){ return ''; }
  }
  function getRole(){
    try { return localStorage.getItem('role') || ''; } catch(e){ return ''; }
  }

  async function apiFetch(url, opts){
    const token = getToken();
    const headers = Object.assign({ 'Content-Type': 'application/json' }, opts && opts.headers ? opts.headers : {});
    if (token) headers['Authorization'] = 'Bearer ' + token;
    const res = await fetch(url, Object.assign({}, opts, { headers }));
    const contentType = res.headers.get('content-type') || '';
    const isJson = contentType.includes('application/json');
    const data = isJson ? await res.json() : await res.text();
    if (!res.ok) {
      throw { status: res.status, data };
    }
    return data;
  }

  function apiGet(url){ return apiFetch(url, { method: 'GET' }); }
  function apiPost(url, body){ return apiFetch(url, { method: 'POST', body: JSON.stringify(body || {}) }); }
  function apiPut(url, body){ return apiFetch(url, { method: 'PUT', body: JSON.stringify(body || {}) }); }
  function apiDelete(url){ return apiFetch(url, { method: 'DELETE' }); }

  function initTabs() {
    const menuItems = document.querySelectorAll('.menu-item');
    const sections = document.querySelectorAll('.section');

    function show(id) {
        const targetId = id.startsWith('#') ? id.substring(1) : id;
        
        sections.forEach(s => {
            if (s.id === targetId) s.classList.add('active');
            else s.classList.remove('active');
        });
        
        menuItems.forEach(a => {
            const href = a.getAttribute('href');
            if(!href) return;
            const hrefId = href.startsWith('#') ? href.substring(1) : href;
            if (hrefId === targetId) a.classList.add('active');
            else a.classList.remove('active');
        });
    }

    menuItems.forEach(a => {
        a.addEventListener('click', (e) => {
            e.preventDefault();
            const href = a.getAttribute('href');
            if (href) show(href);
        });
    });

    if(window.location.hash) {
        show(window.location.hash);
    } else if (sections.length > 0 && !document.querySelector('.section.active')) {
        show(sections[0].id);
    }
  }

  // expose
  window.DashboardAPI = { apiFetch, apiGet, apiPost, apiPut, apiDelete, getToken, getRole, initTabs };
})();

// Global Leave Manager (Injected Modal Logic)
(function(){
    const LeaveManager = {
        init: function() {
            if (document.getElementById('globalLeaveModal')) return;
            
            const modalHtml = `
                <div id="globalLeaveModal" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.8); z-index:9999; overflow-y:auto; align-items:center; justify-content:center;">
                    <div style="background:#1e2a38; width:90%; max-width:500px; margin:100px auto; padding:20px; border-radius:8px; border:1px solid #2a3b4d; box-shadow: 0 4px 15px rgba(0,0,0,0.5);">
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;">
                            <h2 style="margin:0; color:#fff; font-size:18px;">请假审批详情</h2>
                            <button id="btnCloseLeaveModal" class="btn" style="background:#f44336; width:auto; height:auto; padding:4px 10px;">关闭</button>
                        </div>
                        <div id="leaveModalContent" style="color:#e3f2fd; font-size:14px;"></div>
                        <div id="leaveModalActions" style="text-align:right; border-top:1px solid #2a3b4d; padding-top:20px; margin-top:20px; display:flex; justify-content:flex-end; gap:10px;"></div>
                    </div>
                </div>
            `;
            const div = document.createElement('div');
            div.innerHTML = modalHtml;
            document.body.appendChild(div.firstElementChild);

            // Bind Close
            document.getElementById('btnCloseLeaveModal').onclick = function() {
                document.getElementById('globalLeaveModal').style.display = 'none';
            };
            
            // Close on outside click
            document.getElementById('globalLeaveModal').onclick = function(e) {
                if(e.target === this) this.style.display = 'none';
            }

            // Bind Global Click for View Buttons (Delegation)
            document.addEventListener('click', function(e) {
                // Handle click on the button or its children
                const btn = e.target.closest('.btn-view-leave');
                if (btn) {
                    e.preventDefault(); // Prevent any default action
                    e.stopPropagation(); // Stop propagation
                    LeaveManager.open(btn.dataset);
                }
            });
        },
        open: function(data) {
            const modal = document.getElementById('globalLeaveModal');
            const content = document.getElementById('leaveModalContent');
            const actions = document.getElementById('leaveModalActions');
            
            modal.style.display = 'block'; // Or flex if using flexbox centering
            
            content.innerHTML = `
                <div style="margin-bottom:8px;"><strong>申请ID:</strong> #${data.id}</div>
                <div style="margin-bottom:8px;"><strong>学生ID:</strong> ${data.studentId}</div>
                <div style="margin-bottom:8px;"><strong>时间:</strong> ${data.dateFrom} 至 ${data.dateTo}</div>
                <div style="margin-bottom:15px;"><strong>状态:</strong> 
                    <span style="color:${data.status === 'PENDING' ? '#ff9800' : (data.status === 'APPROVED' ? '#4caf50' : '#f44336')}">
                        ${data.status === 'PENDING' ? '待审批' : (data.status === 'APPROVED' ? '已批准' : '已拒绝')}
                    </span>
                </div>
                <div style="background:rgba(0,0,0,0.2); padding:15px; border-radius:4px; border:1px solid #2a3b4d;">
                    <strong>申请原因：</strong><br/>
                    <div style="margin-top:5px; white-space:pre-wrap;">${data.reason || '无原因'}</div>
                </div>
            `;
            
            if (data.status === 'PENDING') {
                actions.innerHTML = `
                    <button class="btn" id="btnApproveLeave" style="background:#4caf50; width:auto; padding:6px 15px;">批准</button>
                    <button class="btn" id="btnRejectLeave" style="background:#f44336; width:auto; padding:6px 15px;">拒绝</button>
                `;
                document.getElementById('btnApproveLeave').onclick = () => LeaveManager.process(data.id, 'approve');
                document.getElementById('btnRejectLeave').onclick = () => LeaveManager.process(data.id, 'reject');
            } else {
                actions.innerHTML = '<span style="color:#888;">该申请已处理完毕</span>';
            }
        },
        process: async function(id, action) {
            if(!confirm(action === 'approve' ? '确定批准该申请吗?' : '确定拒绝该申请吗?')) return;
            const actions = document.getElementById('leaveModalActions');
            const original = actions.innerHTML;
            actions.innerHTML = '<span style="color:#ccc;">正在提交...</span>';
            
            try {
                 const url = action === 'approve' ? `/api/leave/${id}/approve` : `/api/leave/${id}/reject`;
                 await window.DashboardAPI.apiPut(url, {});
                 
                 document.getElementById('globalLeaveModal').style.display = 'none';
                 
                 // Refresh list if function exists
                 if (typeof window.loadTeacherLeaves === 'function') {
                     window.loadTeacherLeaves();
                 } else {
                     alert('操作成功，请手动刷新列表');
                 }
            } catch(e) {
                alert('操作失败: ' + (e.data?.error || e.message || 'Unknown'));
                actions.innerHTML = original;
                // Rebind
                if(document.getElementById('btnApproveLeave')) 
                    document.getElementById('btnApproveLeave').onclick = () => LeaveManager.process(id, 'approve');
                if(document.getElementById('btnRejectLeave')) 
                    document.getElementById('btnRejectLeave').onclick = () => LeaveManager.process(id, 'reject');
            }
        }
    };

    // Auto init
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', LeaveManager.init);
    } else {
        LeaveManager.init();
    }
})();
