import React from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import CodeBlock from '@theme/CodeBlock';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className="hero-banner">
      <div className="hero-content">
        <h1 className="hero-title">{siteConfig.title}</h1>
        <p className="hero-subtitle">{siteConfig.tagline}</p>
        <div className="hero-buttons">
          <Link className="btn-primary" to="/docs/getting-started">
            Get Started
          </Link>
          <Link className="btn-secondary" to="https://github.com/shiv15/polity4j">
            GitHub
          </Link>
        </div>
      </div>
    </header>
  );
}

const javaPipelineSnippet = `LlmPipeline pipeline = LlmPipeline.builder(primaryClient)
    // 1. Retry up to 3 times on rate limit or overload errors
    .with(new RetryModule(RetryConfig.builder()
        .maxAttempts(3)
        .initialDelay(Duration.ofMillis(500))
        .build()))
    // 2. Open circuit after 5 consecutive provider failures
    .with(new CircuitBreakerModule("openai", CircuitBreakerConfig.builder()
        .failureThreshold(5)
        .cooldownDuration(Duration.ofSeconds(30))
        .build()))
    // 3. Roll over to secondary client if primary is down
    .with(new FallbackChainModule(List.of(fallbackClient)))
    .build();`;

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title="Decoupled LLM Reliability for Java"
      description="Polity4j is a lightweight, zero-dependency reliability orchestration framework for LLM integrations in Java 17+.">
      <HomepageHeader />
      <main>
        <section className="features-section">
          <div className="features-grid">
            <div className="feature-card">
              <span className="feature-icon">🔁</span>
              <h3 className="feature-title">Resilient Retries</h3>
              <p className="feature-desc">
                Handle rate limits (429) and overloaded states (529) seamlessly. 
                Configure custom initial delays, maximum delays, and exponential backoff multipliers.
              </p>
            </div>
            <div className="feature-card">
              <span className="feature-icon">🛡️</span>
              <h3 className="feature-title">Circuit Breaking</h3>
              <p className="feature-desc">
                Prevent cascading failures by failing fast when a provider is down. 
                Features atomic thread-safe state machine transitions and customizable cooldown probes.
              </p>
            </div>
            <div className="feature-card">
              <span className="feature-icon">🔀</span>
              <h3 className="feature-title">Fallback Chains</h3>
              <p className="feature-desc">
                Route requests to alternative models or providers when primary endpoints fail. 
                Complete cause-nesting records the failure history of the entire chain.
              </p>
            </div>
          </div>
        </section>

        <section className="code-section">
          <div className="code-container">
            <h2 className="code-title">Resilience Made Expressive</h2>
            <CodeBlock language="java" title="ResiliencePipeline.java">
              {javaPipelineSnippet}
            </CodeBlock>
          </div>
        </section>
      </main>
    </Layout>
  );
}
