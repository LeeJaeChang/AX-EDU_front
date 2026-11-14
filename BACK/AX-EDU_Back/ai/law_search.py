"""
키워드 기반 법령 조회 (AI_DATA/chunks.jsonl)

민원 처리 흐름에서 이 모듈이 맡는 단계:
    학부모 민원 → (AI) 법령 조회용 키워드 추출 → [여기] 키워드로 법령·판례 등 조회
    → (AI) 조회 결과 + 민원 내용으로 요약 카드 JSON 생성 → 프론트

검색 방식 (표준 라이브러리만 사용, 형태소 분석기 불필요):
    * BM25 순위. 토큰은 한글 2글자 단위(bigram)라 '휴대전화를', '휴대전화의' 같은 조사 붙은 표현도 잡힌다
    * 키워드는 '단어 시작'에서 일치해야 인정 ('교체'가 '학교체육' 안에서 걸리지 않게). 뒤에 조사가 붙는 건 허용
      여러 단어 키워드('교육활동 침해')는 각 단어가 모두 있거나 붙여 쓴 형태('교육활동침해')가 있으면 일치
    * 키워드가 본문에 있으면 가산점, 제목·조문 제목에 있으면 더 큰 가산점
    * 키워드를 여러 개 줬을 때 더 많이 맞춘 청크가 위로 (커버리지 가중)
    * 같은 문서(같은 법령·판례)에서는 최대 per_doc 개, 자료 종류별 최대 per_type 개, 종류별 가중치(TYPE_WEIGHT)

사용:
    python3 ai/law_search.py 휴대전화 압수 생활지도            # 터미널에서 바로 확인
    python3 ai/law_search.py --types 법령,행정규칙 --limit 5 체벌

    from law_search import LawIndex
    idx = LawIndex.load("AI_DATA/chunks.jsonl")
    idx.search(["휴대전화", "압수"], limit=10)
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

DEFAULT_CHUNKS = Path(__file__).resolve().parent.parent / "AI_DATA" / "chunks.jsonl"

# 자료 종류별 가중치. 법령·고시를 근거로 먼저 보여 주고, 지역마다 비슷한 조례가 많은 자치법규는 조금 낮춘다
TYPE_WEIGHT = {
    "법령": 1.0,
    "행정규칙": 1.0,
    "판례": 0.9,
    "법령해석례": 0.9,
    "인권위결정례": 0.85,
    "자치법규": 0.75,
}
ALL_TYPES = tuple(TYPE_WEIGHT)

# BM25 파라미터와 가산점
K1 = 1.2
B = 0.75
HEADER_WEIGHT = 2.0       # 제목·조문 제목 BM25 비중
PHRASE_BONUS_TEXT = 1.5   # 키워드가 본문에 그대로 있을 때 (키워드당)
PHRASE_BONUS_HEADER = 3.0 # 키워드가 제목·조문 제목에 그대로 있을 때 (키워드당)
PHRASE_BONUS_ADJACENT = 1.0  # 여러 단어 키워드가 붙어서(어순 그대로) 나올 때
COVERAGE_POWER = 1.5      # 키워드 일부만 맞춘 청크를 얼마나 깎을지 (클수록 많이 맞춘 쪽이 유리)
SNIPPET_LEN = 180

_NON_WORD = re.compile(r"[^0-9a-z가-힣]+")


def normalize(text: str) -> str:
    """NFC, 소문자, 기호 → 공백. 'ㆍ' '「」' 같은 문자는 모두 공백이 된다"""
    text = unicodedata.normalize("NFC", text or "").lower()
    return _NON_WORD.sub(" ", text).strip()


def compact(text: str) -> str:
    """공백까지 없앤 형태. '교육 활동'과 '교육활동'을 같게 본다"""
    return normalize(text).replace(" ", "")


def tokenize(text: str) -> list[str]:
    """단어마다 2글자 조각. 1글자 단어는 그대로 (숫자·영문 단어는 통째로)"""
    tokens: list[str] = []
    for word in normalize(text).split():
        if len(word) == 1 or not re.search(r"[가-힣]", word):
            tokens.append(word)
        else:
            tokens.extend(word[i:i + 2] for i in range(len(word) - 1))
    return tokens


@dataclass
class Chunk:
    chunk_id: str
    type: str
    title: str
    text: str
    header: str           # 제목 + 조문/섹션 제목 (가산점·헤더 BM25 용)
    doc_key: str          # 같은 문서 묶음 키 (chunk_id 의 마지막 '-번호' 를 뗀 것)
    meta: dict
    text_compact: str = ""
    header_compact: str = ""
    text_norm: str = ""
    header_norm: str = ""


@dataclass
class _Field:
    """BM25 한 필드(본문 또는 헤더)의 역색인"""
    postings: dict[str, dict[int, int]] = field(default_factory=lambda: defaultdict(dict))
    lengths: list[int] = field(default_factory=list)
    avg_len: float = 1.0

    def add(self, doc: int, tokens: list[str]) -> None:
        self.lengths.append(len(tokens))
        for tok, tf in Counter(tokens).items():
            self.postings[tok][doc] = tf

    def finish(self) -> None:
        self.avg_len = (sum(self.lengths) / len(self.lengths)) if self.lengths else 1.0

    def score(self, tokens: list[str], n_docs: int, scores: dict[int, float], weight: float) -> None:
        for tok in set(tokens):
            plist = self.postings.get(tok)
            if not plist:
                continue
            idf = math.log(1 + (n_docs - len(plist) + 0.5) / (len(plist) + 0.5))
            for doc, tf in plist.items():
                norm = K1 * (1 - B + B * self.lengths[doc] / self.avg_len)
                scores[doc] = scores.get(doc, 0.0) + weight * idf * tf * (K1 + 1) / (tf + norm)


class LawIndex:
    def __init__(self, chunks: list[Chunk]):
        self.chunks = chunks
        self.body = _Field()
        self.head = _Field()
        for i, c in enumerate(chunks):
            body_tokens = tokenize(c.text)
            head_tokens = tokenize(c.header)
            self.body.add(i, body_tokens)
            self.head.add(i, head_tokens)
        self.body.finish()
        self.head.finish()

    # ------------------------------------------------------------------ 로드
    @classmethod
    def load(cls, path: str | os.PathLike | None = None) -> "LawIndex":
        path = Path(path or os.environ.get("LAW_CHUNKS_PATH") or DEFAULT_CHUNKS)
        if not path.exists():
            raise FileNotFoundError(f"법령 청크 파일이 없습니다: {path} (AI_DATA/chunks.jsonl 또는 LAW_CHUNKS_PATH)")
        rows = []
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    rows.append(json.loads(line))
        return cls.from_rows(rows)

    @classmethod
    def from_rows(cls, rows: list[dict]) -> "LawIndex":
        chunks = []
        for r in rows:
            text = (r.get("text") or "").strip()
            if not text:
                continue
            sub = r.get("article_title") or r.get("section") or ""
            header = " ".join(x for x in (r.get("title", ""), r.get("article", ""), sub) if x)
            chunk_id = r.get("chunk_id") or f"chunk-{len(chunks)}"
            meta = {k: v for k, v in r.items() if k not in ("text",)}
            c = Chunk(chunk_id=chunk_id, type=r.get("type", ""), title=r.get("title", ""), text=text,
                      header=header, doc_key=chunk_id.rsplit("-", 1)[0], meta=meta)
            c.text_compact = compact(text)
            c.header_compact = compact(header)
            c.text_norm = " " + normalize(text)
            c.header_norm = " " + normalize(header)
            chunks.append(c)
        return cls(chunks)

    # ------------------------------------------------------------------ 검색
    def search(self, keywords: list[str] | str, limit: int = 10, types: list[str] | None = None,
               per_doc: int = 2, per_type: int | None = None, org: str | None = None) -> list[dict]:
        """
        keywords: 키워드 목록 (예: ["휴대전화", "압수", "생활지도"]). 문자열 하나면 공백으로 나눈다
        limit:    돌려줄 최대 개수
        types:    자료 종류 필터 (법령, 행정규칙, 판례, 법령해석례, 인권위결정례, 자치법규). None 이면 전체
        per_doc:  같은 문서(법령·판례 하나)에서 최대 몇 개 청크까지
        per_type: 자료 종류별 최대 개수 (예: 3 이면 법령 3·판례 3·…). None 이면 제한 없음
        org:      자치법규를 이 기관(예: '서울특별시교육청')만 남김. 다른 종류는 그대로
        """
        if isinstance(keywords, str):
            keywords = keywords.split()
        keywords = [k.strip() for k in keywords if k and k.strip()]
        # 중복 키워드 제거 (순서 유지)
        keywords = list(dict.fromkeys(keywords))
        if not keywords:
            return []
        allowed = set(types) if types else None
        n = len(self.chunks)

        # 1) 키워드 전체로 BM25 (본문 + 헤더)
        q_tokens = [t for k in keywords for t in tokenize(k)]
        scores: dict[int, float] = {}
        self.body.score(q_tokens, n, scores, 1.0)
        self.head.score(q_tokens, n, scores, HEADER_WEIGHT)

        # 2) 키워드별 일치 여부 → 가산점 + 커버리지
        patterns = [(kw, _keyword_patterns(kw)) for kw in keywords]
        results = []
        for doc, base in scores.items():
            c = self.chunks[doc]
            if allowed and c.type not in allowed:
                continue
            if org and c.type == "자치법규" and org not in (c.meta.get("org") or ""):
                continue
            matched = []
            bonus = 0.0
            for kw, (word_res, phrase) in patterns:
                # 여러 단어 키워드는 붙여 쓴 형태('학생생활지도')로 있어도 일치
                head_phrase = bool(phrase) and phrase in c.header_compact
                text_phrase = bool(phrase) and phrase in c.text_compact
                in_head = head_phrase or all(r.search(c.header_norm) for r in word_res)
                in_text = text_phrase or all(r.search(c.text_norm) for r in word_res)
                if not (in_head or in_text):
                    continue
                matched.append(kw)
                bonus += (PHRASE_BONUS_HEADER if in_head else 0.0) + (PHRASE_BONUS_TEXT if in_text else 0.0)
                if head_phrase or text_phrase:
                    bonus += PHRASE_BONUS_ADJACENT
            if not matched:
                continue
            coverage = len(matched) / len(keywords)
            score = (base + bonus) * (coverage ** COVERAGE_POWER) * TYPE_WEIGHT.get(c.type, 0.8)
            results.append((score, doc, matched))

        results.sort(key=lambda x: -x[0])

        # 3) 같은 문서는 per_doc 개, 같은 종류는 per_type 개까지만
        out = []
        per_doc_count: Counter = Counter()
        per_type_count: Counter = Counter()
        for score, doc, matched in results:
            c = self.chunks[doc]
            if per_doc and per_doc_count[c.doc_key] >= per_doc:
                continue
            if per_type and per_type_count[c.type] >= per_type:
                continue
            per_doc_count[c.doc_key] += 1
            per_type_count[c.type] += 1
            out.append(self._to_result(c, score, matched))
            if len(out) >= limit:
                break
        return out

    def _to_result(self, c: Chunk, score: float, matched: list[str]) -> dict:
        m = c.meta
        return {
            "chunkId": c.chunk_id,
            "type": c.type,
            "title": c.title,
            "article": m.get("article"),
            "articleTitle": m.get("article_title") or m.get("section"),
            "caseNo": m.get("case_no"),
            "org": m.get("org"),
            "lawKind": m.get("law_kind"),
            "effectiveDate": m.get("effective_date"),
            "sourceUrl": m.get("source_url") or None,
            "text": c.text,
            "snippet": snippet(c.text, matched),
            "matchedKeywords": matched,
            "score": round(score, 3),
        }

    def stats(self) -> dict:
        return {"chunks": len(self.chunks), "types": dict(Counter(c.type for c in self.chunks))}


def _keyword_patterns(keyword: str) -> tuple[list[re.Pattern], str | None]:
    """키워드의 각 단어가 '단어 시작'에서 나오는지 보는 정규식 목록 + (여러 단어면) 붙여 쓴 형태"""
    words = normalize(keyword).split()
    res = [re.compile(r"(?<![0-9a-z가-힣])" + re.escape(w)) for w in words]
    return res, ("".join(words) if len(words) > 1 else None)


def snippet(text: str, keywords: list[str], size: int = SNIPPET_LEN) -> str:
    """첫 번째로 보이는 키워드 주변을 잘라 화면 미리보기용으로"""
    flat = re.sub(r"\s+", " ", text).strip()
    pos = -1
    for kw in keywords:
        pos = flat.find(kw)
        if pos >= 0:
            break
    if pos < 0 or len(flat) <= size:
        return flat[:size] + ("…" if len(flat) > size else "")
    start = max(0, pos - size // 3)
    end = min(len(flat), start + size)
    return ("…" if start > 0 else "") + flat[start:end] + ("…" if end < len(flat) else "")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="키워드로 법령·판례 등 조회 (AI_DATA/chunks.jsonl)")
    p.add_argument("keywords", nargs="+", help="검색 키워드 (여러 개 가능)")
    p.add_argument("--limit", type=int, default=5)
    p.add_argument("--types", help=f"쉼표로 구분: {','.join(ALL_TYPES)}")
    p.add_argument("--per-type", type=int, help="자료 종류별 최대 개수")
    p.add_argument("--org", help="자치법규 기관 필터 (예: 서울특별시교육청)")
    p.add_argument("--data", help="chunks.jsonl 경로 (기본 AI_DATA/chunks.jsonl)")
    p.add_argument("--json", action="store_true", help="JSON 으로 출력")
    a = p.parse_args(argv)

    idx = LawIndex.load(a.data)
    res = idx.search(a.keywords, limit=a.limit, types=a.types.split(",") if a.types else None,
                     per_type=a.per_type, org=a.org)
    if a.json:
        print(json.dumps(res, ensure_ascii=False, indent=2))
        return 0
    for i, r in enumerate(res, 1):
        where = " ".join(x for x in (r["article"], r["articleTitle"] and f"({r['articleTitle']})") if x)
        print(f"{i}. [{r['type']}] {r['title']} {where}  score={r['score']}  matched={r['matchedKeywords']}")
        print(f"   {r['snippet']}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
