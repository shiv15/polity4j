package io.polity4j.cost.router;

import io.polity4j.core.LlmRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicComplexityScorerTest {

    private final HeuristicComplexityScorer scorer = new HeuristicComplexityScorer();

    @Test
    void shortSimplePromptScoresLow() {
        var request = LlmRequest.builder("What is 2+2?", "gpt-4o").build();
        assertThat(scorer.score(request)).isLessThan(0.3);
    }

    @Test
    void promptWithCodeBlockScoresHigher() {
        var withCode = LlmRequest.builder(
                "Fix this: ```public void foo() { return; }```", "gpt-4o").build();
        var withoutCode = LlmRequest.builder(
                "Fix this sentence please", "gpt-4o").build();

        assertThat(scorer.score(withCode)).isGreaterThan(scorer.score(withoutCode));
    }

    @Test
    void longPromptScoresHigherThanShortPrompt() {
        String longPrompt = "explain ".repeat(300);
        var longRequest = LlmRequest.builder(longPrompt, "gpt-4o").build();
        var shortRequest = LlmRequest.builder("hi", "gpt-4o").build();

        assertThat(scorer.score(longRequest)).isGreaterThan(scorer.score(shortRequest));
    }

    @Test
    void deepConversationHistoryIncreasesScore() {
        var deepHistory = List.of(
                new LlmRequest.Message("user", "1"),
                new LlmRequest.Message("assistant", "2"),
                new LlmRequest.Message("user", "3"),
                new LlmRequest.Message("assistant", "4"),
                new LlmRequest.Message("user", "5"),
                new LlmRequest.Message("assistant", "6"));

        var withHistory = LlmRequest.builder("continue", "gpt-4o")
                .conversationHistory(deepHistory).build();
        var withoutHistory = LlmRequest.builder("continue", "gpt-4o").build();

        assertThat(scorer.score(withHistory))
                .isGreaterThan(scorer.score(withoutHistory));
    }

    @Test
    void multipleQuestionsIncreaseScore() {
        var multiQuestion = LlmRequest.builder(
                "What is this? Why does it happen? How do I fix it?",
                "gpt-4o").build();
        var singleQuestion = LlmRequest.builder(
                "What is this?", "gpt-4o").build();

        assertThat(scorer.score(multiQuestion))
                .isGreaterThan(scorer.score(singleQuestion));
    }

    @Test
    void scoreIsAlwaysBetweenZeroAndOne() {
        // Stress every signal simultaneously
        String hugePrompt = ("```code``` ".repeat(500)) + "? ? ? ? ? ?";
        var history = List.of(
                new LlmRequest.Message("user", "1"),
                new LlmRequest.Message("assistant", "2"),
                new LlmRequest.Message("user", "3"),
                new LlmRequest.Message("assistant", "4"),
                new LlmRequest.Message("user", "5"),
                new LlmRequest.Message("assistant", "6"),
                new LlmRequest.Message("user", "7"),
                new LlmRequest.Message("assistant", "8"),
                new LlmRequest.Message("user", "9"),
                new LlmRequest.Message("assistant", "10"),
                new LlmRequest.Message("user", "11"),
                new LlmRequest.Message("assistant", "12"));

        var request = LlmRequest.builder(hugePrompt, "gpt-4o")
                .conversationHistory(history).build();

        double score = scorer.score(request);
        assertThat(score).isLessThanOrEqualTo(1.0);
        assertThat(score).isGreaterThanOrEqualTo(0.0);
    }
}
