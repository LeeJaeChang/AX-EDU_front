import { buildCard, explain } from './mockAi';
import { emptyConsult } from './mockConsult';
import { addRoom, complaints, messages, users, type ComplaintRow } from './store';
import type { Message, UserSummary } from '../types';

const { usr_t1: T, usr_t2: T2, usr_s1: S1, usr_s5: S5, usr_p1: P1, usr_p2: P2 } = users;

const text = (id: string, roomId: string, sender: UserSummary, content: string, createdAt: string, read = true): Message => ({
  id,
  roomId,
  type: 'TEXT',
  sender,
  content,
  attachments: [],
  readByCounterpart: read,
  createdAt,
});

let seeded = false;

export function seed() {
  if (seeded) return;
  seeded = true;

  // ---- 교사–학생 (화면 디자인 그대로) ----
  addRoom({ id: 'room_rh', type: 'DIRECT', members: ['usr_t1', 'usr_s1'], createdAt: '2026-09-08T15:12:40+09:00', availableHoursOverride: null }, [
    text('msg_rh_1', 'room_rh', T, '리현아 오늘 야자실 안왔던데 학원상담 갔니?', '2026-09-08T15:13:00+09:00'),
    text('msg_rh_2', 'room_rh', S1, '아..넵! 학원 상담이라고 야자감독선생님께 말씀 드리긴했는데 전달을 못받으셨나보네요ㅠㅠ', '2026-09-08T15:20:00+09:00'),
    text('msg_rh_3', 'room_rh', T, '그랬구나~~다음부터는 나한테 먼저 말해주렴', '2026-09-08T15:23:00+09:00'),
    text('msg_rh_4', 'room_rh', S1, '넵 !', '2026-09-09T13:41:00+09:00'),
  ]);
  addRoom({ id: 'room_ms', type: 'DIRECT', members: ['usr_t1', 'usr_s5'], createdAt: '2026-09-08T10:00:00+09:00', availableHoursOverride: null });
  addRoom({ id: 'room_es', type: 'DIRECT', members: ['usr_t1', 'usr_s4'], createdAt: '2026-09-16T11:30:00+09:00', availableHoursOverride: null });
  addRoom({ id: 'room_wj', type: 'DIRECT', members: ['usr_t1', 'usr_s3'], createdAt: '2026-11-13T10:05:00+09:00', availableHoursOverride: null });
  addRoom({ id: 'room_sa', type: 'DIRECT', members: ['usr_t1', 'usr_s2'], createdAt: '2026-11-13T10:02:00+09:00', availableHoursOverride: null });
  addRoom({ id: 'room_grp', type: 'GROUP', groupTitle: '3학년 10반_생활과 윤리_김수학', members: ['usr_t1'], createdAt: '2026-09-08T09:30:00+09:00', availableHoursOverride: { from: '12:00', to: '18:00' } });

  // ---- 공지형 ----
  addRoom({ id: 'room_notice', type: 'NOTICE', members: ['usr_notice', 'usr_t1', 'usr_p1', 'usr_p2'], createdAt: '2026-03-02T09:00:00+09:00', availableHoursOverride: null, unread: { usr_t1: 1, usr_p1: 1, usr_p2: 1 } });
  messages.set('room_notice', [
    text('msg_notice_1', 'room_notice', users.usr_notice, '[생기부·상담 시즌, 단 하루 만에 완벽 대비! 가능합니다.] 어느덧 상담과 생기부의 시즌이 돌아왔습니다. 슈퍼스쿨의 상담 기록과 생기부 도우미로 준비 시간을 줄여 보세요.', '2026-11-04T09:00:00+09:00'),
  ]);

  // ---- 학부모–교사 ----
  addRoom({ id: 'room_p1', type: 'DIRECT', members: ['usr_t1', 'usr_p1'], createdAt: '2026-09-02T17:00:00+09:00', availableHoursOverride: null }, [
    text('msg_p1_1', 'room_p1', T, '안녕하세요, 리현이 담임 김수학입니다. 1년 동안 잘 부탁드립니다.', '2026-09-02T17:01:00+09:00'),
    text('msg_p1_2', 'room_p1', P1, '네 선생님, 잘 부탁드립니다!', '2026-09-02T17:30:00+09:00'),
  ]);
  addRoom({ id: 'room_p1_en', type: 'DIRECT', members: ['usr_t2', 'usr_p1'], createdAt: '2026-09-03T10:00:00+09:00', availableHoursOverride: null }, [
    text('msg_p1en_1', 'room_p1_en', T2, '안녕하세요, 3학년 영어 담당 이영어입니다. 수행평가 일정은 가정통신문으로 안내드렸습니다.', '2026-09-03T10:05:00+09:00'),
  ]);
  addRoom({ id: 'room_p2', type: 'DIRECT', members: ['usr_t1', 'usr_p2'], createdAt: '2026-09-02T17:00:00+09:00', availableHoursOverride: null, unread: { usr_t1: 1 } }, [
    text('msg_p2_1', 'room_p2', T, '안녕하세요, 민수 담임 김수학입니다. 1년 동안 잘 부탁드립니다.', '2026-09-02T17:02:00+09:00'),
  ]);

  // ---- 이미 제출된 민원 1건 (교사 화면 데모용) ----
  const original =
    '아이 수행평가 점수가 왜 이렇게 낮은지 이해가 안 됩니다!! 발표 준비를 3주나 했는데 감점 이유를 아무도 설명해 주지 않았어요. 아이가 많이 속상해서 울었어요. 채점 기준표를 보여 주시고 감점 사유를 알려 주세요. 연락은 전화로 오후 4시~5시에 주세요.';
  const createdAt = '2026-11-13T20:10:00+09:00';
  const submittedAt = '2026-11-13T20:14:00+09:00';
  const card = { ...buildCard(original, S5, createdAt), version: 1, confirmedByParentAt: submittedAt };
  const c: ComplaintRow = {
    id: 'cmp_seed1',
    roomId: 'room_p2',
    parentId: P2.id,
    teacherId: T.id,
    studentId: S5.id,
    status: 'SUBMITTED',
    originalText: original,
    explanation: explain(original),
    consultation: [],
    consult: emptyConsult(),
    cards: [{ ...card, source: 'AI_GENERATED' }],
    submittedVersion: 1,
    reply: null,
    escalation: null,
    createdAt,
    submittedAt,
    acknowledgedAt: null,
    closedAt: null,
    resolvedAt: null,
  };
  complaints.set(c.id, c);
  messages.get('room_p2')!.push({
    id: 'msg_p2_card',
    roomId: 'room_p2',
    type: 'COMPLAINT_CARD',
    sender: P2,
    content: '민원 요약 카드를 보냈습니다.',
    attachments: [],
    complaintId: c.id,
    readByCounterpart: false,
    createdAt: submittedAt,
  });
}
