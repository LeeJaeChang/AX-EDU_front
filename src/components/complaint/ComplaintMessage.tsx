import type { Message, Role } from '../../api';
import { useComplaint } from '../../hooks/useComplaint';
import { CONTACT_LABEL } from '../../lib/complaint';
import { formatDateTime } from '../../lib/format';
import StatusPill from './StatusPill';
import SummaryCardView from './SummaryCardView';

interface Props {
  m: Message;
  viewerRole: Role;
  onOpenComplaint?: (id: string) => void;
}

/** 채팅방의 COMPLAINT_CARD(민원 보냄) · COMPLAINT_REPLY(교사의 첫 답변) 말풍선 */
export default function ComplaintMessage({ m, viewerRole, onOpenComplaint }: Props) {
  const { complaint, error } = useComplaint(m.complaintId);

  if (m.type === 'COMPLAINT_REPLY') {
    const r = complaint?.teacherReply;
    return (
      <div className="cmsg cmsg--reply">
        <span className="bubble__label">민원 답변</span>
        <p className="bubble__text">{m.content}</p>
        {r && r.contactMethod !== 'MESSAGE' && (
          <div className="cmsg__booking">
            <strong>
              {CONTACT_LABEL[r.contactMethod]} 예약{r.scheduledAt && ` · ${formatDateTime(r.scheduledAt)}`}
            </strong>
            <span>
              {r.contactMethod === 'CALL'
                ? '슈퍼스쿨 안심전화로 연결돼요. 선생님 개인번호는 보이지 않아요.'
                : '방문 당일 슈퍼패스로 보호자 확인을 해요.'}
            </span>
          </div>
        )}
      </div>
    );
  }

  // COMPLAINT_CARD
  return (
    <div className="cmsg cmsg--card">
      <div className="cmsg__head">
        <span className="bubble__label">{viewerRole === 'PARENT' ? '민원 보냄' : '학부모 민원 도착'}</span>
        {complaint && <StatusPill status={complaint.status} viewerRole={viewerRole} />}
      </div>
      {error && <p className="cmsg__error">{error}</p>}
      {!complaint && !error && <p className="cmsg__loading">카드를 불러오는 중…</p>}
      {complaint?.summaryCard && (
        <SummaryCardView
          card={complaint.summaryCard}
          compact
          showEmotion={viewerRole !== 'PARENT'}
          footer={
            viewerRole === 'TEACHER' && onOpenComplaint ? (
              <button type="button" className="scard__cta" onClick={() => onOpenComplaint(complaint.id)}>
                {complaint.teacherReply || complaint.status === 'ESCALATED' || complaint.status === 'RESOLVED' ? '민원 처리 현황 보기' : '민원 응대하기'}
              </button>
            ) : undefined
          }
        />
      )}
    </div>
  );
}
