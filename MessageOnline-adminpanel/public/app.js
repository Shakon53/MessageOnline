/* ════════════════════════════════════════════════════════════════
   MessageOnline Admin Panel — Frontend v2
   ════════════════════════════════════════════════════════════════ */

let usersPage    = 1;
let messagesPage = 1;
let friendsPage  = 1;
let sseSource    = null;
let onlineCount  = 0;
const activityLog = []; // последние 50 событий

// ─── Init ─────────────────────────────────────────────────────────────────────
window.addEventListener('DOMContentLoaded', async () => {
  const me = await api('/api/me');
  if (me.authenticated) showApp();
  document.getElementById('loginPassword').addEventListener('keydown', e => {
    if (e.key === 'Enter') doLogin();
  });
});

// ─── Auth ──────────────────────────────────────────────────────────────────────
async function doLogin() {
  const pw  = document.getElementById('loginPassword').value;
  const err = document.getElementById('loginError');
  err.style.display = 'none';
  try {
    await api('/api/login', 'POST', { password: pw });
    showApp();
  } catch (e) {
    err.textContent = 'Неверный пароль';
    err.style.display = 'block';
  }
}

async function doLogout() {
  if (sseSource) { sseSource.close(); sseSource = null; }
  await api('/api/logout', 'POST');
  document.getElementById('app').style.display = 'none';
  document.getElementById('loginScreen').style.display = 'flex';
  document.getElementById('loginPassword').value = '';
}

function showApp() {
  document.getElementById('loginScreen').style.display = 'none';
  document.getElementById('app').style.display = 'flex';
  startSSE();
  navigateTo('dashboard');
}

// ─── SSE Real-time ────────────────────────────────────────────────────────────
function startSSE() {
  if (sseSource) sseSource.close();
  sseSource = new EventSource('/api/live');

  sseSource.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data);
      handleLiveEvent(data);
    } catch (_) {}
  };

  sseSource.onerror = () => {
    setJavaStatus(false);
  };
}

function handleLiveEvent(data) {
  switch (data.type) {
    case 'status':
      setJavaStatus(data.javaConnected);
      updateOnlineCount(data.onlineCount || 0);
      break;

    case 'java_status':
      setJavaStatus(data.connected);
      break;

    case 'connected':
      setJavaStatus(true);
      updateOnlineCount(data.onlineCount || 0);
      break;

    case 'online_list':
      updateOnlineCount(data.count || 0);
      break;

    case 'user_joined':
      updateOnlineCount(data.onlineCount || onlineCount + 1);
      pushActivity('join', `🟢 <b>${esc(data.username)}</b> вошёл`, data.username);
      break;

    case 'user_left':
      updateOnlineCount(data.onlineCount || Math.max(0, onlineCount - 1));
      pushActivity('leave', `🔴 <b>${esc(data.username)}</b> вышел`, data.username);
      break;

    case 'new_message':
      const icon = data.msgType === 'global' ? '🌐' : '🔒';
      const preview = data.content.length > 50 ? data.content.substring(0, 50) + '…' : data.content;
      pushActivity('msg', `${icon} <b>${esc(data.from)}</b>: ${esc(preview)}`);
      // Refresh messages table if on that page
      if (document.getElementById('page-messages').classList.contains('active')) {
        loadMessages();
      }
      break;
  }
}

function setJavaStatus(connected) {
  const dot  = document.getElementById('javaStatusDot');
  const text = document.getElementById('javaStatusText');
  if (!dot) return;
  dot.className  = 'status-dot ' + (connected ? 'online' : 'offline');
  text.textContent = connected ? 'Сервер подключён' : 'Сервер недоступен';
}

function updateOnlineCount(count) {
  onlineCount = count;
  const el = document.getElementById('onlineCountBadge');
  if (el) el.textContent = count + ' онлайн';
  const st = document.getElementById('st-online');
  if (st) st.textContent = count;
}

