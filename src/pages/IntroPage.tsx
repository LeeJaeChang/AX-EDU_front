import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { USE_MOCK } from '../api';
import { useSwitchUser } from '../hooks/useMe';
import '../styles/intro.css';

// 도입 페이지 — 프로젝트 README 역할. 기획 문서 '아이디어 정리'를 화면으로 옮기고, 교사·학부모 화면으로 들어가는 입구가 된다.

type Entry = 'teacher' | 'parent';

const ENTRY = {
  teacher: { userId: 'usr_t1', to: '/chat' },
  parent: { userId: 'usr_p1', to: '/chat' },
} as const;

function useEnter() {
  const navigate = useNavigate();
  const switchUser = useSwitchUser();
  return async (who: Entry) => {
    // 목 모드: 데모 계정으로 바꿔 들어간다. 실제 서버: 로그인 토큰의 역할을 그대로 쓴다.
    if (USE_MOCK) await switchUser(ENTRY[who].userId);
    navigate(ENTRY[who].to);
  };
}

const STATS = [
  { value: '78%', label: '민원 창구 단일화가 작동하지 않는다고 답한 초등교사', note: '전혀 작동 안 함 43.2% + 형식적으로만 존재 34.8% · 초등교사노조, 2026.3, 987명', src: 's5' },
  { value: '49.5% : 2.9%', label: '교권 침해를 겪었다는 교원 vs 침해했다는 학부모', note: '같은 행동도 교사는 정당한 지도, 학부모는 부당한 대우로 받아들임 · 정이화(2026)', src: 's0' },
  { value: '62.4%', label: '교권 침해를 겪고도 교권보호위원회를 열지 않은 교사', note: '실질적인 도움이 됐다는 응답은 10.8%', src: 's7' },
  { value: '84.3%', label: '학교 민원 매뉴얼 연수·안내를 받은 적 없는 교사', note: '매뉴얼 배포 넉 달 뒤 조사', src: 's10' },
];

const CAUSES = [
  {
    title: '학부모는 한쪽 이야기만 듣고 불안한 채 연락합니다',
    body: '아이 말만 들은 정보 비대칭 상태라 당장 확인하고 싶어 합니다. 같은 행동을 교사와 학부모가 전혀 다르게 받아들입니다.',
  },
  {
    title: '그 첫 연락을 받는 창구가 담임 개인뿐입니다',
    body: '2026년 3월부터 교사 개인 연락처로 민원 접수가 금지됐지만, 공식 창구는 느리고 문턱이 높아 학부모는 결국 담임에게 직접 연락합니다.',
  },
  {
    title: '학교의 보호 장치는 모두 ‘사후’에 작동합니다',
    body: '민원대응팀은 담임이 먼저 노출된 뒤에야 움직이고, 교권보호위원회는 문턱이 높아 요구하는 교사가 적습니다.',
  },
];

interface Step {
  no: number;
  title: string;
  who: string;
  points: string[];
  example?: string;
  planned?: string[];
}

