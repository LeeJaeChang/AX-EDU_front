import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { chatApi, USE_MOCK, type UserSummary } from '../api';
import { setCurrentUser } from '../api/mock/store';

interface MeState {
  me: UserSummary | null;
  error: string | null;
  /** 목 모드 전용: 다른 데모 계정으로 바꿔 본다 */
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
      if (!USE_MOCK) return;
      setCurrentUser(id);
      await load();
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
