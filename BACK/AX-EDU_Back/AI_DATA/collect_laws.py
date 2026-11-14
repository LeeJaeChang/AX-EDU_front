"""
교권 민원 중재 AI용 법령 데이터 수집 스크립트 (국가법령정보 공동활용 Open API)

사용법:
    py collect_laws.py --oc 내OC값
    (또는 환경변수 LAW_OC 설정 후 py collect_laws.py)

결과 (data/ 폴더):
    raw/<종류>/<ID>.json   API 원본 응답
    documents.csv          수집한 문서 목록 (제목, 시행일, 출처)
    chunks.jsonl           RAG용 청크 (법령·고시·조례는 조문 단위, 나머지는 길이 단위)

표준 라이브러리만 사용합니다. API 서버 부담을 줄이려고 호출 사이에 0.5초씩 쉽니다.
"""
import argparse
import csv
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE = "http://www.law.go.kr/DRF/"
SLEEP = 0.7

# ---------------------------------------------------------------------------
# 수집 대상 설정: 필요하면 여기만 고치면 됩니다.
# ---------------------------------------------------------------------------

# 법률·시행령: (검색어, 법령명 정규식)
LAWS = [
    ("교원의 지위 향상", r"^교원의 지위 향상 및 교육활동 보호를 위한 특별법( 시행령| 시행규칙)?$"),
    ("중등교육법", r"^초.중등교육법( 시행령| 시행규칙)?$"),
    ("교육기본법", r"^교육기본법( 시행령)?$"),
    ("아동학대범죄", r"^아동학대범죄의 처벌 등에 관한 특례법( 시행령)?$"),
    ("아동복지법", r"^아동복지법( 시행령)?$"),
    ("학교폭력예방", r"^학교폭력예방 및 대책에 관한 법률( 시행령)?$"),
    ("개인정보 보호법", r"^개인정보 보호법( 시행령)?$"),
    ("민원 처리에 관한 법률", r"^민원 처리에 관한 법률( 시행령)?$"),
]

# 행정규칙(고시 등): 교육부 소관 중 이름에 키워드가 들어간 현행 규칙
ADMRUL_QUERIES = ["학생생활지도", "교육활동 침해", "교육활동 보호", "민원"]
ADMRUL_NAME_RE = r"생활지도|교육활동|민원"

# 자치법규: 시·도교육청 조례 중 학생인권·교권 관련
ORDIN_QUERIES = ["학생인권", "교권", "교육활동 보호", "교원의 교육활동"]
ORDIN_NAME_RE = r"인권|교권|교육활동"

# 판례: 본문 검색 후, 학교 맥락이 있는 것만
PREC_QUERIES = ["교육활동 침해", "학생생활지도", "교권보호위원회", "교사 아동학대"]
PREC_MAX_PER_QUERY = 20
PREC_MUST_CONTAIN = ["교사", "교원", "담임"]

# 법제처 법령해석례
EXPC_QUERIES = ["학교폭력", "학생", "학부모", "아동학대", "생활지도", "학칙", "학교 규칙", "교육활동"]
EXPC_NAME_RE = r"생활지도|교육활동|학교폭력|학부모|보호자|아동학대|학생인권|학교 ?규칙|학칙|체벌|징계|민원"

# 국가인권위원회 결정례
NHRCK_QUERIES = ["휴대전화", "생활지도", "체벌", "두발", "학생 인권", "교사"]
NHRCK_MAX_PER_QUERY = 30
NHRCK_MUST_CONTAIN = ["학교", "학생"]

# 교육부 법령해석 (API 신청에서 '교육부 법령해석'을 체크해야 동작)
MOE_QUERIES = ["학생생활지도", "교육활동", "교원", "학부모", "학교폭력"]

CHUNK_SIZE = 1500
CHUNK_OVERLAP = 200


# ---------------------------------------------------------------------------
# API 호출
# ---------------------------------------------------------------------------
class NotAuthorized(Exception):
    pass


def call(path, oc, **params):
    params.update(OC=oc, type="JSON")
    url = BASE + path + "?" + urllib.parse.urlencode(params)
    last_err = None
    for attempt in range(3):
        try:
            time.sleep(SLEEP)
            with urllib.request.urlopen(url, timeout=40) as r:
                text = r.read().decode("utf-8", errors="replace")
            if "미신청된 목록/본문" in text:
                raise NotAuthorized(params.get("target"))
            if text.lstrip().startswith("<"):
                raise ValueError("JSON이 아닌 응답: " + re.sub(r"<[^>]+>|\s+", " ", text)[:200])
            return json.loads(text)
        except NotAuthorized:
            raise
        except (OSError, ValueError) as e:  # URLError, 연결 끊김, 타임아웃 포함
            last_err = e
            time.sleep(5 * (attempt + 1))
    raise RuntimeError(f"호출 실패 {params.get('target')}: {last_err}")


