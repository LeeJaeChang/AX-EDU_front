import { Link, useNavigate } from 'react-router-dom';
import { SWITCHABLE_ACCOUNTS, USE_MOCK } from '../api';
import { useMe, useSwitchUser } from '../hooks/useMe';

/** 시연용 상단 띠: 보는 사람(교사·학부모)을 바꿔 민원 흐름 양쪽을 확인한다 (목 모드, 또는 실제 서버 + 시연 토큰) */
export default function DemoBar() {
  const me = useMe();
  const switchUser = useSwitchUser();
  const navigate = useNavigate();

  return (
    <div className="demo-bar" role="region" aria-label="데모 설정">
      <Link to="/" className="demo-bar__home">← 서비스 소개</Link>
      <span className="demo-bar__tag">{USE_MOCK ? '목 데이터' : '시연 계정'}</span>
      <label className="demo-bar__label">
        보는 사람
        <select
          className="demo-bar__select"
          value={me.id}
          onChange={async (e) => {
            await switchUser(e.target.value);
            navigate('/chat');
          }}
        >
          {SWITCHABLE_ACCOUNTS.map((a) => (
            <option key={a.id} value={a.id}>
              {a.label}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
