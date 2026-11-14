package com.axedu.entity.enums;

import java.util.EnumSet;
import java.util.Set;

public enum ComplaintStatus {
    EXPLAINED, CLOSED_BY_EXPLANATION, CARD_DRAFTING, SUBMITTED, TEACHER_REVIEWING, IN_CONVERSATION, ESCALATED, RESOLVED;

    /** 교사에게 보이지 않는 상태 (학부모가 '확인하고 보내기' 전) */
    public static final Set<ComplaintStatus> PRE_SUBMIT = EnumSet.of(EXPLAINED, CLOSED_BY_EXPLANATION, CARD_DRAFTING);
    /** 끝난 민원 */
    public static final Set<ComplaintStatus> FINISHED = EnumSet.of(CLOSED_BY_EXPLANATION, RESOLVED);

    public boolean isPreSubmit() {
        return PRE_SUBMIT.contains(this);
    }

    public boolean isFinished() {
        return FINISHED.contains(this);
    }
}
