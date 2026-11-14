/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_USE_MOCK?: string;
  readonly VITE_API_HOST?: string;
  /** 실제 서버 시연용 고정 계정 토큰 (백엔드 `./gradlew demoTokens`) */
  readonly VITE_DEMO_TOKEN_USR_T1?: string;
  readonly VITE_DEMO_TOKEN_USR_P1?: string;
  readonly VITE_DEMO_TOKEN_USR_P2?: string;
}
