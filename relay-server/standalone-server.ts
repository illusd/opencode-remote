// Standalone WebSocket Relay Server
// 可部署到 Fly.io, Railway, Render, Heroku 等免費平台
// 比 Vercel 更適合 WebSocket 長連線

import { WebSocketServer, WebSocket } from 'ws';
import { createServer, IncomingMessage } from 'http';
import { parse } from 'url';
import { v4 as uuidv4 } from 'uuid';

import {
  BaseMessage,
  MessageType,
  AuthPayload,
  CommandPayload,
  OutputPayload,
  HeartbeatPayload,
  ErrorPayload,
  SessionInfo,
  ProjectInfo,
  FileNode,
  createMessage,
  isAuthMessage,
  isCommandMessage,
  isOutputMessage,
} from './lib/protocol';
import {
  generateToken,
  verifyToken,
  createSessionBinding,
  getSessionBinding,
  touchSession,
  getClientSessions,
  removeSessionBinding,
  findSessionByTokenHash,
  TokenPair,
  SessionBinding,
} from './lib/auth';

// ===== 配置 =====
const PORT = parseInt(process.env.PORT || '8080', 10);
const HEARTBEAT_INTERVAL = 25000;
const HEARTBEAT_TIMEOUT = 60000;
const TOKEN_TTL = 10 * 60 * 1000;

// ===== 狀態管理 =====
interface ConnectedClient {
  id: string;
  ws: WebSocket;
  type: 'mobile' | 'desktop';
  state: 'connecting' | 'authenticated' | 'ready' | 'disconnected';
  tokenPair?: TokenPair;
  sessionId?: string;
  lastHeartbeat: number;
  messageQueue: BaseMessage[];
  info?: {
    name: string;
    version: string;
    platform: string;
  };
}

const clients = new Map<string, ConnectedClient>();
const desktopClients = new Map<string, ConnectedClient>(); // sessionId -> desktop
const mobileClients = new Map<string, ConnectedClient>();  // sessionId -> mobile
const sessionBindings = new Map<string, SessionBinding>();

// ===== HTTP Server + WebSocket =====
const server = createServer((req, res) => {
  // 健康檢查端點
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ 
      status: 'ok', 
      clients: clients.size,
      sessions: desktopClients.size,
      uptime: process.uptime(),
    }));
    return;
  }
  
  // Token 產生端點 (供桌面端呼叫)
  if (req.url === '/api/token' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      const { token, expiresIn } = generateToken();
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ token, expiresIn: expiresIn - Date.now() }));
    });
    return;
  }
  
  // 狀態端點
  if (req.url === '/api/stats') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      totalClients: clients.size,
      mobileClients: Array.from(clients.values()).filter(c => c.type === 'mobile').length,
      desktopClients: Array.from(clients.values()).filter(c => c.type === 'desktop').length,
      activeSessions: desktopClients.size,
    }));
    return;
  }
  
  res.writeHead(404);
  res.end('Not Found');
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws: WebSocket, req: IncomingMessage) => {
  const url = parse(req.url || '', true);
  const clientId = url.query.clientId as string || uuidv4();
  const clientType = (url.query.type as 'mobile' | 'desktop') || 'mobile';
  const version = url.query.version as string || 'unknown';
  const platform = url.query.platform as string || 'unknown';
  
  console.log(`[WS] New connection: ${clientType} ${clientId} v${version} (${platform})`);
  
  const client: ConnectedClient = {
    id: clientId,
    ws,
    type: clientType,
    state: 'connecting',
    lastHeartbeat: Date.now(),
    messageQueue: [],
    info: { name: clientType, version, platform },
  };
  
  clients.set(clientId, client);
  
  // 發送歡迎訊息
  ws.send(JSON.stringify(createMessage('welcome', {
    message: 'Connected to OpenCode Relay Server',
    clientId,
    serverTime: Date.now(),
  })));
  
  ws.on('message', (data: Buffer) => {
    try {
      const msg: BaseMessage = JSON.parse(data.toString());
      routeMessage(client, msg);
    } catch (e) {
      console.error('[WS] Parse error:', e);
      sendError(ws, 'parse_error', 'Invalid JSON message');
    }
  });
  
  ws.on('close', (code, reason) => {
    console.log(`[WS] Disconnected: ${clientType} ${clientId} (code: ${code})`);
    handleDisconnect(client);
  });
  
  ws.on('error', (error) => {
    console.error(`[WS] Error for ${clientId}:`, error);
    handleDisconnect(client);
  });
});

// ===== 訊息路由 =====
function routeMessage(client: ConnectedClient, msg: BaseMessage): void {
  switch (msg.type) {
    case 'auth':
      handleAuth(client, msg as BaseMessage<AuthPayload>);
      break;
    case 'command':
      handleCommand(client, msg as BaseMessage<CommandPayload>);
      break;
    case 'output':
      handleOutput(client, msg as BaseMessage<OutputPayload>);
      break;
    case 'heartbeat':
      handleHeartbeat(client, msg as BaseMessage<HeartbeatPayload>);
      break;
    case 'session_list':
      handleSessionList(client, msg);
      break;
    case 'session_create':
      handleSessionCreate(client, msg);
      break;
    case 'session_close':
      handleSessionClose(client, msg);
      break;
    case 'project_list':
      handleProjectList(client, msg);
      break;
    case 'file_tree':
      handleFileTree(client, msg);
      break;
    case 'file_read':
      handleFileRead(client, msg);
      break;
    case 'file_write':
      handleFileWrite(client, msg);
      break;
    default:
      console.log('[Route] Unknown type:', msg.type);
      sendError(client.ws, 'unknown_type', `Unknown message type: ${msg.type}`, msg.requestId);
  }
}