const STEPS: Step[] = [
  {
    no: 1,
    title: '민원 입력 → AI가 학교 규정과 비교해 설명',
    who: '학부모',
    points: [
      '학부모가 민원을 자유롭게 입력합니다.',
      'AI는 학칙·가정통신문처럼 학교가 공개한 자료만 근거로 삼고, 출처를 함께 보여 줍니다.',
      '공감 → 그렇게 한 이유 → 대안 순서로 안내합니다. 이해되면 여기서 마무리, 아니면 언제든 ‘선생님과 대화하기’.',
      '규정으로 답이 나오지 않거나 개선 요청이 타당하면 AI가 먼저 교사와의 대화를 권합니다.',
    ],
    example: '“많이 놀라셨겠어요. → 학칙에 따라 수업 중 휴대전화는 보관하고 종례 때 돌려줍니다. → 급한 연락은 교무실로 해 주세요.”',
  },
  {
    no: 2,
    title: 'AI가 감정을 덜어 낸 요약 카드 작성',
    who: '학부모',
    points: [
      '감정적인 표현은 빼고 기본 정보·상황·걱정하는 점·요청 사항·희망 연락·관련 규정만 정리합니다.',
      '상황에는 ‘학부모님 설명에 따르면’을 붙여 확인된 사실처럼 보이지 않게 합니다.',
      '학부모가 보내기 전에 카드를 직접 확인하고 고칠 수 있습니다. 교사와 학부모는 같은 카드를 봅니다.',
    ],
  },
  {
    no: 3,
    title: '교사가 근무 시간에 카드를 확인하고 입장 정리',
    who: '교사',
    points: [
      '감정적인 말을 직접 받지 않아 정서적 부담이 줄어듭니다.',
      'AI가 당시 상황·그렇게 한 이유·앞으로의 조치를 정리하도록 돕고, 답변 시점과 방법은 교사가 정합니다.',
    ],
    planned: ['카드를 슈퍼스쿨 AI 상담 기록과 같은 학생 기록에 쌓기'],
  },
  {
    no: 4,
    title: '교사와 학부모가 플랫폼 안에서 직접 대화',
    who: '교사 · 학부모',
    points: [
      '두 사람 모두 같은 요약 카드를 본 상태라 감정 대신 사실에 집중해서 대화를 시작합니다.',
      '전화는 슈퍼스쿨 안심전화로 걸어 교사 개인 번호가 노출되지 않고, 대면 면담은 슈퍼스쿨에서 예약합니다.',
    ],
    planned: ['방문 당일 슈퍼패스로 보호자 확인, 면담 기록을 카드에 추가'],
  },
  {
    no: 5,
    title: '혼자 해결하기 어려우면 학교 차원으로 넘기기',
    who: '교사 → 민원대응팀',
    points: [
      '같은 요구 반복, 폭언·협박·근무 시간 밖 연락 강요, 교사 권한 밖 요청(반 교체, 규정 변경)일 때.',
      '‘민원 대응팀 도움 요청’을 누르면 요약 카드·AI 규정 판단·대화 기록이 그대로 사건 파일로 전달됩니다. 처음부터 다시 설명할 필요가 없습니다.',
    ],
    planned: ['폭언·협박 감지, 같은 요청 3회 이상, 시간 외 연락 반복 시 자동 이관', '민원대응팀 24시간 미확인 시 교감 자동 알림'],
  },
];

const CARD_ROWS = [
  { item: '기본 정보', value: '학생 학년·반, 접수 일시, 민원 유형 태그', note: '생활지도 / 학습·평가 / 교우관계 / 안전 / 기타' },
  { item: '상황', value: '언제, 어디서, 무슨 일이 있었는지 시간 순서로', note: '‘학부모님 설명에 따르면’을 붙여 확인된 사실처럼 보이지 않게' },
  { item: '걱정하는 점', value: '학생에게 어떤 영향이 있었는지', note: '감정은 빼고 영향만 (예: “아이가 등교를 망설임”)' },
  { item: '요청 사항', value: '학부모가 원하는 것', note: '여러 개면 하나씩 나눠서' },
  { item: '희망 연락 방법', value: '전화 / 문자 / 상담 방문 + 연락받기 좋은 시간대', note: '교사가 연락 시점을 정하기 쉽게' },
  { item: '관련 규정', value: '학생생활규정·학칙·가정통신문 원문 일부', note: '대화 전부터 같은 사실을 공유' },
];

const LINKS = [
  { name: '채팅 + 교사 대화 가능 시간', desc: 'AI 1차 응대를 채팅 앞에 붙이고, 대화 가능 시간 밖에는 AI가 응대하고 카드만 만들어 둡니다.' },
  { name: '상담 신청', desc: '‘선생님과 대화하기’가 상담 신청(학부모 신청 → 교사 일정 확정)으로 바로 이어집니다.' },
  { name: 'AI 상담 기록 (STT 기록·요약)', desc: '전화·대면 상담 내용도 요약 카드에 이어 붙여 하나의 사건 기록으로 만듭니다.' },
  { name: '안심전화 (2026.3 베타)', desc: '교사가 개인 번호 노출 없이 학부모에게 연락합니다.' },
  { name: '공지사항·가정통신문', desc: 'AI가 근거로 삼는 자료가 이미 플랫폼 안에 있어 규정 근거 답변에 바로 씁니다.' },
  { name: '슈퍼패스 (토스 FacePASS)', desc: '요약 카드 → 면담 예약 → 얼굴 인증 출입 → 면담 기록이 하나로 이어집니다.' },
];

