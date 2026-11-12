package com.thropic.talki.notification.infrastructure.messaging;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class EventDeduplicatorTest {

    @Test
    void firstSighting_isNotDuplicate() {
        EventDeduplicator deduplicator = new EventDeduplicator();

        assertThat(deduplicator.isDuplicate("evt-1")).isFalse();
    }

    @Test
    void sameEventIdTwice_withinTtl_isDuplicate() {
        EventDeduplicator deduplicator = new EventDeduplicator();

        deduplicator.isDuplicate("evt-1");

        assertThat(deduplicator.isDuplicate("evt-1")).isTrue();
    }

    @Test
    void differentEventIds_areNotDuplicates() {
        EventDeduplicator deduplicator = new EventDeduplicator();

        assertThat(deduplicator.isDuplicate("evt-1")).isFalse();
        assertThat(deduplicator.isDuplicate("evt-2")).isFalse();
    }

    @Test
    void entry_expiresAfterTtl() throws InterruptedException {
        EventDeduplicator deduplicator = new EventDeduplicator(Duration.ofMillis(50));

        deduplicator.isDuplicate("evt-1");
        Thread.sleep(80); // TTL vence

        assertThat(deduplicator.isDuplicate("evt-1")).isFalse();
    }

    @Test
    void nullOrBlankEventId_neverDuplicates() {
        EventDeduplicator deduplicator = new EventDeduplicator();

        assertThat(deduplicator.isDuplicate(null)).isFalse();
        assertThat(deduplicator.isDuplicate("")).isFalse();
        assertThat(deduplicator.isDuplicate("  ")).isFalse();
    }
}
