package com.subshift.backend.email;

import com.subshift.backend.catalog.Service;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit tests: no Spring, no database. */
class ServiceMatcherTest {

    private final ServiceMatcher matcher = new ServiceMatcher();

    private static Service service(int id, String name) {
        Service s = new Service();
        s.setId(id);
        s.setName(name);
        return s;
    }

    private static final List<Service> CATALOG = List.of(
            service(1, "Netflix"), service(2, "Spotify"), service(3, "Amazon Prime"),
            service(4, "YouTube Premium"), service(5, "Google One"), service(6, "Microsoft 365"),
            service(7, "Disney+"), service(8, "Claude"));

    private void expect(String extracted, String serviceName, String plan) {
        Optional<ServiceMatcher.Match> match = matcher.matchWithPlan(extracted, CATALOG);
        assertEquals(serviceName, match.map(m -> m.service().getName()).orElse(null), extracted);
        assertEquals(plan, match.map(ServiceMatcher.Match::plan).orElse(null), extracted + " (plan)");
    }

    @Test
    void planVariantsOfOneBrandAreTheSameService() {
        expect("Claude", "Claude", null);
        expect("Claude Pro", "Claude", "Pro");
        expect("Claude Plus", "Claude", "Plus");
        expect("claude max plan", "Claude", "Max Plan");
        expect("Spotify Premium Family", "Spotify", "Premium Family");
        expect("Microsoft 365 Family", "Microsoft 365", "Family");
        expect("Google One 200 GB", "Google One", "200GB");
    }

    @Test
    void differentProductsStaySeparate() {
        expect("YouTube Music", null, null);
        expect("Google Play Pass", null, null);
        expect("Prime Video", null, null);
        expect("Amazon", null, null);
        expect("Disney+ Hotstar", null, null);
    }

    @Test
    void spellingNoiseAndOwnPlanWordsAreHandled() {
        expect("NETFLIX India", "Netflix", null);
        expect("Netflix.com", "Netflix", null);
        expect("Netflis", "Netflix", null);
        expect("Disney Plus", "Disney+", null);
        expect("YouTube Premium", "YouTube Premium", null);
        expect("YouTube Premium Family", "YouTube Premium", "Family");
        expect("YouTube", "YouTube Premium", null);
    }

    @Test
    void aNameMadeOnlyOfPlanWordsIsNotReducedToNothing() {
        expect("Pro", null, null);
    }

    @Test
    void oldDuplicatesAreResolvedToTheBrandRow() {
        List<Service> legacy = List.of(service(20, "Claude Pro"), service(21, "Claude"), service(22, "Claude Plus"));
        Optional<ServiceMatcher.Match> match = matcher.matchWithPlan("Claude Max", legacy);
        assertTrue(match.isPresent());
        assertEquals(21, match.get().service().getId());
        assertEquals("Max", match.get().plan());
    }

    @Test
    void newServicesAreStoredAsTheBrandOnly() {
        assertEquals("Claude", matcher.catalogName("Claude Pro"));
        assertEquals("Claude", matcher.catalogName("Claude Pro Plan"));
        assertEquals("Google One", matcher.catalogName("Google One 200 GB"));
        assertEquals("Disney+", matcher.catalogName("Disney+"));
        assertEquals("Notion", matcher.catalogName("Notion"));
        assertEquals("YouTube Music", matcher.catalogName("YouTube Music"));
    }

    @Test
    void planNamesFromTheModelAreValidated() {
        assertEquals("Pro", matcher.cleanPlan("  Pro "));
        assertEquals("Family (6 users)", matcher.cleanPlan("Family (6 users)"));
        assertEquals(null, matcher.cleanPlan("<script>"));
        assertEquals(null, matcher.cleanPlan(""));
    }
}
