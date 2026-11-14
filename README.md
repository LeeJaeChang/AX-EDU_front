# AX-EDU_front

AX-EDU 해커톤 FRONTEND 소스코드

슈퍼스쿨 앱에 들어갈 **채팅(교사·학생·학부모 1:1 대화)** 과 **AI 민원 소통 창구** 의 웹 프론트엔드입니다.
학부모의 첫 화면은 대화 목록이 아닌 **민원하기** 입니다. 여기서 **똑똑 AI 상담사** 와 먼저 이야기합니다. 상담사는 한 번에 답을 내지 않고 여러 대화에 걸쳐 공감 → 사실관계 확인 → 관련 규정·법령 안내 → 누구에게 전할지 추론을 거친 뒤, 감정 표현을 덜어 낸 **요약 카드** 를 만들어 정한 선생님께 전달합니다. 교사는 카드를 보고 AI 도움을 받아 답변하고, 필요하면 민원대응팀에 도움을 요청합니다.

- 근거 문서: `AXEDU 채팅·민원 소통 API 명세서 v1`, `AXEDU DB 스키마 설계서 v1.1`
- 디자인: 채팅 목록·상세 화면 디자인 + [`superschool-style/`](superschool-style/STYLE-GUIDE.md) 스타일가이드

## 빠른 시작

```bash
npm install
npm run dev        # http://localhost:5173
```

```bash
npm run build      # 타입 검사(tsc -b) + 프로덕션 빌드
npm run typecheck  # 타입 검사만
```

처음 열리는 `/`는 **서비스 소개(도입) 페이지**입니다. 기획 문서 ‘아이디어 정리’의 문제 정의 → 원인 → 5단계 핵심 기능 → 요약 카드 → 슈퍼스쿨 연결 → 기존 방식과의 차이 → 보안·윤리 → 기대효과를 한 페이지로 보여 주고, **교사 화면 / 학부모 화면** 버튼으로 데모에 들어갑니다. 코드는 [`src/pages/IntroPage.tsx`](src/pages/IntroPage.tsx)입니다.

기본은 **목(mock) 모드**라 서버 없이 바로 동작합니다. 도입 페이지 버튼이 해당 데모 계정으로 바꿔 들어가고, 들어간 뒤에는 화면 맨 위 검은 띠의 **"보는 사람"** 에서 계정을 바꾸거나 **"← 서비스 소개"** 로 돌아올 수 있습니다.

| 데모 계정 | 볼 수 있는 것 |
| --- | --- |
| 교사 · 김수학 (3학년 9반 담임) | 학생·학부모 채팅, 도착한 민원 카드 응대 (`김민수 학부모` 방에 제출된 민원 1건이 미리 들어 있음) |
| 학부모 · 김리현 보호자 | **민원하기** 화면 → 새 상담 시작 → 상담사의 질문에 답하기(버튼 또는 직접 입력) → 규정·법령 안내 → 선생님 정하기 → 연락 방법·시간 → 요약 카드 → 보내기 |
| 학부모 · 김민수 보호자 | 민원하기 → 지난 민원 → 김수학 선생님과의 대화에서 보낸 카드 확인 (교사 계정에서 답하면 안 읽은 수와 답변·예약 정보가 보임) |

> 목 데이터는 메모리에만 있어 새로고침하면 처음 상태로 돌아갑니다. 민원 내용에 `[AI실패]`를 넣으면 AI 실패(422) → 재시도 흐름을 재현할 수 있습니다.

### 실제 서버에 연결하기

`.env.example`을 `.env`로 복사해 값을 채웁니다.

```bash
VITE_USE_MOCK=false
VITE_API_HOST=https://{superschool-host}
```

- REST: `{VITE_API_HOST}/api/axedu/v1`, `Authorization: Bearer {accessToken}`
- WebSocket: `wss://{host}/ws/axedu/v1?token={accessToken}`
- 액세스 토큰은 `localStorage`의 `axedu.accessToken`에서 읽습니다(슈퍼스쿨 SSO 토큰). 로그인 사용자 정보(`userId`, `role`, `name`)는 토큰 payload에서 읽습니다.

#### 실제 서버에서 시연 계정 바꾸기

서버에 로그인 API가 없어서, 시연 계정의 고정 토큰을 빌드 때 넣어 두고 상단 **‘보는 사람’**에서 바꿔 끼웁니다.