const COMPARE = [
  { name: '전화 · 카카오톡 1:1', tone: 'before', points: ['감정적인 학부모 민원에 교사가 그대로 노출', '근무 시간과 관계없이 연락'] },
  { name: '현재 정책 · 민원대응팀', tone: 'mid', points: ['교사 노출 차단에는 효과적', '그러나 첫 연락은 여전히 교사에게 가는 경우가 대부분'] },
  {
    name: 'AXEDU 민원 소통 창구',
    tone: 'after',
    points: ['감정적인 문장 대신 학칙에 따라 사실관계가 정리된 카드로 대응', '부담되면 버튼 하나로 민원대응팀에 도움 요청', '대응팀은 정리된 카드로 사건 정리 없이 바로 대응'],
  },
];

const EFFECTS = [
  { who: '교사', head: '교권 보호', points: ['감정이 걸러진 카드만 근무 시간에 확인', '학칙으로 답할 수 있는 민원은 AI가 먼저 설명', '안심전화로 번호 노출 없이 연락, 도움 요청 부담 감소'] },
  { who: '학생', head: '위축되지 않는 교육', points: ['교사가 민원 걱정 없이 정당한 생활지도를 이어 감', '같은 카드에서 시작해 대화가 학생에게 필요한 것을 향함'] },
  { who: '학부모', head: '정당한 소통', points: ['언제든 즉시 응답을 받아 불안이 커지기 전에 해소', '아이 말뿐 아니라 학교 자료로 확인한 사실을 앎', '보내기 전 카드를 직접 확인해 ‘진상 민원’ 걱정 없이 전달'] },
  { who: '민원대응팀', head: '바로 움직이는 대응', points: ['사실관계가 정리된 카드가 곧 사건 파일', '초기 대응 시간 단축'] },
];

const SOURCES: { id: string; label: string; url?: string }[] = [
  { id: 's0', label: '정이화 (2026). 교권에 대한 초·중등 학교구성원의 인식 차이. 『교육문화연구』 32(2), 553–580.' },
  { id: 's1', label: '제주 중학교 교사 사망 사건 관련 보도 (jnuri.net)', url: 'https://www.jnuri.net/mobile/article.html?no=68997' },
  { id: 's2', label: '민원대응팀 관련 보도 (educhang.co.kr)', url: 'https://www.educhang.co.kr/news/articleView.html?idxno=9448' },
  { id: 's3', label: 'YTN · 교사 개인 연락처로 ‘학부모 민원’ 금지된다…3월부터 시행', url: 'https://www.ytn.co.kr/_ln/0103_202601221652293658' },
  { id: 's4', label: '더팩트 · 좋은교사운동 인터뷰 (민원 창구 일원화)', url: 'https://news.tf.co.kr/read/life/2209167.htm' },
  { id: 's5', label: '서플 · 초등교사 10명 중 9명 “민원 발생 시 학교의 적절한 지원 못 받아”', url: 'https://supple.kr/news/cmnn4gi9p004ah7abx16mzpu7' },
  { id: 's6', label: '헤럴드경제 · “학부모 민원 창구 제대로 작동” 3.4%뿐', url: 'https://biz.heraldcorp.com/article/10857610?ref=naver' },
  { id: 's7', label: '세계일보 · 교권보호위원회 관련 조사', url: 'https://www.segye.com/newsView/20260707519667' },
  { id: 's8', label: '헤럴드경제 미주판 · 교총 교권 상담 사례 (학부모에 의한 피해 45.4%)', url: 'https://www.heraldk.com/article/2026051123450061168' },
  { id: 's10', label: '서플 · 학교민원 매뉴얼 배포 넉 달…“초등교사 10명 중 7명 존재 몰라”', url: 'https://supple.kr/news/cmskx7i6m00mmzaeu8ov71g6w' },
];

