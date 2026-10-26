package com.subshift.backend.email;

import com.subshift.backend.catalog.Service;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides which catalog service an extracted name refers to, using plain deterministic rules (no LLM).
 *
 * <p>A name is split into a BRAND and a PLAN. Trailing plan words such as Pro, Plus, Premium, Family or a
 * storage size (200GB) belong to the plan, so "Claude", "Claude Pro" and "Claude Plus" are one service
 * (brand "claude") with different plans. Names that differ in the brand part stay separate services:
 * "YouTube Music" is not "YouTube Premium", and "Google One" is not "Google Play Pass".
 *
 * <p>Normalisation: "Netflix.com", "NETFLIX India" and "netflix" all become "netflix". After the brand is
 * known, a single-character typo is tolerated for brands of 6+ characters.
 */
@Component
public class ServiceMatcher {

    /** The catalog service a name resolved to, and the plan that was left over (null when there is none). */
    public record Match(Service service, String plan) {}

    private static final Set<String> NOISE =
            Set.of("www", "com", "in", "co", "inc", "ltd", "llc", "pvt", "private", "limited", "india", "the");

    /**
     * Words that name a tier of a service rather than a different service. Only TRAILING words count, and a
     * name is never reduced to nothing, so "Disney+ Hotstar" or a service literally called "Max" are safe.
     * "prime" is deliberately absent: Amazon Prime and Amazon (shopping) are different things.
     */
    private static final Set<String> PLAN_WORDS = Set.of(
            "pro", "plus", "premium", "max", "ultra", "basic", "standard", "essential", "essentials",
            "starter", "lite", "free", "family", "duo", "individual", "personal", "student", "team", "teams",
            "business", "enterprise", "annual", "yearly", "monthly", "mobile", "plan", "membership",
            "subscription", "tier");
    private static final Pattern STORAGE_SIZE = Pattern.compile("^\\d+(gb|tb|mb)$");

