 # AGENTS.md

 Guidance for code agents and contributors working on this repository.

 ## Project Overview
 - Stack: Java 21, Spring Boot 3, Maven.
 - NLP: Stanford CoreNLP (pipeline initialized once in `NlpService`).
 - API: `POST /api/parse` accepts `{ text: string }` and returns intent, amount, currency, recipient.
 - Docs: Swagger UI at `/swagger-ui.html` when the app is running.

 ## Run, Build, Test
 - Run (dev): `mvn spring-boot:run`
 - Package: `mvn -q -DskipTests package`
 - Tests: `mvn test`
 - Formatting: `mvn spotless:apply` (Google Java Format)

 ## Code Conventions
 - Keep changes minimal and scoped to the task.
 - Follow existing structure and naming; do not introduce new modules unless required.
 - Formatting is enforced via Spotless; run it before submitting changes.
 - Prefer adding unit tests under `src/test/java` when modifying logic in `NlpService` or APIs.

 ## API Compatibility
 - Endpoint: `POST /api/parse` in `ParseController`.
 - Request model: `ParseRequest(text: String)`.
 - Response model: `ParseResponse(intent, amountText, amountValue, currency, recipient)`.
 - Backward compatibility: avoid breaking field names or types; update tests and docs if changes are intentional.

 ## Performance Notes
 - CoreNLP models are large; avoid creating new pipelines inside request handlers.
 - If extending NLP behavior, prefer adding normalization or lightweight heuristics before heavier passes.

 ## Common Tasks
 - Add a new currency keyword/symbol: update normalization in `NlpService` (see `mapCurrencySymbol` and `mapCurrencyWord`).
 - Improve recipient parsing: adjust `extractRecipientByPreposition` or dependency-graph logic near root verb.
 - Expand intents: extend `INTENT_MAP` and tests.

 ## Operational Notes
 - Default port: 8080.
 - Memory: allocate ~2 GB heap for comfortable local runs due to CoreNLP models.
 - Logging: default Spring Boot logging; no external APM.

 ## Contribution Etiquette
 - Don’t commit unrelated refactors.
 - Keep PRs small and focused; update README and Swagger annotations where relevant.
 - Do not add license headers unless explicitly requested.

