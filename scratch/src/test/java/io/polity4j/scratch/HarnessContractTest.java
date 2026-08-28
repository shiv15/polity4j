package io.polity4j.scratch;

import io.polity4j.adapters.openai.OpenAiAdapter;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessContractTest {

    private static final LlmRequest REQUEST = LlmRequest.builder("Test prompt", "gpt-4o").build();

    @Test
    @DisplayName("Contract Test 6a: Transient-only Parity (RATE_LIMITED -> RATE_LIMITED -> SUCCESS)")
    void testTransientOnlyParity() {
        FaultProfile profile = FaultProfile.of(
                FaultType.RATE_LIMITED,
                FaultType.RATE_LIMITED,
                FaultType.SUCCESS
        );

        // 1. Polity4j In-Process
        AttemptRecorder recorderPolityInProcess = new AttemptRecorder();
        InProcessFakeBackend fakeBackendPolity = new InProcessFakeBackend(profile, recorderPolityInProcess);
        PolityHarness polityHarness = new PolityHarness();

        LlmResponse polityResp = polityHarness.execute(REQUEST, fakeBackendPolity);
        assertThat(polityResp.content()).contains("InProcess Success");
        assertThat(recorderPolityInProcess.totalAttempts()).isEqualTo(3);
        assertThat(recorderPolityInProcess.endedWithSuccess()).isTrue();

        // 2. Polity4j WireMock
        AttemptRecorder recorderPolityWireMock = new AttemptRecorder();
        try (WireMockBackend wireMockBackend = new WireMockBackend(profile, recorderPolityWireMock)) {
            OpenAiAdapter openAiAdapter = new OpenAiAdapter(
                    HttpClient.newHttpClient(),
                    "demo-key",
                    wireMockBackend.baseUrl() + "/v1/chat/completions"
            );
            LlmResponse wireMockPolityResp = polityHarness.execute(REQUEST, openAiAdapter::call);
            assertThat(wireMockPolityResp.content()).contains("WireMock OpenAI Success");
            assertThat(recorderPolityWireMock.totalAttempts()).isEqualTo(3);
            assertThat(recorderPolityWireMock.endedWithSuccess()).isTrue();
        }

        // 3. Resilience4j In-Process
        AttemptRecorder recorderR4jInProcess = new AttemptRecorder();
        InProcessFakeBackend fakeBackendR4j = new InProcessFakeBackend(profile, recorderR4jInProcess);
        Resilience4jHarness r4jHarness = new Resilience4jHarness();

        String r4jResp = r4jHarness.execute(fakeBackendR4j);
        assertThat(r4jResp).contains("InProcess Success");
        assertThat(recorderR4jInProcess.totalAttempts()).isEqualTo(3);
        assertThat(recorderR4jInProcess.endedWithSuccess()).isTrue();

        // 4. Resilience4j WireMock
        AttemptRecorder recorderR4jWireMock = new AttemptRecorder();
        try (WireMockBackend wireMockBackend = new WireMockBackend(profile, recorderR4jWireMock)) {
            OpenAiAdapter openAiAdapter = new OpenAiAdapter(
                    HttpClient.newHttpClient(),
                    "demo-key",
                    wireMockBackend.baseUrl() + "/v1/chat/completions"
            );
            String r4jWireMockResp = r4jHarness.execute(() -> openAiAdapter.call(REQUEST).content());
            assertThat(r4jWireMockResp).contains("WireMock OpenAI Success");
            assertThat(recorderR4jWireMock.totalAttempts()).isEqualTo(3);
            assertThat(recorderR4jWireMock.endedWithSuccess()).isTrue();
        }

        // 5. LangChain4j WireMock
        AttemptRecorder recorderLangChain = new AttemptRecorder();
        try (WireMockBackend wireMockBackend = new WireMockBackend(profile, recorderLangChain)) {
            LangChain4jHarness langChainHarness = new LangChain4jHarness(wireMockBackend.baseUrl() + "/v1");
            String lcResp = langChainHarness.execute("Test prompt");
            assertThat(lcResp).contains("WireMock OpenAI Success");
            assertThat(recorderLangChain.totalAttempts()).isEqualTo(3);
            assertThat(recorderLangChain.endedWithSuccess()).isTrue();
        }
    }

    @Test
    @DisplayName("Contract Test 6b: Mixed Transient + Permanent (RATE_LIMITED -> PERMANENT_4XX -> SUCCESS)")
    void testMixedTransientAndPermanent() {
        FaultProfile profile = FaultProfile.of(
                FaultType.RATE_LIMITED,
                FaultType.PERMANENT_4XX,
                FaultType.SUCCESS
        );

        // 1. Polity4j In-Process
        AttemptRecorder recorderPolity = new AttemptRecorder();
        InProcessFakeBackend fakeBackendPolity = new InProcessFakeBackend(profile, recorderPolity);
        PolityHarness polityHarness = new PolityHarness();

        System.out.println("--- Contract Test 6b: Executing Polity4j ---");
        assertThatThrownBy(() -> polityHarness.execute(REQUEST, fakeBackendPolity))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Permanent 4XX error");

        System.out.println("Polity4j total attempts made: " + recorderPolity.totalAttempts());
        System.out.println("Polity4j recorded attempts: " + recorderPolity.getRecords());
        // Expectation per FINDINGS.md: Polity4j stops after attempt 2 (PERMANENT_4XX)
        assertThat(recorderPolity.totalAttempts()).isEqualTo(2);
        assertThat(recorderPolity.endedWithSuccess()).isFalse();

        // 2. Resilience4j In-Process (configured with retryOnException matching ANY exception)
        AttemptRecorder recorderR4j = new AttemptRecorder();
        InProcessFakeBackend fakeBackendR4j = new InProcessFakeBackend(profile, recorderR4j);
        Resilience4jHarness r4jHarness = new Resilience4jHarness();

        System.out.println("\n--- Contract Test 6b: Executing Resilience4j ---");
        String r4jResult = r4jHarness.execute(fakeBackendR4j);
        System.out.println("Resilience4j total attempts made: " + recorderR4j.totalAttempts());
        System.out.println("Resilience4j recorded attempts: " + recorderR4j.getRecords());
        System.out.println("Resilience4j final result: " + r4jResult);

        // Expectation: Resilience4j retries the PERMANENT_4XX and reaches attempt 3 (SUCCESS)
        assertThat(recorderR4j.totalAttempts()).isEqualTo(3);
        assertThat(recorderR4j.endedWithSuccess()).isTrue();
    }
}