function pushActivity(type, html, username) {
  const now = new Date().toLocaleTimeString('ru', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  activityLog.unshift({ type, html, time: now });
  if (activityLog.length > 50) activityLog.pop();
  renderActivityLog();
}

function renderActivityLog() {
  const el = document.getElementById('activityLog');
  if (!el) return;
  if (!activityLog.length) {
    el.innerHTML = '<div class="activity-empty">Ожидание событий…</div>';
    return;
  }
  el.innerHTML = activityLog.map(e => `
    <div class="activity-row activity-${e.type}">
      <span class="activity-time">${e.time}</span>
      <span>${e.html}</span>
    </div>
  `).join('');
}

// ─── Navigation ───────────────────────────────────────────────────────────────
document.querySelectorAll('.nav-item').forEach(el => {
  el.addEventListener('click', e => { e.preventDefault(); navigateTo(el.dataset.page); });
});

function navigateTo(page) {
  document.querySelectorAll('.nav-item').forEach(el => el.classList.toggle('active', el.dataset.page === page));
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  document.getElementById('page-' + page).classList.add('active');
  if (page === 'dashboard') loadDashboard();
  if (page === 'users')     { usersPage = 1; loadUsers(); }
  if (page === 'messages')  { messagesPage = 1; loadMessages(); }
  if (page === 'friends')   { friendsPage = 1; loadFriends(); }
}

// ─── Dashboard ────────────────────────────────────────────────────────────────
async function loadDashboard() {
  try {
    const [stats, info] = await Promise.all([api('/api/stats'), api('/api/dbinfo')]);

    set('st-users',       stats.totalUsers);
    set('st-messages',    stats.totalMessages);
    set('st-global',      stats.globalMessages);
    set('st-private',     stats.privateMessages);
    set('st-friends',     stats.totalFriends);
    set('st-pending',     stats.pendingReqs);
    set('st-online',      stats.onlineCount);
    set('st-users-today', stats.newUsersToday  > 0 ? `+${stats.newUsersToday} сегодня`  : '');
    set('st-msg-today',   stats.newMsgToday    > 0 ? `+${stats.newMsgToday} сегодня`    : '');
    set('dbInfo',         `📂 ${info.size}`);
    setJavaStatus(info.javaConnected);
    updateOnlineCount(stats.onlineCount || 0);

    renderChart(stats.activity);
    renderActivityLog();
  } catch (e) {
    showToast('Ошибка: ' + e.message, 'error');
  }
}

function renderChart(activity) {
  const wrap = document.querySelector('.chart-wrap');
  if (!wrap) return;
  wrap.innerHTML = '';
  const max = Math.max(...activity.map(a => a.count), 1);
  const barWrap = document.createElement('div');
  barWrap.className = 'bar-wrap';
  activity.forEach(a => {
    const col = document.createElement('div');
    col.className = 'bar-col';
    const pct = Math.max((a.count / max) * 100, 4);
    col.innerHTML = `
      <div class="bar-val">${a.count || ''}</div>
      <div class="bar" style="height:${pct}%"></div>
      <div class="bar-label">${a.day}</div>
    `;
    barWrap.appendChild(col);
  });
  wrap.appendChild(barWrap);
}

// ─── Users ────────────────────────────────────────────────────────────────────
async function loadUsers() {
  const search = document.getElementById('userSearch')?.value || '';
  try {
    const data  = await api(`/api/users?page=${usersPage}&search=${encodeURIComponent(search)}`);
    const tbody = document.getElementById('usersBody');
    if (!data.users.length) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="9">Пользователи не найдены</td></tr>';
    } else {
      tbody.innerHTML = data.users.map(u => `
        <tr>
          <td class="text-muted">#${u.id}</td>
          <td>
            <div class="user-cell">
              <div class="avatar" onclick="openUser(${u.id})" style="background:${avatarColor(u.username)}">
                ${u.username[0].toUpperCase()}
              </div>
              <div>
                <div class="user-name">${esc(u.username)}</div>
              </div>
            </div>
          </td>
          <td class="text-muted">${esc(u.phone)}</td>
          <td class="truncate">${esc(u.status_text || '—')}</td>
          <td>${u.msg_count}</td>
          <td>${u.friend_count}</td>
          <td><span class="badge ${u.privacy_mode === 'friends_only' ? 'badge-warn' : 'badge-success'}">
            ${u.privacy_mode === 'friends_only' ? 'Друзья' : 'Все'}
          </span></td>
          <td class="text-muted">${fmtDate(u.created_at)}</td>
          <td>
            <button class="btn btn-ghost btn-icon btn-sm" onclick="openUser(${u.id})">🔍</button>
            <button class="btn btn-ghost btn-icon btn-sm text-danger" onclick="confirmDelete('user',${u.id},'${esc(u.username)}')">🗑</button>
          </td>
        </tr>
      `).join('');
    }
    renderPagination('usersPagination', data.page, data.pages, p => { usersPage = p; loadUsers(); });
  } catch (e) { showToast('Ошибка загрузки', 'error'); }
}