def unwrap(d):
    """{'LawSearch': {...}} 같은 최상위 래퍼를 벗긴다."""
    return next(iter(d.values())) if isinstance(d, dict) and len(d) == 1 else d


def search(oc, target, query, max_items=200, **extra):
    """검색 결과 목록(dict 리스트)을 페이지를 넘기며 가져온다."""
    items, page = [], 1
    while len(items) < max_items:
        res = unwrap(call("lawSearch.do", oc, target=target, query=query,
                          display=100, page=page, **extra))
        batch = next((v for v in res.values() if isinstance(v, list)), None)
        if batch is None:
            single = next((v for v in res.values() if isinstance(v, dict)), None)
            batch = [single] if single else []
        items.extend(batch)
        total = int(res.get("totalCnt", 0) or 0)
        if not batch or len(items) >= total:
            break
        page += 1
    return items[:max_items]


def field(item, *suffixes):
    """키 이름이 suffix로 끝나는 첫 값 (API마다 키 이름이 조금씩 달라서)."""
    for s in suffixes:
        for k, v in item.items():
            if k.endswith(s) and v:
                return v
    return ""


def detail_params(item):
    """'...상세링크'에서 ID/MST 파라미터를 꺼낸다."""
    link = field(item, "상세링크")
    q = urllib.parse.parse_qs(urllib.parse.urlparse(link).query)
    return {k: q[k][0] for k in ("ID", "MST") if k in q}


# ---------------------------------------------------------------------------
# 텍스트 정리·청크
# ---------------------------------------------------------------------------
def clean(s):
    s = re.sub(r"<br\s*/?>", "\n", str(s))
    s = re.sub(r"<[^>]+>", "", s)
    s = re.sub(r"[ \t]+", " ", s)
    return re.sub(r"\n\s*\n+", "\n", s).strip()


def collect_text(node, keys):
    """중첩 구조에서 지정한 키의 문자열을 문서 순서대로 모은다."""
    out = []
    if isinstance(node, dict):
        for k, v in node.items():
            if k in keys:
                out.extend(flatten_str(v))
            elif isinstance(v, (dict, list)):
                out.extend(collect_text(v, keys))
    elif isinstance(node, list):
        for x in node:
            out.extend(collect_text(x, keys))
    return out


def flatten_str(v):
    if isinstance(v, str):
        return [v]
    if isinstance(v, list):
        return [s for x in v for s in flatten_str(x)]
    return []


def split_len(text, size=CHUNK_SIZE, overlap=CHUNK_OVERLAP):
    if len(text) <= size:
        return [text]
    parts, i = [], 0
    while i < len(text):
        parts.append(text[i:i + size])
        i += size - overlap
    return parts


def as_list(v):
    return v if isinstance(v, list) else [v] if v else []


ARTICLE_KEYS = {"조문내용", "항내용", "호내용", "목내용"}


def law_chunks(body, meta):
    out = []
    for a in as_list(body.get("조문", {}).get("조문단위")):
        if a.get("조문여부") != "조문":
            continue
        text = clean("\n".join(collect_text(a, ARTICLE_KEYS)))
        no = a.get("조문번호", "")
        if a.get("조문가지번호"):
            no += "의" + a["조문가지번호"]
        out.append({**meta, "article": f"제{no}조", "article_title": a.get("조문제목", ""), "text": text})
    return out


def admrul_chunks(body, meta):
    out, chapter = [], ""
    for s in flatten_str(body.get("조문내용")):
        s = clean(s)
        m = re.match(r"^(제\d+조(?:의\d+)?)\s*\(([^)]*)\)", s)
        if m:
            out.append({**meta, "article": m.group(1), "article_title": m.group(2),
                        "chapter": chapter, "text": s})
        elif re.match(r"^제\d+장", s):
            chapter = s
        elif out:
            out[-1]["text"] += "\n" + s
    return out


def ordin_chunks(body, meta):
    out = []
    for a in as_list(body.get("조문", {}).get("조")):
        if a.get("조문여부") not in ("Y", "조문"):
            continue
        s = clean(a.get("조내용", ""))
        m = re.match(r"^(제\d+조(?:의\d+)?)\s*\(([^)]*)\)", s)
        out.append({**meta, "article": m.group(1) if m else "", "article_title": m.group(2) if m else "",
                    "text": s})
    return out


# 판례·해석례·결정례: 핵심 필드 우선, 나머지는 길이 단위로 자른다
DOC_FIELDS = {
    "prec": ["판시사항", "판결요지", "참조조문", "참조판례", "판례내용"],
    "expc": ["안건명", "질의요지", "회답", "이유"],
    "nhrck": ["사건명", "결정요지", "판단요지", "주문", "이유"],
}


