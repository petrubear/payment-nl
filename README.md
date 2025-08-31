 # Payment NLP API

 A small Spring Boot service that extracts intent, amount, currency, and recipient from short natural‑language payment requests in English and Spanish.

 - Intent detection: pay, send, transfer (with Spanish verbs pagar, enviar, transferir)
 - Amount parsing: symbols (e.g., $15, €1,234.50) and word forms (e.g., 20 dollars, 15 euros)
 - Currency normalization: USD, EUR, GBP, JPY, INR, CHF, MXN, COP, ARS, CLP, PEN, BRL, CAD, AUD
 - Recipient extraction: names, @handles, short noun phrases (e.g., “to the coffee shop”)

 ## Requirements
 - JDK 21
 - Maven 3.9+
 - Memory: CoreNLP models are large; allocate ~2 GB heap for comfortable local runs

 ## Quick Start

 - Build: `mvn -q -DskipTests package`
 - Run (dev): `mvn spring-boot:run`
 - Run (jar): `java -jar target/payment-nlp-0.1.0.jar`
 - API docs: open `http://localhost:8080/swagger-ui.html`

 ## API
 - POST `/api/parse`
 - Request body:
   `{ "text": "could you please send $15 to gaby?" }`
 - Response body (example):
   `{ "intent": "send", "amountText": "$15", "amountValue": 15.0, "currency": "USD", "recipient": "gaby" }`

 Example curl:
 ```
 curl -s \
   -H 'Content-Type: application/json' \
   -d '{"text":"transfer €1,234.50 to @alex99"}' \
   http://localhost:8080/api/parse | jq
 ```

 ## Development
 - Tests: `mvn test`
 - Formatting: `mvn spotless:apply` (Google Java Format)
 - Main entry: `src/main/java/app/PaymentNlpApplication.java`
 - Core logic: `src/main/java/app/nlp/NlpService.java`
 - REST API: `src/main/java/app/api/ParseController.java`

 ## Notes
 - The NLP pipeline is initialized once (singleton Spring `@Service`) because CoreNLP startup is expensive.
 - Spanish support covers common verbs and currency words; extend intent and currency maps in `NlpService` as needed.

 ## License
 Unspecified (see `OpenApiConfig` for the API info block).

