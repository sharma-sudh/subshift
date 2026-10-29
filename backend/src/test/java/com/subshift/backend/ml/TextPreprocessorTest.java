package com.subshift.backend.ml;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit tests: no Spring, no database. */
class TextPreprocessorTest {

    @Test
    void nullAndBlankTextGiveNoTerms() {
        assertTrue(TextPreprocessor.terms(null).isEmpty());
        assertTrue(TextPreprocessor.terms("   ").isEmpty());
    }

    @Test
    void pricesUrlsAddressesAndNumbersAreRemoved() {
        List<String> terms = TextPreprocessor.terms("Your plan costs Rs. 499 or $9.99. Visit https://example.com or mail help@example.com 2026");
        assertFalse(terms.stream().anyMatch(t -> t.matches(".*\\d.*")), "no digits should survive: " + terms);
        assertFalse(terms.stream().anyMatch(t -> t.contains("example") || t.contains("http")), "urls and addresses should go: " + terms);
        assertTrue(terms.contains("plan"));
    }

    @Test
    void renewalWordFamilyIsGroupedAndStopWordsDropped() {
        assertEquals("renew", TextPreprocessor.stem("renewal"));
        assertEquals("renew", TextPreprocessor.stem("renewed"));
        assertEquals("subscri", TextPreprocessor.stem("subscription"));
        List<String> terms = TextPreprocessor.terms("The subscription will be renewed");
        assertTrue(terms.contains("subscri"));
        assertTrue(terms.contains("renew"));
        assertFalse(terms.contains("the"));
    }

    @Test
    void bigramsAreBuiltFromNeighbouringWords() {
        List<String> terms = TextPreprocessor.terms("monthly plan renewal");
        assertTrue(terms.contains("monthly plan"));
        assertTrue(terms.contains("plan renew"));
    }
}
