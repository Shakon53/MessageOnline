require('dotenv').config();
const express   = require('express');
const session   = require('express-session');
const path      = require('path');
const fs        = require('fs');
const initSqlJs = require('sql.js');
const WebSocket = require('ws');
const EventEmitter = require('events');

const app  = express();
const PORT = process.env.PORT || 3001;
// Auto-detect DB path: try env, then several common locations
function findDbPath() {
  if (process.env.DB_PATH) return path.resolve(process.env.DB_PATH);
  const candidates = [
    path.join(__dirname, '../server/chat.db'),
    path.join(__dirname, '../../server/chat.db'),
    path.join(__dirname, '../chat.db'),
    '/data/chat.db',        // Railway volume
    'chat.db',              // same dir
  ];
  const found = candidates.find(p => fs.existsSync(p));
  if (found) { console.log(`📂 Auto-detected DB: ${found}`); return found; }
  return candidates[0]; // fallback, will show error later
}
const DB_PATH = findDbPath();
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin123';
const SESSION_SECRET = process.env.SESSION_SECRET || 'messageonline_secret';
const JAVA_WS_URL    = process.env.JAVA_WS_URL    || 'ws://localhost:8080';
const ADMIN_SECRET   = process.env.ADMIN_SECRET   || 'messageonline_admin_2025';

// ─── sql.js ───────────────────────────────────────────────────────────────────
let SQL = null;
const sqlReady = initSqlJs().then(s => { SQL = s; console.log('✅ sql.js готов'); });

function getDb() {
  if (!SQL) throw new Error('sql.js не инициализирован');
  if (!fs.existsSync(DB_PATH)) throw new Error(`БД не найдена: ${DB_PATH}`);
  return new SQL.Database(fs.readFileSync(DB_PATH));
}

function dbAll(db, sql, params = []) {
  const stmt = db.prepare(sql);
  stmt.bind(params);
  const rows = [];
  while (stmt.step()) {
    const cols = stmt.getColumnNames();
    const vals = stmt.get();
    const row  = {};
    cols.forEach((c, i) => row[c] = vals[i]);
    rows.push(row);
  }
  stmt.free();
  return rows;
}
function dbGet(db, sql, params = []) { return dbAll(db, sql, params)[0] || null; }
function dbRun(db, sql, params = []) { db.run(sql, params); return { changes: db.getRowsModified() }; }
function saveDb(db) { fs.writeFileSync(DB_PATH, Buffer.from(db.export())); }

// ─── Real-time event bus ──────────────────────────────────────────────────────
const events = new EventEmitter();
events.setMaxListeners(100);

// ─── WebSocket connection to Java server ──────────────────────────────────────
let javaWs = null;
let javaConnected = false;
let reconnectTimer = null;
const liveStats = { onlineCount: 0, onlineUsers: [] };
// Cache of data received from Java server via WebSocket
const javaData = { userCount: 0, messageCount: 0, users: [], recentMessages: [] };

function connectToJava() {
  if (javaWs) return;
  console.log(`🔌 Подключение к Java серверу: ${JAVA_WS_URL}`);
  try {
    javaWs = new WebSocket(JAVA_WS_URL);

    javaWs.on('open', () => {
      javaConnected = true;
      console.log('✅ Подключено к Java серверу');
      clearTimeout(reconnectTimer);
      // Login as admin
      javaWs.send(JSON.stringify({ type: 'ADMIN_LOGIN', key: ADMIN_SECRET }));
      events.emit('java_status', { connected: true });
    });

    javaWs.on('message', (data) => {
      try {
        const packet = JSON.parse(data.toString());
        handleJavaPacket(packet);
      } catch (e) {}
    });

    javaWs.on('close', () => {
      javaConnected = false;
      javaWs = null;
      liveStats.onlineCount = 0;
      liveStats.onlineUsers = [];
      events.emit('java_status', { connected: false });
      console.log('❌ Java сервер отключён. Переподключение через 5с...');
      reconnectTimer = setTimeout(connectToJava, 5000);
    });

    javaWs.on('error', (err) => {
      console.log('⚠️  Java WS ошибка:', err.message);
      javaWs = null;
    });
  } catch (e) {
    console.log('⚠️  Не удалось подключиться к Java:', e.message);
    reconnectTimer = setTimeout(connectToJava, 5000);
  }
}

