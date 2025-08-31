package app.api;

import app.nlp.NlpService;
import app.nlp.NlpService.ParseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Schema(
    name = "ParseRequest",
    description = "Texto libre para extraer intención, monto y destinatario")
record ParseRequest(
    @Schema(description = "Texto a analizar (EN/ES)", example = "send $15 to gaby") String text) {}

@Schema(
    name = "ParseResponse",
    description = "Resultado de parseo: intención, monto, moneda y destinatario")
record ParseResponse(
    @Schema(example = "send") String intent,
    @Schema(example = "$15") String amountText,
    @Schema(example = "15.0") Double amountValue,
    @Schema(example = "USD") String currency,
    @Schema(example = "gaby") String recipient) {}

@RestController
@RequestMapping("/api")
@Tag(name = "Parse", description = "NLP parsing for payment-like requests")
public class ParseController {

  private final NlpService nlp;

  public ParseController(NlpService nlp) {
    this.nlp = nlp;
  }

  @PostMapping(
      path = "/parse",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Parse natural language payment request",
      description =
          "Extracts intent (send/pay/transfer), amount, currency and recipient from free text (English/Spanish).",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Parsed successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ParseResponse.class)))
      })
  public ParseResponse parse(@RequestBody ParseRequest req) {
    ParseResult r = nlp.parse(req.text());
    return new ParseResponse(r.intent, r.amountText, r.amountValue, r.currency, r.recipient);
  }
}
