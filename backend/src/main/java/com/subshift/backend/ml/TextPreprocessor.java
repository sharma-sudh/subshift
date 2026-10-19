package com.subshift.backend.ml;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns raw email text into a list of terms (unigrams and bigrams).
 *
 * Prices, currency symbols, numbers, URLs and addresses are removed on purpose. In a small training
 * set nearly every renewal mentions a price, so a model would otherwise learn "contains a price"
 * instead of "is a renewal", and would then mistake one-off receipts for subscriptions.
 */
public final class TextPreprocessor {

    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern ADDRESS = Pattern.compile("\\S+@\\S+");
    private static final Pattern MONEY = Pattern.compile(
            "(?:(?<!\\p{L})(?:rs\\.?|inr|usd|eur|gbp)|[\\u20B9$\\u00A3\\u20AC])\\s*\\d[\\d,]*(?:\\.\\d+)?");
    private static final Pattern MONEY_SUFFIX = Pattern.compile(
            "\\d[\\d,]*(?:\\.\\d+)?\\s*(?:rs|inr|usd|rupees|lakh)\\b");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern WORD = Pattern.compile("\\p{L}{2,}");

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "an", "and", "are", "as", "at", "be", "been", "but", "by", "can", "do", "for", "from",
            "has", "have", "he", "her", "here", "how", "if", "in", "is", "it", "its", "me", "my", "of",
            "on", "or", "our", "she", "so", "that", "their", "them", "then", "there", "these", "they",
            "this", "to", "up", "us", "was", "we", "were", "what", "when", "which", "who", "with",
            "would", "you", "your", "subject", "hi", "hello", "dear", "thank", "thanks", "regards",
            "please", "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec",
            "january", "february", "march", "april", "june", "july", "august", "september", "october",
            "november", "december", "unsubscribe", "privacy", "policy", "receiving", "because", "account");

    private TextPreprocessor() {}

    public static List<String> terms(String text) {
        String s = text == null ? "" : text.toLowerCase(Locale.ROOT);
        s = URL.matcher(s).replaceAll(" ");
        s = ADDRESS.matcher(s).replaceAll(" ");
        s = MONEY.matcher(s).replaceAll(" ");
        s = MONEY_SUFFIX.matcher(s).replaceAll(" ");
        s = DIGITS.matcher(s).replaceAll(" ");

        List<String> words = new ArrayList<>();
        var matcher = WORD.matcher(s);
        while (matcher.find()) {
            String word = matcher.group();
            if (!STOP_WORDS.contains(word)) {
                words.add(stem(word));
            }
        }
        List<String> terms = new ArrayList<>(words);
        for (int i = 0; i + 1 < words.size(); i++) {
            terms.add(words.get(i) + " " + words.get(i + 1));
        }
        return terms;
    }

    /** Deliberately light: groups the variants that matter here (renew, renewed, renewal, subscription...). */
    static String stem(String word) {
        if (word.startsWith("renew")) return "renew";
        if (word.startsWith("subscri")) return "subscri";
        if (word.startsWith("cancel")) return "cancel";
        if (word.startsWith("charg")) return "charg";
        if (word.startsWith("recur")) return "recur";
        if (word.length() > 5 && word.endsWith("ing")) return word.substring(0, word.length() - 3);
        if (word.length() > 4 && word.endsWith("ed")) return word.substring(0, word.length() - 2);
        if (word.length() > 4 && word.endsWith("es")) return word.substring(0, word.length() - 2);
        if (word.length() > 3 && word.endsWith("s")) return word.substring(0, word.length() - 1);
        return word;
    }
}