```bash
VITE_DEMO_TOKEN_USR_T1=eyJ...   # 김수학 교사 (3학년 9반 담임)
VITE_DEMO_TOKEN_USR_P1=eyJ...   # 김리현 학부모
VITE_DEMO_TOKEN_USR_P2=eyJ...   # 김민수 학부모
```

- 값은 백엔드 저장소에서 `./gradlew demoTokens`로 만듭니다(서버 시작 로그에도 찍힘). 서버의 `AXEDU_JWT_SECRET`과 같은 키로 서명된 토큰이어야 하므로, 키를 바꾸면 토큰도 다시 만들어 넣습니다.
- 하나라도 넣으면 ‘보는 사람’(태그 ‘시연 계정’)이 보이고, 토큰이 있는 계정만 고를 수 있습니다. 하나도 없으면 선택이 숨겨지고 `axedu.accessToken`의 토큰을 그대로 씁니다.
- 처음 열 때 저장된 토큰이 없거나 시연 계정 토큰이 아니면 첫 계정(교사)으로 시작합니다. 계정을 바꾸면 `axedu.accessToken`을 바꾸고 로그인 사용자 정보(토큰 payload의 `userId`·`role`·`name`)를 다시 읽은 뒤 WebSocket을 새 토큰으로 다시 연결합니다.
- `VITE_` 값은 빌드 결과물에 그대로 들어가 누구나 볼 수 있습니다. 만료일이 있는 시연 계정 토큰만 넣습니다.

### Vercel 배포

GitHub 저장소를 Vercel 프로젝트로 가져오면 [`vercel.json`](vercel.json) 설정대로 빌드합니다(Vite, `npm run build`, 출력 `dist`).

- **새로고침 404 방지**: 화면 주소(`/chat`, `/complaint/…`)는 브라우저 라우팅이라 실제 파일이 없습니다. `vercel.json`의 `rewrites`가 실제 파일이 없는 모든 경로를 `index.html`로 돌려줍니다(`/assets/*` 같은 빌드 파일은 그대로 제공).
- **환경 변수**: 목 데이터 시연이면 입력하지 않습니다(값이 없으면 목 모드). 실제 서버에 붙일 때만 Vercel **Settings → Environment Variables**에 `VITE_USE_MOCK=false`, `VITE_API_HOST`(시연 계정을 바꿔 보려면 `VITE_DEMO_TOKEN_USR_T1`·`_P1`·`_P2`도)를 넣고 **다시 배포**합니다(빌드 때 코드에 들어가는 값이라 값을 바꾼 뒤에도 다시 배포해야 반영됩니다). `.env` 파일은 올리지 않습니다.
- **서버 쪽 준비(실서버 연결 시)**: Vercel 도메인을 CORS 허용 목록에 추가하고, `wss://{VITE_API_HOST 호스트}/ws/axedu/v1` 연결을 열어 둡니다.
- 목 데이터는 브라우저 메모리에만 있어 새로고침하면 처음 상태로 돌아가고, 다른 사용자와 공유되지 않습니다.

## 기술 스택

| 항목 | 사용 |
| --- | --- |
| 프레임워크 | React 18 + TypeScript 5 (strict) |
| 빌드 | Vite 5 |
| 라우팅 | react-router-dom 6 |
| 상태 | React 훅 + Context (외부 상태 라이브러리 없음) |
| 스타일 | 순수 CSS. `superschool-style/tokens.css` 토큰만 사용 |
| 실시간 | 브라우저 WebSocket (자동 재접속 + 30초 ping) |

## 화면과 기능

### 1. 채팅 목록 `/chat`

- **대화 목록 / 새 대화** 탭, 대화 상대 검색(디바운스), 커서 기반 무한 스크롤
- 방 유형별 표시: `공지형` 배지, 방별 대화 가능 시간 배지(`12:00~18:00`), 진행 중 `민원` 배지, 안 읽은 수
- 시각 표기: 오늘이면 `15:13`, 어제면 `어제`, 올해면 `11월 4일` (KST 기준)
- **내 채팅 설정**: 교사는 대화 가능 시간·요일·시간 외 허용 역할·안내 문구, 모두 푸시 알림
- **새 대화**: 역할 필터 → 상대 선택 → 방 생성. 이미 방이 있으면(`409 ROOM_ALREADY_EXISTS`) 그 방으로 이동

### 2. 채팅 상세 `/chat/:roomId`

