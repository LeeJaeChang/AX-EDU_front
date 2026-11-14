package com.axedu.config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.axedu.common.security.TextCipher;
import com.axedu.entity.ChatRoom;
import com.axedu.entity.ChatRoomMember;
import com.axedu.entity.Complaint;
import com.axedu.entity.ComplaintAiExplanation;
import com.axedu.entity.ComplaintExplanationCitation;
import com.axedu.entity.ComplaintStatusHistory;
import com.axedu.entity.Message;
import com.axedu.entity.ParentStudent;
import com.axedu.entity.Regulation;
import com.axedu.entity.RegulationChunk;
import com.axedu.entity.ResponseTeamMember;
import com.axedu.entity.SummaryCard;
import com.axedu.entity.SummaryCardRegulation;
import com.axedu.entity.TeacherClass;
import com.axedu.entity.User;
import com.axedu.entity.enums.AiRecommendation;
import com.axedu.entity.enums.AiStatus;
import com.axedu.entity.enums.AssignmentType;
import com.axedu.entity.enums.CardSource;
import com.axedu.entity.enums.ComplaintStatus;
import com.axedu.entity.enums.MessageType;
import com.axedu.entity.enums.RegulationStatus;
import com.axedu.entity.enums.RoomType;
import com.axedu.entity.enums.SourceType;
import com.axedu.entity.enums.TeamPosition;
import com.axedu.entity.enums.UrgencyLevel;
import com.axedu.entity.enums.UserRole;
import com.axedu.entity.value.SituationItem;
import com.axedu.repository.ChatRoomMemberRepository;
import com.axedu.repository.ChatRoomRepository;
import com.axedu.repository.ComplaintAiExplanationRepository;
import com.axedu.repository.ComplaintExplanationCitationRepository;
import com.axedu.repository.ComplaintRepository;
import com.axedu.repository.ComplaintStatusHistoryRepository;
import com.axedu.repository.MessageRepository;
import com.axedu.repository.ParentStudentRepository;
import com.axedu.repository.RegulationChunkRepository;
import com.axedu.repository.RegulationRepository;
import com.axedu.repository.ResponseTeamMemberRepository;
import com.axedu.repository.SummaryCardRegulationRepository;
import com.axedu.repository.SummaryCardRepository;
import com.axedu.repository.TeacherClassRepository;
import com.axedu.repository.UserRepository;
import com.axedu.service.consult.ConsultText;
import com.axedu.service.ContactService;

import lombok.RequiredArgsConstructor;

/**
 * 데모 데이터 — 프론트 목 서버(src/api/mock/seed.ts)와 같은 사용자·방·메시지·민원. DB 가 비어 있을 때 한 번만 넣는다.
 * local 프로필은 항상, 배포(prod)는 AXEDU_DEMO_ENABLED=true 일 때 켜진다 (axedu.demo.enabled).
 * 로컬은 /dev/token, 배포 시연은 데모 계정 고정 토큰(DemoAccounts)으로 쓴다.
 */
@ConditionalOnProperty(prefix = "axedu.demo", name = "enabled", havingValue = "true")
@Order(10)
@Component
@RequiredArgsConstructor
public class LocalSeedData implements ApplicationRunner {

    public static final String SCHOOL = "sch_demo";

    private final UserRepository userRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final TeacherClassRepository teacherClassRepository;
    private final ResponseTeamMemberRepository teamRepository;
    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintAiExplanationRepository explanationRepository;
    private final ComplaintExplanationCitationRepository citationRepository;
    private final ComplaintStatusHistoryRepository historyRepository;
    private final SummaryCardRepository cardRepository;
    private final SummaryCardRegulationRepository cardRegulationRepository;
    private final RegulationRepository regulationRepository;
    private final RegulationChunkRepository chunkRepository;
    private final TextCipher cipher;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        // ---- 사용자 ----
        user("usr_t1", "김수학", UserRole.TEACHER, null);
        user("usr_t2", "이영어", UserRole.TEACHER, null);
        user("usr_s1", "김리현", UserRole.STUDENT, "30901");
        user("usr_s2", "홍세아", UserRole.STUDENT, "10110");
        user("usr_s3", "이우진", UserRole.STUDENT, "10104");
        user("usr_s4", "최은서", UserRole.STUDENT, "20202");
        user("usr_s5", "김민수", UserRole.STUDENT, "30902");
        user("usr_s6", "박지후", UserRole.STUDENT, "30903");
        user("usr_p1", "김리현 학부모", UserRole.PARENT, null);
        user("usr_p2", "김민수 학부모", UserRole.PARENT, null);
        user("usr_notice", "슈퍼스쿨", UserRole.NOTICE, null);
        user("usr_rt1", "박대응", UserRole.RESPONSE_TEAM, null);