// ===== 認證處理 =====
function handleAuth(client: ConnectedClient, msg: BaseMessage<AuthPayload>): void {
  const { token, clientType, clientId, version } = msg.payload;
  
  if (clientId !== client.id) {
    sendError(client.ws, 'client_id_mismatch', 'Client ID mismatch', msg.requestId);
    return;
  }
  
  if (clientType !== client.type) {
    sendError(client.ws, 'type_mismatch', 'Client type mismatch', msg.requestId);
    return;
  }
  
  const tokenPair = verifyToken(token, clientId, clientType);
  if (!tokenPair) {
    sendError(client.ws, 'invalid_token', 'Token 無效、已過期或已被使用', msg.requestId);
    closeClient(client.id, 'auth_failed');
    return;
  }
  
  client.tokenPair = tokenPair;
  client.state = 'authenticated';
  
  // 檢查是否已有配對會話
  const crypto = require('crypto');
  const tokenHash = crypto.createHash('sha256').update(token).digest('hex');
  const existingSession = findSessionByTokenHash(tokenHash);
  
  if (existingSession) {
    bindSession(client, existingSession.sessionId);
    send(client.ws, createMessage('auth_response', {
      success: true,
      sessionId: existingSession.sessionId,
      pairedDevice: {
        clientId: clientType === 'mobile' ? existingSession.desktopClientId : existingSession.mobileClientId,
        clientType: clientType === 'mobile' ? 'desktop' : 'mobile',
        name: clientType === 'mobile' ? 'Desktop Agent' : 'Mobile App',
      },
    }, msg.requestId));
  } else {
    client.state = 'ready';
    send(client.ws, createMessage('auth_response', {
      success: true,
      sessionId: undefined,
    }, msg.requestId));
  }
  
  console.log(`[Auth] ${clientType} authenticated: ${clientId}`);
}

function bindSession(client: ConnectedClient, sessionId: string): void {
  client.sessionId = sessionId;
  client.state = 'ready';
  touchSession(sessionId);
  
  if (client.type === 'desktop') {
    desktopClients.set(sessionId, client);
  } else {
    mobileClients.set(sessionId, client);
  }
  
  const desktop = desktopClients.get(sessionId);
  const mobile = mobileClients.get(sessionId);
  
  if (desktop && mobile) {
    // 雙方都在線，通知配對完成
    send(desktop.ws, createMessage('paired', {
      sessionId,
      peer: { clientId: mobile.id, type: 'mobile', name: mobile.info?.name },
    }));
    send(mobile.ws, createMessage('paired', {
      sessionId,
      peer: { clientId: desktop.id, type: 'desktop', name: desktop.info?.name },
    }));
    
    // 清空佇列
    flushQueue(desktop);
    flushQueue(mobile);
  }
}

// ===== 指令處理 (手機 -> 桌面) =====
function handleCommand(client: ConnectedClient, msg: BaseMessage<CommandPayload>): void {
  if (client.type !== 'mobile') {
    sendError(client.ws, 'permission_denied', 'Only mobile can send commands', msg.requestId);
    return;
  }
  
  if (!client.sessionId) {
    sendError(client.ws, 'no_session', 'Complete pairing first', msg.requestId);
    return;
  }
  
  const desktop = desktopClients.get(client.sessionId);
  if (!desktop || desktop.ws.readyState !== WebSocket.OPEN) {
    client.messageQueue.push(msg);
    sendError(client.ws, 'desktop_offline', 'Desktop offline, command queued', msg.requestId);
    return;
  }
  
  send(desktop.ws, createMessage('command', msg.payload, msg.requestId));
  touchSession(client.sessionId);
}

// ===== 輸出處理 (桌面 -> 手機) =====
function handleOutput(client: ConnectedClient, msg: BaseMessage<OutputPayload>): void {
  if (client.type !== 'desktop') {
    sendError(client.ws, 'permission_denied', 'Only desktop can send output', msg.requestId);
    return;
  }
  
  if (!client.sessionId) return;
  
  const mobile = mobileClients.get(client.sessionId);
  if (!mobile || mobile.ws.readyState !== WebSocket.OPEN) {
    client.messageQueue.push(msg);
    return;
  }
  
  send(mobile.ws, createMessage('output', msg.payload, msg.requestId));
  touchSession(client.sessionId);
}

// ===== 心跳 =====
function handleHeartbeat(client: ConnectedClient, msg: BaseMessage<HeartbeatPayload>): void {
  client.lastHeartbeat = Date.now();
  send(client.ws, createMessage('heartbeat_ack', { serverTime: Date.now() }, msg.requestId));
}

