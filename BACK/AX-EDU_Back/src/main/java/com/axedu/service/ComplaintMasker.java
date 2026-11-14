package com.axedu.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.axedu.entity.Complaint;
import com.axedu.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * AI 에 보낼 문장 가명처리 (DB 설계서 6장: 이름·학년반 등을 가린 입력만 전송, 입력 자체는 저장하지 않고 해시만).
 * 학생·학부모 이름과 부르는 이름 → OOO, 학번 → 00000, 전화번호·이메일·주민번호 → [전화번호] 등.
 */
@Component
@RequiredArgsConstructor
public class ComplaintMasker {

    private static final Pattern PHONE = Pattern.compile("01[016789][-\\s.]?\\d{3,4}[-\\s.]?\\d{4}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern RRN = Pattern.compile("\\d{6}[-\\s]?[1-4]\\d{6}");

    private final UserService userService;

    public String mask(Complaint c, String text) {
        if (text == null) {
            return "";
        }
        User student = userService.get(c.getStudentId());
        User parent = userService.get(c.getParentId());
        String out = text;
        for (String name : List.of(nz(student.getName()), nz(parent.getName()), nickname(student.getName()))) {
            if (name.length() >= 2) {
                out = out.replace(name, "OOO");
            }
        }
        if (student.getStudentNumber() != null) {
            out = out.replace(student.getStudentNumber(), "00000");
        }
        out = RRN.matcher(out).replaceAll("[주민번호]");
        out = PHONE.matcher(out).replaceAll("[전화번호]");
        return EMAIL.matcher(out).replaceAll("[이메일]");
    }

    public List<String> mask(Complaint c, List<String> texts) {
        return texts.stream().map(t -> mask(c, t)).toList();
    }

    /** 부르는 이름: 성을 빼고, 끝 글자에 받침이 있으면 '이' 를 붙인다 (김민수 → 민수, 김리현 → 리현이) */
    public static String nickname(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String given = fullName.length() > 1 ? fullName.substring(1) : fullName;
        char last = given.charAt(given.length() - 1);
        boolean batchim = last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
        return batchim ? given + "이" : given;
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
