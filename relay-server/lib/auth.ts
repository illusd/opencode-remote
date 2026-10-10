// Authentication & Token Management
// 處理 Token 配對、驗證、會話綁定

import { randomBytes, createHash, timingSafeEqual } from 'crypto';

export interface TokenPair {
  token: string;           // 給手機輸入的配對碼 (base64url, 32 chars)
  tokenHash: string;       // 儲存的雜湊值 (SHA-256 hex)
  expiresAt: number;       // 過期時間 (ms)
  used: boolean;           // 是否已配對成功
  pairedMobileId?: string; // 已配對的手機 clientId
  pairedDesktopId?: string;// 已配對的電腦 clientId
}

export interface SessionBinding {
  sessionId: string;
  mobileClientId: string;
  desktopClientId: string;
  createdAt: number;
  lastActiveAt: number;
  tokenHash: string;       // 綁定的 token hash
}

// 記憶體儲存 (生產環境應用 Redis / KV)
const tokenStore = new Map<string, TokenPair>();
const sessionStore = new Map<string, SessionBinding>();
const clientSessions = new Map<string, Set<string>>(); // clientId -> sessionIds

const TOKEN_LENGTH = 24; // 24 bytes = 32 base64url chars
const TOKEN_TTL = 10 * 60 * 1000; // 10 分鐘配對窗口
const SESSION_TTL = 24 * 60 * 60 * 1000; // 24 小時會話存活
const CLEANUP_INTERVAL = 5 * 60 * 1000; // 5 分鐘清理一次

// 定期清理過期 token 和會話
setInterval(cleanup, CLEANUP_INTERVAL);

function cleanup(): void {
  const now = Date.now();
  
  // 清理過期 token
  for (const [hash, token] of tokenStore.entries()) {
    if (token.used || token.expiresAt < now) {
      tokenStore.delete(hash);
    }
  }
  
  // 清理過期會話
  for (const [sessionId, binding] of sessionStore.entries()) {
    if (binding.lastActiveAt + SESSION_TTL < now) {
      sessionStore.delete(sessionId);
      // 從 clientSessions 移除
      for (const [clientId, sessions] of clientSessions.entries()) {
        sessions.delete(sessionId);
        if (sessions.size === 0) clientSessions.delete(clientId);
      }
    }
  }
}

/** 產生新的配對 Token */
export function generateToken(): { token: string; tokenHash: string; expiresAt: number } {
  const raw = randomBytes(TOKEN_LENGTH);
  const token = raw.toString('base64url'); // URL-safe, no padding
  const tokenHash = createHash('sha256').update(token).digest('hex');
  const expiresAt = Date.now() + TOKEN_TTL;
  
  const pair: TokenPair = {
    token,
    tokenHash,
    expiresAt,
    used: false,
  };
  
  tokenStore.set(tokenHash, pair);
  return { token, tokenHash, expiresAt };
}

/** 驗證 Token 並標記為已使用 */
export function verifyToken(token: string, clientId: string, clientType: 'mobile' | 'desktop'): TokenPair | null {
  const tokenHash = createHash('sha256').update(token).digest('hex');
  const pair = tokenStore.get(tokenHash);
  
  if (!pair) return null;
  if (pair.used) return null;
  if (pair.expiresAt < Date.now()) {
    tokenStore.delete(tokenHash);
    return null;
  }
  
  // 標記為已使用，記錄配對裝置
  pair.used = true;
  if (clientType === 'mobile') {
    pair.pairedMobileId = clientId;
  } else {
    pair.pairedDesktopId = clientId;
  }
  
  return pair;
}

/** 取得 Token 資訊 (不驗證，用於查詢) */
export function getTokenInfo(tokenHash: string): TokenPair | undefined {
  return tokenStore.get(tokenHash);
}

/** 建立會話綁定 */
export function createSessionBinding(
  mobileClientId: string,
  desktopClientId: string,
  tokenHash: string
): SessionBinding {
  const sessionId = crypto.randomUUID();
  const now = Date.now();
  
  const binding: SessionBinding = {
    sessionId,
    mobileClientId,
    desktopClientId,
    createdAt: now,
    lastActiveAt: now,
    tokenHash,
  };
  
  sessionStore.set(sessionId, binding);
  
  // 更新 client -> sessions 索引
  for (const clientId of [mobileClientId, desktopClientId]) {
    if (!clientSessions.has(clientId)) {
      clientSessions.set(clientId, new Set());
    }
    clientSessions.get(clientId)!.add(sessionId);
  }
  
  return binding;
}

/** 取得會話綁定 */
export function getSessionBinding(sessionId: string): SessionBinding | undefined {
  return sessionStore.get(sessionId);
}

/** 更新會話最後活躍時間 */
export function touchSession(sessionId: string): boolean {
  const binding = sessionStore.get(sessionId);
  if (!binding) return false;
  binding.lastActiveAt = Date.now();
  return true;
}

/** 取得客戶端的所有會話 */
export function getClientSessions(clientId: string): SessionBinding[] {
  const sessionIds = clientSessions.get(clientId);
  if (!sessionIds) return [];
  
  return Array.from(sessionIds)
    .map(id => sessionStore.get(id))
    .filter((b): b is SessionBinding => b !== undefined);
}

/** 移除會話綁定 */
export function removeSessionBinding(sessionId: string): boolean {
  const binding = sessionStore.get(sessionId);
  if (!binding) return false;
  
  sessionStore.delete(sessionId);
  
  for (const clientId of [binding.mobileClientId, binding.desktopClientId]) {
    const sessions = clientSessions.get(clientId);
    if (sessions) {
      sessions.delete(sessionId);
      if (sessions.size === 0) clientSessions.delete(clientId);
    }
  }
  
  return true;
}

/** 透過 Token Hash 找到會話 */
export function findSessionByTokenHash(tokenHash: string): SessionBinding | undefined {
  for (const binding of sessionStore.values()) {
    if (binding.tokenHash === tokenHash) return binding;
  }
  return undefined;
}

/** 常時安全比較 (防時序攻擊) - 這裡用於 token hash 比較 */
export function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  return timingSafeEqual(Buffer.from(a), Buffer.from(b));
}