- 날짜 구분선(`2026년 09월 08일`), 회색 시스템 메시지, 내 말풍선(오른쪽)·상대 말풍선(왼쪽, 이름·학번)
- 전송: `clientMessageId`로 화면에 먼저 그리고(낙관적 전송), 실패 시 **재전송 / 삭제**
- 첨부: `+` → `POST /files` 업로드 → 메시지에 `attachmentIds`로 연결 (이미지 10MB, 문서 20MB)
- 위로 스크롤 시 이전 메시지(`?before=`), 재접속 시 놓친 메시지(`?after=`) 채우기
- 입력창 안내 문구·전송 가능 여부는 서버 값(`inputPlaceholder`, `canSend`)을 따름 → 학부모·학생은 대화 가능 시간 밖이면 입력이 막힘
- 한글 조합 중 Enter는 무시(IME), Shift+Enter 줄바꿈
- 방 **설정**(알림 끄기, 교사는 방별 대화 가능 시간) · **나가기**(민원 진행 중이면 불가)

### 3. 학부모 – 똑똑 AI 상담사 `/complaint[/:complaintId]`

```
채팅 목록 [민원하기] 탭 ─▶ ① 공감 ─▶ ② 사실·규정 확인 ─▶ ③ 선생님 정하기 ─▶ ④ 요약 카드 ─▶ ⑤ 전달
                          경청      일시·장소·있었던 일      소관 추론 +         수정·직접     받는 선생님
                                   ·아이 상태·바라는 점     연락 방법·시간       고치기        확인 후 보내기
                                   → 규정·법령 설명
```

**입구.** 학부모의 `/chat`에는 대화 목록·새 대화 탭이 없고 **'민원하기' 화면 하나**만 있습니다. 여기서 새 상담을 시작하고, 보내지 않은 상담은 "이어서 하기", 보낸 민원은 "지난 민원"으로 모입니다. 선생님과의 대화는 지난 민원 줄에서 들어가며, 선생님이 답하면 그 줄에 안 읽은 수가 뜹니다. 학부모는 선생님 방을 직접 열지 않고 상담을 거쳐 전할 선생님을 정합니다(공지형 방 등 민원과 무관한 방은 학부모 화면에서 보이지 않음). 교사 화면은 그대로 대화 목록 / 새 대화 탭입니다.

**상담사 말투.** 감정을 먼저 알아주고("많이 걱정되고 답답하셨겠어요"), 한 번에 하나씩 열린 질문으로 묻고, 왜 묻는지 덧붙입니다("날짜나 교시를 알면 선생님이 기록을 찾아 확인하기 쉬워요"). 각 질문에는 빠른 답 버튼이 있고 직접 적어도 됩니다.

**상담은 한 번에 끝나지 않습니다.** 상담사는 서버의 상담 단계(`consultStage`)를 따라 여러 번 주고받습니다.

| 단계 (`consultStage`) | 상담사가 하는 일 | 학부모 |
| --- | --- | --- |
| 사전 분류 | 자세한 이야기 전에 5가지 예시 선택지: 생활지도 / 수업·평가 / 친구 관계 / 안전·사고 / 그 밖의 일. 고르면 분류에 맞춰 이야기를 청함 | 하나 고르기 (건너뛰고 바로 적어도 됨) |
| 첫 민원 | 공감 + 상담 방식 안내 + 첫 질문 | 있었던 일을 편하게 적기 |
| `FACTS` 사실관계 | **대응 시급도** → 일시 → 장소·수업 → 자세한 이야기 → 아이 상태 → 바라는 점을 하나씩. 이미 말한 칸은 건너뜀 | 버튼 또는 직접 입력 |
| `EXPLAIN` 규정·법령 | 사실관계 정리표 + 관련 학칙·가정통신문·법령과 이유·대안 | 선생님께 전하기 / 설명으로 충분 / 정리 고치기 |
| `RECIPIENT` 선생님 정하기 | 근거와 함께 추천 (예: "영어 시간 일이라 이영어 선생님이 당시 상황을 가장 잘 아세요", "휴대전화 보관은 담임 소관") | 추천 선생님 또는 다른 선생님 |
| `CONTACT` 연락 | 연락 방법(채팅·안심전화·방문 면담) → 시간대 | 버튼 또는 직접 입력 |
| `READY` | 최종 정리표(전할 선생님·희망 연락 포함) | 요약 카드 만들기 |