        parentStudentRepository.save(new ParentStudent("usr_p1", "usr_s1"));
        parentStudentRepository.save(new ParentStudent("usr_p2", "usr_s5"));

        short year = ContactService.schoolYear();
        teacherClass("usr_t1", year, 3, 9, "", AssignmentType.HOMEROOM);
        teacherClass("usr_t1", year, 2, 2, "수학", AssignmentType.SUBJECT);
        teacherClass("usr_t1", year, 1, 1, "수학", AssignmentType.SUBJECT);
        teacherClass("usr_t2", year, 3, 9, "영어", AssignmentType.SUBJECT);

        ResponseTeamMember rt = new ResponseTeamMember();
        rt.setSchoolId(SCHOOL);
        rt.setUserId("usr_rt1");
        rt.setPosition(TeamPosition.LEADER);
        teamRepository.save(rt);

        // ---- 규정 (AI 설명·카드 인용 예시용) ----
        regulation("rdoc_rule", "학생생활규정", "reg_12", "학생생활규정 제12조(휴대전화)",
                "수업 시간 중 휴대전화는 담임교사가 일괄 보관하며, 종례 시 돌려준다. 긴급한 연락은 교무실 대표번호로 한다.");
        regulation("rdoc_eval", "학업성적관리규정", "reg_15", "학업성적관리규정 제15조(수행평가)",
                "수행평가의 채점 기준은 평가 전에 학생에게 안내하며, 학생·학부모가 요청하면 채점 결과와 기준을 설명한다.");
        regulation("rdoc_rule", "학생생활규정", "reg_8", "학생생활규정 제8조(용의·복장)",
                "학생은 교복을 단정히 착용하며, 체육 활동 시에는 체육복을 입는다. 계절·활동별 착용 기준은 학교장이 따로 정한다.");
        regulation("rdoc_eval", "학업성적관리규정", "reg_9", "학업성적관리규정 제9조(출결)",
                "질병·경조사 등으로 결석한 경우 결석 후 5일 이내에 증빙 서류를 담임교사에게 제출한다.");
        regulation("rdoc_n87", "가정통신문 제2026-87호", SourceType.NOTICE_LETTER, "reg_n87", "가정통신문 제2026-87호(야간 자율학습 운영 안내)",
                "야간 자율학습에 빠질 때는 보호자가 미리 담임교사에게 알려 주세요. 사전 연락이 없으면 보호자께 문자로 안내합니다.");

        // ---- 교사–학생 ----
        room("room_rh", RoomType.DIRECT, null, null, "2026-09-08T15:12:40+09:00", List.of("usr_t1", "usr_s1"));
        text("msg_rh_1", "room_rh", "usr_t1", "리현아 오늘 야자실 안왔던데 학원상담 갔니?", "2026-09-08T15:13:00+09:00");
        text("msg_rh_2", "room_rh", "usr_s1", "아..넵! 학원 상담이라고 야자감독선생님께 말씀 드리긴했는데 전달을 못받으셨나보네요ㅠㅠ", "2026-09-08T15:20:00+09:00");
        text("msg_rh_3", "room_rh", "usr_t1", "그랬구나~~다음부터는 나한테 먼저 말해주렴", "2026-09-08T15:23:00+09:00");
        text("msg_rh_4", "room_rh", "usr_s1", "넵 !", "2026-09-09T13:41:00+09:00");
        readUpTo("room_rh", "msg_rh_4", "usr_t1", "usr_s1");

