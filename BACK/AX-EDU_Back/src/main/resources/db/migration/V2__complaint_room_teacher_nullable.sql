-- =====================================================================
-- 학부모 민원 흐름 개편 (프론트 PR #4): 민원은 방·교사 없이 시작하고, '확인하고 보내기'에서 받는 교사를 고른다.
--   * complaints.room_id·teacher_id 를 제출 전까지 NULL 허용
--   * 제출(SUBMITTED) 이후 상태에서는 둘 다 있어야 한다
-- =====================================================================

ALTER TABLE complaints ALTER COLUMN room_id DROP NOT NULL;
ALTER TABLE complaints ALTER COLUMN teacher_id DROP NOT NULL;

ALTER TABLE complaints ADD CONSTRAINT chk_complaints_submitted_has_room_teacher
    CHECK (status IN ('EXPLAINED', 'CLOSED_BY_EXPLANATION', 'CARD_DRAFTING')
        OR (room_id IS NOT NULL AND teacher_id IS NOT NULL));

COMMENT ON COLUMN complaints.room_id IS '학부모–교사 DIRECT 방. 제출 전에는 NULL 일 수 있음 (제출 시 확정)';
COMMENT ON COLUMN complaints.teacher_id IS '받는 교사. 제출 전에는 NULL 일 수 있음 (학부모가 제출 시 선택)';