function handleJavaPacket(packet) {
  const type = packet.type;
  if (type === 'ADMIN_LOGIN_SUCCESS') {
    liveStats.onlineCount = packet.onlineCount || 0;
    events.emit('live', { type: 'connected', onlineCount: liveStats.onlineCount });
    return;
  }
  if (type === 'ADMIN_STATS') {
    javaData.userCount      = packet.userCount      || 0;
    javaData.messageCount   = packet.messageCount   || 0;
    javaData.users          = packet.users          || [];
    javaData.recentMessages = packet.recentMessages || [];
    console.log(`📊 Статистика от Java: ${javaData.userCount} польз., ${javaData.messageCount} сообщ.`);
    return;
  }
  if (type === 'ADMIN_ACTION_RESULT') {
    const reqId = packet.reqId;
    if (reqId) events.emit(`admin_result_${reqId}`, { ok: packet.ok, error: packet.error });
    return;
  }
  if (type === 'ADMIN_EVENT') {
    const ev = packet.event;
    const p  = packet.payload || {};

    if (ev === 'online_list') {
      liveStats.onlineCount = packet.count || 0;
      liveStats.onlineUsers = (packet.users || []).map(u => u.username);
      events.emit('live', { type: 'online_list', count: liveStats.onlineCount, users: liveStats.onlineUsers });
      return;
    }
    if (ev === 'user_joined') {
      liveStats.onlineCount = p.onlineCount || liveStats.onlineCount + 1;
      if (!liveStats.onlineUsers.includes(p.username)) liveStats.onlineUsers.push(p.username);
      events.emit('live', { type: 'user_joined', username: p.username, onlineCount: liveStats.onlineCount });
      return;
    }
    if (ev === 'user_left') {
      liveStats.onlineCount = Math.max(0, liveStats.onlineCount - 1);
      liveStats.onlineUsers = liveStats.onlineUsers.filter(u => u !== p.username);
      events.emit('live', { type: 'user_left', username: p.username, onlineCount: liveStats.onlineCount });
      return;
    }
    if (ev === 'broadcast') {
      const inner = p;
      const innerType = inner.type || '';
      if (innerType === 'GLOBAL_MESSAGE' || innerType === 'PRIVATE_MESSAGE') {
        events.emit('live', {
          type: 'new_message',
          msgType: innerType === 'GLOBAL_MESSAGE' ? 'global' : 'private',
          from: inner.senderUsername || inner.sender || '?',
          to:   inner.receiverUsername || (innerType === 'GLOBAL_MESSAGE' ? '#global' : '?'),
          content: (inner.content || '').substring(0, 80),
          ts: Date.now()
        });
      }
    }
  }
}

// ─── Middleware ───────────────────────────────────────────────────────────────
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(session({
  secret: SESSION_SECRET,
  resave: false,
  saveUninitialized: false,
  cookie: { maxAge: 24 * 60 * 60 * 1000 }
}));
app.use(express.static(path.join(__dirname, 'public')));

function requireAuth(req, res, next) {
  if (req.session.authenticated) return next();
  res.status(401).json({ error: 'Не авторизован' });
}

// ─── AUTH ─────────────────────────────────────────────────────────────────────
app.post('/api/login', (req, res) => {
  const { password } = req.body;
  if (password === ADMIN_PASSWORD) {
    req.session.authenticated = true;
    res.json({ ok: true });
  } else {
    res.status(403).json({ error: 'Неверный пароль' });
  }
});
app.post('/api/logout', (req, res) => { req.session.destroy(); res.json({ ok: true }); });
app.get('/api/me', (req, res) => res.json({ authenticated: !!req.session.authenticated }));

// ─── Config (for frontend to know WS URL) ────────────────────────────────────
app.get('/api/config', requireAuth, (req, res) => {
  res.json({ javaWsUrl: JAVA_WS_URL, adminSecret: ADMIN_SECRET });
});