| 화면 동작 | API | 상태 |
| --- | --- | --- |
| 첫 민원 → 상담사 첫 반응 | `POST /complaints` (`roomId` 없이) | `EXPLAINED` |
| AI 실패 시 원문 보존 + "다시 시도" | `POST /complaints/{id}/explanation/retry` | |
| 답할 때마다 → 상담사의 다음 말 | `POST /complaints/{id}/consultations` `{content, value?}` *(추가 제안)* | 유지 |
| "설명으로 충분해요" | `POST /complaints/{id}/close` | `CLOSED_BY_EXPLANATION` |
| "요약 카드 만들기" (`READY`에서만) → 상담에서 확인한 일시·장소·아이 상태·바라는 점·연락을 카드에 반영 | `POST /complaints/{id}/summary-card` | `CARD_DRAFTING` |
| 말로 수정 요청 / 직접 고치기 / AI 순화 제안 | `POST …/revisions` · `PATCH …/summary-card` | |
| 받는 선생님 확인 (상담에서 정한 분이 기본, 바꿀 수 있음) | `GET /complaints/{id}/recipients` *(추가 제안)* | |
| "○○ 선생님께 보내기" → 그 선생님과의 DIRECT 방(없으면 새로 만듦)에 `COMPLAINT_CARD` | `POST /complaints/{id}/submit` (`teacherId` 추가) | `SUBMITTED` |

상담 엔진(목)은 [`src/api/mock/mockConsult.ts`](src/api/mock/mockConsult.ts)에 있습니다. 실제 서버에서는 LLM과 학교 공개 자료 검색이 같은 단계 구조로 답하면 됩니다.

**빠른 대응 여부.** 사실관계 질문의 첫 번째로 "선생님이 얼마나 빨리 확인해 주시면 좋을까요?"를 묻습니다(오늘 안에 / 며칠 안에 / 급하지 않아요). 첫 이야기에 다침·병원·폭력 같은 표현이 있으면 "서둘러 살펴야 할 일일 수도 있겠어요"라고 짚고 묻습니다. '오늘 안에'를 고르면 112·119·교무실 안내를 함께 하고, 카드에 `urgency`(긴급 · 오늘 안에 확인 + 사유)로 담깁니다. 교사 화면에는 붉은 배너와 카드 머리 칩으로 먼저 보입니다.

**학부모 감정 상태.** 상담 중 학부모가 직접 쓴 말(빠른 답 제외)로 감정 상태를 판단해 카드의 `parentEmotion`에 담습니다. 판단 결과는 차분함 / 걱정·속상함 / 격양됨 세 가지이고, 상담 처음과 끝을 비교한 흐름(차분해짐·격해짐)과 교사를 위한 첫 연락 팁도 함께 담습니다. 교사에게는 **원문 대신 신호 분류만** 전합니다(강한 불만 표현, 반복된 느낌표·물음표, 책임 추궁·외부 신고 언급 등). "교육청에 신고할 거예요" 같은 문장은 카드 본문에서 빼고 신호로만 남깁니다. **마음 상태는 교사(와 민원대응팀)에게만 보입니다.** 서버는 학부모 응답의 카드에서 `parentEmotion`을 빼고, 화면도 교사 화면에서만 표시합니다(`SummaryCardView`의 `showEmotion`). 학부모의 상담 정리표에도 넣지 않으며, AI 판단이라 학부모가 고칠 수도 없습니다. 처음 격한 표현이 나오면 상담사는 "충분히 이해해요. 천천히 하나씩 함께 정리해 볼게요"라고 마음을 받아 줍니다.

> **명세서와 다른 점.** 명세서 7.3은 `POST /complaints`에 `roomId`가 필수지만, 새 흐름에서는 받는 선생님을 상담에서 정하므로 선택 값으로 두고 제출 때 `teacherId`를 받습니다. 상담 이어 가기(`/consultations`), 받는 선생님 목록(`/recipients`), 학부모용 응답 필드(`consultation`, `consultStage`, `proposedTeacher`, `category`), 생성 시 `category`, 카드의 `urgency`·`parentEmotion`은 명세서에 없어 서버와 맞춰야 합니다. 교사 화면과 교사가 받는 데이터는 그대로입니다(상담 내용은 원문처럼 교사에게 내려가지 않음).

### 4. 교사 – 민원 응대 (채팅방 오른쪽 드로어)

