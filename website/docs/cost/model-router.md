# Model Router

The `ModelRouterModule` dynamically overrides the requested model in an `LlmRequest` based on a complexity score, routing simple requests to cheaper models (e.g. Claude Haiku) and complex requests to premium models (e.g. Claude Sonnet).

## How It Works

1. **Scoring**: The request is analyzed by a `ComplexityScorer`, which outputs a score between `0.0` (extremely simple) and `1.0` (highly complex).
2. **Policy Evaluation**: The `RoutingPolicy` checks the complexity score against a configured `threshold`:
   - Score **below** threshold → routes to `cheapModel`.
   - Score **at or above** threshold → routes to `expensiveModel`.
3. **Rewriting**: If the routed model differs from the originally requested model, the module rewrites `LlmRequest.model()` before passing the request to the rest of the pipeline.

---

## ⚠️ Important Pipeline Ordering Rule

Because the Model Router rewrites the model parameter on the request, **the `ModelRouterModule` must be placed BEFORE the Cache Module (`ExactCacheModule`) in the pipeline builder**.

If placed after the Cache Module:
- The cache key hash will be computed using the *original* requested model, not the *routed* model.
- This results in cache hits responding with the wrong model, or cache misses executing the routed model but caching it under the wrong key.

**Correct Pipeline Setup**:
```
Request → ModelRouterModule → ExactCacheModule → LlmClient
```

---

## Configuration

### 1. Define Routing Policy

```java
import io.polity4j.cost.router.RoutingPolicy;

RoutingPolicy policy = RoutingPolicy.builder()
    .threshold(0.5) // Boundary threshold
    .cheapModel("claude-3-haiku-20240307")
    .expensiveModel("claude-3-5-sonnet-20241022")
    .build();
```

### 2. Set Up Module

```java
import io.polity4j.cost.router.ModelRouterModule;
import io.polity4j.cost.router.RouterEventListener;

RouterEventListener listener = (original, routed, score) -> {
    System.out.println("Routed from " + original.model() + " -> " + routed.model() + " (Score: " + score + ")");
};

// Uses HeuristicComplexityScorer by default
ModelRouterModule router = new ModelRouterModule(policy);

// Or with custom scorer & listener:
ModelRouterModule customRouter = new ModelRouterModule(new MyCustomScorer(), policy, listener);
```

## Complexity Scorers

### Default: `HeuristicComplexityScorer`

Polity4j provides a zero-dependency `HeuristicComplexityScorer` that evaluates text signals to calculate complexity:

- **Prompt Length**: Longer prompts contribute up to `0.4` to the score, saturating at 2,000 characters.
- **Code Blocks**: Prompt containing code blocks (Markdown backticks) adds `0.3` to the score.
- **Conversation Depth**: Deep conversation context (history size) contributes up to `0.2` (saturating at 10 turns).
- **Multiple Questions**: Multiple question marks in the prompt contribute up to `0.1` (saturating at 3 question marks).

### Custom Complexity Scorer

You can implement a custom `ComplexityScorer` (for example, using LLM-based intent classifiers or vector embeddings) by implementing the functional interface:

```java
import io.polity4j.cost.router.ComplexityScorer;
import io.polity4j.core.LlmRequest;

public class MyCustomScorer implements ComplexityScorer {
    @Override
    public double score(LlmRequest request) {
        // Implement ML, keyword, or rule-based scoring logic
        return 0.8; 
    }
}
```