export default function IntroPage() {
  const enter = useEnter();

  return (
    <div className="intro">
      <header className="intro-top">
        <div className="wrap intro-top__inner">
          <span className="intro-brand">
            <span className="intro-brand__mark" aria-hidden="true">AX</span>
            AXEDU <span className="intro-brand__sub">슈퍼스쿨 AI 민원 소통 창구</span>
          </span>
          <nav className="intro-top__nav" aria-label="데모 화면으로 이동">
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => enter('parent')}>학부모 화면</button>
            <button type="button" className="btn btn--sm" onClick={() => enter('teacher')}>교사 화면</button>
          </nav>
        </div>
      </header>

      {/* ---------- Hero ---------- */}
      <section className="intro-hero">
        <div className="wrap intro-hero__grid">
          <div className="intro-hero__copy">
            <span className="intro-eyebrow">AX-EDU 해커톤 · 프론트엔드 데모</span>
            <h1 className="intro-h1">
              첫 연락에서 감정을 덜어{' '}내면,
              <br />
              <em>교사는 보호받고</em> 대화는 학생을 향합니다
            </h1>
            <p className="intro-lead">
              학부모 민원이 담임 개인에게 바로 닿기 전에, AI가 학교 규정으로 먼저 설명하고 감정을 덜어 낸 요약 카드로 정리합니다.
              교사와 학부모는 같은 카드를 보고 대화를 시작하고, 혼자 감당하기 어려우면 버튼 하나로 민원대응팀에 넘깁니다.
            </p>
            <p className="intro-quote">“인식 격차가 교사 개인을 향한 감정 충돌로 번지지 않게 하는 사전 완충 장치”</p>
          </div>

          <div className="intro-entry" aria-label="데모 들어가기">
            <EntryCard
              role="학부모 화면"
              title="민원하기 → AI 설명 → 요약 카드"
              items={['‘김수학 [교사]’ 방에서 민원하기', 'AI 규정 설명과 근거 자료 확인', '카드를 말로 고치고 확인하고 보내기']}
              cta="학부모로 들어가기"
              onClick={() => enter('parent')}
            />
            <EntryCard
              role="교사 화면"
              title="카드 확인 → 입장 정리 → 답변"
              items={['‘김민수 학부모’ 방에 도착한 민원 카드', 'AI 입장 정리 초안, 안심전화·면담 답변', '민원 대응팀 도움 요청']}
              cta="교사로 들어가기"
              primary
              onClick={() => enter('teacher')}
            />
            {USE_MOCK && (
              <p className="intro-entry__note">서버 없이 목 데이터로 동작합니다. 들어간 뒤에도 화면 맨 위 ‘보는 사람’에서 계정을 바꿀 수 있어요.</p>
            )}
          </div>
        </div>
      </section>

      {/* ---------- 1. 문제 ---------- */}
      <Section id="problem" eyebrow="01 · 문제 정의" title="민원대응팀은 생겼지만, 첫 연락은 여전히 담임 개인에게 갑니다">
        <div className="intro-case">
          <div className="intro-case__head">
            <span className="tag-warn">관련 사건</span>
            <strong>제주 중학교 교사 사망 사건 (2025.5.22)</strong>
            <Cite id="s1" />
          </div>
          <ol className="intro-case__list">
            <li><b>시작은 생활지도였습니다.</b> 무단결석과 흡연을 반복하던 학생을 지도하던 3학년 담임이 학생 가족의 지속적인 민원에 시달렸습니다.</li>
            <li><b>연락은 담임 개인에게 바로 갔습니다.</b> 교사 개인 휴대전화로 하루 열 통 넘게, 새벽과 심야에도 전화가 왔습니다.</li>
            <li><b>대응팀은 있었지만 움직이지 않았습니다.</b> 교육청이 그해 3월 민원대응팀 구성을 안내했지만, 이 사건에서 활동한 흔적은 보이지 않는다는 비판이 나왔습니다.</li>
          </ol>
        </div>
        <p className="intro-question">
          <span>핵심 질문</span>
          민원이 공식 접수되기 전, 담임과 학부모가 처음 연락하는 순간을 어떻게 안전하게 만들 수 있을까?
        </p>
      </Section>

      {/* ---------- 숫자 ---------- */}
      <Section id="numbers" eyebrow="숫자로 보는 현실" title="제도는 이미 있습니다. 그런데 작동하지 않습니다" lead="2026년 3월부터 학교 민원 창구는 학교 대표번호와 이어드림 등 공식 창구로 일원화되고, 교사 개인 연락처·SNS로의 민원 접수가 금지됐습니다. 시행 한 달 뒤 조사 결과입니다.">
        <div className="intro-figs">
          {STATS.map((s) => (
            <div key={s.value} className="intro-fig">
              <strong className="intro-fig__value tnum">{s.value}</strong>
              <span className="intro-fig__label">{s.label}</span>
              <span className="intro-fig__note">
                {s.note} <Cite id={s.src} />
              </span>
            </div>
          ))}
        </div>
      </Section>

      {/* ---------- 원인 ---------- */}
      <Section id="causes" eyebrow="원인" title="왜 담임 혼자 감정이 실린 항의를 받게 될까요">
        <ol className="intro-tri">
          {CAUSES.map((c, i) => (
            <li key={c.title} className="intro-tri__item">
              <span className="intro-num">{i + 1}</span>
              <h3>{c.title}</h3>
              <p>{c.body}</p>
            </li>
          ))}
        </ol>
      </Section>

      {/* ---------- 해결: 5단계 ---------- */}
      <Section id="steps" eyebrow="02 · 핵심 기능" title="대화를 끊지 않고, 감정은 덜고 상황만 정리합니다" lead="목표는 교사와 학부모의 대화를 막는 것이 아니라, 민원 상황에서 학생을 위한 대화를 하게 돕는 것입니다.">
        <ol className="intro-steps">
          {STEPS.map((s) => (
            <li key={s.no} className="intro-step">
              <span className="intro-step__no">STEP {s.no}</span>
              <div className="intro-step__body">
                <div className="intro-step__head">
                  <h3>{s.title}</h3>
                  <span className="pill pill--neutral">{s.who}</span>
                </div>
                <ul className="intro-bullets">
                  {s.points.map((p) => (
                    <li key={p}>{p}</li>
                  ))}
                </ul>
                {s.example && <p className="intro-step__example">{s.example}</p>}
                {s.planned && (
                  <div className="intro-step__planned">
                    <span className="pill pill--mark">기획 단계</span>
                    {s.planned.join(' · ')}
                  </div>
                )}
              </div>
            </li>
          ))}
        </ol>
      </Section>

      {/* ---------- 요약 카드 ---------- */}
      <Section id="card" eyebrow="요약 카드" title="교사와 학부모가 같은 카드를 봅니다">
        <div className="intro-rows" role="table" aria-label="요약 카드 항목">
          <div className="intro-row intro-row--head" role="row">
            <span role="columnheader">항목</span>
            <span role="columnheader">내용</span>
            <span role="columnheader">이렇게 정리해요</span>
          </div>
          {CARD_ROWS.map((r) => (
            <div key={r.item} className="intro-row" role="row">
              <strong role="cell">{r.item}</strong>
              <span role="cell">{r.value}</span>
              <span role="cell" className="intro-row__note">{r.note}</span>
            </div>
          ))}
        </div>
      </Section>

      {/* ---------- 슈퍼스쿨 연결 ---------- */}
      <Section id="superschool" eyebrow="03 · 실현 가능성" title="새 앱이 아니라, 슈퍼스쿨에 이미 있는 기능과 연결합니다" lead="독립 서비스라면 학생·학부모·교사가 앱을 또 설치하고, 교사가 학생을 다시 초대해야 합니다. 학생 관리 통합 플랫폼인 슈퍼스쿨 안에 탑재해 이 번거로움을 없앱니다.">
        <div className="intro-minis">
          {LINKS.map((l) => (
            <div key={l.name} className="intro-mini">
              <strong>{l.name}</strong>
              <p>{l.desc}</p>
            </div>
          ))}
        </div>
      </Section>

      {/* ---------- 차이 ---------- */}
      <Section id="compare" eyebrow="기존 방식과의 차이" title="같은 민원, 세 가지 대응">
        <div className="intro-compare">
          {COMPARE.map((c) => (
            <div key={c.name} className={`intro-cmp intro-cmp--${c.tone}`}>
              <strong className="intro-cmp__name">{c.name}</strong>
              <ul className="intro-bullets">
                {c.points.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </Section>

      {/* ---------- 보안·윤리 ---------- */}
      <Section id="ethics" eyebrow="04 · 보안과 윤리" title="판정하지 않고, 학부모가 확인한 뒤 보냅니다">
        <div className="intro-two">
          <div className="intro-panel">
            <h3>개인정보·보안</h3>
            <ul className="intro-bullets">
              <li><b>슈퍼스쿨과 같은 인프라</b> — KISA 인증 공공기관용 클라우드, 국산 AI 모델(EXAONE), 전송·저장 구간 암호화, 학교 단위 데이터 분리</li>
              <li><b>최소 수집과 가명처리</b> — 필요한 정보만 수집하고 AI에는 이름·학년반을 가려 전달. 민원 데이터는 AI 학습에 쓰지 않음</li>
            </ul>
          </div>
          <div className="intro-panel">
            <h3>윤리 원칙</h3>
            <ul className="intro-bullets">
              <li><b>판정하지 않는다</b> — ‘선생님이 옳습니다’ 같은 표현 없이 규정 사실만 전함. 교사 편도 학부모 편도 아님</li>
              <li><b>학부모가 확인한 뒤 보낸다</b> — 보내기 전에 보고 고칠 수 있어 ‘내 말이 왜곡됐다’는 불신이 없음</li>
              <li><b>교사는 언제든 도움을 요청한다</b> — 부당한 감정적 민원이 계속되면 민원대응팀으로 넘겨 감정 노동에서 벗어남</li>
            </ul>
          </div>
        </div>
      </Section>

      {/* ---------- 기대효과 ---------- */}
      <Section id="effects" eyebrow="05 · 기대효과" title="교사는 보호받고, 학부모는 정당하게 소통하고, 대응팀은 바로 움직입니다">
        <div className="intro-effects">
          {EFFECTS.map((e) => (
            <div key={e.who} className="intro-effect">
              <span className="intro-effect__who">{e.who}</span>
              <h3>{e.head}</h3>
              <ul className="intro-bullets">
                {e.points.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </Section>

      {/* ---------- CTA ---------- */}
      <section className="intro-cta">
        <div className="wrap intro-cta__inner">
          <div>
            <p className="intro-cta__eyebrow">결국, 학생을 위한 변화</p>
            <h2>교사는 위축 없이 생활지도를 이어 가고, 대화는 학생에게 필요한 것에서 시작됩니다</h2>
          </div>
          <div className="intro-cta__actions">
            <button type="button" className="btn btn--on-dark" onClick={() => enter('parent')}>학부모 화면 보기</button>
            <button type="button" className="btn" onClick={() => enter('teacher')}>교사 화면 보기</button>
          </div>
        </div>
      </section>

      <footer className="intro-foot wrap">
        <h2 className="intro-foot__title">출처</h2>
        <ol className="intro-sources">
          {SOURCES.map((s) => (
            <li key={s.id} id={s.id}>
              {s.url ? (
                <a href={s.url} target="_blank" rel="noreferrer">{s.label}</a>
              ) : (
                s.label
              )}
            </li>
          ))}
        </ol>
        <p className="intro-foot__meta">AX-EDU 해커톤 · AXEDU 채팅·민원 소통 API 명세서 v1 · DB 스키마 설계서 v1.1 기반 프론트엔드 데모</p>
      </footer>
    </div>
  );
}

function Section({ id, eyebrow, title, lead, children }: { id: string; eyebrow: string; title: string; lead?: string; children: ReactNode }) {
  return (
    <section id={id} className="intro-sec">
      <div className="wrap">
        <header className="intro-head">
          <span className="intro-eyebrow">{eyebrow}</span>
          <h2 className="intro-h2">{title}</h2>
          {lead && <p className="intro-lead">{lead}</p>}
        </header>
        {children}
      </div>
    </section>
  );
}

function EntryCard({ role, title, items, cta, primary, onClick }: { role: string; title: string; items: string[]; cta: string; primary?: boolean; onClick: () => void }) {
  return (
    <div className={`entry-card ${primary ? 'entry-card--primary' : ''}`}>
      <span className="entry-card__role">{role}</span>
      <strong className="entry-card__title">{title}</strong>
      <ul className="entry-card__list">
        {items.map((i) => (
          <li key={i}>{i}</li>
        ))}
      </ul>
      <button type="button" className={`btn ${primary ? '' : 'btn--soft'}`} onClick={onClick}>
        {cta} →
      </button>
    </div>
  );
}

function Cite({ id }: { id: string }) {
  const n = SOURCES.findIndex((s) => s.id === id) + 1;
  if (n === 0) return null;
  return (
    <a className="cite" href={`#${id}`} aria-label={`출처 ${n}`}>
      [{n}]
    </a>
  );
}
