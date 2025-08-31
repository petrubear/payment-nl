package app.nlp;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.util.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NlpService {

  private final StanfordCoreNLP pipeline;
  // Map possible (lemmatized or raw) intent words in EN/ES -> canonical intent
  private static final Map<String, String> INTENT_MAP =
      Map.ofEntries(
          Map.entry("pay", "pay"),
          Map.entry("send", "send"),
          Map.entry("transfer", "transfer"),
          // Spanish
          Map.entry("pagar", "pay"),
          Map.entry("enviar", "send"),
          Map.entry("transferir", "transfer"));

  public NlpService() {
    Properties props = new Properties();
    // Include 'entitymentions' to populate sentence-level CoreEntityMention list
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,entitymentions,depparse");
    props.setProperty("ner.applyFineGrained", "false");
    props.setProperty("tokenize.options", "ptb3Escaping=false"); // keeps @handles, €
    this.pipeline = new StanfordCoreNLP(props);
  }

  public ParseResult parse(String input) {
    ParseResult out = new ParseResult();
    if (input == null || input.isBlank()) return out;
    Annotation ann = new Annotation(input);
    pipeline.annotate(ann);

    List<CoreMap> sentences = ann.get(CoreAnnotations.SentencesAnnotation.class);

    if (sentences == null || sentences.isEmpty()) return out;
    CoreMap s = sentences.get(0);

    // 1) intent from root verb lemma (fallback: first verb in sentence)
    SemanticGraph graph =
        s.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
    IndexedWord root = (graph != null) ? graph.getFirstRoot() : null;

    if (root != null) {
      String lemma = root.get(CoreAnnotations.LemmaAnnotation.class);
      if (lemma != null) {
        String canonical = INTENT_MAP.get(lemma.toLowerCase(Locale.ROOT));
        if (canonical != null) out.intent = canonical;
      }
    }
    if (out.intent == null) {
      for (CoreLabel t : s.get(CoreAnnotations.TokensAnnotation.class)) {
        String pos = t.get(CoreAnnotations.PartOfSpeechAnnotation.class);
        if (pos != null && pos.startsWith("V")) {
          String lemma = t.get(CoreAnnotations.LemmaAnnotation.class);
          if (lemma != null) {
            String canonical = INTENT_MAP.get(lemma.toLowerCase(Locale.ROOT));
            if (canonical != null) {
              out.intent = canonical;
              break;
            }
          }
        }
      }
    }
    // Final fallback for Spanish/English: raw-text keyword scan
    if (out.intent == null) {
      String low = input.toLowerCase(Locale.ROOT);
      if (containsWord(low, "enviar")) out.intent = "send";
      else if (containsWord(low, "transferir")) out.intent = "transfer";
      else if (containsWord(low, "pagar")) out.intent = "pay";
      else if (containsWord(low, "send")) out.intent = "send";
      else if (containsWord(low, "transfer")) out.intent = "transfer";
      else if (containsWord(low, "pay")) out.intent = "pay";
    }

    // 2) extract MONEY and PERSON mentions
    String money = null;
    String person = null;
    // Use MentionsAnnotation which yields List<CoreMap> for entity mentions
    List<CoreMap> mentions = s.get(CoreAnnotations.MentionsAnnotation.class);
    if (mentions != null) {
      for (CoreMap m : mentions) {
        String ent = m.get(CoreAnnotations.EntityTypeAnnotation.class);
        String txt = m.get(CoreAnnotations.TextAnnotation.class);
        if (ent == null || txt == null) continue;
        if (money == null && "MONEY".equals(ent)) money = txt;
        if (person == null && "PERSON".equals(ent)) person = txt;
      }
    }
    out.amountText = money;
    // Normalize amount (value + currency) from the surface text when present
    if (out.amountText != null) {
      AmountNorm norm = normalizeAmount(out.amountText);
      if (norm != null) {
        out.amountValue = norm.value;
        out.currency = norm.currency;
      }
    }

    // Fallback: detect money directly from input for Spanish/no-NER cases
    if (out.amountText == null) {
      String found = findMoneyInText(input);
      if (found != null) {
        out.amountText = found;
        AmountNorm norm = normalizeAmount(found);
        if (norm != null) {
          out.amountValue = norm.value;
          out.currency = norm.currency;
        }
      }
    }

    // 3) recipient: prefer PERSON, else nmod:to subtree from root verb
    if (person != null) {
      out.recipient = person;
    } else if (root != null && graph != null) {
      for (SemanticGraphEdge e : graph.outgoingEdgeList(root)) {
        if (e.getRelation() != null && e.getRelation().toString().startsWith("nmod:to")) {
          IndexedWord head = e.getDependent();
          Set<IndexedWord> sub = graph.descendants(head);
          List<IndexedWord> nodes = new ArrayList<>(sub);
          nodes.add(head);
          nodes.sort(Comparator.comparingInt(IndexedWord::index));
          out.recipient = nodes.stream().map(IndexedWord::word).collect(Collectors.joining(" "));
          break;
        }
      }
    }

    // (optional) tiny heuristic: capture ORG/email as recipient if PERSON missing
    if (out.recipient == null && mentions != null) {
      for (CoreMap m : mentions) {
        String ent = m.get(CoreAnnotations.EntityTypeAnnotation.class);
        String txt = m.get(CoreAnnotations.TextAnnotation.class);
        if (ent == null || txt == null) continue;
        if ("ORGANIZATION".equals(ent) || "EMAIL".equals(ent)) {
          out.recipient = txt;
          break;
        }
      }
    }

    // Fallback: parse recipient via prepositions ("to", Spanish: "a", "para")
    if (out.recipient == null) {
      out.recipient = extractRecipientByPreposition(s, Set.of("to", "a", "para"));
    }

    // Cleanup recipient punctuation and leading prepositions
    if (out.recipient != null) {
      out.recipient = cleanRecipient(out.recipient);
    }

    // Debug (can be toggled/removed)
    out.debugDependencies =
        (graph != null) ? graph.toString(SemanticGraph.OutputFormat.READABLE) : null;

    return out;
  }

  public static class ParseResult {
    public String intent; // pay|send|transfer (lemma)
    public String amountText; // surface text e.g., "$12", "15 dollars"
    public String recipient; // "John", "@alex99", "my mom", "ACME Inc."
    public String currency; // optional (null unless you add normalization)
    public Double amountValue; // optional numeric value (null unless normalized)
    public String debugDependencies;
  }

  // -------- Helpers --------
  private static class AmountNorm {
    Double value;
    String currency;
  }

  private static final Pattern MONEY_SYMBOL_FIRST =
      Pattern.compile(
          "^(?:about\\s+|around\\s+|approximately\\s+|~)?([\\p{Sc}€£$¥₹₩₽₺₴₦₫])\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern MONEY_WORD =
      Pattern.compile(
          "^([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)\\s*"
              + "(dollars?|d[oó]lares?|bucks|usd|euros?|eur|pounds?|libras?|gbp|yen|jpy|rupees?|rupias?|inr|pesos?|mxn|cop|ars|clp|pen|soles?|cad|aud|chf|francs?|reales?|brl)",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  // Non-anchored finders for fallback scanning inside full input
  private static final Pattern MONEY_SYMBOL_FIRST_ANYWHERE =
      Pattern.compile(
          "(?:about\\s+|around\\s+|approximately\\s+|~)?([\\p{Sc}€£$¥₹₩₽₺₴₦₫])\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern MONEY_WORD_ANYWHERE =
      Pattern.compile(
          "([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)\\s*"
              + "(dollars?|d[oó]lares?|bucks|usd|euros?|eur|pounds?|libras?|gbp|yen|jpy|rupees?|rupias?|inr|pesos?|mxn|cop|ars|clp|pen|soles?|cad|aud|chf|francs?|reales?|brl)",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private AmountNorm normalizeAmount(String text) {
    String t = text.trim();
    // Try symbol-first, e.g., $15, € 20.50
    Matcher m1 = MONEY_SYMBOL_FIRST.matcher(t);
    if (m1.find()) {
      String sym = m1.group(1);
      String num = m1.group(2).replace(",", "");
      AmountNorm norm = new AmountNorm();
      norm.value = safeParseDouble(num);
      norm.currency = mapCurrencySymbol(sym);
      return norm;
    }
    // Try number + word, e.g., 15 dollars, 20 eur
    Matcher m2 = MONEY_WORD.matcher(t);
    if (m2.find()) {
      String num = m2.group(1).replace(",", "");
      String word = m2.group(2).toLowerCase(Locale.ROOT);
      AmountNorm norm = new AmountNorm();
      norm.value = safeParseDouble(num);
      norm.currency = mapCurrencyWord(word);
      return norm;
    }
    return null;
  }

  private static Double safeParseDouble(String s) {
    try {
      return Double.parseDouble(s);
    } catch (Exception e) {
      return null;
    }
  }

  private static String mapCurrencySymbol(String sym) {
    return switch (sym) {
      case "$" -> "USD";
      case "€" -> "EUR";
      case "£" -> "GBP";
      case "¥" -> "JPY";
      case "₹" -> "INR";
      case "₩" -> "KRW";
      case "₽" -> "RUB";
      default -> null;
    };
  }

  private static String mapCurrencyWord(String w) {
    String base = stripAccents(w);
    if (base.startsWith("dollar") || base.equals("bucks") || base.equals("usd")) return "USD";
    if (base.startsWith("euro") || base.equals("eur")) return "EUR";
    if (base.startsWith("pound") || base.equals("libras") || base.equals("gbp")) return "GBP";
    if (base.equals("yen") || base.equals("jpy")) return "JPY";
    if (base.startsWith("rupee") || base.startsWith("rupia") || base.equals("inr")) return "INR";
    if (base.startsWith("franc") || base.equals("chf")) return "CHF";
    if (base.startsWith("peso")) return "MXN"; // generic pesos -> default MXN
    if (base.equals("mxn")) return "MXN";
    if (base.equals("cop")) return "COP";
    if (base.equals("ars")) return "ARS";
    if (base.equals("clp")) return "CLP";
    if (base.equals("pen") || base.startsWith("sol")) return "PEN";
    if (base.equals("brl") || base.startsWith("real")) return "BRL";
    if (base.equals("cad")) return "CAD";
    if (base.equals("aud")) return "AUD";
    return null;
  }

  private static String extractRecipientByPreposition(CoreMap s, Set<String> preps) {
    List<CoreLabel> toks = s.get(CoreAnnotations.TokensAnnotation.class);
    if (toks == null) return null;
    for (int i = 0; i < toks.size(); i++) {
      String lemma = toks.get(i).get(CoreAnnotations.LemmaAnnotation.class);
      String word = toks.get(i).word();
      String lw = word != null ? word.toLowerCase(Locale.ROOT) : null;
      String ll = lemma != null ? lemma.toLowerCase(Locale.ROOT) : null;
      if ((lw != null && preps.contains(lw)) || (ll != null && preps.contains(ll))) {
        StringBuilder sb = new StringBuilder();
        for (int j = i + 1; j < toks.size(); j++) {
          CoreLabel t = toks.get(j);
          String pos = t.get(CoreAnnotations.PartOfSpeechAnnotation.class);
          String w = t.word();
          if (w == null) break;
          // Stop at punctuation (except allow leading '@' for handles) or another preposition/verb
          if (w.matches("[\\p{Punct}]+") && !(w.equals("@") && sb.length() == 0)) break;
          if (pos != null && (pos.startsWith("IN") || pos.startsWith("VB"))) break;
          if (sb.length() > 0) sb.append(' ');
          sb.append(w);
        }
        String cand = sb.toString().trim();
        if (!cand.isEmpty()) return cand;
      }
    }
    return null;
  }

  private static String cleanRecipient(String r) {
    String trimmed = r.trim();
    // Preserve handles like @alex99
    if (trimmed.startsWith("@")) {
      // Only strip trailing punctuation/spaces
      return trimmed.replaceAll("[\\p{Punct}\\s]+$", "");
    }
    String cleaned = trimmed;
    // Remove leading/trailing punctuation and the preposition "to ", "a ", or "para " if present
    cleaned = cleaned.replaceAll("^[\\p{Punct}\\s]+", "");
    cleaned = cleaned.replaceAll("[\\p{Punct}\\s]+$", "");
    if (cleaned.toLowerCase(Locale.ROOT).startsWith("to ")) {
      cleaned = cleaned.substring(3).trim();
    }
    if (cleaned.toLowerCase(Locale.ROOT).startsWith("a ")) {
      cleaned = cleaned.substring(2).trim();
    }
    if (cleaned.toLowerCase(Locale.ROOT).startsWith("para ")) {
      cleaned = cleaned.substring(5).trim();
    }
    return cleaned;
  }

  private static boolean containsWord(String haystack, String needle) {
    return haystack.matches(
        ".*(?i)(?<![A-Za-zÁÉÍÓÚáéíóúÑñ])" + Pattern.quote(needle) + "(?![A-Za-zÁÉÍÓÚáéíóúÑñ]).*");
  }

  private static String findMoneyInText(String input) {
    if (input == null) return null;
    Matcher m1 = MONEY_SYMBOL_FIRST_ANYWHERE.matcher(input);
    if (m1.find()) return m1.group(0).trim();
    Matcher m2 = MONEY_WORD_ANYWHERE.matcher(input);
    if (m2.find()) return (m2.group(1) + " " + m2.group(2)).trim();
    return null;
  }

  private static String stripAccents(String s) {
    if (s == null) return null;
    String norm = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
    return norm.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
  }
}
