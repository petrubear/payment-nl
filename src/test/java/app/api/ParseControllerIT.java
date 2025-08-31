package app.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParseControllerIT {

  @LocalServerPort
  int port;

  @Autowired
  TestRestTemplate rest;

  static class ParseRequest { public String text; ParseRequest() {} ParseRequest(String t) { this.text = t; } }
  static class ParseResponse {
    public String intent;
    public String amountText;
    public Double amountValue;
    public String currency;
    public String recipient;
  }

  @Test
  void parsesHappyPathRequest() {
    String url = "http://localhost:" + port + "/api/parse";
    ParseRequest req = new ParseRequest("could you please send  $15  to gaby?");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<ParseRequest> entity = new HttpEntity<>(req, headers);

    ResponseEntity<ParseResponse> response = rest.postForEntity(url, entity, ParseResponse.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    ParseResponse body = response.getBody();
    assertNotNull(body, "response body");
    assertEquals("send", body.intent, "intent");
    assertEquals("USD", body.currency, "currency");
    assertNotNull(body.amountValue, "amountValue not null");
    assertEquals(15.0, body.amountValue, 1e-6, "amountValue");
    assertEquals("gaby", body.recipient, "recipient");
  }

  @Test
  void handlesBlankRequestGracefully() {
    String url = "http://localhost:" + port + "/api/parse";
    ParseRequest req = new ParseRequest("   \t   ");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<ParseRequest> entity = new HttpEntity<>(req, headers);

    ResponseEntity<ParseResponse> response = rest.postForEntity(url, entity, ParseResponse.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    ParseResponse body = response.getBody();
    assertNotNull(body, "response body");
    assertNull(body.intent, "intent should be null");
    assertNull(body.amountText, "amountText should be null");
    assertNull(body.amountValue, "amountValue should be null");
    assertNull(body.currency, "currency should be null");
    assertNull(body.recipient, "recipient should be null");
  }

  @Test
  void parsesSpanishEurosAndRecipient() {
    String url = "http://localhost:" + port + "/api/parse";
    ParseRequest req = new ParseRequest("enviar 20 euros a Juan.");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<ParseRequest> entity = new HttpEntity<>(req, headers);

    ResponseEntity<ParseResponse> response = rest.postForEntity(url, entity, ParseResponse.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    ParseResponse body = response.getBody();
    assertNotNull(body, "response body");
    assertEquals("send", body.intent, "intent (ES)");
    assertEquals("EUR", body.currency, "currency (ES)");
    assertNotNull(body.amountValue, "amountValue not null (ES)");
    assertEquals(20.0, body.amountValue, 1e-6, "amountValue (ES)");
    assertEquals("Juan", body.recipient, "recipient (ES)");
  }

  @Test
  void parsesSpanishVerbTransferirWithUsd() {
    String url = "http://localhost:" + port + "/api/parse";
    ParseRequest req = new ParseRequest("transferir $15 a gaby");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<ParseRequest> entity = new HttpEntity<>(req, headers);

    ResponseEntity<ParseResponse> response = rest.postForEntity(url, entity, ParseResponse.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    ParseResponse body = response.getBody();
    assertNotNull(body, "response body");
    assertEquals("transfer", body.intent, "intent transferir (ES)");
    assertEquals("USD", body.currency, "currency (ES)");
    assertNotNull(body.amountValue, "amountValue not null (ES)");
    assertEquals(15.0, body.amountValue, 1e-6, "amountValue (ES)");
    assertEquals("gaby", body.recipient, "recipient (ES)");
  }
}
