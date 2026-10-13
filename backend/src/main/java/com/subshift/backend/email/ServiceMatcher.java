package com.subshift.backend.email;

import com.subshift.backend.catalog.Service;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides which catalog service an extracted name refers to, using plain deterministic rules:
 * normalise ("Netflix.com", "NETFLIX India" and "netflix" all become "netflix"), then allow a
 * single-character typo for names of 6+ characters. No LLM involved.
 */
@Component
public class ServiceMatcher {

    private static final Set<String> NOISE =
            Set.of("www", "com", "in", "co", "inc", "ltd", "llc", "pvt", "private", "limited", "india", "the");
    private static final Pattern VALID_NAME = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} .&+'\\-:]{1,59}$");
    private static final int MIN_FUZZY_LENGTH = 6;

    public String normalize(String name) {
        String s = name.toLowerCase(Locale.ROOT).replace("+", " plus ").replace("&", " and ");
        StringBuilder key = new StringBuilder();
        for (String token : s.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().split(" ")) {
            if (!token.isEmpty() && !NOISE.contains(token)) {
                key.append(token);
            }
        }
        return key.toString();
    }

    /** Guards the shared services table: only sensible-looking names may create a new row. */
    public boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name.trim()).matches() && !normalize(name).isEmpty();
    }

    public String displayName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    public Optional<Service> match(String name, Collection<Service> known) {
        String key = normalize(name);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        List<Service> fuzzy = new ArrayList<>();
        for (Service service : known) {
            String other = normalize(service.getName());
            if (other.equals(key)) {
                return Optional.of(service);
            }
            if (key.length() >= MIN_FUZZY_LENGTH && other.length() >= MIN_FUZZY_LENGTH
                    && Math.abs(key.length() - other.length()) <= 1
                    && editDistance(key, other) <= 1) {
                fuzzy.add(service);
            }
        }
        // A fuzzy hit is only trusted when it is unambiguous.
        return fuzzy.size() == 1 ? Optional.of(fuzzy.get(0)) : Optional.empty();
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
