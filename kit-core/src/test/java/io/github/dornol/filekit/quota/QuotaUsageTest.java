package io.github.dornol.filekit.quota;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuotaUsageTest {

    @Test
    void remainingBytes_normalCase() {
        QuotaUsage usage = new QuotaUsage(300, 1000);
        assertEquals(700, usage.remainingBytes());
    }

    @Test
    void remainingBytes_atLimit() {
        QuotaUsage usage = new QuotaUsage(1000, 1000);
        assertEquals(0, usage.remainingBytes());
    }

    @Test
    void remainingBytes_overLimit_returnsZero() {
        QuotaUsage usage = new QuotaUsage(1500, 1000);
        assertEquals(0, usage.remainingBytes());
    }

    @Test
    void remainingBytes_noUsage() {
        QuotaUsage usage = new QuotaUsage(0, 1000);
        assertEquals(1000, usage.remainingBytes());
    }

    @Test
    void recordFields() {
        QuotaUsage usage = new QuotaUsage(42, 100);
        assertEquals(42, usage.usedBytes());
        assertEquals(100, usage.maxBytes());
    }
}
