import { useEffect } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { realtime, SWITCHABLE_ACCOUNTS } from './api';
import DemoBar from './components/DemoBar';
import { MeProvider, useMe } from './hooks/useMe';
import ChatListPage from './pages/ChatListPage';
import ChatRoomPage from './pages/ChatRoomPage';
import ComplaintChatbotPage from './pages/ComplaintChatbotPage';
import IntroPage from './pages/IntroPage';

export default function App() {
  useEffect(() => {
    realtime.connect();
    return () => realtime.disconnect();
  }, []);

  return (
    <MeProvider>
      <Shell />
    </MeProvider>
  );
}

function Shell() {
  const me = useMe();
  const { pathname } = useLocation();
  const isIntro = pathname === '/';
  return (
    <div className="app">
      {SWITCHABLE_ACCOUNTS.length > 0 && !isIntro && <DemoBar />}
      {/* 계정을 바꾸면 화면 상태를 새로 시작한다 */}
      <div className="app-main" key={me.id}>
        <Routes>
          {/* 도입 페이지: 프로젝트 소개 + 교사·학부모 화면 입구 */}
          <Route path="/" element={<IntroPage />} />
          <Route path="/chat" element={<ChatListPage />} />
          <Route path="/chat/:roomId" element={<ChatRoomPage />} />
          {/* 학부모 전용 AI 민원 상담 (채팅 목록의 '민원하기') */}
          <Route path="/complaint" element={<ParentOnly><ComplaintChatbotPage /></ParentOnly>} />
          <Route path="/complaint/:complaintId" element={<ParentOnly><ComplaintChatbotPage /></ParentOnly>} />
          {/* 예전 주소(방 안에서 시작하던 흐름)는 새 상담 화면으로 */}
          <Route path="/chat/:roomId/complaint/*" element={<Navigate to="/complaint" replace />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
    </div>
  );
}

function ParentOnly({ children }: { children: JSX.Element }) {
  return useMe().role === 'PARENT' ? children : <Navigate to="/chat" replace />;
}
