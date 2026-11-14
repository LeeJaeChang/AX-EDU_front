package com.axedu.common.util;

import java.security.SecureRandom;

/**
 * 외부 노출 ID = 접두어 + ULID (DB 설계서 1.2). 같은 밀리초 안에서도 단조 증가하도록 랜덤부를 1씩 올린다.
 */
public final class IdGenerator {

    public static final String USER = "usr_";
    public static final String ROOM = "room_";
    public static final String MESSAGE = "msg_";
    public static final String FILE = "file_";
    public static final String COMPLAINT = "cmp_";
    public static final String ESCALATION = "esc_";
    public static final String REG_DOC = "rdoc_";
    public static final String REG_CHUNK = "reg_";
    public static final String SAFE_CALL = "sc_";
    public static final String VISIT = "vs_";
    public static final String CONSULT_MESSAGE = "cm_";

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static long lastTime = -1;
    private static final byte[] lastRandom = new byte[10];

    private IdGenerator() {
    }

    public static String newId(String prefix) {
        return prefix + ulid();
    }

    public static synchronized String ulid() {
        long now = System.currentTimeMillis();
        if (now == lastTime) {
            increment(lastRandom);
        } else {
            RANDOM.nextBytes(lastRandom);
            lastTime = now;
        }
        char[] out = new char[26];
        long t = now;
        for (int i = 9; i >= 0; i--) {
            out[i] = ALPHABET[(int) (t & 31)];
            t >>>= 5;
        }
        // 80비트 랜덤 → 16글자
        long hi = 0;
        for (int i = 0; i < 5; i++) {
            hi = (hi << 8) | (lastRandom[i] & 0xff);
        }
        long lo = 0;
        for (int i = 5; i < 10; i++) {
            lo = (lo << 8) | (lastRandom[i] & 0xff);
        }
        for (int i = 17; i >= 10; i--) {
            out[i] = ALPHABET[(int) (hi & 31)];
            hi >>>= 5;
        }
        for (int i = 25; i >= 18; i--) {
            out[i] = ALPHABET[(int) (lo & 31)];
            lo >>>= 5;
        }
        return new String(out);
    }

    private static void increment(byte[] bytes) {
        for (int i = bytes.length - 1; i >= 0; i--) {
            if (++bytes[i] != 0) {
                return;
            }
        }
    }
}
