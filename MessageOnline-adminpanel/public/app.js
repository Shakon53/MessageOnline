/* ══════════════════════════════════════════════════════
   MessageOnline Admin Panel — App v3 (Vuexy Style)
══════════════════════════════════════════════════════ */

// ─── State ────────────────────────────────────────────
let currentPage = 'dashboard';
let usersPage = 1, msgsPage = 1, friendsPage = 1;
let msgFilter = 'all', friendFilter = 'accepted';
let userSearchTimer = null, msgSearchTimer = null;
let activityLog = [];
let activityChart = null;
let sseSource = null;

// ─── Init ─────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  const r = await api('/api/me');
  if (r.authenticated) showApp();
  else showLogin();
});

// ─── AUTH ─────────────────────────────────────────────
function showLogin() {
  document.getElementById('loginScreen').style.display = '';
  document.getElementById('app').style.display = 'none';
}
function showApp() {
  document.getElementById('loginScreen').style.display = 'none';
  document.getElementById('app').style.display = '';
  document.getElementById('todayDate').textContent = new Date().toLocaleDateString('ru', { day:'numeric', month:'long', year:'numeric' });
  setupNav();
  setupSearch();
  startSSE();
  loadStats();
  const refreshBtn = document.getElementById('refreshBtn');
  if (refreshBtn) refreshBtn.addEventListener('click', () => {
    if (currentPage === 'dashboard') loadStats();
    else if (currentPage === 'users') loadUsers(usersPage);
    else if (currentPage === 'messages') loadMessages(msgsPage);
    else if (currentPage === 'friends') loadFriends(friendsPage);
    toast('Обновлено', 'info');
  });
}

document.getElementById('loginForm').addEventListener('submit', async e => {
  e.preventDefault();
  const pw = document.getElementById('loginPassword').value;
  const err = document.getElementById('loginError');
  err.textContent = '';
  const r = await api('/api/login', 'POST', { password: pw });
  if (r.ok) { showApp(); }
  else { err.textContent = r.error || 'Неверный пароль'; }
});

document.getElementById('logoutBtn').addEventListener('click', async () => {
  await api('/api/logout', 'POST');
  stopSSE();
  showLogin();
});

// ─── NAVIGATION ───────────────────────────────────────
const pageTitles    = { dashboard: 'Дашборд', users: 'Пользователи', messages: 'Сообщения', friends: 'Друзья', live: 'Live' };
const pageSubtitles = { dashboard: 'Обзор системы', users: 'Управление пользователями', messages: 'Все сообщения платформы', friends: 'Связи между пользователями', live: 'Активность в реальном времени' };

function setupNav() {
  document.querySelectorAll('.nav-item').forEach(el => {
    el.addEventListener('click', e => {
      e.preventDefault();
      const page = el.dataset.page;
      if (page) navigateTo(page);
    });
  });
}

function navigateTo(page) {
  currentPage = page;
  document.querySelectorAll('.nav-item').forEach(el => el.classList.toggle('active', el.dataset.page === page));
  document.querySelectorAll('.page').forEach(el => el.classList.toggle('active', el.id === `page-${page}`));
  document.getElementById('pageTitle').textContent    = pageTitles[page]    || page;
  const sub = document.getElementById('pageSubtitle');
  if (sub) sub.textContent = pageSubtitles[page] || '';

  if (page === 'users')    { usersPage = 1; loadUsers(); }
  if (page === 'messages') { msgsPage = 1; loadMessages(); }
  if (page === 'friends')  { friendsPage = 1; loadFriends(); }
}

function setupSearch() {
  // Top search — navigate to users with query
  document.getElementById('topSearch').addEventListener('input', e => {
    const q = e.target.value.trim();
    if (q) { navigateTo('users'); document.getElementById('userSearch').value = q; clearTimeout(userSearchTimer); userSearchTimer = setTimeout(() => loadUsers(1, q), 300); }
  });
  // User search
  document.getElementById('userSearch').addEventListener('input', e => {
    clearTimeout(userSearchTimer);
    userSearchTimer = setTimeout(() => loadUsers(1, e.target.value.trim()), 350);
  });
  // Message search
  document.getElementById('msgSearch').addEventListener('input', e => {
    clearTimeout(msgSearchTimer);
    msgSearchTimer = setTimeout(() => loadMessages(1), 350);
  });
  // Message filter tabs
  document.querySelectorAll('#page-messages .ftab').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('#page-messages .ftab').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      msgFilter = btn.dataset.filter;
      loadMessages(1);
    });
  });
  // Friends filter tabs
  document.querySelectorAll('#page-friends .ftab').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('#page-friends .ftab').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      friendFilter = btn.dataset.filter;
      loadFriends(1);
    });
  });
  // Clear logs
  document.getElementById('clearDashLog').addEventListener('click', () => {
    activityLog = [];
    document.getElementById('dashActivityLog').innerHTML = '';
  });
  document.getElementById('clearLiveLog').addEventListener('click', () => {
    document.getElementById('liveFullLog').innerHTML = '';
  });
}

