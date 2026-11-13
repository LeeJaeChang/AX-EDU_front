import { useNavigate } from 'react-router-dom';
import { DEMO_ACCOUNTS } from '../api/mock/store';
import { useMe, useSwitchUser } from '../hooks/useMe';

/** 목 모드에서만 보이는 상단 띠: 보는 사람(교사·학부모)을 바꿔 민원 흐름 양쪽을 확인한다 */
export default function DemoBar() {
  const me = useMe();
  const switchUser = useSwitchUser();
  const navigate = useNavigate();

  return (
    <div className="demo-bar" role="region" aria-label="데모 설정">
      <span className="demo-bar__tag">목 데이터</span>
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
          {DEMO_ACCOUNTS.map((a) => (
            <option key={a.id} value={a.id}>
              {a.label}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