채팅방 위 민원 배너 또는 카드 메시지의 **민원 응대하기** 로 엽니다. 학부모 원문과 AI 설명은 교사에게 내려오지 않습니다(명세서 3.5·7.7).

| 단계 | 화면 동작 | API | 상태 |
| --- | --- | --- | --- |
| STEP 3 | 드로어를 열면 열람 처리 | `POST /complaints/{id}/acknowledge` | `TEACHER_REVIEWING` |
| | ① 입장 정리: 상황·이유·앞으로의 조치 메모 → AI 답변 초안 | `POST …/reply-drafts` | |
| STEP 4 | ② 학부모에게 보낼 연락: 채팅 / 안심전화 / 방문 면담 + 시각 | `POST /complaints/{id}/reply` | `IN_CONVERSATION` |
| STEP 5 | 민원 대응팀 도움 요청: 사유(복수) + 상황 설명(2,000자) | `POST …/escalations` | `ESCALATED` |
| | 처리 종료 | `POST /complaints/{id}/resolve` | `RESOLVED` |

학부모가 원하는 연락 방법이 기본으로 선택되고, 답변은 채팅방에 `COMPLAINT_REPLY` 메시지(예약 정보 포함)로 올라갑니다.

## 프로젝트 구조

```
src/
├─ main.tsx                 # 진입점 (스타일 토큰 → app.css 순서로 로드)
├─ App.tsx                  # 라우팅, 데모 띠, 학부모 전용 라우트 가드
├─ api/
│  ├─ types.ts              # 명세서 3장 데이터 모델 + 요청/응답 + WebSocket 이벤트 타입
│  ├─ client.ts             # fetch 래퍼: Base URL, Bearer 토큰, 응답 봉투 해석, ApiError
│  ├─ chatApi.ts            # 채팅 API 인터페이스 + HTTP 구현 (명세서 4·5·6장)
│  ├─ complaintApi.ts       # 민원 API 인터페이스 + HTTP 구현 (명세서 7장)
│  ├─ index.ts              # VITE_USE_MOCK에 따라 HTTP / 목 구현 선택
│  └─ mock/                 # 서버 없이 동작하는 메모리 목 서버
│     ├─ store.ts           # 사용자·방·메시지·민원 저장소, 대화 가능 시간 판정, 이벤트 버스
│     ├─ seed.ts            # 화면 디자인 기준 시드 데이터
│     ├─ mockChatApi.ts     # 채팅 API 목 (권한·멱등성·409·403 규칙 포함)
│     ├─ mockComplaintApi.ts# 민원 API 목 (상태 전이·역할별 응답 모양)
│     ├─ mockAi.ts          # 키워드 규칙으로 흉내 낸 AI (규정·법령 설명·카드·수정·순화·답변 초안)
│     └─ mockConsult.ts     # 똑똑 AI 상담사: 공감 → 사실관계 → 규정·법령 → 선생님 추론 → 연락 → 카드 준비
├─ realtime/socket.ts       # WebSocket 클라이언트 (재접속 백오프, ping, 재접속 콜백)
├─ hooks/
│  ├─ useMe.tsx             # 로그인 사용자 Context, 데모 계정 전환
│  ├─ useRooms.ts           # 목록 + 실시간 갱신
│  ├─ useMessages.ts        # 메시지 페이징·낙관적 전송·재전송·읽음
│  ├─ useComplaint.ts       # 민원 1건 조회 + 상태 변경 이벤트 시 다시 불러오기
│  └─ useDebounced.ts
├─ lib/
│  ├─ format.ts             # KST 날짜·시각 표기, 파일 크기
│  └─ complaint.ts          # 민원 상태·태그·연락 방법·이관 사유 라벨
├─ pages/
│  ├─ ChatListPage.tsx       # 교사: 대화 목록·새 대화 탭 / 학부모: 민원하기 화면
│  ├─ ChatRoomPage.tsx
│  └─ ComplaintChatbotPage.tsx
├─ components/
│  ├─ Composer.tsx, MessageList.tsx, RoomListItem.tsx, ContactList.tsx, Avatar.tsx …
│  ├─ Modal.tsx, ConfirmModal.tsx, ChatSettingsModal.tsx, RoomSettingsModal.tsx, DemoBar.tsx
│  └─ complaint/
│     ├─ SummaryCardView.tsx      # 요약 카드 (학부모·교사 공용)
│     ├─ ExplanationView.tsx      # AI 규정 설명
│     ├─ RegulationList.tsx       # 근거 규정 목록
│     ├─ CardEditorModal.tsx      # 카드 직접 수정
│     ├─ TeacherComplaintDrawer.tsx # 교사 응대 드로어
│     ├─ EscalationModal.tsx      # 민원 대응팀 도움 요청
│     ├─ ComplaintMessage.tsx     # 채팅방의 카드·답변 말풍선
│     ├─ ComplaintBanner.tsx      # 채팅방 상단 민원 안내
│     ├─ ParentComplaintEntry.tsx # 학부모 첫 화면 '민원하기' (새 상담 · 이어서 할 상담 · 지난 민원 + 안 읽은 수)
│     └─ StatusPill.tsx           # 역할별 상태 배지
└─ styles/app.css           # 화면 스타일 (토큰 별칭 → 컴포넌트 → 반응형)
superschool-style/           # 스타일가이드 원본 (tokens.css를 앱이 직접 import)
```

