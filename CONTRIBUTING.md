# Contributing to Polity4j

First off, thank you for considering contributing to Polity4j! It's people like you that make the open source community such an amazing place to learn, inspire, and create.

To help you get started, please read through the guidelines below.

---

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](./CODE_OF_CONDUCT.md).

---

## How Can I Contribute?

### Reporting Bugs
If you find a bug, please open an Issue. Include:
- A clear, descriptive title.
- Steps to reproduce.
- Expected vs. actual behavior.
- Relevant log files, stack traces, and environment details (JDK version, Maven version, OS).

### Suggesting Enhancements
We welcome ideas for new features or improvements. When opening an Issue for enhancements:
- Explain the use case and why the enhancement is needed.
- Describe your proposed solution if you have one.

### Pull Requests
Ready to make a change? Here is how to submit a PR:

1. **Fork the repository** and clone your fork locally.
2. **Create a branch** for your feature or fix (e.g., `feature/custom-backoff` or `bugfix/rate-limit-parse`).
3. **Write code and tests**:
   - Keep classes stateless where possible (core design principle).
   - Ensure all public APIs include Javadocs.
   - Enforce parameter validations (checks against `null` and out-of-bounds inputs).
   - Map all provider exceptions to standard `PolityException` hierarchy subtypes.
4. **Run the tests**:
   Make sure the Maven build is fully green before submitting your PR:
   ```bash
   mvn clean test
   ```
5. **Commit your changes**:
   Write clear, semantic commit messages (e.g. `feat(reliability): add backoff jitter option`).
6. **Push to your fork** and submit a Pull Request.

---

## Development Standards

- **Java Version**: Code must target Java 17 and should not use preview features to maintain compatibility.
- **Dependencies**: Keep the core modules dependencies-free (`pom.xml` should only have test-scoped dependencies like JUnit 5 and AssertJ).
- **Thread Safety**: All stateful pipeline modules (e.g., caches, circuit breakers) must be thread-safe.
