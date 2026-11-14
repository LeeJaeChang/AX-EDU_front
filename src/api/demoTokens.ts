import { getAccessToken, setAccessToken } from './client';
import { DEMO_ACCOUNTS } from './mock/store';

// 실제 서버 시연용 고정 계정 토큰. 서버에 로그인 API가 없어 빌드 때 넣은 JWT를 바꿔 끼워 계정을 바꾼다.
// 값은 백엔드 `./gradlew demoTokens`로 만들고, 서버의 AXEDU_JWT_SECRET과 같은 키로 서명돼야 한다.

const DEMO_TOKENS: Record<string, string | undefined> = {
  usr_t1: import.meta.env.VITE_DEMO_TOKEN_USR_T1?.trim(),
  usr_p1: import.meta.env.VITE_DEMO_TOKEN_USR_P1?.trim(),
  usr_p2: import.meta.env.VITE_DEMO_TOKEN_USR_P2?.trim(),
};

/** 토큰이 설정된 시연 계정만 (순서: 교사 → 학부모) */
export const DEMO_TOKEN_ACCOUNTS = DEMO_ACCOUNTS.filter((a) => DEMO_TOKENS[a.id]).map((a) => ({
  ...a,
  token: DEMO_TOKENS[a.id] as string,
}));

export function demoTokenOf(userId: string): string | undefined {
  return DEMO_TOKEN_ACCOUNTS.find((a) => a.id === userId)?.token;
}

/** 저장된 토큰이 없거나 시연 계정 토큰이 아니면 첫 시연 계정(교사)으로 시작한다 */
export function ensureDemoToken() {
  if (DEMO_TOKEN_ACCOUNTS.length === 0) return;
  const stored = getAccessToken();
  if (stored && DEMO_TOKEN_ACCOUNTS.some((a) => a.token === stored)) return;
  setAccessToken(DEMO_TOKEN_ACCOUNTS[0].token);
}
