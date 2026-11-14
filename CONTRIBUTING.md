# Contributing

During private development, discuss scope with the maintainer before large changes. Contributions and reuse follow the MIT license; public contributions begin when the repository is published.

Use Java 21 and run `./mvnw verify`. Keep endpoint changes compatible with `/api/v1` or introduce a versioned contract. Add Flyway migrations rather than modifying applied migration files. Include tenant authorization and concurrent-write tests when changing booking rules. Use synthetic data only and never commit `.env`, access tokens, employer code or real customer records.

PRs should explain the concrete behaviour changed and relevant verification. Keep external effects out of booking transactions; use a durable outbox when implementing integrations.
