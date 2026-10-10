// Vercel WebSocket Handler using vercel-websocket adapter
// 這是實際可部署到 Vercel 的版本
// 需安裝: npm i vercel-websocket ws uuid

import { WebSocketServer } from 'ws';
import { createWebSocketHandler } from 'vercel-websocket';
import { v4 as uuidv4 } from 'uuid';

import {
  BaseMessage,
  MessageType,
  AuthPayload,
  CommandPayload,
  OutputPayload,
  HeartbeatPayload,
  createMessage,
  isAuthMessage,
  isCommandMessage,
  isOutputMessage,
} from '../lib/protocol';
import {
  registerClient,
  unregisterClient,
  handleAuth,
  handleCommand,
  handleOutput,
  handleHeartbeat,
  closeClient,
} from '../lib/sessions';

// 建立 WebSocket 處理器
export const { GET, POST } = createWebSocketHandler({
  // 連線建立時
  onConnection: (ws, req) => {
    const clientId = req.headers.get('x-client-id') || uuidv4();
    const clientType = (req.headers.get('x-client-type') as 'mobile' | 'desktop') || 'mobile';
    
    console.log(`[WS] Connection: ${clientType} ${clientId}`);
    
    const client = registerClient(ws, clientId, clientType);
    
    // 發送歡迎訊息
    ws.send(JSON.stringify(createMessage('system', {
      message: 'Connected to OpenCode Relay',
      clientId,
      serverTime: Date.now(),
    })));
  },
  
  // 收到訊息時
  onMessage: (ws, message, req) => {
    const clientId = req.headers.get('x-client-id') || 'unknown';
    
    try {
      const msg: BaseMessage = JSON.parse(message.toString());
      routeMessage(ws, clientId, msg);
    } catch (e) {
      console.error('[WS] Parse error:', e);
      ws.send(JSON.stringify(createMessage('error', {
        code: 'parse_error',
        message: 'Invalid JSON message',
      })));
    }
  },
  
  // 連線關閉時
  onClose: (ws, req) => {
    const clientId = req.headers.get('x-client-id') || 'unknown';
    unregisterClient(clientId);
  },
  
  // 錯誤處理
  onError: (ws, error, req) => {
    const clientId = req.headers.get('x-client-id') || 'unknown';
    console.error('[WS] Error:', error);
    unregisterClient(clientId);
  },
});

// 訊息路由邏輯
function routeMessage(ws: any, clientId: string, msg: BaseMessage): void {
  // 從全域註冊表取得 client (需從 sessions.ts 導出 getClient)
  // 這裡簡化：直接根據類型處理
  
  switch (msg.type) {
    case 'auth':
      handleAuthMessage(ws, clientId, msg as BaseMessage<AuthPayload>);
      break;
    case 'command':
      handleCommandMessage(ws, clientId, msg as BaseMessage<CommandPayload>);
      break;
    case 'output':
      handleOutputMessage(ws, clientId, msg as BaseMessage<OutputPayload>);
      break;
    case 'heartbeat':
      handleHeartbeatMessage(ws, clientId, msg as BaseMessage<HeartbeatPayload>);
      break;
    default:
      console.log('[Route] Unhandled type:', msg.type);
  }
}

// 這些函數需要從 sessions.ts 導出對應的處理邏輯
// 為了簡化，這裡內聯實作關鍵邏輯

function handleAuthMessage(ws: any, clientId: string, msg: BaseMessage<AuthPayload>): void {
  // 實際實作需從 sessions.ts 導出完整邏輯
  // 這裡只示範結構
  console.log('[Auth] Received from', clientId);
}

function handleCommandMessage(ws: any, clientId: string, msg: BaseMessage<CommandPayload>): void {
  console.log('[Command] From mobile:', clientId, msg.payload.command);
}

function handleOutputMessage(ws: any, clientId: string, msg: BaseMessage<OutputPayload>): void {
  console.log('[Output] From desktop:', clientId);
}

function handleHeartbeatMessage(ws: any, clientId: string, msg: BaseMessage<HeartbeatPayload>): void {
  ws.send(JSON.stringify(createMessage('heartbeat_ack', { serverTime: Date.now() }, msg.requestId)));
}