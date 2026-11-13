// 명세서 3장: 날짜 구분선·시각·'어제'·'11월 4일' 표기는 클라이언트가 createdAt으로 만든다.
// 서버 값은 KST 오프셋이 붙은 ISO 8601. 기기 시간대와 관계없이 KST로 표시한다.

const TZ = 'Asia/Seoul';

const partsFmt = new Intl.DateTimeFormat('en-CA', {
  timeZone: TZ,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

interface KstParts {
  y: number;
  m: number;
  d: number;
  hh: string;
  mm: string;
}

function kst(input: string | Date): KstParts {
  const date = typeof input === 'string' ? new Date(input) : input;
  const p: Record<string, string> = {};
  for (const part of partsFmt.formatToParts(date)) p[part.type] = part.value;
  return { y: +p.year, m: +p.month, d: +p.day, hh: p.hour, mm: p.minute };
}

/** KST 기준 날짜 키 (YYYY-MM-DD) — 날짜 구분선 비교용 */
export function dayKey(input: string | Date) {
  const { y, m, d } = kst(input);
  return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

/** 15:13 */
export function formatTime(iso: string) {
  const { hh, mm } = kst(iso);
  return `${hh}:${mm}`;
}

/** 2026년 09월 08일 */
export function formatDateDivider(iso: string) {
  const { y, m, d } = kst(iso);
  return `${y}년 ${String(m).padStart(2, '0')}월 ${String(d).padStart(2, '0')}일`;
}

/** 목록 우측 시각: 오늘 15:13 · 어제 · 11월 4일 · 2025. 3. 2. */
export function formatListTime(iso: string, now = new Date()) {
  const t = kst(iso);
  const n = kst(now);
  if (dayKey(iso) === dayKey(now)) return `${t.hh}:${t.mm}`;
  const yesterday = new Date(now.getTime() - 24 * 3600 * 1000);
  if (dayKey(iso) === dayKey(yesterday)) return '어제';
  if (t.y === n.y) return `${t.m}월 ${t.d}일`;
  return `${t.y}. ${t.m}. ${t.d}.`;
}

/** 아바타 이니셜 ('김리현' → '김') */
export function initialOf(name: string) {
  return name.trim().charAt(0) || '?';
}

/** 화면의 '김리현 30901' 표기 */
export function senderLabel(name: string, studentNumber?: string | null) {
  return studentNumber ? `${name} ${studentNumber}` : name;
}

export function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)}KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}

const dowFmt = new Intl.DateTimeFormat('ko-KR', { timeZone: TZ, weekday: 'short' });

/** 11월 13일(금) 20:14 */
export function formatDateTime(iso: string) {
  const t = kst(iso);
  return `${t.m}월 ${t.d}일(${dowFmt.format(new Date(iso))}) ${t.hh}:${t.mm}`;
}

/** <input type="datetime-local"> 값(KST로 입력) → ISO 8601 + KST 오프셋 */
export function localToKstIso(local: string) {
  if (!local) return null;
  return `${local.length === 16 ? `${local}:00` : local}+09:00`;
}

/** ISO → datetime-local 값 (KST) */
export function isoToKstLocal(iso: string) {
  const t = kst(iso);
  return `${t.y}-${String(t.m).padStart(2, '0')}-${String(t.d).padStart(2, '0')}T${t.hh}:${t.mm}`;
}

/** 오프셋이 다른 ISO 문자열(+09:00 / Z)끼리 정렬하려면 문자열이 아니라 시각으로 비교해야 한다 */
export function toMs(iso: string | null | undefined) {
  return iso ? Date.parse(iso) : 0;
}