async function openUser(id) {
  try {
    const { user, recentMsgs, friends } = await api(`/api/users/${id}`);
    set('modalUsername', esc(user.username));
    set('modalPhone',    esc(user.phone));
    const av = document.getElementById('modalAvatar');
    av.textContent = user.username[0].toUpperCase();
    av.style.background = avatarColor(user.username);
    document.getElementById('modalMeta').innerHTML = `
      <div class="modal-meta-item"><span>ID</span> #${user.id}</div>
      <div class="modal-meta-item"><span>Статус</span> ${esc(user.status_text || '—')}</div>
      <div class="modal-meta-item"><span>Приватность</span> ${user.privacy_mode}</div>
      <div class="modal-meta-item"><span>Регистрация</span> ${fmtDate(user.created_at)}</div>
    `;
    document.getElementById('modalMessages').innerHTML = recentMsgs.length
      ? recentMsgs.map(m => `
        <div style="padding:6px 0;border-bottom:1px solid var(--border);display:flex;gap:8px;align-items:center">
          <span class="badge ${m.is_global ? 'badge-global' : 'badge-private'}" style="font-size:10px">
            ${m.is_global ? '🌐' : `→ ${esc(m.receiver_username)}`}
          </span>
          <span class="truncate" style="flex:1">${esc(m.content)}</span>
          <span class="text-muted" style="font-size:11px;white-space:nowrap">${fmtDate(m.timestamp)}</span>
        </div>`).join('')
      : '<p class="text-muted" style="font-size:13px">Нет сообщений</p>';
    document.getElementById('modalFriends').innerHTML = friends.length
      ? `<div style="display:flex;flex-wrap:wrap;gap:8px">${friends.map(f => `<span class="badge badge-info">👤 ${esc(f.username)}</span>`).join('')}</div>`
      : '<p class="text-muted" style="font-size:13px">Нет друзей</p>';
    document.getElementById('modalDeleteBtn').onclick = () => confirmDelete('user', user.id, user.username);
    document.getElementById('userModal').style.display = 'flex';
  } catch (e) { showToast('Ошибка загрузки', 'error'); }
}

function closeUserModal() { document.getElementById('userModal').style.display = 'none'; }
function closeModal(e)    { if (e.target.classList.contains('modal-overlay')) closeUserModal(); }

// ─── Messages ─────────────────────────────────────────────────────────────────
async function loadMessages() {
  const type   = document.getElementById('msgType')?.value   || 'all';
  const search = document.getElementById('msgSearch')?.value || '';
  try {
    const data  = await api(`/api/messages?page=${messagesPage}&type=${type}&search=${encodeURIComponent(search)}`);
    const tbody = document.getElementById('messagesBody');
    if (!data.messages.length) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="7">Сообщений не найдено</td></tr>';
    } else {
      tbody.innerHTML = data.messages.map(m => `
        <tr>
          <td class="text-muted">#${m.id}</td>
          <td><b>${esc(m.sender_username)}</b></td>
          <td class="text-muted">${m.is_global ? '—' : esc(m.receiver_username)}</td>
          <td>
            <span class="badge ${m.is_global ? 'badge-global' : 'badge-private'}">
              ${m.is_global ? '🌐 Глобальный' : '🔒 Личный'}
            </span>
            ${m.message_type !== 'text' ? `<span class="badge badge-info" style="margin-left:4px">${m.message_type}</span>` : ''}
          </td>
          <td>
            <div class="msg-preview ${m.message_type !== 'text' ? 'media' : ''}">
              ${m.message_type === 'image' ? '🖼 Изображение' : m.message_type === 'audio' ? '🎵 Аудио' : esc(m.content_edited || m.content)}
              ${m.content_edited ? '<span class="text-muted" style="font-size:11px"> (ред.)</span>' : ''}
            </div>
          </td>
          <td class="text-muted" style="white-space:nowrap">${fmtDateTime(m.timestamp)}</td>
          <td>
            <button class="btn btn-ghost btn-icon btn-sm text-danger" onclick="confirmDelete('message',${m.id},null)">🗑</button>
          </td>
        </tr>`).join('');
    }
    renderPagination('msgPagination', data.page, data.pages, p => { messagesPage = p; loadMessages(); });
  } catch (e) { showToast('Ошибка загрузки', 'error'); }
}

