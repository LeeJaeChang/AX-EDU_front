# Superschool 기능 소개 페이지 — 스타일 분석

- 분석 대상: `https://front.superschool.link/guides/superschool-features.html`
- 분석일: 2026-11-13
- 원본 구성: 단일 HTML 파일, 인라인 `<style>` 1개(약 23KB), Google Fonts(Noto Sans KR 400–800), 탭 전환용 인라인 스크립트 1개. 프레임워크·CSS 라이브러리 없음. 이미지는 base64로 HTML에 내장.

## 파일 구성

| 파일 | 내용 |
|---|---|
| `tokens.css` | 색상·폰트·타입 스케일·간격·radius·그림자 토큰 (최종값) |
| `superschool-style.css` | 베이스 + 레이아웃 + 컴포넌트, 역할별로 재정리 |
| `tabs.js` | "탭 하나 = 글 한 편" 전환 스크립트 (키보드·해시·기억 지원) |
| `demo.html` | 모든 컴포넌트 미리보기 |

불러오는 순서: `tokens.css` → `superschool-style.css` → (탭을 쓸 때) `tabs.js`

---

## 1. 디자인 성격 한 줄 요약

**웜 화이트 배경 + 오렌지 단일 액센트 + 단일 한글 산세리프(Noto Sans KR) + 얇은 보더 카드.**
장식은 거의 없고, 정보 구조를 보더·헤어라인·번호로 드러냅니다. 그림자는 제품 화면(목업, 스크린샷)에만 씁니다.

## 2. 색상

| 토큰 | 값 | 용도 |
|---|---|---|
| `--bg` | `#FAFAF8` | 페이지 배경 |
| `--surface` | `#FFFFFF` | 카드, 헤더, 탭바 |
| `--ink` | `#1B1B1B` | 텍스트, CTA 밴드 배경 |
| `--muted` | `#62625E` | 보조 텍스트 (카드 설명, 캡션) |
| `--line` | `#E8E6E1` | 모든 보더와 구분선 |
| `--accent` | `#FF600C` | 버튼, 아바타, 원형 번호, 진행바, 활성 탭 밑줄 |
| `--accent-ink` | `#D2470A` | 오렌지 **텍스트** (대비 확보용 진한 버전) |
| `--accent-soft` | `#FFEFE6` | 오렌지 틴트 배경 |
| `--mark` / `-soft` | `#B7791F` / `#FBF1DC` | 메모·코멘트·"예정" |
| `--ok` / `-soft` | `#1E8A5A` / `#E3F4EB` | 개선점·제안·완료 |
| `--warn` / `-soft` | `#B4380B` / `#FDE9DF` | 문제점·경고 |

규칙:
- 오렌지는 **채움(배경)엔 `--accent`, 글자엔 `--accent-ink`**로 나눠 씁니다. 흰 배경 위 오렌지 텍스트의 가독성을 위한 구분입니다.
- 상태색은 항상 `진한색 + soft` 쌍으로 씁니다 (글자=진한색, 배경=soft).
- 3단계 표현(stage/phase)에서 1단계만 오렌지, 2·3단계는 회색(`#3A3A38`, `#8D8A83`)으로 내려 주목도를 하나에 모읍니다.
- 히어로 섹션은 `#FFF3EA → --bg` 세로 그라디언트로 아주 옅게 물들입니다.

## 3. 타이포그래피

폰트는 **Noto Sans KR 하나**로 display/body/mono 역할을 모두 처리합니다. 숫자는 `font-variant-numeric: tabular-nums`로 자릿수를 맞춥니다.

| 요소 | 크기 | 굵기 | 자간 | 행간 |
|---|---|---|---|---|
| h1 | `clamp(2rem, 5vw, 3.25rem)` (32→52px) | 800 | -0.03em | 1.3 |
| h2 | `clamp(1.5rem, 3.2vw, 2.125rem)` (24→34px) | 800 | -0.02em | 1.3 |
| h3 | 1.125rem (18px) | 700 | – | 1.3 |
| 큰 h3 (지그재그 등) | 1.375rem (22px) | 700 | – | 1.3 |
| lead | 1.125rem | 400 | – | 1.75 |
| 본문 | 16px | 400 | – | 1.75 |
| 카드 설명 | .9375rem (15px) | 400 | – | 1.75 |
| 라벨(eyebrow) | .875rem | 700 | 0.02em | – |
| 칩·노트 | .75rem (12px) | 400–700 | – | – |
| 통계 숫자 | `clamp(1.75rem, 4vw, 2.5rem)` | 600 | – | 1 |

