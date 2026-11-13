import type { ComplaintStatus, Role } from '../../api';
import { PARENT_STATUS, TEACHER_STATUS } from '../../lib/complaint';

export default function StatusPill({ status, viewerRole }: { status: ComplaintStatus; viewerRole: Role }) {
  const s = viewerRole === 'PARENT' ? PARENT_STATUS[status] : TEACHER_STATUS[status] ?? PARENT_STATUS[status];
  return <span className={`pill pill--${s.tone}`}>{s.label}</span>;
}
