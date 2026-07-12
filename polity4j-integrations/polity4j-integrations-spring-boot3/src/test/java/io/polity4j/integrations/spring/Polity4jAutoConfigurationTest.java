package io.polity4j.integrations.spring;

import io.polity4j.core.LlmClient;
import io.polity4j.core.LlmPipeline;
import io.polity4j.core.LlmRequest;
import io.polity4j.core.LlmResponse;
import io.polity4j.core.PipelineModule;
import io.polity4j.core.exception.PolityException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class Polity4jAutoConfigurationTest {

    private static final LlmClient STUB_CLIENT = new LlmClient() {
        @Override
        public LlmResponse call(LlmRequest request) {
            return LlmResponse.builder("ok", request.model(), "stub")
                    .estimatedCost(BigDecimal.ZERO)
                    .build();
        }
        @Override
        public String provider() { return "stub"; }
    };

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            Polity4jAutoConfiguration.class));

    @Test
    void doesNotConfigureWithoutLlmClient() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(LlmPipeline.class);
        });
    }

    @Test
    void configuresPipelineWhenLlmClientPresent() {
        runner.withBean(LlmClient.class, () -> STUB_CLIENT)
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmPipeline.class);
                });
    }

    @Test
    void configuresAspectWhenLlmClientPresent() {
        runner.withBean(LlmClient.class, () -> STUB_CLIENT)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(Polity4jGuardedAspect.class);
                });
    }

    @Test
    void picksUpPipelineModuleBeans() {
        var callOrder = new ArrayList<String>();

        PipelineModule moduleA = new PipelineModule() {
            @Override
            public LlmResponse process(LlmRequest request,
                                       io.polity4j.core.PipelineChain next)
                    throws PolityException {
                callOrder.add("A");
                return next.proceed(request);
            }
            @Override
            public String name() { return "module-a"; }
        };

        runner.withBean(LlmClient.class, () -> STUB_CLIENT)
                .withBean("moduleA", PipelineModule.class, () -> moduleA)
                .run(context -> {
                    LlmPipeline pipeline =
                            context.getBean(LlmPipeline.class);
                    pipeline.execute(
                            LlmRequest.builder("hello", "gpt-4o").build());
                    assertThat(callOrder).containsExactly("A");
                });
    }

    @Test
    void backsOffWhenUserDefinesPipeline() {
        LlmPipeline customPipeline =
                LlmPipeline.builder(STUB_CLIENT).build();

        runner.withBean(LlmClient.class, () -> STUB_CLIENT)
                .withBean(LlmPipeline.class, () -> customPipeline)
                .run(context -> {
                    assertThat(context.getBean(LlmPipeline.class))
                            .isSameAs(customPipeline);
                });
    }
}
