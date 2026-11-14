package com.axedu.common.response;

import java.util.LinkedHashMap;
import java.util.Map;

/** 목록 응답 meta 생성 도우미. nextCursor 는 끝이면 null 로 "키는 있게" 내려 준다. */
public final class PageMeta {

    private PageMeta() {
    }

    public static Map<String, Object> cursor(String nextCursor) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("nextCursor", nextCursor);
        meta.put("hasMore", nextCursor != null);
        return meta;
    }

    public static Map<String, Object> cursor(String nextCursor, boolean hasMore) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("nextCursor", nextCursor);
        meta.put("hasMore", hasMore);
        return meta;
    }
}
