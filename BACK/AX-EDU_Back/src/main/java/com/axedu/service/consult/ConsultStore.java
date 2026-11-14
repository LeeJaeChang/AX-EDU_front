package com.axedu.service.consult;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.axedu.common.security.TextCipher;
import com.axedu.dto.complaint.ConsultMessageResponse;
import com.axedu.entity.ComplaintConsultMessage;
import com.axedu.entity.ComplaintConsultState;
import com.axedu.repository.ComplaintConsultMessageRepository;
import com.axedu.repository.ComplaintConsultStateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * 상담 상태·대화 저장. 학부모 발화가 담긴 부분(슬롯·카드 재료·대화 본문)은 원문처럼 AES-256-GCM 으로 암호화한다.
 */
@Component
@RequiredArgsConstructor
public class ConsultStore {

    private final ComplaintConsultStateRepository stateRepository;
    private final ComplaintConsultMessageRepository messageRepository;
    private final TextCipher cipher;
    private final ObjectMapper mapper;

    public Optional<ConsultSession> load(String complaintId) {
        return stateRepository.findById(complaintId).map(this::toSession);
    }

    /** 목록 응답용: 단계·분류·추천 교사만 (복호화 없음) */
    public Map<String, ComplaintConsultState> rows(Collection<String> complaintIds) {
        if (complaintIds.isEmpty()) {
            return Map.of();
        }
        return stateRepository.findByComplaintIdIn(complaintIds).stream()
                .collect(Collectors.toMap(ComplaintConsultState::getComplaintId, Function.identity()));
    }

    public Optional<ComplaintConsultState> row(String complaintId) {
        return stateRepository.findById(complaintId);
    }

    public void save(String complaintId, ConsultSession s) {
        ComplaintConsultState row = stateRepository.findById(complaintId).orElseGet(() -> {
            ComplaintConsultState r = new ComplaintConsultState();
            r.setComplaintId(complaintId);
            return r;
        });
        row.setStage(s.stage);
        row.setCategory(s.category);
        row.setProposedTeacherId(s.proposedTeacherId);
        row.setEmotionAcked(s.emotionAcked);
        row.setStateEnc(cipher.encrypt(json(s.secret)));
        stateRepository.save(row);
    }

    public List<ConsultMessageResponse> messages(String complaintId) {
        return messageRepository.findByComplaintIdOrderBySeqAsc(complaintId).stream()
                .map(m -> read(cipher.decrypt(m.getBodyEnc()), ConsultMessageResponse.class))
                .toList();
    }

    public long count(String complaintId) {
        return messageRepository.countByComplaintId(complaintId);
    }

    public void append(String complaintId, List<ConsultMessageResponse> messages) {
        int seq = (int) messageRepository.countByComplaintId(complaintId);
        for (ConsultMessageResponse m : messages) {
            ComplaintConsultMessage row = new ComplaintConsultMessage();
            row.setId(m.id());
            row.setComplaintId(complaintId);
            row.setSeq(seq++);
            row.setSender(m.from());
            row.setBodyEnc(cipher.encrypt(json(m)));
            row.setValue(m.value() == null ? null : m.value().length() > 64 ? m.value().substring(0, 64) : m.value());
            row.setCreatedAt(m.createdAt());
            messageRepository.save(row);
        }
    }

    private ConsultSession toSession(ComplaintConsultState row) {
        ConsultSession s = new ConsultSession();
        s.stage = row.getStage();
        s.category = row.getCategory();
        s.proposedTeacherId = row.getProposedTeacherId();
        s.emotionAcked = row.isEmotionAcked();
        s.secret = read(cipher.decrypt(row.getStateEnc()), ConsultSession.Secret.class);
        return s;
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("상담 상태를 저장하지 못했습니다", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("상담 상태를 읽지 못했습니다", e);
        }
    }
}