// ─── SSE — real-time stream to browser ───────────────────────────────────────
app.get('/api/live', requireAuth, (req, res) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders();

  // Send current status immediately
  res.write(`data: ${JSON.stringify({ type: 'status', javaConnected, onlineCount: liveStats.onlineCount, onlineUsers: liveStats.onlineUsers })}\n\n`);

  const onLive = (data) => {
    res.write(`data: ${JSON.stringify(data)}\n\n`);
  };
  const onStatus = (data) => {
    res.write(`data: ${JSON.stringify({ type: 'java_status', ...data })}\n\n`);
  };
  events.on('live', onLive);
  events.on('java_status', onStatus);

  // Heartbeat
  const heartbeat = setInterval(() => {
    res.write(`: ping\n\n`);
  }, 15000);

  req.on('close', () => {
    clearInterval(heartbeat);
    events.off('live', onLive);
    events.off('java_status', onStatus);
  });
});

// ─── STATS ────────────────────────────────────────────────────────────────────
app.get('/api/stats', requireAuth, (req, res) => {
  // Try local DB first; fall back to data received from Java server via WebSocket
  try {
    const db = getDb();
    const totalUsers      = dbGet(db, 'SELECT COUNT(*) as c FROM users').c;
    const totalMessages   = dbGet(db, 'SELECT COUNT(*) as c FROM messages').c;
    const globalMessages  = dbGet(db, 'SELECT COUNT(*) as c FROM messages WHERE is_global = 1').c;
    const privateMessages = dbGet(db, 'SELECT COUNT(*) as c FROM messages WHERE is_global = 0').c;
    const totalFriends    = dbGet(db, "SELECT COUNT(*) as c FROM friends WHERE status = 'accepted'").c;
    const pendingReqs     = dbGet(db, "SELECT COUNT(*) as c FROM friends WHERE status = 'pending'").c;
    const newUsersToday   = dbGet(db, 'SELECT COUNT(*) as c FROM users WHERE created_at > ?', [Date.now() - 86400000]).c;
    const newMsgToday     = dbGet(db, 'SELECT COUNT(*) as c FROM messages WHERE timestamp > ?', [Date.now() - 86400000]).c;
    const activity = [];
    for (let i = 6; i >= 0; i--) {
      const from  = Date.now() - (i + 1) * 86400000;
      const to    = Date.now() - i * 86400000;
      const count = dbGet(db, 'SELECT COUNT(*) as c FROM messages WHERE timestamp > ? AND timestamp <= ?', [from, to]).c;
      const d     = new Date(to);
      activity.push({ day: d.toLocaleDateString('ru', { weekday: 'short' }), count });
    }
    db.close();
    res.json({ totalUsers, totalMessages, globalMessages, privateMessages, totalFriends, pendingReqs, newUsersToday, newMsgToday, activity, onlineCount: liveStats.onlineCount });
  } catch (e) {
    // DB not available — use data received from Java server
    const activity = [];
    for (let i = 6; i >= 0; i--) {
      const d = new Date(Date.now() - i * 86400000);
      activity.push({ day: d.toLocaleDateString('ru', { weekday: 'short' }), count: 0 });
    }
    res.json({
      totalUsers: javaData.userCount,
      totalMessages: javaData.messageCount,
      globalMessages: 0, privateMessages: 0,
      totalFriends: 0, pendingReqs: 0,
      newUsersToday: 0, newMsgToday: 0,
      activity,
      onlineCount: liveStats.onlineCount,
      source: 'java_ws'
    });
  }
});

// ─── USERS ────────────────────────────────────────────────────────────────────
app.get('/api/users', requireAuth, (req, res) => {
  try {
    const db     = getDb();
    const search = req.query.search ? `%${req.query.search}%` : '%';
    const page   = parseInt(req.query.page) || 1;
    const limit  = 20;
    const offset = (page - 1) * limit;
    const users  = dbAll(db, `
      SELECT u.id, u.username, u.phone, u.status_text, u.created_at,
             COALESCE(u.privacy_mode,'all') as privacy_mode,
             (SELECT COUNT(*) FROM messages m WHERE m.sender_id = u.id) as msg_count,
             (SELECT COUNT(*) FROM friends f WHERE (f.user_id=u.id OR f.friend_id=u.id) AND f.status='accepted') as friend_count
      FROM users u WHERE u.username LIKE ? OR u.phone LIKE ?
      ORDER BY u.id DESC LIMIT ? OFFSET ?
    `, [search, search, limit, offset]);
    const total = dbGet(db, 'SELECT COUNT(*) as c FROM users WHERE username LIKE ? OR phone LIKE ?', [search, search]).c;
    db.close();
    res.json({ users, total, page, pages: Math.ceil(total / limit) });
  } catch (e) {
    // DB not available — use data from Java server
    const search = (req.query.search || '').toLowerCase();
    let users = javaData.users;
    if (search) users = users.filter(u => u.username && u.username.toLowerCase().includes(search));
    const page  = parseInt(req.query.page) || 1;
    const limit = 20;
    const total = users.length;
    const slice = users.slice((page - 1) * limit, page * limit).map(u => ({
      id: u.id, username: u.username, phone: u.phone,
      status_text: u.statusText, created_at: u.createdAt,
      privacy_mode: u.privacyMode, msg_count: 0, friend_count: 0
    }));
    res.json({ users: slice, total, page, pages: Math.ceil(total / limit), source: 'java_ws' });
  }
});

