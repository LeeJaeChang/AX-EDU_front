import { httpChatApi, type ChatApi } from './chatApi';
import { httpComplaintApi, type ComplaintApi } from './complaintApi';
import { DEMO_TOKEN_ACCOUNTS, ensureDemoToken } from './demoTokens';
import { mockChatApi } from './mock/mockChatApi';
import { mockComplaintApi } from './mock/mockComplaintApi';
import { DEMO_ACCOUNTS, bus as mockBus } from './mock/store';
import { WebSocketClient, type RealtimeClient } from '../realtime/socket';

/** VITE_USE_MOCK=false 일 때만 실제 서버를 부른다 (기본은 목) */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false';

export const chatApi: ChatApi = USE_MOCK ? mockChatApi : httpChatApi;
export const complaintApi: ComplaintApi = USE_MOCK ? mockComplaintApi : httpComplaintApi;
export const realtime: RealtimeClient = USE_MOCK ? mockBus : new WebSocketClient();

// 실제 서버 + 시연 토큰이 있으면 첫 화면을 그리기 전에 기본 계정 토큰을 넣어 둔다
if (!USE_MOCK) ensureDemoToken();

/** 상단 '보는 사람'에서 고를 수 있는 계정. 목: 데모 계정 전부, 실제 서버: 토큰이 설정된 시연 계정만 */
export const SWITCHABLE_ACCOUNTS: { id: string; label: string }[] = USE_MOCK ? DEMO_ACCOUNTS : DEMO_TOKEN_ACCOUNTS;

export { ApiError } from './client';
export type * from './types';