def doc_chunks(body, meta, target):
    fields = DOC_FIELDS.get(target)
    if not fields:  # 구조를 모르는 종류: 긴 문자열 필드 전부
        fields = [k for k, v in body.items() if isinstance(v, str) and len(v) > 30]
    out = []
    for f in fields:
        v = body.get(f)
        if not v or not isinstance(v, str):
            continue
        for i, part in enumerate(split_len(clean(v))):
            out.append({**meta, "section": f, "part": i, "text": part})
    return out


# ---------------------------------------------------------------------------
# 수집
# ---------------------------------------------------------------------------
class Collector:
    def __init__(self, oc, out):
        self.oc, self.out = oc, Path(out)
        self.docs, self.chunks, self.skipped, self.added = [], [], [], set()
        (self.out / "raw").mkdir(parents=True, exist_ok=True)

    def fetch(self, target, item):
        params = detail_params(item)
        key = params.get("MST") or params.get("ID")
        raw_path = self.out / "raw" / target / f"{key}.json"
        if raw_path.exists():
            data = json.loads(raw_path.read_text(encoding="utf-8"))
        else:
            data = call("lawService.do", self.oc, target=target, **params)
            raw_path.parent.mkdir(parents=True, exist_ok=True)
            raw_path.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
        return key, unwrap(data)

    def add(self, target, kind, key, title, date, url, chunks):
        dedup = (kind, title, date)  # 같은 사건이 다른 일련번호로 두 번 나오는 경우 제외
        if dedup in self.added:
            return
        self.added.add(dedup)
        self.docs.append({"type": kind, "target": target, "id": key, "title": title,
                          "date": date, "url": url, "chunks": len(chunks)})
        for i, c in enumerate(chunks):
            c["chunk_id"] = f"{target}-{key}-{i}"
            self.chunks.append(c)
        print(f"  + [{kind}] {title} ({len(chunks)}개 청크)")

    def run_section(self, name, fn):
        print(f"\n== {name}")
        try:
            fn()
        except NotAuthorized:
            msg = f"{name}: API 미신청 (open.law.go.kr에서 '{name}' 목록/본문 JSON 신청 필요)"
            print("  ! " + msg)
            self.skipped.append(msg)
        except RuntimeError as e:  # 네트워크 오류: 이 종류만 건너뛰고 계속 (다시 실행하면 이어서 받음)
            msg = f"{name}: 중간에 실패 - {e}. 다시 실행하면 받은 것은 건너뛰고 이어서 받습니다."
            print("  ! " + msg)
            self.skipped.append(msg)

    # --- 종류별 ---
    def laws(self):
        seen = set()
        for query, pattern in LAWS:
            for it in search(self.oc, "law", query, max_items=100):
                title = it.get("법령명한글", "")
                if it.get("현행연혁코드") != "현행" or not re.match(pattern, title) or title in seen:
                    continue
                seen.add(title)
                key, body = self.fetch("law", it)
                date = it.get("시행일자", "")
                url = "https://www.law.go.kr/법령/" + urllib.parse.quote(title)
                meta = {"type": "법령", "title": title, "effective_date": date, "source_url": url,
                        "law_kind": it.get("법령구분명", "")}
                self.add("law", "법령", key, title, date, url, law_chunks(body, meta))

    def admruls(self):
        seen = set()
        for q in ADMRUL_QUERIES:
            for it in search(self.oc, "admrul", q, max_items=100):
                title = it.get("행정규칙명", "").strip()
                if (it.get("현행연혁구분") != "현행" or it.get("소관부처명") != "교육부"
                        or not re.search(ADMRUL_NAME_RE, title) or title in seen):
                    continue
                seen.add(title)
                key, body = self.fetch("admrul", it)
                date = it.get("시행일자", "")
                url = "https://www.law.go.kr/행정규칙/" + urllib.parse.quote(title)
                meta = {"type": "행정규칙", "title": title, "effective_date": date, "source_url": url,
                        "law_kind": it.get("행정규칙종류", "")}
                self.add("admrul", "행정규칙", key, title, date, url, admrul_chunks(body, meta))

    def ordins(self):
        seen = set()
        for q in ORDIN_QUERIES:
            for it in search(self.oc, "ordin", q, max_items=200):
                title = it.get("자치법규명", "").strip()
                org = it.get("지자체기관명", "")
                if not org.endswith("교육청") or not re.search(ORDIN_NAME_RE, title) or title in seen:
                    continue
                seen.add(title)
                key, body = self.fetch("ordin", it)
                date = it.get("시행일자", "")
                url = "https://www.law.go.kr/자치법규/" + urllib.parse.quote(title)
                meta = {"type": "자치법규", "title": title, "effective_date": date, "source_url": url,
                        "org": org}
                self.add("ordin", "자치법규", key, title, date, url, ordin_chunks(body, meta))

    def precs(self):
        seen = set()
        for q in PREC_QUERIES:
            for it in search(self.oc, "prec", q, max_items=PREC_MAX_PER_QUERY, search=2):
                key = detail_params(it).get("ID")
                if key in seen:
                    continue
                seen.add(key)
                key, body = self.fetch("prec", it)
                full = json.dumps(body, ensure_ascii=False)
                if not any(w in full for w in PREC_MUST_CONTAIN):
                    continue
                title = f"{it.get('법원명', '')} {it.get('사건번호', '')} {it.get('사건명', '')}".strip()
                meta = {"type": "판례", "title": title, "effective_date": body.get("선고일자", ""),
                        "case_no": it.get("사건번호", ""), "source_url": ""}
                self.add("prec", "판례", key, title, meta["effective_date"], "", doc_chunks(body, meta, "prec"))

    def expcs(self):
        seen = set()
        for q in EXPC_QUERIES:
            for it in search(self.oc, "expc", q, max_items=300):
                title = it.get("안건명", "")
                key = detail_params(it).get("ID")
                if key in seen or title in seen or not re.search(EXPC_NAME_RE, title):
                    continue
                seen.update([key, title])
                key, body = self.fetch("expc", it)
                meta = {"type": "법령해석례", "title": title, "effective_date": body.get("해석일자", ""),
                        "case_no": it.get("안건번호", ""), "source_url": ""}
                self.add("expc", "법령해석례", key, title, meta["effective_date"], "",
                         doc_chunks(body, meta, "expc"))

    def nhrcks(self):
        seen = set()
        for q in NHRCK_QUERIES:
            for it in search(self.oc, "nhrck", q, max_items=NHRCK_MAX_PER_QUERY):
                key = detail_params(it).get("ID")
                title = it.get("사건명", "")
                if key in seen or not any(w in title for w in NHRCK_MUST_CONTAIN):
                    continue
                seen.add(key)
                key, body = self.fetch("nhrck", it)
                url = body.get("바로보기URL", "")
                meta = {"type": "인권위결정례", "title": title, "effective_date": it.get("의결일자", ""),
                        "case_no": it.get("사건번호", ""), "source_url": url}
                self.add("nhrck", "인권위결정례", key, title, meta["effective_date"], url,
                         doc_chunks(body, meta, "nhrck"))

    def moe(self):
        seen = set()
        for q in MOE_QUERIES:
            for it in search(self.oc, "moeCgmExpc", q, max_items=100):
                key = detail_params(it).get("ID")
                if key in seen:
                    continue
                seen.add(key)
                key, body = self.fetch("moeCgmExpc", it)
                title = field(it, "안건명", "제목") or key
                meta = {"type": "교육부법령해석", "title": title,
                        "effective_date": field(it, "회신일자", "해석일자", "일자"), "source_url": ""}
                self.add("moeCgmExpc", "교육부법령해석", key, title, meta["effective_date"], "",
                         doc_chunks(body, meta, "moeCgmExpc"))

    def save(self):
        with open(self.out / "chunks.jsonl", "w", encoding="utf-8") as f:
            for c in self.chunks:
                f.write(json.dumps(c, ensure_ascii=False) + "\n")
        with open(self.out / "documents.csv", "w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=["type", "target", "id", "title", "date", "url", "chunks"])
            w.writeheader()
            w.writerows(self.docs)


def main():
    ap = argparse.ArgumentParser(description="교권 민원 AI용 법령 데이터 수집")
    ap.add_argument("--oc", default=os.environ.get("LAW_OC"), help="국가법령정보 OPEN API 인증키(OC)")
    ap.add_argument("--out", default="data", help="저장 폴더 (기본: data)")
    ap.add_argument("--only", nargs="*", help="일부만 수집: law admrul ordin prec expc nhrck moe")
    args = ap.parse_args()
    if not args.oc:
        sys.exit("OC 값이 필요합니다: py collect_laws.py --oc 내OC값")

    c = Collector(args.oc, args.out)
    sections = {
        "law": ("법령", c.laws), "admrul": ("행정규칙", c.admruls), "ordin": ("자치법규", c.ordins),
        "prec": ("판례", c.precs), "expc": ("법령해석례", c.expcs), "nhrck": ("국가인권위원회", c.nhrcks),
        "moe": ("교육부 법령해석", c.moe),
    }
    for k, (name, fn) in sections.items():
        if not args.only or k in args.only:
            c.run_section(name, fn)
    c.save()

    print("\n== 요약")
    counts = {}
    for d in c.docs:
        counts[d["type"]] = counts.get(d["type"], 0) + 1
    for t, n in counts.items():
        print(f"  {t}: 문서 {n}건")
    print(f"  청크 합계: {len(c.chunks)}개 -> {c.out / 'chunks.jsonl'}")
    for s in c.skipped:
        print("  ! " + s)


if __name__ == "__main__":
    main()
