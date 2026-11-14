# AX-EDU

AX-EDU 해커톤 최종 제출 저장소입니다. 슈퍼스쿨 앱에 들어갈 **채팅**과 **AI 민원 소통 창구**의 프론트엔드와 백엔드를 함께 담고 있습니다.

| 폴더 | 내용 | 문서 |
| --- | --- | --- |
| [`FRONT/`](FRONT) | 웹 프론트엔드 (React 18 + Vite + TypeScript) | [FRONT/README.md](FRONT/README.md) |
| [`BACK/AX-EDU_Back/`](BACK/AX-EDU_Back) | 채팅·민원 API 서버 (Spring Boot) + 법령 조회 AI 서버 (Python) | [BACK/AX-EDU_Back/README.md](BACK/AX-EDU_Back/README.md) |

## 빠른 시작

```bash
# 프론트엔드 (기본 목 모드, 서버 없이 동작)
cd FRONT
npm install
npm run dev        # http://localhost:5173
```

```bash
# 프론트엔드 (배포완료, 백엔드 연결 X 목 모드로 서버 없이 동작)
https://ax-edu-front.vercel.app/
```


```bash
# 백엔드 (PostgreSQL 필요, 자세한 내용은 BACK/AX-EDU_Back/README.md)
cd BACK/AX-EDU_Back
cp .env.example .env
./gradlew bootRun  # http://localhost:8080
```

프론트를 로컬 백엔드에 연결하려면 `FRONT/.env` 에 `VITE_USE_MOCK=false`, `VITE_API_HOST=http://localhost:8080` 을 적습니다.

## 배포

- 프론트(Vercel): 루트의 [`vercel.json`](vercel.json)이 `FRONT/` 를 빌드해 `FRONT/dist` 를 배포합니다. Vercel 설정은 바꿀 필요 없습니다.