    private static final Pattern VALID_NAME = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} .&+'\\-:]{1,59}$");
    private static final Pattern VALID_PLAN = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} .&+'\\-:/()]{0,99}$");
    private static final int MIN_FUZZY_LENGTH = 6;
    private static final int MIN_BRAND_LENGTH = 3;

    /** Lower-case words of the name, noise removed. "Disney+" becomes [disney, plus]. */
    private static List<String> tokens(String name) {
        String s = name.toLowerCase(Locale.ROOT).replace("+", " plus ").replace("&", " and ");
        List<String> tokens = new ArrayList<>();
        for (String token : s.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().split(" ")) {
            if (token.isEmpty() || NOISE.contains(token)) {
                continue;
            }
            boolean unit = token.equals("gb") || token.equals("tb") || token.equals("mb");
            if (unit && !tokens.isEmpty() && tokens.get(tokens.size() - 1).matches("\\d+")) {
                tokens.set(tokens.size() - 1, tokens.get(tokens.size() - 1) + token); // "200 GB" is one token
            } else {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String join(List<String> tokens) {
        return String.join("", tokens);
    }

    private static boolean isPlanWord(String token) {
        return PLAN_WORDS.contains(token) || STORAGE_SIZE.matcher(token).matches();
    }

    /** How many trailing tokens are plan words (0 when stripping them would leave too little brand). */
    private static int planTokenCount(List<String> tokens) {
        int count = 0;
        while (count < tokens.size() - 1 && isPlanWord(tokens.get(tokens.size() - 1 - count))) {
            count++;
        }
        if (count > 0 && join(tokens.subList(0, tokens.size() - count)).length() < MIN_BRAND_LENGTH) {
            return 0;
        }
        return count;
    }

    private static String brandKey(List<String> tokens) {
        return join(tokens.subList(0, tokens.size() - planTokenCount(tokens)));
    }

    public String normalize(String name) {
        return join(tokens(name));
    }

    /** The brand of a name with its plan words removed, as a comparison key: "Claude Pro Plan" gives "claude". */
    public String brand(String name) {
        return brandKey(tokens(name));
    }

    /** Guards the shared services table: only sensible-looking names may create a new row. */
    public boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name.trim()).matches() && !normalize(name).isEmpty();
    }

    public String displayName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    /**
     * The name to store for a NEW service: the brand without its plan words, so the first email seen
     * ("Claude Pro") does not turn the plan into part of the shared catalog entry. Names are left exactly as
     * written when nothing would be left, or when removing words could not be done cleanly ("Disney+").
     */
    public String catalogName(String name) {
        String display = displayName(name);
        List<String> tokens = tokens(display);
        int plan = planTokenCount(tokens);
        if (plan == 0) {
            return display;
        }
        // Drop trailing words until what is left has exactly the brand's tokens ("Disney+ Premium" can't be
        // cut cleanly, so it stays as written).
        List<String> brandTokens = tokens.subList(0, tokens.size() - plan);
        String[] words = display.split(" ");
        for (int keep = words.length - 1; keep >= 1; keep--) {
            String candidate = String.join(" ", Arrays.copyOfRange(words, 0, keep));
            if (tokens(candidate).equals(brandTokens)) {
                return candidate;
            }
        }
        return display;
    }

    /** Validates and tidies a plan name given by the model; null when it is unusable. */
    public String cleanPlan(String plan) {
        if (plan == null) {
            return null;
        }
        String tidy = plan.trim().replaceAll("\\s+", " ");
        return VALID_PLAN.matcher(tidy).matches() ? tidy : null;
    }

    public Optional<Service> match(String name, Collection<Service> known) {
        return matchWithPlan(name, known).map(Match::service);
    }

    public Optional<Match> matchWithPlan(String name, Collection<Service> known) {
        List<String> tokens = tokens(name);
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        String key = join(tokens);
        int planCount = planTokenCount(tokens);
        String brand = join(tokens.subList(0, tokens.size() - planCount));

        // 1. Services with the same brand. Prefer the one named exactly like the brand ("Claude"), then one
        //    named exactly like the full name, then the oldest row, so duplicates from before this rule
        //    don't keep multiplying.
        List<Service> sameBrand = new ArrayList<>();
        for (Service service : known) {
            if (brandKey(tokens(service.getName())).equals(brand)) {
                sameBrand.add(service);
            }
        }
        if (!sameBrand.isEmpty()) {
            Service chosen = sameBrand.stream()
                    .min(Comparator.<Service, Integer>comparing(s -> rank(s, brand, key))
                            .thenComparing(s -> s.getId() == null ? Integer.MAX_VALUE : s.getId()))
                    .orElseThrow();
            return Optional.of(new Match(chosen, leftoverPlan(tokens, planCount, chosen)));
        }

        // 2. A one-character typo in the brand, trusted only when exactly one service is that close.
        List<Service> fuzzy = new ArrayList<>();
        if (brand.length() >= MIN_FUZZY_LENGTH) {
            for (Service service : known) {
                String other = brandKey(tokens(service.getName()));
                if (other.length() >= MIN_FUZZY_LENGTH
                        && Math.abs(brand.length() - other.length()) <= 1
                        && editDistance(brand, other) <= 1) {
                    fuzzy.add(service);
                }
            }
        }
        if (fuzzy.size() == 1) {
            return Optional.of(new Match(fuzzy.get(0), leftoverPlan(tokens, planCount, fuzzy.get(0))));
        }
        return Optional.empty();
    }

    private int rank(Service service, String brand, String fullKey) {
        String own = normalize(service.getName());
        if (own.equals(brand)) {
            return 0;
        }
        return own.equals(fullKey) ? 1 : 2;
    }

    /** The plan words of the incoming name that the chosen service's own name doesn't already say. */
    private String leftoverPlan(List<String> tokens, int planCount, Service chosen) {
        if (planCount == 0) {
            return null;
        }
        Set<String> own = new HashSet<>(tokens(chosen.getName()));
        List<String> words = new ArrayList<>();
        for (String token : tokens.subList(tokens.size() - planCount, tokens.size())) {
            if (!own.contains(token)) {
                words.add(STORAGE_SIZE.matcher(token).matches()
                        ? token.toUpperCase(Locale.ROOT)
                        : Character.toUpperCase(token.charAt(0)) + token.substring(1));
            }
        }
        return words.isEmpty() ? null : String.join(" ", words);
    }

    static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
