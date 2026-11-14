import { Link } from 'react-router-dom';
import type { Role } from '../../api';
import { useComplaint } from '../../hooks/useComplaint';
import { isDrafting } from '../../lib/complaint';
import StatusPill from './StatusPill';

interface Props {
  complaintId: string;
  viewerRole: Role;
  onOpen: (id: string) => void;
}

/** 채팅방 상단: 진행 중인 민원 안내 */
export default function ComplaintBanner({ complaintId, viewerRole, onOpen }: Props) {
  const { complaint } = useComplaint(complaintId);
  if (!complaint) return null;

  if (viewerRole === 'PARENT') {
    if (isDrafting(complaint.status)) {
      return (
        <div className="banner">
          <StatusPill status={complaint.status} viewerRole="PARENT" />
          <p className="banner__text">아직 선생님께 보내지 않은 민원이 있어요.</p>
          <Link className="btn btn--sm" to={`/complaint/${complaint.id}`}>이어서 쓰기</Link>
        </div>
      );
    }
    return (
      <div className="banner">
        <StatusPill status={complaint.status} viewerRole="PARENT" />
        <p className="banner__text">보낸 민원 요약 카드는 아래 대화에서 볼 수 있어요.</p>
      </div>
    );
  }

  const fresh = complaint.status === 'SUBMITTED';
  const urgent = complaint.summaryCard?.urgency?.level === 'URGENT';
  return (
    <div className={`banner ${fresh ? 'banner--accent' : ''} ${urgent && fresh ? 'banner--urgent' : ''}`}>
      <StatusPill status={complaint.status} viewerRole="TEACHER" />
      <p className="banner__text">
        {fresh
          ? urgent
            ? `긴급 · ${complaint.parent.name}님이 오늘 안에 확인을 요청한 민원 카드가 도착했어요.`
            : `${complaint.parent.name}님의 민원 요약 카드가 도착했어요.`
          : `${complaint.student.name} 학생 관련 민원을 처리하고 있어요.`}
      </p>
      <button type="button" className="btn btn--sm" onClick={() => onOpen(complaint.id)}>
        {fresh ? '카드 확인하기' : '민원 보기'}
      </button>
    </div>
  );
}
