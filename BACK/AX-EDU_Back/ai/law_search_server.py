"""
법령 조회 HTTP 서버 (표준 라이브러리만 사용). Spring Boot 백엔드가 LawSearchClient 로 호출한다.

실행:
    python3 ai/law_search_server.py                 # 기본 127.0.0.1:8090
    python3 ai/law_search_server.py --port 8090 --data AI_DATA/chunks.jsonl
    (환경 변수: LAW_SEARCH_HOST, LAW_SEARCH_PORT, LAW_CHUNKS_PATH)

API:
    GET  /health  → {"status":"ok","chunks":3762,"types":{...}}
    POST /search  ← {"keywords":["휴대전화","압수"], "limit":10, "types":["법령"], "perType":4, "perDoc":2, "org":null}
                  → {"keywords":[...], "count":N, "results":[{chunkId,type,title,article,articleTitle,caseNo,org,
                     lawKind,effectiveDate,sourceUrl,text,snippet,matchedKeywords,score}, ...]}
    잘못된 요청은 400 {"error":"..."}
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))  # 어디서 실행해도 같은 폴더의 law_search 를 쓰도록
from law_search import ALL_TYPES, LawIndex  # noqa: E402

MAX_LIMIT = 50
MAX_KEYWORDS = 20
MAX_BODY = 64 * 1024


class BadRequest(ValueError):
    pass


def parse_search(body: dict) -> dict:
    """요청 JSON 검증 → LawIndex.search 인자"""
    if not isinstance(body, dict):
        raise BadRequest("JSON 객체를 보내 주세요.")
    keywords = body.get("keywords")
    if isinstance(keywords, str):
        keywords = keywords.split()
    if not isinstance(keywords, list) or not all(isinstance(k, str) for k in keywords):
        raise BadRequest("keywords 는 문자열 배열이어야 합니다.")
    keywords = [k.strip() for k in keywords if k.strip()][:MAX_KEYWORDS]
    if not keywords:
        raise BadRequest("keywords 를 한 개 이상 보내 주세요.")

    def int_field(name, default, lo, hi):
        v = body.get(name, default)
        if v is None:
            return default
        if not isinstance(v, int) or isinstance(v, bool) or not (lo <= v <= hi):
            raise BadRequest(f"{name} 은 {lo}~{hi} 사이 정수여야 합니다.")
        return v

    types = body.get("types")
    if types is not None:
        if not isinstance(types, list) or any(t not in ALL_TYPES for t in types):
            raise BadRequest(f"types 는 {list(ALL_TYPES)} 중에서 고르세요.")
        types = types or None
    org = body.get("org")
    if org is not None and not isinstance(org, str):
        raise BadRequest("org 는 문자열이어야 합니다.")
    return {
        "keywords": keywords,
        "limit": int_field("limit", 10, 1, MAX_LIMIT),
        "types": types,
        "per_type": int_field("perType", None, 1, MAX_LIMIT),
        "per_doc": int_field("perDoc", 2, 1, MAX_LIMIT),
        "org": org or None,
    }


def make_handler(index: LawIndex):
    class Handler(BaseHTTPRequestHandler):
        server_version = "AxeduLawSearch/1.0"

        def _send(self, status: int, payload: dict) -> None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def _read_body(self) -> bytes:
            """Content-Length 본문과 Transfer-Encoding: chunked 본문 모두 읽는다"""
            if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
                data = b""
                while True:
                    size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                    if size == 0:
                        self.rfile.readline()  # 마지막 빈 줄
                        break
                    data += self.rfile.read(size)
                    self.rfile.readline()      # 조각 끝 CRLF
                    if len(data) > MAX_BODY:
                        raise BadRequest("요청 본문이 너무 큽니다.")
                return data
            length = int(self.headers.get("Content-Length") or 0)
            if length <= 0 or length > MAX_BODY:
                raise BadRequest("요청 본문이 비었거나 너무 큽니다.")
            return self.rfile.read(length)

        def do_GET(self):  # noqa: N802
            if self.path.split("?")[0] == "/health":
                self._send(HTTPStatus.OK, {"status": "ok", **index.stats()})
            else:
                self._send(HTTPStatus.NOT_FOUND, {"error": "not found"})

        def do_POST(self):  # noqa: N802
            if self.path.split("?")[0] != "/search":
                self._send(HTTPStatus.NOT_FOUND, {"error": "not found"})
                return
            try:
                body = json.loads(self._read_body().decode("utf-8"))
                args = parse_search(body)
            except (BadRequest, json.JSONDecodeError, UnicodeDecodeError, ValueError) as e:
                self._send(HTTPStatus.BAD_REQUEST, {"error": str(e) if isinstance(e, BadRequest) else "JSON 형식이 아닙니다."})
                return
            started = time.perf_counter()
            results = index.search(**args)
            self._send(HTTPStatus.OK, {
                "keywords": args["keywords"],
                "count": len(results),
                "tookMs": round((time.perf_counter() - started) * 1000, 1),
                "results": results,
            })

        def log_message(self, fmt, *args):  # 한 줄 접근 로그
            sys.stderr.write("[law-search] %s - %s\n" % (self.address_string(), fmt % args))

    return Handler


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="법령 조회 HTTP 서버")
    p.add_argument("--host", default=os.environ.get("LAW_SEARCH_HOST", "127.0.0.1"))
    p.add_argument("--port", type=int, default=int(os.environ.get("LAW_SEARCH_PORT", "8090")))
    p.add_argument("--data", default=None, help="chunks.jsonl 경로 (기본 AI_DATA/chunks.jsonl 또는 LAW_CHUNKS_PATH)")
    a = p.parse_args(argv)

    t = time.perf_counter()
    index = LawIndex.load(a.data)
    st = index.stats()
    print(f"[law-search] 청크 {st['chunks']}개 색인 완료 ({time.perf_counter() - t:.2f}s) {st['types']}", flush=True)
    server = ThreadingHTTPServer((a.host, a.port), make_handler(index))
    print(f"[law-search] http://{a.host}:{a.port}  (POST /search, GET /health)", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
