-- =====================================================================
-- 똑똑 AI 상담사 (프론트 PR #4 다단계 상담 + PR #5 사전 분류·대응 시급도·학부모 감정 상태)
--   * complaint_consult_states   민원당 상담 진행 상태 1행 (단계·분류·추천 교사 + 암호화된 슬롯)
--   * complaint_consult_messages 상담 대화 (학부모 전용, 원문과 같은 보호 수준: 본문 암호화, 교사 조회 제외)
--   * summary_cards              대응 시급도(학부모·교사 모두) + 학부모 감정 상태(교사·대응팀만)
-- 상태값(complaint_status)·상담 단계 전이는 애플리케이션이 관리한다.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 상담 진행 상태
-- ---------------------------------------------------------------------
CREATE TABLE complaint_consult_states (
    complaint_id        VARCHAR(32)    NOT NULL,
    stage               VARCHAR(16),                         -- FACTS | EXPLAIN | RECIPIENT | CONTACT | READY | NULL(첫 응답 전·카드 이후)
    category            VARCHAR(20),                         -- 학부모가 고른 사전 분류 (ComplaintTag)
    proposed_teacher_id VARCHAR(32),                         -- 상담에서 정한 받는 교사
    emotion_acked       BOOLEAN        NOT NULL DEFAULT FALSE,
    state_enc           BYTEA          NOT NULL,             -- 슬롯·대기 질문·카드 재료 문장 (학부모 발화 포함 → 암호화)
    updated_at          TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_complaint_consult_states PRIMARY KEY (complaint_id),
    CONSTRAINT fk_complaint_consult_states_complaint_id FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_complaint_consult_states_proposed_teacher_id FOREIGN KEY (proposed_teacher_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_complaint_consult_states_stage CHECK (stage IN ('FACTS', 'EXPLAIN', 'RECIPIENT', 'CONTACT', 'READY')),
    CONSTRAINT chk_complaint_consult_states_category
        CHECK (category IN ('LIFE_GUIDANCE', 'STUDY_EVALUATION', 'PEER_RELATION', 'SAFETY', 'ETC'))
);
CREATE INDEX idx_complaint_consult_states_proposed_teacher_id ON complaint_consult_states (proposed_teacher_id);
CREATE TRIGGER trg_complaint_consult_states_updated_at BEFORE UPDATE ON complaint_consult_states
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- 상담 대화
-- ---------------------------------------------------------------------
CREATE TABLE complaint_consult_messages (
    id           VARCHAR(32)    NOT NULL,                    -- 'cm_' + ULID
    complaint_id VARCHAR(32)    NOT NULL,
    seq          INTEGER        NOT NULL,                    -- 민원 안에서의 순서 (같은 시각에 여러 메시지가 생겨도 순서 보장)
    sender       VARCHAR(8)     NOT NULL,                    -- PARENT | AI
    body_enc     BYTEA          NOT NULL,                    -- 본문·보조 설명·정리표·규정 설명·빠른 답 (JSON, 암호화)
    reply_value  VARCHAR(64),                                -- 학부모가 누른 빠른 답 값 (예: TO_TEACHER, usr_t1) — 개인정보 아님
    created_at   TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_complaint_consult_messages PRIMARY KEY (id),
    CONSTRAINT uq_complaint_consult_messages_complaint_id_seq UNIQUE (complaint_id, seq),
    CONSTRAINT fk_complaint_consult_messages_complaint_id FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT chk_complaint_consult_messages_sender CHECK (sender IN ('PARENT', 'AI'))
);
COMMENT ON TABLE complaint_consult_messages IS 'AI 상담 대화 (학부모 전용). 교사 응답에서 제외, 원문과 같은 보호 수준';

-- ---------------------------------------------------------------------
-- 요약 카드: 대응 시급도 + 학부모 감정 상태 (버전마다 저장, 수정 시 이전 값을 이어받음)
-- ---------------------------------------------------------------------
ALTER TABLE summary_cards
    ADD COLUMN urgency_level  VARCHAR(8)   NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN urgency_reason VARCHAR(100),
    ADD COLUMN parent_emotion JSONB;                         -- {level, trend, signals[], summary}. signals 는 라벨만 (원문 금지)
ALTER TABLE summary_cards
    ADD CONSTRAINT chk_summary_cards_urgency_level CHECK (urgency_level IN ('URGENT', 'SOON', 'NORMAL'));

-- 교사 목록에서 긴급 카드를 먼저 찾기 위한 부분 인덱스
CREATE INDEX idx_summary_cards_complaint_id_urgent ON summary_cards (complaint_id) WHERE urgency_level = 'URGENT';
