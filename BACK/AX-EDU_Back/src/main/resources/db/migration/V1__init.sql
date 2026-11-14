-- =====================================================================
-- AXEDU 초기 스키마 (DB 스키마 설계서 v1.1, PostgreSQL 15+)
--   * Flyway 가 앱 시작 시 한 번 실행한다. 이 파일은 고치지 말고, 변경은 V2__... 로 추가할 것
--   * 이름 규칙: pk_ · uq_ · idx_ · fk_ · chk_ + {테이블}_{컬럼들}
--   * 미적용(차후): 학교 단위 RLS 정책 — 앱이 요청마다 SET LOCAL app.school_id 를 하게 된 뒤 별도 마이그레이션으로 추가
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------
-- 4. Enum
-- ---------------------------------------------------------------------
CREATE TYPE day_of_week       AS ENUM ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN');
CREATE TYPE user_role         AS ENUM ('PARENT', 'TEACHER', 'STUDENT', 'RESPONSE_TEAM', 'ADMIN', 'NOTICE');
CREATE TYPE user_status       AS ENUM ('ACTIVE', 'INACTIVE');
CREATE TYPE parent_relation   AS ENUM ('MOTHER', 'FATHER', 'GUARDIAN', 'ETC');
CREATE TYPE assignment_type   AS ENUM ('HOMEROOM', 'SUBJECT');
CREATE TYPE team_position     AS ENUM ('MEMBER', 'LEADER', 'VICE_PRINCIPAL', 'PRINCIPAL');
CREATE TYPE room_type         AS ENUM ('DIRECT', 'NOTICE', 'GROUP');
CREATE TYPE message_type      AS ENUM ('TEXT', 'IMAGE', 'FILE', 'SYSTEM', 'COMPLAINT_CARD', 'COMPLAINT_REPLY');
CREATE TYPE file_purpose      AS ENUM ('MESSAGE', 'COMPLAINT', 'REGULATION');
CREATE TYPE file_status       AS ENUM ('TEMP', 'ATTACHED', 'DELETED');
CREATE TYPE complaint_status  AS ENUM ('EXPLAINED', 'CLOSED_BY_EXPLANATION', 'CARD_DRAFTING', 'SUBMITTED',
                                       'TEACHER_REVIEWING', 'IN_CONVERSATION', 'ESCALATED', 'RESOLVED');
CREATE TYPE ai_status         AS ENUM ('SUCCEEDED', 'FAILED');
CREATE TYPE ai_recommendation AS ENUM ('EXPLAINED', 'TALK_TO_TEACHER');
CREATE TYPE card_source       AS ENUM ('AI_GENERATED', 'AI_REVISION', 'PARENT_EDIT');
CREATE TYPE contact_method    AS ENUM ('MESSAGE', 'CALL', 'VISIT');
CREATE TYPE escalation_stage  AS ENUM ('RESPONSE_TEAM', 'VICE_PRINCIPAL', 'PRINCIPAL');
CREATE TYPE escalation_status AS ENUM ('REQUESTED', 'IN_PROGRESS', 'RESOLVED');
CREATE TYPE escalation_action AS ENUM ('CREATED', 'ASSIGNED', 'STATUS_CHANGED', 'FORWARDED', 'NOTE');
CREATE TYPE access_type       AS ENUM ('VIEW_ORIGINAL', 'VIEW_CASE_FILE');
CREATE TYPE source_type       AS ENUM ('SCHOOL_RULE', 'NOTICE_LETTER', 'LAW');
CREATE TYPE regulation_status AS ENUM ('PROCESSING', 'ACTIVE', 'ARCHIVED');

-- updated_at 자동 갱신 트리거 함수
CREATE FUNCTION set_updated_at() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------
-- 3.1 사용자·조직
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                  VARCHAR(32)    NOT NULL,
    school_id           VARCHAR(32)    NOT NULL,
    superschool_user_id VARCHAR(64)    NOT NULL,
    role                user_role      NOT NULL,
    name                VARCHAR(50)    NOT NULL,
    grade               SMALLINT,
    class_no            SMALLINT,
    student_number      CHAR(5),
    profile_image_url   VARCHAR(500),
    status              user_status    NOT NULL DEFAULT 'ACTIVE',
    synced_at           TIMESTAMPTZ(3),
    created_at          TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_superschool_user_id UNIQUE (superschool_user_id)
);
CREATE INDEX idx_users_school_id_role ON users (school_id, role);
CREATE INDEX idx_users_school_id_grade_class_no ON users (school_id, grade, class_no);
CREATE INDEX idx_users_school_id_name ON users (school_id, name);
COMMENT ON TABLE users IS '슈퍼스쿨 사용자의 사본. 원본은 슈퍼스쿨 회원 DB';

