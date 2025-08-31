package app.nlp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class NlpServiceTest {

  private final NlpService service = new NlpService();

  @Test
  void parsesSimpleDollarAmountAndRecipient() {
    String input = "could you please send  $15  to gaby?";
    NlpService.ParseResult r = service.parse(input);
    assertEquals("send", r.intent, "intent");
    assertEquals("$15", r.amountText, "amountText");
    assertNotNull(r.amountValue, "amountValue should not be null");
    assertEquals(15.0, r.amountValue, 1e-6, "amountValue");
    assertEquals("USD", r.currency, "currency");
    assertEquals("gaby", r.recipient, "recipient");
  }

  @Test
  void parsesWordCurrencyAndPersonRecipient() {
    String input = "send 20 dollars to John.";
    NlpService.ParseResult r = service.parse(input);
    assertEquals("send", r.intent, "intent");
    assertEquals("20 dollars", r.amountText.toLowerCase(), "amountText");
    assertNotNull(r.amountValue, "amountValue should not be null");
    assertEquals(20.0, r.amountValue, 1e-6, "amountValue");
    assertEquals("USD", r.currency, "currency");
    assertEquals("John", r.recipient, "recipient");
  }

  @Test
  void parsesEuroWithCommasAndHandleRecipient() {
    String input = "transfer €1,234.50 to @alex99";
    NlpService.ParseResult r = service.parse(input);
    assertEquals("transfer", r.intent, "intent");
    assertTrue(r.amountText.contains("€"), "amountText contains euro symbol");
    assertNotNull(r.amountValue, "amountValue should not be null");
    assertEquals(1234.50, r.amountValue, 1e-6, "amountValue");
    assertEquals("EUR", r.currency, "currency");
    assertEquals("@alex99", r.recipient, "recipient");
  }

  @Test
  void parsesSpanishEurosAndPersonRecipient() {
    String input = "enviar 20 euros a Juan.";
    NlpService.ParseResult r = service.parse(input);
    assertEquals("send", r.intent, "intent");
    assertTrue(r.amountText.toLowerCase().contains("20"), "amountText has number");
    assertTrue(r.amountText.toLowerCase().contains("euro"), "amountText has euros");
    assertNotNull(r.amountValue, "amountValue should not be null");
    assertEquals(20.0, r.amountValue, 1e-6, "amountValue");
    assertEquals("EUR", r.currency, "currency");
    assertEquals("Juan", r.recipient, "recipient");
  }

  @Test
  void allowsDeterminantsInRecipientPhrase() {
    String input = "send $10 to the coffee shop.";
    NlpService.ParseResult r = service.parse(input);
    assertEquals("send", r.intent, "intent");
    assertEquals("USD", r.currency, "currency");
    assertEquals(10.0, r.amountValue, 1e-6, "amountValue");
    assertEquals("the coffee shop", r.recipient, "recipient with determiner");
  }

  @Test
  void handlesNullAndBlankInput() {
    // Null input: should not throw, returns empty result
    NlpService.ParseResult rNull = service.parse(null);
    assertNull(rNull.intent, "intent null for null input");
    assertNull(rNull.amountText, "amountText null for null input");
    assertNull(rNull.recipient, "recipient null for null input");

    // Blank input: should not throw, returns empty result
    NlpService.ParseResult rBlank = service.parse("   \t  ");
    assertNull(rBlank.intent, "intent null for blank input");
    assertNull(rBlank.amountText, "amountText null for blank input");
    assertNull(rBlank.recipient, "recipient null for blank input");
  }
}
