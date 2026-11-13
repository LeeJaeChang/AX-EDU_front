import type { ApiMeta, ErrorCode } from './types';

// 명세서 2장 공통 규약: Base URL, Bearer 토큰, 응답 봉투, 에러 코드

const API_HOST = import.meta.env.VITE_API_HOST ?? '';
export const API_BASE = `${API_HOST}/api/axedu/v1`;
/** https → wss, http → ws. 호스트가 비어 있으면 현재 origin 기준 */
export const API_HOST_WS = (API_HOST || window.location.origin).replace(/^http/, 'ws');

const TOKEN_KEY = 'axedu.accessToken';

export function getAccessToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setAccessToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* 저장소 접근 불가 시 무시 */
  }
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: ErrorCode;
  readonly details: Record<string, unknown>;

  constructor(status: number, code: ErrorCode, message: string, details: Record<string, unknown> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

type Envelope<T> =
  | { success: true; data: T; meta?: ApiMeta }
  | { success: false; error: { code: ErrorCode; message: string; details?: Record<string, unknown> } };

export interface ApiResult<T> {
  data: T;
  meta: ApiMeta;
}

type Query = Record<string, string | number | boolean | undefined | null>;

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  query?: Query;
  body?: unknown;
  headers?: Record<string, string>;
}

function buildUrl(path: string, query?: Query) {
  const url = `${API_BASE}${path}`;
  if (!query) return url;
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v !== undefined && v !== null && v !== '') params.set(k, String(v));
  }
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<ApiResult<T>> {
  const headers: Record<string, string> = { ...opts.headers };
  const token = getAccessToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  let body: BodyInit | undefined;
  if (opts.body instanceof FormData) {
    body = opts.body; // multipart — 브라우저가 boundary를 붙인다
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json; charset=utf-8';
    body = JSON.stringify(opts.body);
  }

  let res: Response;
  try {
    res = await fetch(buildUrl(path, opts.query), { method: opts.method ?? 'GET', headers, body });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', '네트워크에 연결할 수 없습니다.');
  }

  if (res.status === 204) return { data: undefined as T, meta: {} };

  let json: Envelope<T>;
  try {
    json = (await res.json()) as Envelope<T>;
  } catch {
    throw new ApiError(res.status, 'INTERNAL_ERROR', '서버 응답을 해석할 수 없습니다.');
  }

  if (!json.success) {
    throw new ApiError(res.status, json.error.code, json.error.message, json.error.details);
  }
  return { data: json.data, meta: json.meta ?? {} };
}

/** 클라이언트 중복 방지 키 (명세서: clientMessageId / Idempotency-Key) */
export function newClientId(prefix = 'c_') {
  const rand =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID().replace(/-/g, '')
      : Math.random().toString(36).slice(2) + Date.now().toString(36);
  return `${prefix}${rand}`;
}