// ===== Session 管理 =====
function handleSessionList(client: ConnectedClient, msg: BaseMessage): void {
  if (!client.sessionId) {
    sendError(client.ws, 'no_session', 'No active session', msg.requestId);
    return;
  }
  
  const sessions = getClientSessions(client.id);
  send(client.ws, createMessage('session_list', {
    sessions: sessions.map(s => ({
      id: s.sessionId,
      name: `Session ${s.sessionId.slice(0, 8)}`,
      projectPath: '/unknown', // 實際需從桌面端查詢
      status: 'running',
      createdAt: s.createdAt,
      lastActiveAt: s.lastActiveAt,
    })),
  }, msg.requestId));
}

function handleSessionCreate(client: ConnectedClient, msg: BaseMessage): void {
  // 轉發給桌面端處理
  if (client.type === 'mobile' && client.sessionId) {
    const desktop = desktopClients.get(client.sessionId);
    if (desktop) send(desktop.ws, msg);
  }
}

function handleSessionClose(client: ConnectedClient, msg: BaseMessage): void {
  if (client.type === 'mobile' && client.sessionId) {
    const desktop = desktopClients.get(client.sessionId);
    if (desktop) send(desktop.ws, msg);
  }
}

// ===== 專案/檔案 (轉發給桌面端) =====
function handleProjectList(client: ConnectedClient, msg: BaseMessage): void {
  forwardToDesktop(client, msg);
}

function handleFileTree(client: ConnectedClient, msg: BaseMessage): void {
  forwardToDesktop(client, msg);
}

function handleFileRead(client: ConnectedClient, msg: BaseMessage): void {
  forwardToDesktop(client, msg);
}

function handleFileWrite(client: ConnectedClient, msg: BaseMessage): void {
  forwardToDesktop(client, msg);
}

function forwardToDesktop(client: ConnectedClient, msg: BaseMessage): void {
  if (!client.sessionId) {
    sendError(client.ws, 'no_session', 'No active session', msg.requestId);
    return;
  }
  const desktop = desktopClients.get(client.sessionId);
  if (desktop && desktop.ws.readyState === WebSocket.OPEN) {
    send(desktop.ws, msg);
  } else {
    sendError(client.ws, 'desktop_offline', 'Desktop agent offline', msg.requestId);
  }
}

// ===== 斷線處理 =====
function handleDisconnect(client: ConnectedClient): void {
  if (client.sessionId) {
    // 通知另一端
    const other = client.type === 'desktop' 
      ? mobileClients.get(client.sessionId)
      : desktopClients.get(client.sessionId);
    
    if (other && other.ws.readyState === WebSocket.OPEN) {
      send(other.ws, createMessage('peer_disconnected', {
        sessionId: client.sessionId,
        peerType: client.type,
      }));
    }
    
    desktopClients.delete(client.sessionId);
    mobileClients.delete(client.sessionId);
  }
  
  clients.delete(client.id);
}

function closeClient(clientId: string, reason: string): void {
  const client = clients.get(clientId);
  if (client) {
    try { client.ws.close(1000, reason); } catch {}
    handleDisconnect(client);
  }
}

// ===== 工具函數 =====
function send(ws: WebSocket, msg: BaseMessage): boolean {
  if (ws.readyState !== WebSocket.OPEN) return false;
  try {
    ws.send(JSON.stringify(msg));
    return true;
  } catch { return false; }
}

function sendError(ws: WebSocket, code: string, message: string, requestId?: string): void {
  send(ws, createMessage('error', { code, message, requestId } as ErrorPayload));
}

function flushQueue(client: ConnectedClient): void {
  while (client.messageQueue.length > 0) {
    const msg = client.messageQueue.shift()!;
    send(client.ws, msg);
  }
}

// ===== 定期心跳檢查 =====
setInterval(() => {
  const now = Date.now();
  for (const [id, client] of clients.entries()) {
    if (now - client.lastHeartbeat > HEARTBEAT_TIMEOUT) {
      console.log(`[Heartbeat] Timeout: ${id}`);
      closeClient(id, 'heartbeat_timeout');
    } else if (client.state === 'ready' && client.ws.readyState === WebSocket.OPEN) {
      send(client.ws, createMessage('heartbeat', { clientId: id }));
    }
  }
}, HEARTBEAT_INTERVAL);

// ===== 啟動 =====
server.listen(PORT, () => {
  console.log(`
╔══════════════════════════════════════════════════════════╗
║  OpenCode Relay Server                                   ║
║  Running on port ${PORT}                                    ║
║  WebSocket: ws://localhost:${PORT}                          ║
║  Health:    http://localhost:${PORT}/health                 ║
║  Token API: POST http://localhost:${PORT}/api/token         ║
╚══════════════════════════════════════════════════════════╝
  `);
});

// 優雅關閉
process.on('SIGINT', () => {
  console.log('\n[Shutdown] Closing all connections...');
  for (const client of clients.values()) {
    try { client.ws.close(1001, 'Server shutting down'); } catch {}
  }
  server.close(() => process.exit(0));
});

export { server, wss };