package io.polity4j.cost.router;

import io.polity4j.core.LlmRequest;

import java.util.regex.Pattern;

/**
 * Zero-dependency complexity scorer based on simple text signals.
 *
 * This is intentionally crude — it trades accuracy for being
 * immediately usable with no external dependencies. Signals used:
 *
 *   - Prompt length        — longer prompts tend to be more complex
 *   - Code block presence  — code-related requests are usually complex
 *   - Conversation depth   — deep conversations carry more context to reason over
 *   - Question markers     — multiple questions in one prompt suggest complexity
 *
 * Each signal contributes a weighted amount to the final score,
 * capped at 1.0.
 *
 * This is a starting point, not a finished product. Users who need
 * more accuracy should implement ComplexityScorer with an embedding
 * or ML-based approach — the router does not care how the score
 * is produced.
 */
public final class HeuristicComplexityScorer implements ComplexityScorer {

    private static final Pattern CODE_BLOCK = Pattern.compile("```|`[^`]+`");
    private static final Pattern QUESTION_MARK = Pattern.compile("\\?");

    // Weights — tunable, sum does not need to equal 1.0 since
    // the final score is capped
    private static final double LENGTH_WEIGHT = 0.4;
    private static final double CODE_WEIGHT = 0.3;
    private static final double HISTORY_WEIGHT = 0.2;
    private static final double MULTI_QUESTION_WEIGHT = 0.1;

    // Prompt length beyond this many characters is considered
    // maximally complex for the length signal
    private static final int LENGTH_SATURATION_CHARS = 2000;

    @Override
    public double score(LlmRequest request) {
        double score = 0.0;

        score += LENGTH_WEIGHT * lengthSignal(request.prompt());
        score += CODE_WEIGHT * codeSignal(request.prompt());
        score += HISTORY_WEIGHT * historySignal(request);
        score += MULTI_QUESTION_WEIGHT * multiQuestionSignal(request.prompt());

        return Math.min(score, 1.0);
    }

    private double lengthSignal(String prompt) {
        return Math.min(
                (double) prompt.length() / LENGTH_SATURATION_CHARS, 1.0);
    }

    private double codeSignal(String prompt) {
        return CODE_BLOCK.matcher(prompt).find() ? 1.0 : 0.0;
    }

    private double historySignal(LlmRequest request) {
        int historySize = request.conversationHistory().size();
        // Saturate at 10 turns — beyond that, treat as maximally complex
        return Math.min(historySize / 10.0, 1.0);
    }

    private double multiQuestionSignal(String prompt) {
        long questionCount = QUESTION_MARK.matcher(prompt).results().count();
        // 1 question = simple, 3+ questions = complex
        return Math.min(questionCount / 3.0, 1.0);
    }
}
