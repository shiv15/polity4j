# Prompt Optimizer

The `PromptOptimizerModule` is a zero-dependency, lightweight pipeline module in the `polity4j-quality` module. It trims, cleans, and structures requests before they are sent to the LLM provider to minimize prompt waste and prevent context window exceptions.

## Features

1. **Whitespace Normalization**: Trims leading and trailing whitespace from the prompt, and collapses three or more consecutive newlines into exactly two. This prevents formatting bloat from empty lines in templates.
2. **Conversation Deduplication**: Analyzes the conversation history list. If a message (same role and content) is found multiple times, the framework keeps the **latest** occurrence and discards the earlier one.
3. **Context Truncation**: Truncates history messages from the front (oldest first) if the total prompt size exceeds the `maxPromptChars` threshold. The system always prioritizes preserving the newest prompt context.

---

## Conversation Deduplication Behavior

> [!NOTE]
> Unlike basic deduplicators that keep the *first* (earliest) occurrence, Polity4j keeps the **latest** occurrence.
>
> If the history is:
> 1. `User: hello` (Turn 1)
> 2. `Assistant: hi` (Turn 2)
> 3. `User: hello` (Turn 3)
>
> Keeping the *first* would result in `[User: hello, Assistant: hi]`, leaving the conversation ending on an assistant turn.
>
> Keeping the *latest* yields `[Assistant: hi, User: hello]`, which preserves the chronological flow and correctly maintains the alternating user/assistant message structure.

---

## Configuration

All optimizations in the `PromptOptimizerModule` are **opt-in** by default. Use `PromptOptimizerConfig` to select features:

```java
import io.polity4j.quality.prompt.PromptOptimizerConfig;
import io.polity4j.quality.prompt.PromptOptimizerModule;

PromptOptimizerConfig config = PromptOptimizerConfig.builder()
    .normalizeWhitespace(true)  // Collape redundant lines
    .deduplicateHistory(true)   // Keep latest unique messages
    .maxPromptChars(2000)       // Truncate oldest history beyond 2k characters
    .build();

PromptOptimizerModule optimizerModule = new PromptOptimizerModule(config);
```

## No-Allocation Passthrough

If the request requires no modifications (for example, whitespace is already clean, history has no duplicates, and character count is under the limit), the module passes the original request reference downstream unchanged. This ensures **zero allocation overhead** for clean requests.
