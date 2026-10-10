// Relay Protocol Types
// 定義手機 App <-> Relay Server <-> Desktop Agent 之間的訊息協定

export type MessageType =
  | 'auth'           // 認證/配對
  | 'auth_response'  // 認證回應
  | 'command'        // 發送指令給 OpenCode
  | 'output'         // OpenCode 輸出回傳
  | 'heartbeat'      // 心跳
  | 'heartbeat_ack'  // 心跳回應
  | 'error'          // 錯誤
  | 'session_list'   // 會話列表請求/回應
  | 'session_create' // 建立會話
  | 'session_close'  // 關閉會話
  | 'project_list'   // 專案列表
  | 'file_tree'      // 檔案樹
  | 'file_read'      // 讀取檔案
  | 'file_write'     // 寫入檔案
  | 'config_get'     // 取得設定
  | 'config_set';    // 設定變更

export interface BaseMessage<T = unknown> {
  type: MessageType;
  requestId: string;
  timestamp: number;
  payload: T;
}

// ===== Auth =====
export interface AuthPayload {
  token: string;           // 配對 Token
  clientType: 'mobile' | 'desktop';
  clientId: string;        // 裝置唯一 ID
  version: string;         // 客戶端版本
}

export interface AuthResponsePayload {
  success: boolean;
  sessionId?: string;      // 成功後的會話 ID
  pairedDevice?: {
    clientId: string;
    clientType: 'mobile' | 'desktop';
    name: string;
  };
  error?: string;
}

// ===== Command (手機 -> OpenCode) =====
export interface CommandPayload {
  sessionId: string;
  command: string;         // 原始指令文字
  args?: string[];         // 參數陣列
  cwd?: string;            // 工作目錄
}

export interface OutputPayload {
  sessionId: string;
  stream: 'stdout' | 'stderr' | 'system';
  data: string;            // ANSI 轉義序列包含的輸出
  isComplete: boolean;     // 是否為該指令的最後一塊輸出
  exitCode?: number;       // 指令結束時的退出碼
}

// ===== Session Management =====
export interface SessionListPayload {
  sessions: SessionInfo[];
}

export interface SessionInfo {
  id: string;
  name: string;
  projectPath: string;
  status: 'running' | 'idle' | 'exited';
  createdAt: number;
  lastActiveAt: number;
}

export interface SessionCreatePayload {
  projectPath: string;
  name?: string;
}

export interface SessionClosePayload {
  sessionId: string;
  force?: boolean;
}

// ===== Project / File System =====
export interface ProjectListPayload {
  projects: ProjectInfo[];
}

export interface ProjectInfo {
  path: string;
  name: string;
  gitBranch?: string;
  gitStatus?: 'clean' | 'dirty' | 'unknown';
  lastOpened: number;
}

export interface FileTreePayload {
  projectPath: string;
  tree: FileNode[];
}

export interface FileNode {
  name: string;
  path: string;
  isDirectory: boolean;
  size?: number;
  modifiedAt: number;
  children?: FileNode[];
}

export interface FileReadPayload {
  path: string;
  encoding?: 'utf-8' | 'base64';
}

export interface FileReadResponsePayload {
  path: string;
  content: string;
  encoding: 'utf-8' | 'base64';
}

export interface FileWritePayload {
  path: string;
  content: string;
  encoding?: 'utf-8' | 'base64';
}

// ===== Config =====
export interface ConfigGetPayload {
  keys?: string[];  // undefined = 取得全部
}

export interface ConfigSetPayload {
  [key: string]: unknown;
}

export interface ConfigResponsePayload {
  [key: string]: unknown;
}

// ===== Heartbeat =====
export interface HeartbeatPayload {
  clientId: string;
}

export interface HeartbeatAckPayload {
  serverTime: number;
}

// ===== Error =====
export interface ErrorPayload {
  code: string;
  message: string;
  details?: unknown;
  requestId?: string;  // 原始請求的 ID
}

// ===== Type Guards =====
export function isAuthMessage(msg: BaseMessage): msg is BaseMessage<AuthPayload> {
  return msg.type === 'auth';
}

export function isCommandMessage(msg: BaseMessage): msg is BaseMessage<CommandPayload> {
  return msg.type === 'command';
}

export function isOutputMessage(msg: BaseMessage): msg is BaseMessage<OutputPayload> {
  return msg.type === 'output';
}

// ===== Factory Functions =====
export function createMessage<T>(type: MessageType, payload: T, requestId?: string): BaseMessage<T> {
  return {
    type,
    requestId: requestId || crypto.randomUUID(),
    timestamp: Date.now(),
    payload,
  };
}

export function createAuthMessage(token: string, clientType: 'mobile' | 'desktop', clientId: string, version: string): BaseMessage<AuthPayload> {
  return createMessage('auth', { token, clientType, clientId, version });
}

export function createCommandMessage(sessionId: string, command: string, args?: string[], cwd?: string): BaseMessage<CommandPayload> {
  return createMessage('command', { sessionId, command, args, cwd });
}

export function createHeartbeatMessage(clientId: string): BaseMessage<HeartbeatPayload> {
  return createMessage('heartbeat', { clientId });
}