## 설계 메모

**API 계층을 인터페이스로 분리했습니다.** `ChatApi`, `ComplaintApi` 인터페이스를 HTTP 구현과 목 구현이 함께 따르므로, 화면 코드는 어느 쪽인지 모릅니다. 서버가 준비되면 `VITE_USE_MOCK=false`만 바꾸면 됩니다.

**응답 봉투와 에러.** 모든 응답은 `{ success, data, meta }` / `{ success: false, error: { code, message, details } }` 형태이고, `client.ts`가 실패를 `ApiError(status, code, details)`로 바꿉니다. 화면은 `code`로 분기합니다. 예: `ROOM_ALREADY_EXISTS` → 기존 방으로 이동, `AI_PROCESSING_FAILED` + `details.complaintId` → 저장된 민원으로 이어서 재시도, `VALIDATION_ERROR` → `details.fields`를 항목별로 표시.

**멱등성.** 메시지는 `clientMessageId`, 민원 생성·제출은 `Idempotency-Key` 헤더를 보내 중복 전송을 막습니다.

**실시간 갱신.** 전송은 REST, 수신은 WebSocket 하나로 받습니다(`message.created`, `message.read`, `room.updated`, `room.unread_changed`, `complaint.status_changed`, `complaint.card_updated`, `escalation.*`). 목 모드에서는 같은 인터페이스의 메모리 이벤트 버스가 이 역할을 합니다.

**역할별로 다르게 보이는 데이터.** 교사에게는 학부모 원문·AI 설명을 주지 않고 제출된 버전의 카드만 보여 줍니다. 학부모에게는 이관 여부를 드러내지 않고 "학교에서 확인 중"으로 표시합니다(명세서 미결 사항).

**시간대.** 서버 시각은 KST 오프셋 ISO 8601입니다. 표기는 기기 시간대와 무관하게 `Asia/Seoul`로 하고, 정렬은 문자열이 아니라 실제 시각(`Date.parse`)으로 비교합니다.

**스타일.** 웜 화이트 배경 + 오렌지 단일 액센트(채움은 `--accent`, 글자는 `--accent-ink`) + 1px `--line` 보더. 색·간격·radius·그림자는 모두 `tokens.css` 토큰을 쓰고, 640px 이하에서 모바일 레이아웃으로 바뀝니다.

## 남은 일

- 민원대응팀 화면(사건 파일 확인, 담당 배정, 교감·교장 상신) — 명세서에서도 "미설계"
- 명세서 미결 사항 반영: 시간 외 메시지 예약 전달, 자녀가 여럿인 학부모의 학생 선택(지금은 "자녀 1/2"로 표시), AI 응답 SSE 스트리밍 여부, 이관 시 학부모 알림
- 새 학부모 흐름의 API 합의: `POST /complaints`의 `roomId` 선택화, `POST /complaints/{id}/consultations`, `GET /complaints/{id}/recipients`, 제출 시 `teacherId`, 상담 단계 필드, 카드의 `urgency`·`parentEmotion`, 긴급 카드의 시간 외 즉시 알림 여부 — 받는 선생님 후보는 실제 학급·교과 배정 데이터로, 법령 인용은 실제 등록 자료로 바꿔야 함
- 실제 서버 연동 테스트 (현재는 목 서버로만 검증)
- 목 AI는 키워드 규칙이라 문장 정리가 어색할 수 있음 — 실제로는 서버의 규정 검색 + LLM이 담당