기타:
- `word-break: keep-all` — 한글을 어절 단위로 줄바꿈.
- `text-wrap: balance` — 제목 줄 길이 균형.
- 히어로 h1에서 강조 단어를 `<em>`으로 감싸 오렌지(`--accent`)로 칠합니다 (이탤릭 제거).

## 4. 레이아웃

- 컨테이너: `max-width: 72rem` (1152px), 좌우 여백 `max(16px, safe-area-inset)`.
- 섹션: 상하 64px, 섹션 사이 1px `--line` 구분선. 히어로는 72px / 56px.
- 섹션 머리(`.head`): 라벨 → h2 → (리드), 간격 12px, 아래 여백 32px, 최대폭 44rem.
- 그리드는 대부분 `repeat(auto-fit, minmax(min(100%, Xrem), 1fr))` 패턴으로 미디어쿼리 없이 줄어듭니다. 카드 20rem, 작은 박스 15rem, 흐름 11rem.
- 고정 열 그리드에는 항상 `minmax(0, 1fr)`을 써서 긴 내용이 열을 밀어내지 않게 합니다.
- 페이지 구조: **고정 헤더 → sticky 탭바 → 탭마다 `<article class="topic">` 한 편 → 공통 CTA 밴드.**

### 반응형 브레이크포인트

| 너비 | 변화 |
|---|---|
| 900px | 히어로 2열 → 1열 |
| 860px | 3단계 로드맵 세로 배치, 화살표 90° 회전 |
| 820px | 스크린샷 2열·문제/해결 행·지그재그 → 1열 |
| 760px | 3열 헤어라인 그리드 → 1열 |
| 720px | 정의 표 row → 1열 |
| 640px | 통계·비교 2열 → 1열 |
| 560px | 헤더 태그라인 숨김 |
| 460px | 목업 타일 2열 → 1열 |

## 5. 형태 (Radius / Shadow / Border)

- Radius 단계: 5 (키워드) · 6 (토큰, 포커스) · 10 (타일, 코멘트) · 12 (일반 박스, 프레임) · 14 (헤어라인 묶음) · 16 (카드, 목업) · 999 (버튼, 태그).
- 보더는 전부 `1px solid var(--line)`. 강조용 변형: 정의 표 상단 `2px solid var(--ink)`, 로드맵 상단 `4px` 컬러, 코멘트 좌측 `3px` 머스터드, 점선(`dashed`)은 하위 구분과 "준비 중" 프레임.
- 그림자는 **웜 브라운(rgba(60,40,20))에 음수 spread**를 줘 바닥에만 은은하게 깔립니다. 일반 카드엔 그림자가 없습니다.
  - 목업: `0 12px 32px -18px rgba(60,40,20,.35)`
  - 스크린샷: `0 14px 34px -22px rgba(60,40,20,.45)`

## 6. 컴포넌트 목록

원본 클래스명 일부를 쓰임새가 드러나게 바꿨습니다. 원본 HTML을 그대로 옮겨 올 때는 아래 표를 참고해 이름을 맞추세요.

