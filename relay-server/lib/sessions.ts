// Session Management
// 管理 WebSocket 連線、訊息路由、心跳檢測

import WebSocket from 'ws';
import { v4 as uuidv4 } from 'uuid';
import {
  BaseMessage,
  MessageType,
  AuthPayload,
  AuthResponsePayload,
  CommandPayload,
  OutputPayload,
  SessionInfo,
  HeartbeatPayload,
  ErrorPayload,
  createMessage,
  isAuthMessage,
  isCommandMessage,
} from './protocol';
import {
  verifyToken,
  createSessionBinding,
  getSessionBinding,
  touchSession,
  getClientSessions,
  removeSessionBinding,
  findSessionByTokenHash,
  TokenPair,
} from './auth';

// 連線狀態
export type ConnectionState = 'connecting' | 'authenticated' | 'ready' | 'disconnected';

export interface ConnectedClient {
  id: string;                    // clientId (裝置唯一 ID)
  ws: WebSocket;
  type: 'mobile' | 'desktop';
  state: ConnectionState;
  tokenPair?: TokenPair;         // 配對成功後的 token 資訊
  sessionId?: string;            // 綁定的會話 ID
  lastHeartbeat: number;
  messageQueue: BaseMessage[];   // 離線/未就緒時的訊息佇列
}

// 全域連線註冊表
const clients = new Map<string, ConnectedClient>(); // clientId -> client
const desktopClients = new Map<string, ConnectedClient>(); // sessionId -> desktop client
const mobileClients = new Map<string, ConnectedClient>();  // sessionId -> mobile client

const HEARTBEAT_INTERVAL = 25000; // 25 秒發送心跳
const HEARTBEAT_TIMEOUT = 60000;  // 60 秒無心跳視為斷線

// 定期心跳檢查
setInterval(checkHeartbeats, HEARTBEAT_INTERVAL);

function checkHeartbeats(): void {
  const now = Date.now();
  for (const [clientId, client] of clients.entries()) {
    if (now - client.lastHeartbeat > HEARTBEAT_TIMEOUT) {
      console.log(`[Heartbeat] Client ${clientId} timeout, closing`);
      closeClient(clientId, 'heartbeat_timeout');
    } else if (client.state === 'ready') {
      // 發送心跳
      send(client.ws, createMessage('heartbeat', { clientId }));
    }
  }
}

/** 註冊新連線 */
export function registerClient(ws: WebSocket, clientId: string, type: 'mobile' | 'desktop'): ConnectedClient {
  const client: ConnectedClient = {
    id: clientId,
    ws,
    type,
    state: 'connecting',
    lastHeartbeat: Date.now(),
    messageQueue: [],
  };
  
  clients.set(clientId, client);
  console.log(`[Register] ${type} client connected: ${clientId} (total: ${clients.size})`);
  return client;
}

/** 移除連線 */
export function unregisterClient(clientId: string): void {
  const client = clients.get(clientId);
  if (!client) return;
  
  // 清理會話綁定
  if (client.sessionId) {
    removeSessionBinding(client.sessionId);
    desktopClients.delete(client.sessionId);
    mobileClients.delete(client.sessionId);
  }
  
  clients.delete(clientId);
  console.log(`[Unregister] ${client.type} client disconnected: ${clientId} (remaining: ${clients.size})`);
}

/** 發送訊息 (自動處理連線狀態) */
export function send(ws: WebSocket, msg: BaseMessage): boolean {
  if (ws.readyState !== WebSocket.OPEN) return false;
  
  try {
    ws.send(JSON.stringify(msg));
    return true;
  } catch (e) {
    console.error('[Send] Failed:', e);
    return false;
  }
}

/** 發送給特定客戶端 */
export function sendToClient(clientId: string, msg: BaseMessage): boolean {
  const client = clients.get(clientId);
  if (!client) return false;
  return send(client.ws, msg);
}

/** 處理認證訊息 */
export function handleAuth(client: ConnectedClient, msg: BaseMessage<AuthPayload>): void {
  const { token, clientType, clientId, version } = msg.payload;
  
  // 驗證 clientId 一致性
  if (clientId !== client.id) {
    sendError(client.ws, 'client_id_mismatch', 'Client ID does not match connection', msg.requestId);
    return;
  }
  
  // 驗證 Token
  const tokenPair = verifyToken(token, clientId, clientType);
  if (!tokenPair) {
    sendError(client.ws, 'invalid_token', 'Token 無效、已過期或已被使用', msg.requestId);
    closeClient(clientId, 'auth_failed');
    return;
  }
  
  // 更新客戶端狀態
  client.tokenPair = tokenPair;
  client.state = 'authenticated';
  
  // 檢查是否已有對應裝置配對
  const existingSession = findSessionByTokenHash(
    require('crypto').createHash('sha256').update(token).digest('hex')
  );
  
  if (existingSession) {
    // 已有會話，直接綁定
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
    // 等待另一端配對
    client.state = 'ready';
    send(client.ws, createMessage('auth_response', {
      success: true,
      sessionId: undefined,
    }, msg.requestId));
  }
  
  console.log(`[Auth] ${clientType} authenticated: ${clientId}`);
}

