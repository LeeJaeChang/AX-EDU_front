package com.axedu;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 테스트용: 빠른 답을 눌러 가며 상담을 READY 까지 진행한다 */
final class ConsultDriver {

    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final String parent;

    ConsultDriver(MockMvc mvc, ObjectMapper mapper, String parentBearer) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.parent = parentBearer;
    }

    JsonNode get(String id) throws Exception {
        String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/axedu/v1/complaints/" + id)
                .header("Authorization", parent)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    JsonNode send(String id, String content, String value) throws Exception {
        ObjectNode req = mapper.createObjectNode().put("content", content);
        if (value != null) {
            req.put("value", value);
        }
        String body = mvc.perform(post("/api/axedu/v1/complaints/" + id + "/consultations").header("Authorization", parent)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    /** 마지막 AI 메시지의 빠른 답 */
    static JsonNode lastQuickReplies(JsonNode complaint) {
        JsonNode msgs = complaint.path("consultation");
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if ("AI".equals(msgs.get(i).path("from").asText()) && msgs.get(i).has("quickReplies")) {
                return msgs.get(i).path("quickReplies");
            }
        }
        return mapper().createArrayNode();
    }

    /**
     * READY 까지 진행. 대응 시급도는 urgency, 선생님은 teacherId(없으면 추천), 나머지는 첫 빠른 답.
     */
    JsonNode toReady(String id, String urgency, String teacherId) throws Exception {
        JsonNode c = get(id);
        for (int i = 0; i < 20 && !"READY".equals(c.path("consultStage").asText()); i++) {
            String stage = c.path("consultStage").asText();
            JsonNode quick = lastQuickReplies(c);
            JsonNode pick = quick.get(0);
            for (JsonNode q : quick) {
                String v = q.path("value").asText("");
                if (("FACTS".equals(stage) && v.equals(urgency)) || ("EXPLAIN".equals(stage) && v.equals("TO_TEACHER"))
                        || ("RECIPIENT".equals(stage) && teacherId != null && v.equals(teacherId))) {
                    pick = q;
                }
            }
            c = send(id, pick.path("label").asText(), pick.hasNonNull("value") ? pick.path("value").asText() : null);
        }
        return c;
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper();
    }
}
