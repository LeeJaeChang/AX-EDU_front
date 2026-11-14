import { httpChatApi, type ChatApi } from './chatApi';
import { httpComplaintApi, type ComplaintApi } from './complaintApi';
import { mockChatApi } from './mock/mockChatApi';
import { mockComplaintApi } from './mock/mockComplaintApi';
import { bus as mockBus } from './mock/store';
import { WebSocketClient, type RealtimeClient } from '../realtime/socket';

/** VITE_USE_MOCK=false 일 때만 실제 서버를 부른다 (기본은 목) */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false';

export const chatApi: ChatApi = USE_MOCK ? mockChatApi : httpChatApi;
export const complaintApi: ComplaintApi = USE_MOCK ? mockComplaintApi : httpComplaintApi;
export const realtime: RealtimeClient = USE_MOCK ? mockBus : new WebSocketClient();

export { ApiError } from './client';
export type * from './types';
