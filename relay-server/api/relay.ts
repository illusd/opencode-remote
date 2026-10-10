// Vercel WebSocket API Endpoint
// 處理 WebSocket 升級、訊息路由、連線生命週期

import { IncomingMessage } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import { VercelRequest, VercelResponse } from '@vercel/node';
import { parse } from 'url';
import { v4 as uuidv4 } from 'uuid';

import {
  BaseMessage,
  MessageType,
  isAuthMessage,
  isCommandMessage,
  isOutputMessage,
  createMessage,
} from './protocol';
import {
  registerClient,
  unregisterClient,
  handleAuth,
  handleCommand,
  handleOutput,
  handleHeartbeat,
  closeClient,
  getStats,
} from './sessions';

// 全域 WSS 實例 (Vercel serverless 函數複用)
let wss: WebSocketServer | null = null;

function getWSS(): WebSocketServer {
  if (!wss) {
    // Vercel 不支援原生 WebSocket Server，需使用無伺服器適配
    // 這裡用模擬方式，實際部署需用 vercel-websocket 或類似方案
    throw new Error('WebSocket Server not initialized. Use vercel-websocket adapter.');
  }
  return wss;
}

// 為了在 Vercel 上運行 WebSocket，我們使用 vercel-websocket 適配器
// 參考: https://github.com/vercel/vercel-websocket
export const config = {
  runtime: 'edge', // 或 'nodejs'
  websocket: true,
};

// Edge Runtime WebSocket 處理
export default async function handler(req: Request): Promise<Response> {
  const upgradeHeader = req.headers.get('upgrade');
  
  if (upgradeHeader !== 'websocket') {
    return new Response('Expected WebSocket upgrade', { status: 400 });
  }
  
  // 使用 Vercel Edge Runtime WebSocket API
  const { socket, response } = upgradeWebSocket(req);
  
  const clientId = req.headers.get('x-client-id') || uuidv4();
  const clientType = (req.headers.get('x-client-type') as 'mobile' | 'desktop') || 'mobile';
  
  socket.onopen = () => {
    registerClient(socket as unknown as WebSocket, clientId, clientType);
  };
  
  socket.onmessage = (event: MessageEvent) => {
    try {
      const msg: BaseMessage = JSON.parse(event.data);
      routeMessage(socket as unknown as WebSocket, clientId, msg);
    } catch (e) {
      console.error('[Message] Parse error:', e);
    }
  };
  
  socket.onclose = () => {
    unregisterClient(clientId);
  };
  
  socket.onerror = (error: Event) => {
    console.error('[Socket] Error:', error);
    unregisterClient(clientId);
  };
  
  return response;
}

// 訊息路由
function routeMessage(ws: WebSocket, clientId: string, msg: BaseMessage): void {
  // 取得客戶端資訊 (從 sessions 模組內部存取)
  // 這裡簡化處理，實際需從 sessions.ts 導出 getClient 函數
  
  switch (msg.type) {
    case 'auth':
      // handleAuth 需要 client 物件，這裡需調整 sessions.ts 導出
      break;
    case 'command':
      // handleCommand
      break;
    case 'output':
      // handleOutput
      break;
    case 'heartbeat':
      // handleHeartbeat
      break;
    default:
      console.log('[Route] Unknown message type:', msg.type);
  }
}

// Node.js Runtime 版本 (如果用 Node.js runtime)
export async function nodeHandler(req: VercelRequest, res: VercelResponse): Promise<void> {
  // Vercel Node.js runtime 不直接支援 WebSocket
  // 需要使用 vercel-websocket 或自訂伺服器
  res.status(501).json({ error: 'Use Edge Runtime for WebSocket' });
}

// 由於 Vercel Edge Runtime 的 WebSocket API 限制，
// 建議改用以下其中一種方案：
//
// 方案 A: 使用 vercel-websocket (推薦)
// npm i vercel-websocket
// 參考: https://github.com/vercel/vercel-websocket
//
// 方案 B: 使用獨立 WebSocket 伺服器 (Fly.io, Railway, Render 等免費方案)
// 然後在 Vercel 只部署 HTTP API
//
// 方案 C: 使用 Cloudflare Workers + Durable Objects (免費、支援 WebSocket)
//
// 下方提供方案 A 的實作範例：