CREATE TABLE parent_students (
    parent_id  VARCHAR(32)     NOT NULL,
    student_id VARCHAR(32)     NOT NULL,
    relation   parent_relation NOT NULL DEFAULT 'GUARDIAN',
    is_primary BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ(3)  NOT NULL DEFAULT now(),
    CONSTRAINT pk_parent_students PRIMARY KEY (parent_id, student_id),
    CONSTRAINT fk_parent_students_parent_id FOREIGN KEY (parent_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_parent_students_student_id FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_parent_students_student_id ON parent_students (student_id);

CREATE TABLE teacher_classes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    teacher_id      VARCHAR(32)     NOT NULL,
    school_year     SMALLINT        NOT NULL,
    grade           SMALLINT        NOT NULL,
    class_no        SMALLINT        NOT NULL,
    subject         VARCHAR(50)     NOT NULL DEFAULT '',
    assignment_type assignment_type NOT NULL,
    created_at      TIMESTAMPTZ(3)  NOT NULL DEFAULT now(),
    CONSTRAINT pk_teacher_classes PRIMARY KEY (id),
    CONSTRAINT uq_teacher_classes_teacher_id_school_year_grade_class_c9cd49
        UNIQUE (teacher_id, school_year, grade, class_no, subject),
    CONSTRAINT fk_teacher_classes_teacher_id FOREIGN KEY (teacher_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_teacher_classes_school_year_grade_class_no ON teacher_classes (school_year, grade, class_no);

CREATE TABLE response_team_members (
    school_id  VARCHAR(32)    NOT NULL,
    user_id    VARCHAR(32)    NOT NULL,
    position   team_position  NOT NULL DEFAULT 'MEMBER',
    active     BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_response_team_members PRIMARY KEY (school_id, user_id),
    CONSTRAINT fk_response_team_members_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_response_team_members_user_id ON response_team_members (user_id);

-- ---------------------------------------------------------------------
-- 3.2 채팅
-- ---------------------------------------------------------------------
CREATE TABLE chat_settings (
    user_id                 VARCHAR(32)    NOT NULL,
    available_from          TIME           NOT NULL DEFAULT '09:00:00',
    available_to            TIME           NOT NULL DEFAULT '18:00:00',
    available_days          day_of_week[]  NOT NULL DEFAULT '{MON,TUE,WED,THU,FRI}',
    allow_outside_hours_for user_role[]    NOT NULL DEFAULT '{TEACHER}',
    outside_hours_notice    VARCHAR(200),
    push_enabled            BOOLEAN        NOT NULL DEFAULT TRUE,
    updated_at              TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_chat_settings PRIMARY KEY (user_id),
    CONSTRAINT fk_chat_settings_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_chat_settings_available_from_to CHECK (available_from < available_to)
);

CREATE TABLE chat_rooms (
    id                      VARCHAR(32)    NOT NULL,
    school_id               VARCHAR(32)    NOT NULL,
    type                    room_type      NOT NULL,
    title                   VARCHAR(100),
    direct_key              VARCHAR(70),
    owner_id                VARCHAR(32),
    available_from_override TIME,
    available_to_override   TIME,
    last_message_id         VARCHAR(32),
    last_message_at         TIMESTAMPTZ(3),
    created_at              TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_chat_rooms PRIMARY KEY (id),
    CONSTRAINT uq_chat_rooms_direct_key UNIQUE (direct_key),
    CONSTRAINT fk_chat_rooms_owner_id FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_chat_rooms_school_id_last_message_at ON chat_rooms (school_id, last_message_at);
CREATE INDEX idx_chat_rooms_owner_id ON chat_rooms (owner_id);

CREATE TABLE chat_room_members (
    room_id              VARCHAR(32)    NOT NULL,
    user_id              VARCHAR(32)    NOT NULL,
    joined_at            TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    left_at              TIMESTAMPTZ(3),
    muted                BOOLEAN        NOT NULL DEFAULT FALSE,
    last_read_message_id VARCHAR(32),
    unread_count         INTEGER        NOT NULL DEFAULT 0,
    CONSTRAINT pk_chat_room_members PRIMARY KEY (room_id, user_id),
    CONSTRAINT fk_chat_room_members_room_id FOREIGN KEY (room_id) REFERENCES chat_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_room_members_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_chat_room_members_user_id_left_at ON chat_room_members (user_id, left_at);

CREATE TABLE files (
    id            VARCHAR(32)    NOT NULL,
    school_id     VARCHAR(32)    NOT NULL,
    uploader_id   VARCHAR(32)    NOT NULL,
    original_name VARCHAR(255)   NOT NULL,
    mime_type     VARCHAR(100)   NOT NULL,
    size_bytes    INTEGER        NOT NULL,
    storage_key   VARCHAR(500)   NOT NULL,
    purpose       file_purpose   NOT NULL,
    status        file_status    NOT NULL DEFAULT 'TEMP',
    expires_at    TIMESTAMPTZ(3),
    created_at    TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_files PRIMARY KEY (id),
    CONSTRAINT fk_files_uploader_id FOREIGN KEY (uploader_id) REFERENCES users (id) ON DELETE RESTRICT
);
CREATE INDEX idx_files_uploader_id ON files (uploader_id);
CREATE INDEX idx_files_expires_at ON files (expires_at) WHERE status = 'TEMP';

-- ---------------------------------------------------------------------
-- 3.3 민원 (messages 가 complaints·summary_cards 를 참조하므로 먼저 만든다)
-- ---------------------------------------------------------------------
CREATE TABLE complaints (
    id                     VARCHAR(32)      NOT NULL,
    school_id              VARCHAR(32)      NOT NULL,
    room_id                VARCHAR(32)      NOT NULL,
    parent_id              VARCHAR(32)      NOT NULL,
    teacher_id             VARCHAR(32)      NOT NULL,
    student_id             VARCHAR(32)      NOT NULL,
    status                 complaint_status NOT NULL DEFAULT 'EXPLAINED',
    original_text_enc      BYTEA            NOT NULL,
    submitted_card_version INTEGER,
    version                INTEGER          NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ(3)   NOT NULL DEFAULT now(),
    submitted_at           TIMESTAMPTZ(3),
    acknowledged_at        TIMESTAMPTZ(3),
    replied_at             TIMESTAMPTZ(3),
    escalated_at           TIMESTAMPTZ(3),
    closed_at              TIMESTAMPTZ(3),
    resolved_at            TIMESTAMPTZ(3),
    updated_at             TIMESTAMPTZ(3)   NOT NULL DEFAULT now(),
    CONSTRAINT pk_complaints PRIMARY KEY (id),
    CONSTRAINT fk_complaints_room_id FOREIGN KEY (room_id) REFERENCES chat_rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_complaints_parent_id FOREIGN KEY (parent_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_complaints_teacher_id FOREIGN KEY (teacher_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_complaints_student_id FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE RESTRICT
);
CREATE INDEX idx_complaints_teacher_id_status_submitted_at ON complaints (teacher_id, status, submitted_at);
CREATE INDEX idx_complaints_parent_id_created_at ON complaints (parent_id, created_at);
CREATE INDEX idx_complaints_student_id_created_at ON complaints (student_id, created_at);
CREATE INDEX idx_complaints_room_id_status ON complaints (room_id, status);
CREATE INDEX idx_complaints_school_id_status ON complaints (school_id, status);
COMMENT ON COLUMN complaints.original_text_enc IS '학부모 원문 AES-256-GCM 암호문. 교사 조회(v_complaints_teacher)에서 제외';

CREATE TABLE summary_cards (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY,
    complaint_id         VARCHAR(32)    NOT NULL,
    version              INTEGER        NOT NULL,
    source               card_source    NOT NULL,
    revision_instruction VARCHAR(1000),
    grade                SMALLINT       NOT NULL,
    class_no             SMALLINT       NOT NULL,
    received_at          TIMESTAMPTZ(3) NOT NULL,
    tags                 JSONB          NOT NULL,
    attribution          VARCHAR(50)    NOT NULL DEFAULT '학부모님 설명에 따르면',
    situation            JSONB          NOT NULL,
    concerns             JSONB          NOT NULL,
    requests             JSONB          NOT NULL,
    preferred_methods    JSONB          NOT NULL,
    preferred_from       TIME,
    preferred_to         TIME,
    suggestions          JSONB,
    created_by           VARCHAR(32)    NOT NULL,
    confirmed_at         TIMESTAMPTZ(3),
    created_at           TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_summary_cards PRIMARY KEY (id),
    CONSTRAINT uq_summary_cards_complaint_id_version UNIQUE (complaint_id, version),
    CONSTRAINT fk_summary_cards_complaint_id FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_summary_cards_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT
);
CREATE INDEX idx_summary_cards_created_by ON summary_cards (created_by);

CREATE TABLE messages (
    id                VARCHAR(32)    NOT NULL,
    room_id           VARCHAR(32)    NOT NULL,
    school_id         VARCHAR(32)    NOT NULL,
    sender_id         VARCHAR(32),
    type              message_type   NOT NULL,
    content           TEXT,
    complaint_id      VARCHAR(32),
    summary_card_id   BIGINT,
    client_message_id VARCHAR(64),
    created_at        TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ(3),
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT uq_messages_sender_id_client_message_id UNIQUE (sender_id, client_message_id),
    CONSTRAINT fk_messages_room_id FOREIGN KEY (room_id) REFERENCES chat_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender_id FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_messages_complaint_id FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE SET NULL,
    CONSTRAINT fk_messages_summary_card_id FOREIGN KEY (summary_card_id) REFERENCES summary_cards (id) ON DELETE SET NULL
);
CREATE INDEX idx_messages_room_id_id ON messages (room_id, id);
CREATE INDEX idx_messages_room_id_created_at ON messages (room_id, created_at);
CREATE INDEX idx_messages_complaint_id ON messages (complaint_id);
CREATE INDEX idx_messages_summary_card_id ON messages (summary_card_id);

CREATE TABLE message_attachments (
    message_id VARCHAR(32) NOT NULL,
    file_id    VARCHAR(32) NOT NULL,
    sort_order SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT pk_message_attachments PRIMARY KEY (message_id, file_id),
    CONSTRAINT uq_message_attachments_file_id UNIQUE (file_id),
    CONSTRAINT fk_message_attachments_message_id FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_attachments_file_id FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE RESTRICT
);

CREATE TABLE complaint_attachments (
    complaint_id VARCHAR(32) NOT NULL,
    file_id      VARCHAR(32) NOT NULL,
    sort_order   SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT pk_complaint_attachments PRIMARY KEY (complaint_id, file_id),
    CONSTRAINT uq_complaint_attachments_file_id UNIQUE (file_id),
    CONSTRAINT fk_complaint_attachments_complaint_id FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_complaint_attachments_file_id FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE RESTRICT
);

-- ---------------------------------------------------------------------
-- 3.5 AI 근거 자료 (인용 테이블이 regulation_chunks 를 참조하므로 먼저 만든다)
-- ---------------------------------------------------------------------
CREATE TABLE regulations (
    id             VARCHAR(32)       NOT NULL,
    school_id      VARCHAR(32),
    source_type    source_type       NOT NULL,
    title          VARCHAR(200)      NOT NULL,
    file_id        VARCHAR(32),
    document_url   VARCHAR(500),
    effective_from DATE              NOT NULL,
    effective_to   DATE,
    status         regulation_status NOT NULL DEFAULT 'PROCESSING',
    uploaded_by    VARCHAR(32),
    created_at     TIMESTAMPTZ(3)    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ(3)    NOT NULL DEFAULT now(),
    CONSTRAINT pk_regulations PRIMARY KEY (id),
    CONSTRAINT fk_regulations_file_id FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE SET NULL,
    CONSTRAINT fk_regulations_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_regulations_school_id_status_source_type ON regulations (school_id, status, source_type);
CREATE INDEX idx_regulations_file_id ON regulations (file_id);
CREATE INDEX idx_regulations_uploaded_by ON regulations (uploaded_by);

CREATE TABLE regulation_chunks (
    id            VARCHAR(32)    NOT NULL,
    regulation_id VARCHAR(32)    NOT NULL,
    article_label VARCHAR(100)   NOT NULL,
    content       TEXT           NOT NULL,
    sort_order    INTEGER        NOT NULL DEFAULT 0,
    vector_id     VARCHAR(100),
    created_at    TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_regulation_chunks PRIMARY KEY (id),
    CONSTRAINT fk_regulation_chunks_regulation_id FOREIGN KEY (regulation_id) REFERENCES regulations (id) ON DELETE CASCADE
);
CREATE INDEX idx_regulation_chunks_regulation_id_sort_order ON regulation_chunks (regulation_id, sort_order);
CREATE INDEX idx_regulation_chunks_article_label_trgm ON regulation_chunks USING gin (article_label gin_trgm_ops);
CREATE INDEX idx_regulation_chunks_content_trgm ON regulation_chunks USING gin (content gin_trgm_ops);

-- ---------------------------------------------------------------------
-- 3.3 민원 (나머지)
-- ---------------------------------------------------------------------
CREATE TABLE complaint_ai_explanations (
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    complaint_id      VARCHAR(32)       NOT NULL,
    attempt           SMALLINT          NOT NULL DEFAULT 1,
    status            ai_status         NOT NULL,
    empathy           VARCHAR(500),
    reason            TEXT,
    alternative       VARCHAR(500),
    recommendation    ai_recommendation,
    model_name        VARCHAR(50)       NOT NULL,
    masked_input_hash CHAR(64)          NOT NULL,
    error_code        VARCHAR(50),
    latency_ms        INTEGER,
    created_at        TIMESTAMPTZ(3)    NOT NULL DEFAULT now(),
    CONSTRAINT pk_complaint_ai_explanations PRIMARY KEY (id),
    CONSTRAINT uq_complaint_ai_explanations_complaint_id_attempt UNIQUE (complaint_id, attempt),
    CONSTRAINT fk_complaint_ai_explanations_complaint_id FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE
);

CREATE TABLE complaint_explanation_citations (
    explanation_id      BIGINT      NOT NULL,
    regulation_chunk_id VARCHAR(32) NOT NULL,
    sort_order          SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT pk_complaint_explanation_citations PRIMARY KEY (explanation_id, regulation_chunk_id),
    CONSTRAINT fk_complaint_explanation_citations_explanation_id
        FOREIGN KEY (explanation_id) REFERENCES complaint_ai_explanations (id) ON DELETE CASCADE,
    CONSTRAINT fk_complaint_explanation_citations_regulation_chunk_id
        FOREIGN KEY (regulation_chunk_id) REFERENCES regulation_chunks (id) ON DELETE RESTRICT
);
CREATE INDEX idx_complaint_explanation_citations_regulation_chunk_id ON complaint_explanation_citations (regulation_chunk_id);

CREATE TABLE summary_card_regulations (
    card_id             BIGINT      NOT NULL,
    regulation_chunk_id VARCHAR(32) NOT NULL,
    sort_order          SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT pk_summary_card_regulations PRIMARY KEY (card_id, regulation_chunk_id),
    CONSTRAINT fk_summary_card_regulations_card_id FOREIGN KEY (card_id) REFERENCES summary_cards (id) ON DELETE CASCADE,
    CONSTRAINT fk_summary_card_regulations_regulation_chunk_id
        FOREIGN KEY (regulation_chunk_id) REFERENCES regulation_chunks (id) ON DELETE RESTRICT
);
CREATE INDEX idx_summary_card_regulations_regulation_chunk_id ON summary_card_regulations (regulation_chunk_id);

CREATE TABLE complaint_status_histories (
    id           BIGINT GENERATED ALWAYS AS IDENTITY,
    complaint_id VARCHAR(32)      NOT NULL,
    from_status  complaint_status,
    to_status    complaint_status NOT NULL,
    actor_id     VARCHAR(32),
    actor_role   user_role,
    note         VARCHAR(500),
    created_at   TIMESTAMPTZ(3)   NOT NULL DEFAULT now(),
    CONSTRAINT pk_complaint_status_histories PRIMARY KEY (id),
    CONSTRAINT fk_complaint_status_histories_complaint_id FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_complaint_status_histories_actor_id FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_complaint_status_histories_complaint_id_created_at ON complaint_status_histories (complaint_id, created_at);
CREATE INDEX idx_complaint_status_histories_actor_id ON complaint_status_histories (actor_id);

CREATE TABLE teacher_reply_drafts (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY,
    complaint_id       VARCHAR(32)    NOT NULL,
    teacher_id         VARCHAR(32)    NOT NULL,
    input_situation    TEXT,
    input_reason       TEXT,
    input_next_actions TEXT,
    draft_text         TEXT           NOT NULL,
    model_name         VARCHAR(50)    NOT NULL,
    created_at         TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_teacher_reply_drafts PRIMARY KEY (id),
    CONSTRAINT fk_teacher_reply_drafts_complaint_id FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_teacher_reply_drafts_teacher_id FOREIGN KEY (teacher_id) REFERENCES users (id) ON DELETE RESTRICT
);
CREATE INDEX idx_teacher_reply_drafts_complaint_id_created_at ON teacher_reply_drafts (complaint_id, created_at);
CREATE INDEX idx_teacher_reply_drafts_teacher_id ON teacher_reply_drafts (teacher_id);

CREATE TABLE complaint_replies (
    complaint_id             VARCHAR(32)    NOT NULL,
    teacher_id               VARCHAR(32)    NOT NULL,
    message_id               VARCHAR(32)    NOT NULL,
    content                  TEXT           NOT NULL,
    contact_method           contact_method NOT NULL,
    scheduled_at             TIMESTAMPTZ(3),
    safe_call_reservation_id VARCHAR(64),
    visit_reservation_id     VARCHAR(64),
    based_on_draft_id        BIGINT,
    sent_at                  TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_complaint_replies PRIMARY KEY (complaint_id),
    CONSTRAINT uq_complaint_replies_message_id UNIQUE (message_id),
    CONSTRAINT fk_complaint_replies_complaint_id FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_complaint_replies_teacher_id FOREIGN KEY (teacher_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_complaint_replies_message_id FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE RESTRICT,
    CONSTRAINT fk_complaint_replies_based_on_draft_id
        FOREIGN KEY (based_on_draft_id) REFERENCES teacher_reply_drafts (id) ON DELETE SET NULL
);
CREATE INDEX idx_complaint_replies_teacher_id ON complaint_replies (teacher_id);
CREATE INDEX idx_complaint_replies_based_on_draft_id ON complaint_replies (based_on_draft_id);

CREATE TABLE complaint_access_logs (
    id           BIGINT GENERATED ALWAYS AS IDENTITY,
    complaint_id VARCHAR(32)    NOT NULL,
    user_id      VARCHAR(32)    NOT NULL,
    access_type  access_type    NOT NULL,
    ip_address   VARCHAR(45),
    created_at   TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_complaint_access_logs PRIMARY KEY (id),
    CONSTRAINT fk_complaint_access_logs_complaint_id FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_complaint_access_logs_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);
CREATE INDEX idx_complaint_access_logs_complaint_id_created_at ON complaint_access_logs (complaint_id, created_at);
CREATE INDEX idx_complaint_access_logs_user_id_created_at ON complaint_access_logs (user_id, created_at);

-- ---------------------------------------------------------------------
-- 3.4 민원대응팀 이관
-- ---------------------------------------------------------------------
CREATE TABLE escalations (
    id           VARCHAR(32)       NOT NULL,
    school_id    VARCHAR(32)       NOT NULL,
    complaint_id VARCHAR(32)       NOT NULL,
    requested_by VARCHAR(32)       NOT NULL,
    reason_types JSONB             NOT NULL,
    description  TEXT              NOT NULL,
    stage        escalation_stage  NOT NULL DEFAULT 'RESPONSE_TEAM',
    status       escalation_status NOT NULL DEFAULT 'REQUESTED',
    assignee_id  VARCHAR(32),
    requested_at TIMESTAMPTZ(3)    NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ(3),
    updated_at   TIMESTAMPTZ(3)    NOT NULL DEFAULT now(),
    CONSTRAINT pk_escalations PRIMARY KEY (id),
    CONSTRAINT uq_escalations_complaint_id UNIQUE (complaint_id),
    CONSTRAINT fk_escalations_complaint_id FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE RESTRICT,
    CONSTRAINT fk_escalations_requested_by FOREIGN KEY (requested_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_escalations_assignee_id FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_escalations_school_id_stage_status_requested_at ON escalations (school_id, stage, status, requested_at);
CREATE INDEX idx_escalations_assignee_id_status ON escalations (assignee_id, status);
CREATE INDEX idx_escalations_requested_by ON escalations (requested_by);

CREATE TABLE escalation_logs (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    escalation_id VARCHAR(32)       NOT NULL,
    action        escalation_action NOT NULL,
    from_stage    escalation_stage,
    to_stage      escalation_stage,
    from_status   escalation_status,
    to_status     escalation_status,
    assignee_id   VARCHAR(32),
    note          VARCHAR(1000),
    actor_id      VARCHAR(32)       NOT NULL,
    created_at    TIMESTAMPTZ(3)    NOT NULL DEFAULT now(),
    CONSTRAINT pk_escalation_logs PRIMARY KEY (id),
    CONSTRAINT fk_escalation_logs_escalation_id FOREIGN KEY (escalation_id) REFERENCES escalations (id) ON DELETE CASCADE,
    CONSTRAINT fk_escalation_logs_actor_id FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_escalation_logs_assignee_id FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_escalation_logs_escalation_id_created_at ON escalation_logs (escalation_id, created_at);
CREATE INDEX idx_escalation_logs_actor_id ON escalation_logs (actor_id);
CREATE INDEX idx_escalation_logs_assignee_id ON escalation_logs (assignee_id);

-- ---------------------------------------------------------------------
-- 3.6 시스템
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    school_id     VARCHAR(32)    NOT NULL,
    user_id       VARCHAR(32)    NOT NULL,
    event_type    VARCHAR(50)    NOT NULL,
    payload       JSONB          NOT NULL,
    deliver_after TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    sent_at       TIMESTAMPTZ(3),
    read_at       TIMESTAMPTZ(3),
    created_at    TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_notifications_user_id_created_at ON notifications (user_id, created_at);
CREATE INDEX idx_notifications_deliver_after ON notifications (deliver_after) WHERE sent_at IS NULL;

CREATE TABLE idempotency_keys (
    user_id         VARCHAR(32)    NOT NULL,
    idem_key        VARCHAR(64)    NOT NULL,
    endpoint        VARCHAR(100)   NOT NULL,
    response_status SMALLINT       NOT NULL,
    response_body   JSONB          NOT NULL,
    created_at      TIMESTAMPTZ(3) NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ(3) NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (user_id, idem_key),
    CONSTRAINT fk_idempotency_keys_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);

-- ---------------------------------------------------------------------
-- updated_at 트리거
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_response_team_members_updated_at BEFORE UPDATE ON response_team_members
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_chat_settings_updated_at BEFORE UPDATE ON chat_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_chat_rooms_updated_at BEFORE UPDATE ON chat_rooms
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_complaints_updated_at BEFORE UPDATE ON complaints
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_escalations_updated_at BEFORE UPDATE ON escalations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_regulations_updated_at BEFORE UPDATE ON regulations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- 교사 권한 조회용 뷰: 학부모 원문(original_text_enc) 제외. security_invoker 라 RLS 가 그대로 적용된다
-- ---------------------------------------------------------------------
CREATE VIEW v_complaints_teacher WITH (security_invoker = true) AS
SELECT id, school_id, room_id, parent_id, teacher_id, student_id, status,
       submitted_card_version, version, created_at, submitted_at, acknowledged_at,
       replied_at, escalated_at, closed_at, resolved_at, updated_at
FROM complaints
WHERE status NOT IN ('EXPLAINED', 'CLOSED_BY_EXPLANATION', 'CARD_DRAFTING');
