# Budget Guardrails

The `polity4j-cost` module provides simple budget guardrails to prevent excessive spending when making AI calls. It checks cumulative spend at the call, caller, and organization levels.

## Configuration

You can configure budget limits using `BudgetConfig`. All values are specified as `BigDecimal`. If a limit is not needed, set it to `null`.

```java
import io.polity4j.cost.BudgetConfig;
import java.math.BigDecimal;

BudgetConfig config = new BudgetConfig(
    new BigDecimal("0.50"),  // Max $0.50 per call (checked post-facto)
    new BigDecimal("5.00"),  // Max $5.00 cumulative per caller (requires LlmRequest.callerId())
    new BigDecimal("100.00") // Max $100.00 cumulative for the entire organization
);
```

## How It Works

The `BudgetGuardrailModule` acts as a Pipeline Module intercepting `LlmRequest` calls:

1. **Pre-flight Check**: Before a request is made, the module checks if the cumulative spend tracker for the organization or caller has already reached or exceeded the ceiling limit. If so, a `BudgetExceededException` is thrown, blocking the request from going out.
2. **Execution**: The pipeline proceeds and the AI provider returns a response with its actual cost.
3. **Post-flight Reconciliation**: Once the request succeeds, the cost returned by the LLM (`response.estimatedCost()`) is atomically added to the `spent` pool.

## Event Hooks

You can optionally listen for budget breaches using the `BudgetEventListener`:

```java
import io.polity4j.cost.BudgetEventListener;
import io.polity4j.core.LlmRequest;
import java.math.BigDecimal;

BudgetEventListener listener = (LlmRequest request, String scope, BigDecimal limit, BigDecimal spent) -> {
    System.out.println("ALERT: " + scope + " budget breached! Spent: $" + spent + ", Limit: $" + limit);
};
```

## Integrating into the Pipeline

```java
import io.polity4j.cost.BudgetGuardrailModule;

BudgetGuardrailModule budgetModule = new BudgetGuardrailModule(config, listener);

// Add to your pipeline builder
```

## In-Memory Limitations

Currently, the caller and organization spending trackers are held in memory. For multi-instance SaaS clusters, you should implement a distributed budget tracker (e.g., using Redis) by integrating with the `BudgetGuardrailModule` concepts, or wrapping the pipeline with a custom module.