// ─── API ──────────────────────────────────────────────
async function api(url, method = 'GET', body = null) {
  try {
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body) opts.body = JSON.stringify(body);
    const r = await fetch(url, opts);
    return await r.json();
  } catch (e) { return { error: e.message }; }
}

// ─── SSE ──────────────────────────────────────────────
function startSSE() {
  if (sseSource) sseSource.close();
  sseSource = new EventSource('/api/live');
  sseSource.onmessage = e => {
    try { handleLiveEvent(JSON.parse(e.data)); } catch {}
  };
  sseSource.onerror = () => { setJavaStatus(false); };
}
function stopSSE() { if (sseSource) { sseSource.close(); sseSource = null; } }

function handleLiveEvent(ev) {
  const t = ev.type;
  if (t === 'status') {
    setJavaStatus(ev.javaConnected);
    updateOnlineCount(ev.onlineCount, ev.onlineUsers);
    return;
  }
  if (t === 'java_status') { setJavaStatus(ev.connected); return; }
  if (t === 'connected') {
    setJavaStatus(true);
    updateOnlineCount(ev.onlineCount);
    loadStats();
    return;
  }
  if (t === 'online_list') {
    updateOnlineCount(ev.count, ev.users);
    return;
  }
  if (t === 'user_joined') {
    updateOnlineCount(ev.onlineCount);
    addActivityLog({ icon: '🟢', text: `<strong>${esc(ev.username)}</strong> вошёл`, cls: 'join' });
    loadStats();
    return;
  }
  if (t === 'user_left') {
    updateOnlineCount(ev.onlineCount);
    addActivityLog({ icon: '🔴', text: `<strong>${esc(ev.username)}</strong> вышел`, cls: 'leave' });
    return;
  }
  if (t === 'new_message') {
    const who = ev.msgType === 'global' ? `в #global` : `→ ${esc(ev.to)}`;
    addActivityLog({ icon: '💬', text: `<strong>${esc(ev.from)}</strong> ${who}: ${esc(ev.content)}`, cls: 'msg' });
    return;
  }
}

function setJavaStatus(online) {
  const dot = document.getElementById('javaStatusDot');
  const txt = document.getElementById('javaStatusText');
  const ring = document.getElementById('serverRing');
  const icon = document.getElementById('ringIcon');
  const lbl  = document.getElementById('ringLabel');
  const srv  = document.getElementById('srv-status');
  dot.className = 'sdot ' + (online ? 'online' : 'offline');
  txt.textContent = online ? 'Java сервер онлайн' : 'Java сервер офлайн';
  if (ring)  ring.className  = 'server-ring ' + (online ? 'online' : '');
  if (icon)  icon.textContent = online ? '✅' : '●';
  if (lbl)   lbl.textContent  = online ? 'Онлайн' : 'Офлайн';
  if (srv)   srv.textContent  = online ? 'Работает' : 'Офлайн';
}

function updateOnlineCount(count, users) {
  const n = count || 0;
  document.getElementById('st-online').textContent = n;
  document.getElementById('onlineBadge').textContent = n;
  document.getElementById('srv-online').textContent = n;
  const liveCount = document.getElementById('liveOnlineCount');
  if (liveCount) liveCount.textContent = n;
  const sub = document.getElementById('st-online-names');
  if (sub) sub.textContent = n > 0 && users ? users.slice(0,3).join(', ') + (users.length > 3 ? '...' : '') : 'нет активных';
  const pill = document.getElementById('welcomeOnline');
  if (pill) pill.textContent = `${n} онлайн`;
  // Live page user chips
  const chips = document.getElementById('liveUserChips');
  if (chips && users) {
    chips.innerHTML = users.map(u =>
      `<div class="uchip"><span class="uchip-dot"></span>${esc(u)}</div>`
    ).join('');
  }
}

function addActivityLog(entry) {
  const ts = new Date().toLocaleTimeString('ru');
  activityLog.unshift({ ...entry, ts });
  if (activityLog.length > 60) activityLog.pop();

  const html = `<div class="alog-row ${entry.cls}"><span class="alog-icon">${entry.icon}</span><span class="alog-text">${entry.text}</span><span class="alog-time">${ts}</span></div>`;

  const dashLog = document.getElementById('dashActivityLog');
  if (dashLog) dashLog.insertAdjacentHTML('afterbegin', html);

  const liveLog = document.getElementById('liveFullLog');
  if (liveLog) liveLog.insertAdjacentHTML('afterbegin', html);
}

