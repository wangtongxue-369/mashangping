/**
 * 判题进度 WebSocket 客户端（@stomp/stompjs）。
 * 端点 /ws/judge：原生 WebSocket + STOMP（后端无 SockJS）。
 * HTTP 握手刻意 permitAll，鉴权只在 CONNECT 帧的 `Authorization: Bearer <token>`（WsAuthChannelInterceptor）。
 * 订阅 /user/queue/judge-progress；push 是尽力而为，连不上不阻断提交流程。
 */
import { Client } from '@stomp/stompjs';
import type { JudgeProgressEvent } from './studentTypes';

let client: Client | null = null;

/** 由当前页面协议推导 ws/wss，指向同源 /ws/judge（开发期 vite proxy 转发到后端 8080）。 */
function wsBrokerUrl(): string {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const host = window.location.host;
  return `${proto}://${host}/ws/judge`;
}

/** 解析 STOMP 帧体为 JudgeProgressEvent；载荷异常时原样忽略（best-effort）。 */
function parseEvent(payload: string, onEvent: (e: JudgeProgressEvent) => void): void {
  try {
    onEvent(JSON.parse(payload) as JudgeProgressEvent);
  } catch {
    // 忽略无法解析的帧，不打断页面。
  }
}

/**
 * 建立判题进度连接并订阅，返回清理函数（断开订阅与连接）。
 * options.token 为 JWT；options.onEvent 按 JudgeProgressEvent 消费事件（调用方按 submissionId 匹配）。
 */
export function connect(options: { token: string; onEvent: (e: JudgeProgressEvent) => void }): () => void {
  // 重复调用先断旧连接，避免多客户端堆积。
  disconnect();

  client = new Client({
    brokerURL: wsBrokerUrl(),
    connectHeaders: { Authorization: `Bearer ${options.token}` },
    reconnectDelay: 0, // 本期不做自动重连，断线由页面按需重连/手动刷新。
    onConnect: () => {
      client?.subscribe('/user/queue/judge-progress', (frame) => {
        parseEvent(frame.body, options.onEvent);
      });
    },
    onWebSocketError: () => {
      // 尽力而为的通知面；连不上仅丢实时进度，不阻断提交流程（页面退化为查询刷新）。
    },
  });
  client.activate();

  return () => disconnect();
}

/** 断开当前连接（无活动连接时可安全调用）。 */
export function disconnect(): void {
  if (client) {
    try {
      client.deactivate();
    } catch {
      // 连接可能已处于非活动态，忽略 deactivate 的竞态异常。
    }
    client = null;
  }
}
