package io.polity4j.scratch;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;

import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * WireMock HTTP backend mapping FaultType to OpenAI-compatible HTTP responses
 * and logging every incoming attempt to an AttemptRecorder.
 */
public final class WireMockBackend implements AutoCloseable {

    private final WireMockServer wireMockServer;
    private final FaultProfile faultProfile;
    private final AttemptRecorder recorder;
    private final AtomicInteger attemptCount = new AtomicInteger(0);

    public WireMockBackend(FaultProfile faultProfile, AttemptRecorder recorder) {
        this.faultProfile = faultProfile;
        this.recorder = recorder;

        ResponseTransformer faultTransformer = new ResponseTransformer() {
            @Override
            public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
                return handleRequest();
            }

            @Override
            public String getName() {
                return "fault-injector";
            }

            @Override
            public boolean applyGlobally() {
                return false;
            }
        };

        this.wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
                .dynamicPort()
                .extensions(faultTransformer));
        this.wireMockServer.start();

        setupStubs();
    }

    public String baseUrl() {
        return wireMockServer.baseUrl();
    }

    public int port() {
        return wireMockServer.port();
    }

    public int getAttemptCount() {
        return attemptCount.get();
    }

    private void setupStubs() {
        wireMockServer.stubFor(post(urlPathMatching("/.*"))
                .willReturn(aResponse()
                        .withTransformers("fault-injector")));
    }

    private Response handleRequest() {
        int attempt = attemptCount.incrementAndGet();
        FaultType fault = faultProfile.getFaultForAttempt(attempt);
        boolean isTerminal = (fault == FaultType.SUCCESS);

        recorder.recordAttempt(attempt, fault, isTerminal);

        HttpHeaders headers = new HttpHeaders(new HttpHeader("Content-Type", "application/json"));

        switch (fault) {
            case RATE_LIMITED:
                return Response.response()
                        .status(429)
                        .headers(headers)
                        .body("{\"error\": {\"message\": \"Rate limit exceeded\", \"type\": \"requests\"}}")
                        .build();

            case OVERLOADED:
                return Response.response()
                        .status(503)
                        .headers(headers)
                        .body("{\"error\": {\"message\": \"Engine overloaded\", \"type\": \"server_error\"}}")
                        .build();

            case TRANSIENT_5XX:
                return Response.response()
                        .status(500)
                        .headers(headers)
                        .body("{\"error\": {\"message\": \"Internal server error\", \"type\": \"server_error\"}}")
                        .build();

            case PERMANENT_4XX:
                return Response.response()
                        .status(400)
                        .headers(headers)
                        .body("{\"error\": {\"message\": \"Invalid request payload\", \"type\": \"invalid_request_error\"}}")
                        .build();

            case MALFORMED_RESPONSE:
                return Response.response()
                        .status(200)
                        .headers(headers)
                        .body("{\"id\": \"chatcmpl-123\", \"choices\": [{ \"message\": ") // broken JSON
                        .build();

            case SUCCESS:
            default:
                return Response.response()
                        .status(200)
                        .headers(headers)
                        .body("{\n" +
                                "  \"id\": \"chatcmpl-123\",\n" +
                                "  \"object\": \"chat.completion\",\n" +
                                "  \"created\": 1677652288,\n" +
                                "  \"model\": \"gpt-4o\",\n" +
                                "  \"choices\": [{\n" +
                                "    \"index\": 0,\n" +
                                "    \"message\": {\n" +
                                "      \"role\": \"assistant\",\n" +
                                "      \"content\": \"WireMock OpenAI Success\"\n" +
                                "    },\n" +
                                "    \"finish_reason\": \"stop\"\n" +
                                "  }],\n" +
                                "  \"usage\": {\n" +
                                "    \"prompt_tokens\": 9,\n" +
                                "    \"completion_tokens\": 12,\n" +
                                "    \"total_tokens\": 21\n" +
                                "  }\n" +
                                "}")
                        .build();
        }
    }

    public void reset() {
        attemptCount.set(0);
        wireMockServer.resetAll();
        setupStubs();
    }

    @Override
    public void close() {
        wireMockServer.stop();
    }
}