        room("room_ms", RoomType.DIRECT, null, null, "2026-09-08T10:00:00+09:00", List.of("usr_t1", "usr_s5"));
        room("room_es", RoomType.DIRECT, null, null, "2026-09-16T11:30:00+09:00", List.of("usr_t1", "usr_s4"));
        room("room_wj", RoomType.DIRECT, null, null, "2026-11-13T10:05:00+09:00", List.of("usr_t1", "usr_s3"));
        room("room_sa", RoomType.DIRECT, null, null, "2026-11-13T10:02:00+09:00", List.of("usr_t1", "usr_s2"));
        ChatRoom grp = room("room_grp", RoomType.GROUP, "3학년 10반_생활과 윤리_김수학", "usr_t1", "2026-09-08T09:30:00+09:00", List.of("usr_t1"));
        grp.setAvailableFromOverride(LocalTime.of(12, 0));
        grp.setAvailableToOverride(LocalTime.of(18, 0));
        roomRepository.save(grp);

        // ---- 공지형 ----
        room("room_notice", RoomType.NOTICE, "슈퍼스쿨", "usr_notice", "2026-03-02T09:00:00+09:00",
                List.of("usr_notice", "usr_t1", "usr_p1", "usr_p2"));
        text("msg_notice_1", "room_notice", "usr_notice",
                "[생기부·상담 시즌, 단 하루 만에 완벽 대비! 가능합니다.] 어느덧 상담과 생기부의 시즌이 돌아왔습니다. 슈퍼스쿨의 상담 기록과 생기부 도우미로 준비 시간을 줄여 보세요.",
                "2026-11-04T09:00:00+09:00");
        unread("room_notice", 1, "usr_t1", "usr_p1", "usr_p2");

        // ---- 학부모–교사 ----
        room("room_p1", RoomType.DIRECT, null, null, "2026-09-02T17:00:00+09:00", List.of("usr_t1", "usr_p1"));
        text("msg_p1_1", "room_p1", "usr_t1", "안녕하세요, 리현이 담임 김수학입니다. 1년 동안 잘 부탁드립니다.", "2026-09-02T17:01:00+09:00");
        text("msg_p1_2", "room_p1", "usr_p1", "네 선생님, 잘 부탁드립니다!", "2026-09-02T17:30:00+09:00");
        readUpTo("room_p1", "msg_p1_2", "usr_t1", "usr_p1");

        room("room_p1_en", RoomType.DIRECT, null, null, "2026-09-03T10:00:00+09:00", List.of("usr_t2", "usr_p1"));
        text("msg_p1en_1", "room_p1_en", "usr_t2", "안녕하세요, 3학년 영어 담당 이영어입니다. 수행평가 일정은 가정통신문으로 안내드렸습니다.", "2026-09-03T10:05:00+09:00");
        readUpTo("room_p1_en", "msg_p1en_1", "usr_t2", "usr_p1");

        room("room_p2", RoomType.DIRECT, null, null, "2026-09-02T17:00:00+09:00", List.of("usr_t1", "usr_p2"));
        text("msg_p2_1", "room_p2", "usr_t1", "안녕하세요, 민수 담임 김수학입니다. 1년 동안 잘 부탁드립니다.", "2026-09-02T17:02:00+09:00");
        readUpTo("room_p2", "msg_p2_1", "usr_t1", "usr_p2");

