import { useEffect } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { realtime, USE_MOCK } from './api';
import DemoBar from './components/DemoBar';
import { MeProvider, useMe } from './hooks/useMe';
import ChatListPage from './pages/ChatListPage';
import ChatRoomPage from './pages/ChatRoomPage';
import ComplaintChatbotPage from './pages/ComplaintChatbotPage';

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
  return (
    <div className="app">
      {USE_MOCK && <DemoBar />}
      {/* 계정을 바꾸면 화면 상태를 새로 시작한다 */}
      <div className="app-main" key={me.id}>
        <Routes>
          <Route path="/chat" element={<ChatListPage />} />
          <Route path="/chat/:roomId" element={<ChatRoomPage />} />
          {/* 학부모 전용 AI 챗봇 영역 (민원하기) */}
          <Route path="/chat/:roomId/complaint" element={<ParentOnly><ComplaintChatbotPage /></ParentOnly>} />
          <Route path="/chat/:roomId/complaint/:complaintId" element={<ParentOnly><ComplaintChatbotPage /></ParentOnly>} />
          <Route path="*" element={<Navigate to="/chat" replace />} />
        </Routes>
      </div>
    </div>
  );
}

function ParentOnly({ children }: { children: JSX.Element }) {
  return useMe().role === 'PARENT' ? children : <Navigate to="/chat" replace />;
}