/** 綁定會話 (雙邊都已連線) */
function bindSession(client: ConnectedClient, sessionId: string): void {
  client.sessionId = sessionId;
  client.state = 'ready';
  touchSession(sessionId);
  
  const binding = getSessionBinding(sessionId);
  if (!binding) return;
  
  if (client.type === 'desktop') {
    desktopClients.set(sessionId, client);
  } else {
    mobileClients.set(sessionId, client);
  }
  
  // 如果雙方都在線，發送 ready 通知
  const desktop = desktopClients.get(sessionId);
  const mobile = mobileClients.get(sessionId);
  
  if (desktop && mobile) {
    send(desktop.ws, createMessage('auth_response', {
      success: true,
      sessionId,
      pairedDevice: {
        clientId: mobile.id,
        clientType: 'mobile',
        name: 'Mobile App',
      },
    }));
    send(mobile.ws, createMessage('auth_response', {
      success: true,
      sessionId,
      pairedDevice: {
        clientId: desktop.id,
        clientType: 'desktop',
        name: 'Desktop Agent',
      },
    }));
    
    // 清空訊息佇列
    flushQueue(desktop);
    flushQueue(mobile);
  }
}

/** 處理指令訊息 (手機 -> 桌面) */
export function handleCommand(client: ConnectedClient, msg: BaseMessage<CommandPayload>): void {
  if (client.type !== 'mobile') {
    sendError(client.ws, 'permission_denied', 'Only mobile clients can send commands', msg.requestId);
    return;
  }
  
  if (!client.sessionId) {
    sendError(client.ws, 'no_session', 'No active session. Complete pairing first.', msg.requestId);
    return;
  }
  
  const desktop = desktopClients.get(client.sessionId);
  if (!desktop || desktop.ws.readyState !== WebSocket.OPEN) {
    // 桌面端離線，佇列訊息
    client.messageQueue.push(msg);
    sendError(client.ws, 'desktop_offline', 'Desktop agent is offline. Command queued.', msg.requestId);
    return;
  }
  
  // 轉發給桌面端
  const forwarded = createMessage('command', msg.payload, msg.requestId);
  send(desktop.ws, forwarded);
  touchSession(client.sessionId);
}

/** 處理輸出訊息 (桌面 -> 手機) */
export function handleOutput(client: ConnectedClient, msg: BaseMessage<OutputPayload>): void {
  if (client.type !== 'desktop') {
    sendError(client.ws, 'permission_denied', 'Only desktop clients can send output', msg.requestId);
    return;
  }
  
  if (!client.sessionId) {
    sendError(client.ws, 'no_session', 'No active session', msg.requestId);
    return;
  }
  
  const mobile = mobileClients.get(client.sessionId);
  if (!mobile || mobile.ws.readyState !== WebSocket.OPEN) {
    // 手機離線，佇列訊息
    client.messageQueue.push(msg);
    return;
  }
  
  send(mobile.ws, createMessage('output', msg.payload, msg.requestId));
  touchSession(client.sessionId);
}

/** 處理心跳 */
export function handleHeartbeat(client: ConnectedClient, msg: BaseMessage<HeartbeatPayload>): void {
  client.lastHeartbeat = Date.now();
  send(client.ws, createMessage('heartbeat_ack', { serverTime: Date.now() }, msg.requestId));
}

/** 發送錯誤回應 */
function sendError(ws: WebSocket, code: string, message: string, requestId?: string): void {
  send(ws, createMessage('error', { code, message, requestId } as ErrorPayload));
}

/** 關閉客戶端連線 */
export function closeClient(clientId: string, reason: string): void {
  const client = clients.get(clientId);
  if (!client) return;
  
  try {
    client.ws.close(1000, reason);
  } catch {}
  
  unregisterClient(clientId);
}

/** 清空訊息佇列 */
function flushQueue(client: ConnectedClient): void {
  while (client.messageQueue.length > 0) {
    const msg = client.messageQueue.shift()!;
    send(client.ws, msg);
  }
}

/** 取得連線統計 */
export function getStats(): { total: number; mobile: number; desktop: number; sessions: number } {
  return {
    total: clients.size,
    mobile: Array.from(clients.values()).filter(c => c.type === 'mobile').length,
    desktop: Array.from(clients.values()).filter(c => c.type === 'desktop').length,
    sessions: desktopClients.size,
  };
}