| 정리본 클래스 | 원본 클래스 | 설명 |
|---|---|---|
| `.mast`, `.tabs`, `.tab` | 동일 | 헤더, sticky 탭바 (활성: 3px 오렌지 밑줄) |
| `.t-hero` | 동일 | 그라디언트 히어로 |
| `.split`, `.split-copy` | `.sb-grid`, `.sb-copy` | 히어로 2열 (카피 1 : 목업 1.1) |
| `.btn`, `.btn.ghost` | 동일 | 오렌지 pill 버튼 / 어두운 배경용 외곽선 버튼 |
| `.tag`, `.tag.later`, `.ktag`, `.badge`, `.chip`, `.kw` | 동일 | 라벨류 |
| `.cards` / `.card` | 동일 | 기본 카드 (radius 16, 그림자 없음) |
| `.minis` / `.mini` | `.manage` / `.m` | 작은 기능 박스 |
| `.figs` / `.fig` | 동일 | 1px 헤어라인 통계 그리드 |
| `.tri-grid` | `.ai3` | 1px 헤어라인 3단 설명 |
| `.rows` / `.row` | 동일 | 정의 표 (항목 · 값 · 부가설명) |
| `.sol-item` | 동일 | 문제 → "달라지는 점" 3열 행 |
| `.compare .before/.after` | `.pg .p/.g` | 이전/이후 비교 박스 |
| `.flow` | 동일 | CSS counter 자동 번호 가로 흐름 |
| `.timeline` | `.ai-steps` | 원형 번호 + 세로 연결선 |
| `.chain` | 동일 | 인라인 단계 토큰 |
| `.stages` / `.stage` | `.stages` / `.stage`, `.st-*` | 3단계 로드맵 (상단 컬러 보더, `--c` 변수로 색 전환) |
| `.shots` / `.shot` / `.frame` | 동일 | macOS 창 프레임 스크린샷 |
| `.mock` | `.board`, `.rec` | HTML로 그린 제품 화면 카드 |
| `.tiles`, `.bar`, `.comment`, `.utt`, `.next` | 동일 | 목업 내부 요소 |
| `.zrows` / `.zrow` | 동일 | 텍스트·이미지 지그재그 |
| `.cta` | 동일 | 어두운 CTA 밴드 |

### 눈여겨볼 기법

- **헤어라인 그리드**: 부모에 `gap:1px; background:var(--line)`, 자식에 흰 배경을 주면 1px 구분선이 생깁니다. 바깥은 `border + radius + overflow:hidden`.
- **스크린샷 창 프레임**: `.frame::before`에 `radial-gradient` 세 겹으로 신호등 점 3개를 이미지 없이 그립니다.
- **컬러 변수 스위칭**: `.stage`에 `--c`, `--cs`를 정의하고 `.s2`, `.s3`에서 값만 바꿔 보더·번호·태그 색을 한 번에 바꿉니다.
- **자동 번호**: `counter-reset` / `counter-increment` / `content: counter()`로 HTML에 번호를 적지 않습니다.
- **"달라지는 점" 라벨**: `.sol-item .g::before { content: "달라지는 점" }`로 마크업 없이 라벨을 붙입니다.
- **safe-area 대응**: `body::before`로 노치 영역을 헤더색으로 채우고, 탭바 `top`과 좌우 여백에 `env(safe-area-inset-*)`를 씁니다.

## 7. 탭 동작 (`tabs.js`)

원본은 "탭 하나 = 독립된 글 한 편" 구조입니다. `<article class="topic" id="..." data-tab="이름">`을 나열하면 스크립트가 탭 버튼을 만들고, 선택된 article만 보여 줍니다.

- 클릭 시 맨 위로 스크롤 + URL 해시 갱신 (`#superboard` 처럼 공유 가능)
- ← → Home End 키로 탭 이동 (ARIA `tablist`/`tab`/`tabpanel`)
- 마지막으로 본 탭을 `localStorage`에 기억
- 탭 전환 시 0.25초 페이드업 (`prefers-reduced-motion` 존중)

원본에는 탭바 끝에 "+ 다음 기능 / 준비 중" 자리표시자와 탭 부제(`<small>`)가 있었지만, 최종 스타일에서 `display:none` 처리돼 있어 정리본에서는 뺐습니다.

## 8. 적용 시 참고

- 로고, 서비스명, 문구, 스크린샷은 원본 서비스의 것이므로 빼고 직접 채워 넣으세요. 정리본과 데모에는 넣지 않았습니다.
- 다크 모드는 원본에 없습니다 (`color-scheme: light` 고정). 필요하면 `tokens.css`의 색상 변수만 `@media (prefers-color-scheme: dark)`에서 재정의하면 됩니다.
- 원본 CSS 뒤쪽에 "슈퍼스쿨 홈페이지 톤" 오버라이드 블록이 있어 제목 굵기(700→800), 버튼(사각 ink → 오렌지 pill), 카드 radius(12→16), 탭 밑줄색 등이 덮어써져 있었습니다. 정리본은 덮어쓴 **최종값**만 반영했습니다.