        // ---- 이미 제출된 민원 1건 (교사 화면 데모용) ----
        seedComplaint();
    }

    private void seedComplaint() {
        String original = "아이 수행평가 점수가 왜 이렇게 낮은지 이해가 안 됩니다!! 발표 준비를 3주나 했는데 감점 이유를 아무도 설명해 주지 않았어요. "
                + "아이가 많이 속상해서 울었어요. 채점 기준표를 보여 주시고 감점 사유를 알려 주세요. 연락은 전화로 오후 4시~5시에 주세요.";
        OffsetDateTime createdAt = at("2026-11-13T20:10:00+09:00");
        OffsetDateTime submittedAt = at("2026-11-13T20:14:00+09:00");

        Complaint c = new Complaint();
        c.setId("cmp_seed1");
        c.setSchoolId(SCHOOL);
        c.setRoomId("room_p2");
        c.setParentId("usr_p2");
        c.setTeacherId("usr_t1");
        c.setStudentId("usr_s5");
        c.setStatus(ComplaintStatus.SUBMITTED);
        c.setOriginalTextEnc(cipher.encrypt(original));
        c.setSubmittedCardVersion(1);
        c.setCreatedAt(createdAt);
        c.setSubmittedAt(submittedAt);
        complaintRepository.save(c);

        ComplaintAiExplanation e = new ComplaintAiExplanation();
        e.setComplaintId(c.getId());
        e.setStatus(AiStatus.SUCCEEDED);
        e.setEmpathy("3주나 준비한 발표였는데 이유를 모른 채 점수만 받아서 많이 속상하셨겠어요.");
        e.setReason("학업성적관리규정에 따라 수행평가 채점 기준은 미리 안내되고, 요청하시면 채점 결과와 기준을 설명받으실 수 있어요.");
        e.setAlternative("담임 선생님께 채점 기준표와 감점 사유 안내를 요청해 보세요.");
        e.setRecommendation(AiRecommendation.TALK_TO_TEACHER);
        e.setModelName("seed");
        e.setMaskedInputHash("0".repeat(64));
        e.setCreatedAt(createdAt);
        e = explanationRepository.save(e);
        citationRepository.save(new ComplaintExplanationCitation(e.getId(), "reg_15", (short) 0));

        SummaryCard card = new SummaryCard();
        card.setComplaintId(c.getId());
        card.setVersion(1);
        card.setSource(CardSource.AI_GENERATED);
        card.setGrade((short) 3);
        card.setClassNo((short) 9);
        card.setReceivedAt(createdAt);
        card.setTags(new ArrayList<>(List.of("STUDY_EVALUATION")));
        card.setSituation(new ArrayList<>(List.of(new SituationItem(1, "11월 둘째 주", "교실", "수행평가(발표) 결과 점수가 예상보다 낮게 나옴"))));
        card.setConcerns(new ArrayList<>(List.of("학생이 3주간 준비한 발표의 감점 이유를 몰라 속상해함")));
        card.setRequests(new ArrayList<>(List.of("채점 기준표 공유", "감점 사유 안내")));
        card.setPreferredMethods(new ArrayList<>(List.of("CALL")));
        card.setPreferredFrom(LocalTime.of(16, 0));
        card.setPreferredTo(LocalTime.of(17, 0));
        // 프론트 시드와 같이: 며칠 안에 답변 + 원문으로 판단한 학부모 감정 상태 (교사 데모용)
        card.setUrgencyLevel(UrgencyLevel.SOON);
        card.setParentEmotion(ConsultText.assessEmotion(List.of(original)));
        card.setCreatedBy("usr_p2");
        card.setConfirmedAt(submittedAt);
        card.setCreatedAt(createdAt.plusMinutes(2));
        card = cardRepository.save(card);
        cardRegulationRepository.save(new SummaryCardRegulation(card.getId(), "reg_15", (short) 0));

        history(c.getId(), null, ComplaintStatus.EXPLAINED, createdAt);
        history(c.getId(), ComplaintStatus.EXPLAINED, ComplaintStatus.CARD_DRAFTING, createdAt.plusMinutes(2));
        history(c.getId(), ComplaintStatus.CARD_DRAFTING, ComplaintStatus.SUBMITTED, submittedAt);

        Message m = message("msg_p2_card", "room_p2", "usr_p2", MessageType.COMPLAINT_CARD, "민원 요약 카드를 보냈습니다.",
                "2026-11-13T20:14:00+09:00");
        m.setComplaintId(c.getId());
        m.setSummaryCardId(card.getId());
        messageRepository.save(m);
        unread("room_p2", 1, "usr_t1");
    }

    // ---------------- helpers ----------------

    private void user(String id, String name, UserRole role, String studentNumber) {
        User u = new User();
        u.setId(id);
        u.setSchoolId(SCHOOL);
        u.setSuperschoolUserId("ss_" + id);
        u.setRole(role);
        u.setName(name);
        if (studentNumber != null) {
            u.setStudentNumber(studentNumber);
            u.setGrade(Short.parseShort(studentNumber.substring(0, 1)));
            u.setClassNo(Short.parseShort(studentNumber.substring(1, 3)));
        }
        userRepository.save(u);
    }

    private void teacherClass(String teacherId, short year, int grade, int classNo, String subject, AssignmentType type) {
        TeacherClass tc = new TeacherClass();
        tc.setTeacherId(teacherId);
        tc.setSchoolYear(year);
        tc.setGrade((short) grade);
        tc.setClassNo((short) classNo);
        tc.setSubject(subject);
        tc.setAssignmentType(type);
        teacherClassRepository.save(tc);
    }

    private void regulation(String docId, String title, String chunkId, String label, String content) {
        regulation(docId, title, SourceType.SCHOOL_RULE, chunkId, label, content);
    }

    private void regulation(String docId, String title, SourceType type, String chunkId, String label, String content) {
        Regulation r = new Regulation();
        r.setId(docId);
        r.setSchoolId(SCHOOL);
        r.setSourceType(type);
        r.setTitle(title);
        r.setEffectiveFrom(LocalDate.of(2026, 3, 1));
        r.setStatus(RegulationStatus.ACTIVE);
        regulationRepository.save(r);
        RegulationChunk chunk = new RegulationChunk();
        chunk.setId(chunkId);
        chunk.setRegulationId(docId);
        chunk.setArticleLabel(label);
        chunk.setContent(content);
        chunkRepository.save(chunk);
    }

    private ChatRoom room(String id, RoomType type, String title, String ownerId, String createdAt, List<String> members) {
        ChatRoom r = new ChatRoom();
        r.setId(id);
        r.setSchoolId(SCHOOL);
        r.setType(type);
        r.setTitle(type == RoomType.DIRECT ? null : title);
        r.setOwnerId(ownerId);
        if (type == RoomType.DIRECT) {
            r.setDirectKey(ChatRoom.directKeyOf(members.get(0), members.get(1)));
        }
        r.setCreatedAt(at(createdAt));
        r = roomRepository.save(r);
        for (String userId : members) {
            ChatRoomMember m = new ChatRoomMember(id, userId);
            m.setJoinedAt(at(createdAt));
            memberRepository.save(m);
        }
        message("msg_" + id + "_0", id, null, MessageType.SYSTEM, "채팅방이 생성되었습니다.", createdAt);
        return roomRepository.findById(id).orElseThrow();
    }

    private void text(String id, String roomId, String senderId, String content, String createdAt) {
        message(id, roomId, senderId, MessageType.TEXT, content, createdAt);
    }

    private Message message(String id, String roomId, String senderId, MessageType type, String content, String createdAt) {
        Message m = new Message();
        m.setId(id);
        m.setRoomId(roomId);
        m.setSchoolId(SCHOOL);
        m.setSenderId(senderId);
        m.setType(type);
        m.setContent(content);
        m.setCreatedAt(at(createdAt));
        m = messageRepository.save(m);
        ChatRoom room = roomRepository.findById(roomId).orElseThrow();
        room.setLastMessageId(id);
        room.setLastMessageAt(m.getCreatedAt());
        roomRepository.save(room);
        return m;
    }

    private void readUpTo(String roomId, String messageId, String... userIds) {
        for (String userId : userIds) {
            ChatRoomMember m = memberRepository.findByRoomIdAndUserId(roomId, userId).orElseThrow();
            m.setLastReadMessageId(messageId);
            memberRepository.save(m);
        }
    }

    private void unread(String roomId, int count, String... userIds) {
        for (String userId : userIds) {
            ChatRoomMember m = memberRepository.findByRoomIdAndUserId(roomId, userId).orElseThrow();
            m.setUnreadCount(count);
            memberRepository.save(m);
        }
    }

    private void history(String complaintId, ComplaintStatus from, ComplaintStatus to, OffsetDateTime at) {
        ComplaintStatusHistory h = new ComplaintStatusHistory();
        h.setComplaintId(complaintId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActorId("usr_p2");
        h.setActorRole(UserRole.PARENT);
        h.setCreatedAt(at);
        historyRepository.save(h);
    }

    private static OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }
}
