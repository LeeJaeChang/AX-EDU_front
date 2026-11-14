package com.axedu.service.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.axedu.common.exception.ApiException;
import com.axedu.common.exception.ErrorCode;
import com.axedu.config.AppProperties;
import com.axedu.dto.law.LawSearchRequest;
import com.axedu.dto.law.LawSearchResult;
import com.sun.net.httpserver.HttpServer;

/** 가짜 파이썬 서버(JDK HttpServer)로 LawSearchClient 의 요청·응답·오류 처리를 확인한다 */
class LawSearchClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastContentLength = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private LawSearchClient clientFor(int status, String json) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", ex -> {
            lastContentLength.set(ex.getRequestHeaders().getFirst("Content-Length"));
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return client("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static LawSearchClient client(String baseUrl) {
        AppProperties props = new AppProperties(null, null, new AppProperties.LawSearch(baseUrl, 2000), null);
        return new LawSearchClient(RestClient.builder(), props);
    }

    @Test
    void parsesResultsAndSendsKeywords() throws IOException {
        LawSearchClient c = clientFor(200, """
                {"keywords":["휴대전화","압수"],"count":1,"tookMs":3.1,"results":[{
                  "chunkId":"admrul-1-7","type":"행정규칙","title":"교원의 학생생활지도에 관한 고시","article":"제8조",
                  "articleTitle":"그 밖의 분야","caseNo":null,"org":null,"lawKind":"고시","effectiveDate":"20260301",
                  "sourceUrl":null,"text":"4. 휴대전화 등 스마트기기의 올바른 사용","snippet":"휴대전화 등",
                  "matchedKeywords":["휴대전화"],"score":12.5,"extraField":"무시됨"}]}
                """);

        List<LawSearchResult> res = c.search(List.of("휴대전화", "압수"), 9);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).title()).isEqualTo("교원의 학생생활지도에 관한 고시");
        assertThat(res.get(0).matchedKeywords()).containsExactly("휴대전화");
        assertThat(res.get(0).score()).isEqualTo(12.5);
        assertThat(lastBody.get()).contains("\"keywords\":[\"휴대전화\",\"압수\"]").contains("\"limit\":9").contains("\"perType\":3");
        // 파이썬 http.server 는 chunked 본문을 못 읽으므로 Content-Length 가 꼭 있어야 한다
        assertThat(lastContentLength.get()).isEqualTo(String.valueOf(lastBody.get().getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void badRequestBecomesValidationError() throws IOException {
        LawSearchClient c = clientFor(400, "{\"error\":\"types 는 [...] 중에서 고르세요.\"}");

        assertThatThrownBy(() -> c.search(new LawSearchRequest(List.of("a"), 5, List.of("뉴스"), null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("types")
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void serverErrorBecomesLawSearchException() throws IOException {
        LawSearchClient c = clientFor(500, "{}");

        assertThatThrownBy(() -> c.search(List.of("체벌"), 5)).isInstanceOf(LawSearchException.class);
    }

    @Test
    void serverDownBecomesLawSearchException() throws IOException {
        int freePort;
        try (ServerSocket s = new ServerSocket(0)) {
            freePort = s.getLocalPort();
        }
        LawSearchClient c = client("http://127.0.0.1:" + freePort);

        assertThatThrownBy(() -> c.search(List.of("체벌"), 5))
                .isInstanceOf(LawSearchException.class)
                .hasMessageContaining("연결할 수 없습니다");
    }

    @Test
    void blankKeywordsRejectedWithoutCallingServer() {
        LawSearchClient c = client("http://127.0.0.1:1");

        assertThatThrownBy(() -> c.search(List.of(" ", ""), 5))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
