import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { chatApi, realtime, USE_MOCK, type UserSummary } from '../api';
import { setAccessToken } from '../api/client';
import { demoTokenOf } from '../api/demoTokens';
import { setCurrentUser } from '../api/mock/store';

interface MeState {
  me: UserSummary | null;
  error: string | null;
  /** 다른 데모 계정으로 바꿔 본다 (목 계정, 또는 실제 서버의 시연 토큰 계정) */
  switchUser: (id: string) => Promise<void>;
}

const MeContext = createContext<MeState>({ me: null, error: null, switchUser: async () => {} });

export function MeProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<UserSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setMe(await chatApi.getMe());
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const switchUser = useCallback(
    async (id: string) => {
      if (USE_MOCK) {
        setCurrentUser(id);
        await load();
        return;
      }
      // 실제 서버: 시연 토큰을 바꿔 끼우고, 이전 계정의 실시간 연결을 끊은 뒤 새 토큰으로 다시 붙는다
      const token = demoTokenOf(id);
      if (!token) return;
      realtime.disconnect();
      setAccessToken(token);
      await load();
      realtime.connect();
    },
    [load],
  );

  if (error) {
    return (
      <div className="page-state" role="alert">
        {error}
      </div>
    );
  }
  if (!me) return <div className="page-state">불러오는 중…</div>;
  return <MeContext.Provider value={{ me, error, switchUser }}>{children}</MeContext.Provider>;
}

/** 로그인 사용자. MeProvider 안에서는 항상 존재한다. */
export function useMe(): UserSummary {
  const { me } = useContext(MeContext);
  if (!me) throw new Error('useMe는 MeProvider 안에서만 쓸 수 있습니다.');
  return me;
}

export function useSwitchUser() {
  return useContext(MeContext).switchUser;
}
