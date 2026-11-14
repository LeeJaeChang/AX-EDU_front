"""law_search 단위 테스트 (표준 라이브러리 unittest).  실행: python3 -m unittest discover -s ai -v"""
import unittest

from law_search import DEFAULT_CHUNKS, LawIndex, snippet, tokenize

ROWS = [
    {"type": "법령", "title": "초ㆍ중등교육법", "article": "제20조의2", "article_title": "학교의 장 및 교원의 학생생활지도",
     "text": "학교의 장과 교원은 학생의 인권을 보호하고 교원의 교육활동을 위하여 필요한 경우 법령과 학칙에 따라 학생을 지도할 수 있다.",
     "chunk_id": "law-1-0"},
    {"type": "행정규칙", "title": "교원의 학생생활지도에 관한 고시", "article": "제8조", "article_title": "그 밖의 분야",
     "text": "휴대전화 등 스마트기기의 올바른 사용에 관한 사항", "chunk_id": "admrul-2-0"},
    {"type": "인권위결정례", "title": "고등학교의 휴대전화 소지 제한", "section": "이유",
     "text": "학교는 휴대전화를 소지한 학생의 휴대전화를 압수하고 벌점을 부과하였다.", "chunk_id": "nhrck-3-0"},
    {"type": "인권위결정례", "title": "고등학교의 휴대전화 소지 제한", "section": "이유",
     "text": "휴대전화 압수는 통신의 자유를 과도하게 제한한다.", "chunk_id": "nhrck-3-1"},
    {"type": "인권위결정례", "title": "고등학교의 휴대전화 소지 제한", "section": "주문",
     "text": "휴대전화 압수 관행을 개선할 것을 권고한다.", "chunk_id": "nhrck-3-2"},
    {"type": "법령", "title": "교육기본법", "article": "제22조의4", "article_title": "학교체육",
     "text": "국가와 지방자치단체는 학교체육 활성화를 위하여 노력하여야 한다.", "chunk_id": "law-4-0"},
    {"type": "자치법규", "title": "서울특별시 학생인권 조례", "org": "서울특별시교육청", "article": "제13조",
     "article_title": "사생활의 자유", "text": "학생의 휴대전화 소지 자체를 금지하여서는 아니 된다.", "chunk_id": "ordin-5-0"},
    {"type": "자치법규", "title": "경기도 학생인권 조례", "org": "경기도교육청", "article": "제12조",
     "article_title": "사생활의 자유", "text": "휴대전화 사용 여부는 학생의 자율에 맡긴다.", "chunk_id": "ordin-6-0"},
]


class TokenizeTest(unittest.TestCase):
    def test_korean_bigrams_and_symbols(self):
        self.assertEqual(tokenize("휴대전화"), ["휴대", "대전", "전화"])
        self.assertEqual(tokenize("초ㆍ중등"), ["초", "중등"])
        self.assertEqual(tokenize("제20조 abc"), ["제2", "20", "0조", "abc"])


class SearchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.idx = LawIndex.from_rows(ROWS)

    def test_more_keywords_matched_ranks_higher(self):
        res = self.idx.search(["휴대전화", "압수"], limit=10)
        self.assertEqual(res[0]["matchedKeywords"], ["휴대전화", "압수"])
        self.assertEqual(res[0]["type"], "인권위결정례")

    def test_particles_after_keyword_still_match(self):
        res = self.idx.search(["휴대전화"], limit=10)
        self.assertIn("nhrck-3-0", [r["chunkId"] for r in res])  # '휴대전화를'

    def test_keyword_must_start_a_word(self):
        res = self.idx.search(["교체"], limit=10)
        self.assertNotIn("law-4-0", [r["chunkId"] for r in res])  # '학교체육' 안의 '교체'는 아님

    def test_multi_word_keyword(self):
        res = self.idx.search(["학생 생활지도"], limit=10)
        ids = [r["chunkId"] for r in res]
        self.assertIn("law-1-0", ids)    # 조문 제목의 붙여 쓴 '학생생활지도'
        self.assertTrue(all("학생" in r["title"] + (r["articleTitle"] or "") + r["text"] for r in res))

    def test_per_doc_limit(self):
        res = self.idx.search(["휴대전화", "압수"], limit=10, per_doc=2)
        self.assertEqual(sum(1 for r in res if r["chunkId"].startswith("nhrck-3-")), 2)

    def test_type_filter_and_per_type(self):
        res = self.idx.search(["휴대전화"], limit=10, types=["법령", "행정규칙"])
        self.assertTrue(res and all(r["type"] in ("법령", "행정규칙") for r in res))
        res = self.idx.search(["휴대전화"], limit=10, per_type=1)
        types = [r["type"] for r in res]
        self.assertEqual(len(types), len(set(types)))

    def test_org_filter_only_applies_to_local_rules(self):
        res = self.idx.search(["휴대전화"], limit=10, org="서울특별시교육청")
        orgs = {r["org"] for r in res if r["type"] == "자치법규"}
        self.assertEqual(orgs, {"서울특별시교육청"})
        self.assertTrue(any(r["type"] != "자치법규" for r in res))

    def test_empty_and_unknown(self):
        self.assertEqual(self.idx.search([]), [])
        self.assertEqual(self.idx.search(["   "]), [])
        self.assertEqual(self.idx.search(["존재하지않는단어"]), [])

    def test_result_shape(self):
        r = self.idx.search("휴대전화 압수", limit=1)[0]
        for key in ("chunkId", "type", "title", "article", "articleTitle", "text", "snippet",
                    "matchedKeywords", "score", "sourceUrl", "effectiveDate"):
            self.assertIn(key, r)


class SnippetTest(unittest.TestCase):
    def test_centers_on_keyword(self):
        text = "가" * 300 + " 휴대전화 압수 " + "나" * 300
        s = snippet(text, ["휴대전화"], size=60)
        self.assertIn("휴대전화", s)
        self.assertTrue(s.startswith("…") and s.endswith("…"))


@unittest.skipUnless(DEFAULT_CHUNKS.exists(), "AI_DATA/chunks.jsonl 없음")
class RealDataSmokeTest(unittest.TestCase):
    """실제 AI_DATA 로 대표 질의가 기대한 법령을 찾는지"""

    @classmethod
    def setUpClass(cls):
        cls.idx = LawIndex.load()

    def test_school_violence_measures(self):
        res = self.idx.search(["학교폭력", "가해학생", "조치"], limit=3)
        self.assertTrue(any(r["title"].startswith("학교폭력예방 및 대책에 관한 법률") for r in res))

    def test_child_abuse_report(self):
        res = self.idx.search(["아동학대", "신고"], limit=5, types=["법령"])
        self.assertTrue(any("신고" in (r["articleTitle"] or "") for r in res))

    def test_teacher_activity_infringement(self):
        res = self.idx.search(["교육활동 침해"], limit=5, types=["법령"])
        self.assertTrue(any("교원의 지위 향상" in r["title"] for r in res))


if __name__ == "__main__":
    unittest.main()