// ─── STATS ────────────────────────────────────────────
async function loadStats() {
  const d = await api('/api/stats');
  if (d.error) return;

  const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
  set('st-users',    d.totalUsers    ?? '—');
  set('st-messages', d.totalMessages ?? '—');
  set('st-friends',  d.totalFriends  ?? '—');
  set('srv-users',   d.totalUsers    ?? '—');
  set('srv-msgs',    d.totalMessages ?? '—');
  set('userCountBadge', d.totalUsers ?? 0);
  set('msgCountBadge',  d.totalMessages ?? 0);

  const pill = document.getElementById('welcomeUsers');
  if (pill) pill.textContent = `${d.totalUsers ?? '—'} пользователей`;

  updateOnlineCount(d.onlineCount);
  renderActivityChart(d.activity || []);
}

// ─── CHART ────────────────────────────────────────────
function renderActivityChart(activity) {
  const canvas = document.getElementById('activityChart');
  if (!canvas || !window.Chart) return;
  const labels = activity.map(a => a.day);
  const data   = activity.map(a => a.count);
  if (activityChart) { activityChart.data.labels = labels; activityChart.data.datasets[0].data = data; activityChart.update(); return; }
  activityChart = new Chart(canvas, {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label: 'Сообщений',
        data,
        backgroundColor: 'rgba(115,103,240,.6)',
        borderColor: '#7367F0',
        borderRadius: 6,
        borderWidth: 1,
        hoverBackgroundColor: 'rgba(115,103,240,.85)',
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: true,
      plugins: { legend: { display: false }, tooltip: { callbacks: { label: ctx => ` ${ctx.parsed.y} сообщений` } } },
      scales: {
        x: { grid: { color: 'rgba(59,66,83,.5)' }, ticks: { color: '#676D7D' } },
        y: { grid: { color: 'rgba(59,66,83,.5)' }, ticks: { color: '#676D7D', stepSize: 1 }, beginAtZero: true }
      }
    }
  });
}

// ─── USERS ────────────────────────────────────────────
const AVATAR_COLORS = ['#7367F0','#FF9F43','#28C76F','#00CFE8','#EA5455','#9E95F5','#CE9FFC'];
function avatarColor(str) { let h = 0; for (const c of (str||'?')) h = (h*31 + c.charCodeAt(0)) & 0xffff; return AVATAR_COLORS[h % AVATAR_COLORS.length]; }

async function loadUsers(page, search) {
  if (page) usersPage = page;
  const q = search !== undefined ? search : (document.getElementById('userSearch')?.value || '');
  const d = await api(`/api/users?page=${usersPage}&search=${encodeURIComponent(q)}`);
  if (d.error) return;
  document.getElementById('userTotal').textContent = d.total ?? 0;

  const tbody = document.getElementById('usersTbody');
  if (!d.users || !d.users.length) { tbody.innerHTML = '<tr><td colspan="9" class="tloading">Пользователи не найдены</td></tr>'; return; }

  tbody.innerHTML = d.users.map(u => {
    const av = `<div class="user-av" style="background:${avatarColor(u.username)}">${(u.username||'?')[0].toUpperCase()}</div>`;
    const privacy = u.privacy_mode === 'friends_only' || u.privacy_mode === 'friends'
      ? `<span class="privacy-badge pb-friends">Друзья</span>`
      : `<span class="privacy-badge pb-all">Все</span>`;
    const reg = u.created_at ? new Date(u.created_at).toLocaleDateString('ru') : '—';
    return `<tr>
      <td><span style="color:var(--muted)">#${u.id}</span></td>
      <td><div class="user-cell">${av}<span class="user-cell-name">${esc(u.username)}</span></div></td>
      <td style="color:var(--muted)">${esc(u.phone||'—')}</td>
      <td style="color:var(--muted);max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(u.status_text||'—')}</td>
      <td>${u.msg_count ?? 0}</td>
      <td>${u.friend_count ?? 0}</td>
      <td>${privacy}</td>
      <td style="color:var(--muted)">${reg}</td>
      <td><div class="actions-cell">
        <button class="btn-icon btn-danger" onclick="deleteUser(${u.id},'${esc(u.username)}')" title="Удалить">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/><path d="M9 6V4h6v2"/></svg>
        </button>
      </div></td>
    </tr>`;
  }).join('');

  renderPagination('usersPagination', usersPage, d.pages, p => loadUsers(p));
}

async function deleteUser(id, username) {
  if (!confirm(`Удалить пользователя ${username}? Это действие нельзя отменить.`)) return;
  showToast(`Удаление ${username}...`, 'info');
  const r = await api(`/api/users/${id}`, 'DELETE');
  if (r.ok) { showToast(`✅ Пользователь ${username} удалён`, 'success'); loadUsers(); loadStats(); }
  else showToast(`❌ ${r.error || 'Ошибка удаления'}`, 'error');
}