// ─── Friends ──────────────────────────────────────────────────────────────────
async function loadFriends() {
  const status = document.getElementById('friendStatus')?.value || 'accepted';
  try {
    const data  = await api(`/api/friends?page=${friendsPage}&status=${status}`);
    const tbody = document.getElementById('friendsBody');
    if (!data.rows.length) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="5">Записей нет</td></tr>';
    } else {
      tbody.innerHTML = data.rows.map(r => `
        <tr>
          <td class="text-muted">#${r.id}</td>
          <td><span class="badge badge-info">👤 ${esc(r.user1)}</span></td>
          <td><span class="badge badge-info">👤 ${esc(r.user2)}</span></td>
          <td><span class="badge ${r.status === 'accepted' ? 'badge-success' : 'badge-warn'}">
            ${r.status === 'accepted' ? '✅ Друзья' : '⏳ Запрос'}
          </span></td>
          <td class="text-muted">${fmtDate(r.created_at)}</td>
        </tr>`).join('');
    }
    renderPagination('friendsPagination', data.page, data.pages, p => { friendsPage = p; loadFriends(); });
  } catch (e) { showToast('Ошибка', 'error'); }
}

// ─── Confirm Delete ───────────────────────────────────────────────────────────
function confirmDelete(type, id, name) {
  const modal = document.getElementById('confirmModal');
  set('confirmTitle', type === 'user' ? 'Удалить пользователя?' : 'Удалить сообщение?');
  set('confirmText', type === 'user'
    ? `Аккаунт «${name}» и все данные будут удалены безвозвратно.`
    : 'Сообщение будет удалено из базы данных.');
  document.getElementById('confirmOkBtn').onclick = async () => {
    modal.style.display = 'none';
    try {
      await api(type === 'user' ? `/api/users/${id}` : `/api/messages/${id}`, 'DELETE');
      showToast(type === 'user' ? 'Пользователь удалён' : 'Сообщение удалено', 'success');
      closeUserModal();
      if (type === 'user')    loadUsers();
      if (type === 'message') loadMessages();
      loadDashboard();
    } catch (e) { showToast('Ошибка удаления', 'error'); }
  };
  modal.style.display = 'flex';
}

// ─── Pagination ───────────────────────────────────────────────────────────────
function renderPagination(containerId, current, total, onPage) {
  const el = document.getElementById(containerId);
  if (total <= 1) { el.innerHTML = ''; return; }
  let html = '';
  if (current > 1) html += `<button class="page-btn" onclick="(${onPage})(${current-1})">‹</button>`;
  for (let i = 1; i <= total; i++) {
    if (i === 1 || i === total || Math.abs(i-current) <= 1)
      html += `<button class="page-btn ${i===current?'active':''}" onclick="(${onPage})(${i})">${i}</button>`;
    else if (Math.abs(i-current) === 2)
      html += `<span class="text-muted" style="padding:0 4px">…</span>`;
  }
  if (current < total) html += `<button class="page-btn" onclick="(${onPage})(${current+1})">›</button>`;
  el.innerHTML = html;
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
async function api(url, method = 'GET', body = null) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  if (body) opts.body = JSON.stringify(body);
  const r = await fetch(url, opts);
  if (r.status === 401) {
    document.getElementById('app').style.display = 'none';
    document.getElementById('loginScreen').style.display = 'flex';
    throw new Error('Unauthorized');
  }
  const data = await r.json();
  if (!r.ok) throw new Error(data.error || 'Ошибка');
  return data;
}

function set(id, val) { const el = document.getElementById(id); if (el) el.textContent = val; }
function esc(str) {
  if (!str) return '';
  return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
function fmtDate(ts)     { if (!ts) return '—'; return new Date(ts).toLocaleDateString('ru', {day:'numeric',month:'short',year:'numeric'}); }
function fmtDateTime(ts) { if (!ts) return '—'; return new Date(ts).toLocaleString('ru', {day:'numeric',month:'short',hour:'2-digit',minute:'2-digit'}); }
const COLORS = ['#6366F1','#8B5CF6','#EC4899','#14B8A6','#F59E0B','#22C55E','#3B82F6'];
function avatarColor(name) { let h=0; for(let i=0;i<(name||'').length;i++) h+=name.charCodeAt(i); return COLORS[h%COLORS.length]; }

function showToast(msg, type = 'success') {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.className = `toast ${type} show`;
  clearTimeout(t._timer);
  t._timer = setTimeout(() => t.classList.remove('show'), 3000);
}

function debounce(fn, delay) {
  let timer;
  return function(...args) { clearTimeout(timer); timer = setTimeout(() => fn.apply(this, args), delay); };
}
