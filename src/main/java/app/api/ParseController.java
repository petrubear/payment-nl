package app.api;

import app.nlp.NlpService;
import app.nlp.NlpService.ParseResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

record ParseRequest(String text) {}
record ParseResponse(String intent, String amountText, Double amountValue,
                     String currency, String recipient) {}

@RestController
@RequestMapping("/api")
public class ParseController {

  private final NlpService nlp;

  public ParseController(NlpService nlp) {
    this.nlp = nlp;
  }

  @PostMapping(path = "/parse", consumes = MediaType.APPLICATION_JSON_VALUE,
                               produces = MediaType.APPLICATION_JSON_VALUE)
  public ParseResponse parse(@RequestBody ParseRequest req) {
    ParseResult r = nlp.parse(req.text());
    return new ParseResponse(
        r.intent,
        r.amountText,
        r.amountValue,
        r.currency,
        r.recipient
    );
  }
}
