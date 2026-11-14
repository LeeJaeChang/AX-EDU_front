package com.axedu.service.consult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import com.axedu.entity.enums.AiRecommendation;
import com.axedu.entity.enums.ComplaintTag;
import com.axedu.entity.value.SituationItem;

/**
 * 주제별 규칙 (프론트 목 mockAi.ts RULES). 공감 문구·학교 규정·카드 기본 항목.
 * schoolRegulations 는 regulation_chunks.id (로컬 시드의 학칙·가정통신문). 법령은 법령 조회(AI_DATA)에서 붙인다.
 */
public final class ConsultRules {

    private ConsultRules() {
    }

    public record Rule(Pattern match, ComplaintTag tag, List<String> schoolRegulations, AiRecommendation recommendation,
                       String empathy, String reason, String alternative, List<SituationItem> situation,
                       List<String> concerns, List<String> requests) {

        Rule withTag(ComplaintTag t) {
            return new Rule(match, t, schoolRegulations, recommendation, empathy, reason, alternative, situation, concerns, requests);
        }
    }

    private static SituationItem sit(String when, String where, String what) {
        return new SituationItem(1, when, where, what);
    }

    static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("휴대폰|핸드폰|휴대전화|스마트폰"), ComplaintTag.LIFE_GUIDANCE, List.of("reg_12"), AiRecommendation.EXPLAINED,
                    "아이와 하루 종일 연락이 닿지 않아 많이 걱정되고 답답하셨겠어요.",
                    "학교 학생생활규정을 찾아보니, 수업 시간 중 휴대전화는 담임선생님이 보관했다가 종례 때 돌려주도록 되어 있어요.",
                    "급한 연락은 교무실 대표번호로 해 주시면 학생에게 바로 전달해 드려요.",
                    List.of(sit("수업 시간 중", "교실", "학생의 휴대전화를 보관함")),
                    List.of("급한 상황에 학생과 연락할 방법이 없음"), List.of("휴대전화 보관 중 보호자 긴급 연락 방법 안내")),
            new Rule(Pattern.compile("수행평가|점수|성적|채점|시험|평가"), ComplaintTag.STUDY_EVALUATION, List.of("reg_15"), AiRecommendation.TALK_TO_TEACHER,
                    "아이가 열심히 준비한 만큼, 결과를 보시고 속상하고 납득하기 어려우셨을 것 같아요.",
                    "학업성적관리규정에 따라 수행평가 채점 기준은 학기 초에 공개되고, 결과를 확인한 뒤 7일 안에 이의를 신청할 수 있어요. 구체적인 채점 내용은 담당 선생님이 확인해 주셔야 해요.",
                    "채점 기준표는 학교 홈페이지 공지에서 볼 수 있어요.",
                    List.of(sit("최근", "—", "수행평가 결과를 받음")),
                    List.of("평가 결과가 학생이 준비한 내용과 다르게 반영되었을 수 있음"), List.of("채점 기준과 감점 사유 설명")),
            new Rule(Pattern.compile("야자|야간 ?자율|자율학습"), ComplaintTag.LIFE_GUIDANCE, List.of("reg_n87"), AiRecommendation.EXPLAINED,
                    "갑자기 학교에서 연락을 받으셔서 많이 놀라셨겠어요.",
                    "야간 자율학습 운영 안내에 따라, 미리 알리지 않고 빠지면 보호자께 문자로 안내드리고 있어요.",
                    "빠져야 하는 날은 전날까지 담임선생님께 채팅으로 알려 주시면 돼요.",
                    List.of(sit("야간 자율학습 시간", "학교", "학생의 불참이 확인됨")),
                    List.of("불참 사유가 제대로 전달되지 않음"), List.of("불참 연락 방법 안내")),
            new Rule(Pattern.compile("결석|지각|조퇴|출결|병원"), ComplaintTag.LIFE_GUIDANCE, List.of("reg_9"), AiRecommendation.EXPLAINED,
                    "아이 건강을 챙기시느라 신경 쓰실 일이 많으셨을 텐데, 서류 일까지 겹쳐 번거로우셨겠어요.",
                    "학업성적관리규정에 따라 결석 후 5일 안에 진료확인서 같은 증빙 서류를 담임선생님께 내면 처리돼요.",
                    "서류는 이 채팅에 사진으로 올려 주셔도 돼요.",
                    List.of(sit("최근", "—", "학생이 결석함")),
                    List.of("결석 처리 방법을 알지 못함"), List.of("출결 처리 절차 안내")),
            new Rule(Pattern.compile("교복|복장|체육복"), ComplaintTag.LIFE_GUIDANCE, List.of("reg_8"), AiRecommendation.EXPLAINED,
                    "아이가 지적을 받고 속상해하는 모습을 보셔서 마음이 편치 않으셨겠어요.",
                    "학생생활규정에 따라 교복을 단정히 입고, 체육 시간에는 체육복을 입도록 하고 있어요.",
                    "계절별 착용 기준은 가정통신문으로 다시 안내드릴 수 있어요.",
                    List.of(sit("등교 시간", "교문", "복장 지도를 받음")),
                    List.of("지도 기준이 명확하지 않다고 느낌"), List.of("복장 기준 안내")),
            new Rule(Pattern.compile("다쳤|다치|부상|사고|안전|넘어"), ComplaintTag.SAFETY, List.of(), AiRecommendation.TALK_TO_TEACHER,
                    "아이가 다쳤다는 이야기를 들으셨을 때 얼마나 놀라고 마음 아프셨을지요.",
                    "교육활동 중 일어난 사고는 학교안전공제회에 보상을 청구할 수 있어요. 어떻게 다쳤는지는 선생님이 직접 확인해야 해요.",
                    "병원 진료 기록은 꼭 보관해 주세요.",
                    List.of(sit("교육활동 중", "—", "학생이 다침")),
                    List.of("학생의 부상 정도와 이후 회복"), List.of("사고 경위 확인", "보상 청구 절차 안내")),
            new Rule(Pattern.compile("친구|따돌|괴롭|싸움|왕따|놀림|놀리"), ComplaintTag.PEER_RELATION, List.of(), AiRecommendation.TALK_TO_TEACHER,
                    "아이가 힘들어하는 모습을 곁에서 지켜보시는 게 얼마나 마음 아프셨을지 짐작이 돼요.",
                    "친구 관계 문제는 학교 규정만으로 설명드리기 어려워요. 담임선생님이 상황을 직접 확인하시는 게 필요해요.",
                    "아이가 말한 내용을 날짜별로 적어 두시면 선생님이 확인하는 데 도움이 돼요.",
                    List.of(sit("최근", "학교", "친구 관계에서 어려움을 겪음")),
                    List.of("학생이 학교생활을 힘들어함"), List.of("상황 확인과 지도")));

    static final Rule FALLBACK = new Rule(Pattern.compile("(?!)"), ComplaintTag.ETC, List.of(), AiRecommendation.TALK_TO_TEACHER,
            "이야기를 꺼내 주셔서 고마워요. 그동안 마음이 많이 쓰이셨겠어요.",
            "말씀하신 내용과 바로 맞는 학교 규정을 찾지 못했어요. 선생님이 직접 확인하고 답해 주시는 게 정확해요.",
            "요약 카드를 만들어 선생님께 정리해서 전달해 드릴 수 있어요.",
            List.of(sit("최근", "—", "학부모님이 확인을 요청하신 일이 있음")),
            List.of(), List.of("상황 확인"));

    /**
     * 여러 턴에 걸쳐 말한 내용에서 걸린 규칙들 (없으면 FALLBACK 하나).
     * 텍스트로 못 찾으면 사전 분류(category)를 따른다: 규칙이 하나뿐인 분류는 그 규칙, 아니면 기타 규칙에 분류 태그만.
     */
    public static List<Rule> rulesOf(List<String> texts, ComplaintTag category) {
        List<Rule> found = new ArrayList<>();
        for (String t : texts) {
            for (Rule r : RULES) {
                if (r.match().matcher(t).find()) {
                    if (!found.contains(r)) {
                        found.add(r);
                    }
                    break;
                }
            }
        }
        if (!found.isEmpty()) {
            return found;
        }
        if (category == null || category == ComplaintTag.ETC) {
            return List.of(FALLBACK);
        }
        List<Rule> same = RULES.stream().filter(r -> r.tag() == category).toList();
        return same.size() == 1 ? same : List.of(FALLBACK.withTag(category));
    }

    public static List<String> tagsOf(List<Rule> rules) {
        return new ArrayList<>(new LinkedHashSet<>(rules.stream().map(r -> r.tag().name()).toList()));
    }

    public static List<String> schoolRegulationsOf(List<Rule> rules) {
        return new ArrayList<>(new LinkedHashSet<>(rules.stream().flatMap(r -> r.schoolRegulations().stream()).toList()));
    }
}