// ─── MESSAGES ─────────────────────────────────────────
async function loadMessages(page) {
  if (page) msgsPage = page;
  const q = document.getElementById('msgSearch')?.value || '';
  const d = await api(`/api/messages?page=${msgsPage}&type=${msgFilter}&search=${encodeURIComponent(q)}`);
  if (d.error) return;
  document.getElementById('msgTotal').textContent = d.total ?? 0;

  const tbody = document.getElementById('messagesTbody');
  if (!d.messages || !d.messages.length) { tbody.innerHTML = '<tr><td colspan="7" class="tloading">Сообщений не найдено</td></tr>'; return; }

  tbody.innerHTML = d.messages.map(m => {
    const isGlobal = m.is_global;
    const type = `<span class="type-badge ${isGlobal ? 'tb-global' : 'tb-private'}">${isGlobal ? 'Global' : 'Private'}</span>`;
    const ts = m.timestamp ? new Date(m.timestamp).toLocaleString('ru') : '—';
    const to = esc(m.receiver_username || (isGlobal ? '#global' : '—'));
    return `<tr>
      <td style="color:var(--muted)">#${m.id}</td>
      <td><span style="color:var(--primary);font-weight:600">${esc(m.sender_username||'?')}</span></td>
      <td style="color:var(--muted)">${to}</td>
      <td><div class="msg-content" title="${esc(m.content||'')}">${esc(m.content||'')}</div></td>
      <td>${type}</td>
      <td style="color:var(--muted);font-size:.8rem">${ts}</td>
      <td><button class="btn-icon btn-danger" onclick="deleteMessage(${m.id})" title="Удалить">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/><path d="M9 6V4h6v2"/></svg>
      </button></td>
    </tr>`;
  }).join('');

  renderPagination('msgPagination', msgsPage, d.pages, p => loadMessages(p));
}

async function deleteMessage(id) {
  if (!confirm(`Удалить сообщение #${id}?`)) return;
  const r = await api(`/api/messages/${id}`, 'DELETE');
  if (r.ok) { showToast('✅ Сообщение удалено', 'success'); loadMessages(); loadStats(); }
  else showToast(`❌ ${r.error || 'Ошибка удаления'}`, 'error');
}

// ─── FRIENDS ──────────────────────────────────────────
async function loadFriends(page) {
  if (page) friendsPage = page;
  const d = await api(`/api/friends?page=${friendsPage}&status=${friendFilter}`);
  if (d.error) return;
  document.getElementById('friendTotal').textContent = d.total ?? 0;

  const tbody = document.getElementById('friendsTbody');
  if (!d.rows || !d.rows.length) { tbody.innerHTML = '<tr><td colspan="4" class="tloading">Не найдено</td></tr>'; return; }

  tbody.innerHTML = d.rows.map(f => {
    const status = f.status === 'accepted'
      ? `<span class="privacy-badge pb-all">Друзья</span>`
      : `<span class="privacy-badge pb-friends">Ожидает</span>`;
    const date = f.created_at ? new Date(f.created_at).toLocaleDateString('ru') : '—';
    return `<tr>
      <td><span style="font-weight:600;color:#fff">${esc(f.user1)}</span></td>
      <td><span style="font-weight:600;color:#fff">${esc(f.user2)}</span></td>
      <td>${status}</td>
      <td style="color:var(--muted)">${date}</td>
    </tr>`;
  }).join('');

  renderPagination('friendsPagination', friendsPage, d.pages, p => loadFriends(p));
}

// ─── PAGINATION ───────────────────────────────────────
function renderPagination(containerId, current, total, onPage) {
  const el = document.getElementById(containerId);
  if (!el || total <= 1) { if (el) el.innerHTML = ''; return; }
  let html = '';
  if (current > 1) html += `<button onclick="(${onPage})(${current-1})">&laquo;</button>`;
  const start = Math.max(1, current-2), end = Math.min(total, current+2);
  for (let p = start; p <= end; p++)
    html += `<button class="${p === current ? 'active' : ''}" onclick="(${onPage})(${p})">${p}</button>`;
  if (current < total) html += `<button onclick="(${onPage})(${current+1})">&raquo;</button>`;
  el.innerHTML = html;
}

// ─── TOAST ────────────────────────────────────────────
function showToast(msg, type = 'info', ms = 3500) {
  const c = document.getElementById('toastContainer');
  const icons = { success: '✅', error: '❌', info: 'ℹ️' };
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.innerHTML = `<span>${icons[type]||''}</span><span>${msg}</span>`;
  c.appendChild(el);
  setTimeout(() => { el.style.transition = 'opacity .3s'; el.style.opacity = '0'; setTimeout(() => el.remove(), 300); }, ms);
}

// ─── UTILS ────────────────────────────────────────────
function esc(s) { return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
