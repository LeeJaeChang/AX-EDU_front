package com.axedu.dto.complaint;

public record ReplyDraftResponse(String draft, Sections sections) {

    public record Sections(String situation, String reason, String nextActions) {
    }
}