app.get('/api/users/:id', requireAuth, (req, res) => {
  try {
    const db = getDb(); const id = parseInt(req.params.id);
    const user = dbGet(db, 'SELECT id,username,phone,status_text,created_at,COALESCE(privacy_mode,"all") as privacy_mode FROM users WHERE id=?', [id]);
    if (!user) return res.status(404).json({ error: 'Не найден' });
    const recentMsgs = dbAll(db, 'SELECT content,timestamp,is_global,COALESCE(receiver_username,"") as receiver_username,COALESCE(message_type,"text") as message_type FROM messages WHERE sender_id=? ORDER BY timestamp DESC LIMIT 10', [id]);
    const friends = dbAll(db, 'SELECT u.id,u.username FROM friends f JOIN users u ON u.id=CASE WHEN f.user_id=? THEN f.friend_id ELSE f.user_id END WHERE (f.user_id=? OR f.friend_id=?) AND f.status="accepted"', [id,id,id]);
    db.close();
    res.json({ user, recentMsgs, friends });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.delete('/api/users/:id', requireAuth, (req, res) => {
  const id = parseInt(req.params.id);
  if (javaConnected && javaWs) {
    const reqId = `du_${Date.now()}_${id}`;
    const timer = setTimeout(() => {
      events.removeAllListeners(`admin_result_${reqId}`);
      javaData.users = javaData.users.filter(u => u.id !== id);
      javaData.userCount = Math.max(0, javaData.userCount - 1);
      res.json({ ok: true });
    }, 4000);
    events.once(`admin_result_${reqId}`, (result) => {
      clearTimeout(timer);
      if (result.ok) {
        javaData.users = javaData.users.filter(u => u.id !== id);
        javaData.userCount = Math.max(0, javaData.userCount - 1);
        res.json({ ok: true });
      } else {
        res.status(500).json({ error: result.error || 'Ошибка удаления' });
      }
    });
    javaWs.send(JSON.stringify({ type: 'ADMIN_DELETE_USER', userId: id, reqId }));
    return;
  }
  try {
    const db = getDb();
    dbRun(db, 'DELETE FROM messages WHERE sender_id=? OR receiver_id=?', [id,id]);
    dbRun(db, 'DELETE FROM friends WHERE user_id=? OR friend_id=?', [id,id]);
    const info = dbRun(db, 'DELETE FROM users WHERE id=?', [id]);
    if (!info.changes) { db.close(); return res.status(404).json({ error: 'Не найден' }); }
    saveDb(db); db.close();
    res.json({ ok: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ─── MESSAGES ─────────────────────────────────────────────────────────────────
app.get('/api/messages', requireAuth, (req, res) => {
  try {
    const db     = getDb();
    const type   = req.query.type || 'all';
    const search = req.query.search ? `%${req.query.search}%` : '%';
    const page   = parseInt(req.query.page) || 1;
    const limit  = 30, offset = (page-1)*limit;
    let where = 'WHERE m.content LIKE ?'; const args = [search];
    if (type === 'global')  where += ' AND m.is_global=1';
    if (type === 'private') where += ' AND m.is_global=0';
    const messages = dbAll(db, `SELECT m.id,m.sender_username,COALESCE(m.receiver_username,"") as receiver_username,m.content,m.timestamp,m.is_global,COALESCE(m.message_type,"text") as message_type,COALESCE(m.content_edited,"") as content_edited FROM messages m ${where} ORDER BY m.timestamp DESC LIMIT ? OFFSET ?`, [...args, limit, offset]);
    const total = dbGet(db, `SELECT COUNT(*) as c FROM messages m ${where}`, args).c;
    db.close();
    res.json({ messages, total, page, pages: Math.ceil(total/limit) });
  } catch (e) {
    // DB not available — use recent messages from Java server
    let msgs = javaData.recentMessages;
    const search = (req.query.search || '').toLowerCase();
    if (search) msgs = msgs.filter(m => m.content && m.content.toLowerCase().includes(search));
    const page = parseInt(req.query.page) || 1;
    const limit = 30;
    const total = msgs.length;
    const slice = msgs.slice((page-1)*limit, page*limit).map(m => ({
      id: m.id, sender_username: m.senderUsername,
      receiver_username: m.receiverUsername || '',
      content: m.content, timestamp: m.timestamp,
      is_global: m.type === 'global' ? 1 : 0,
      message_type: 'text', content_edited: ''
    }));
    res.json({ messages: slice, total, page, pages: Math.ceil(total/limit), source: 'java_ws' });
  }
});

app.delete('/api/messages/:id', requireAuth, (req, res) => {
  const id = parseInt(req.params.id);
  if (javaConnected && javaWs) {
    const reqId = `dm_${Date.now()}_${id}`;
    const timer = setTimeout(() => {
      events.removeAllListeners(`admin_result_${reqId}`);
      javaData.recentMessages = javaData.recentMessages.filter(m => m.id !== id);
      res.json({ ok: true });
    }, 4000);
    events.once(`admin_result_${reqId}`, (result) => {
      clearTimeout(timer);
      if (result.ok) {
        javaData.recentMessages = javaData.recentMessages.filter(m => m.id !== id);
        res.json({ ok: true });
      } else {
        res.status(500).json({ error: result.error || 'Ошибка удаления' });
      }
    });
    javaWs.send(JSON.stringify({ type: 'ADMIN_DELETE_MESSAGE', messageId: id, reqId }));
    return;
  }
  try {
    const db = getDb();
    const info = dbRun(db, 'DELETE FROM messages WHERE id=?', [id]);
    if (!info.changes) { db.close(); return res.status(404).json({ error: 'Не найдено' }); }
    saveDb(db); db.close();
    res.json({ ok: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ─── FRIENDS ──────────────────────────────────────────────────────────────────
app.get('/api/friends', requireAuth, (req, res) => {
  try {
    const db = getDb();
    const status = req.query.status || 'accepted';
    const page   = parseInt(req.query.page) || 1;
    const limit  = 30, offset = (page-1)*limit;
    const rows  = dbAll(db, 'SELECT f.id,f.status,f.created_at,u1.username as user1,u2.username as user2 FROM friends f JOIN users u1 ON u1.id=f.user_id JOIN users u2 ON u2.id=f.friend_id WHERE f.status=? ORDER BY f.created_at DESC LIMIT ? OFFSET ?', [status, limit, offset]);
    const total = dbGet(db, 'SELECT COUNT(*) as c FROM friends WHERE status=?', [status]).c;
    db.close();
    res.json({ rows, total, page, pages: Math.ceil(total/limit) });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ─── DB INFO ──────────────────────────────────────────────────────────────────
app.get('/api/dbinfo', requireAuth, (req, res) => {
  try {
    const stats = fs.statSync(DB_PATH);
    res.json({ path: DB_PATH, size: (stats.size/1024).toFixed(1)+' KB', modified: stats.mtime, javaConnected });
  } catch (e) {
    res.json({ path: DB_PATH, size: 'N/A', modified: null, javaConnected,
               note: 'База данных находится на Java сервере. Данные передаются через WebSocket.' });
  }
});

// ─── SPA ──────────────────────────────────────────────────────────────────────
app.get('*', (req, res) => res.sendFile(path.join(__dirname, 'public', 'index.html')));

// ─── Start (after sql.js is ready) ───────────────────────────────────────────
sqlReady.then(() => {
  app.listen(PORT, () => {
    console.log(`\n✅  Admin panel: http://localhost:${PORT}`);
    console.log(`📂  DB: ${DB_PATH} (${fs.existsSync(DB_PATH) ? 'найдена ✓' : 'НЕ НАЙДЕНА ✗'})`);
    console.log(`🔑  Пароль: ${ADMIN_PASSWORD}`);
    console.log(`🔌  Java WS: ${JAVA_WS_URL}\n`);
    connectToJava();
  });
}).catch(e => { console.error('Ошибка sql.js:', e); process.exit(1); });
