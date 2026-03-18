package io.github.dornol.filekit.quota;

import io.github.dornol.filekit.spi.QuotaPolicy;
import io.github.dornol.filekit.spi.QuotaUsageProvider;
import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuotaCheckerTest {

    enum StorageType { LOCAL }

    QuotaPolicy policy = mock(QuotaPolicy.class);
    QuotaUsageProvider usageProvider = mock(QuotaUsageProvider.class);
    QuotaChecker checker;

    @BeforeEach
    void setUp() {
        checker = new QuotaChecker(policy, usageProvider);
        when(policy.getMaxBytes(StorageType.LOCAL, "bucket")).thenReturn(1000L);
    }

    @Nested
    class ConstructorValidation {

        @Test
        void nullPolicy_throws() {
            assertThrows(NullPointerException.class, () -> new QuotaChecker(null, usageProvider));
        }

        @Test
        void nullUsageProvider_throws() {
            assertThrows(NullPointerException.class, () -> new QuotaChecker(policy, null));
        }
    }

    @Nested
    class Check {

        @Test
        void underLimit_passes() {
            when(usageProvider.getUsedBytes(StorageType.LOCAL, "bucket")).thenReturn(500L);
            assertDoesNotThrow(() -> checker.check(StorageType.LOCAL, "bucket", 400));
        }

        @Test
        void atBoundary_passes() {
            when(usageProvider.getUsedBytes(StorageType.LOCAL, "bucket")).thenReturn(500L);
            assertDoesNotThrow(() -> checker.check(StorageType.LOCAL, "bucket", 500));
        }

        @Test
        void overLimit_throwsQuotaExceeded() {
            when(usageProvider.getUsedBytes(StorageType.LOCAL, "bucket")).thenReturn(500L);

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> checker.check(StorageType.LOCAL, "bucket", 501));
            assertEquals(FileStorageException.QUOTA_EXCEEDED, ex.getMessageKey());
        }

        @Test
        void alreadyFull_throwsQuotaExceeded() {
            when(usageProvider.getUsedBytes(StorageType.LOCAL, "bucket")).thenReturn(1000L);

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> checker.check(StorageType.LOCAL, "bucket", 1));
            assertEquals(FileStorageException.QUOTA_EXCEEDED, ex.getMessageKey());
        }

        @Test
        void zeroAdditionalBytes_atLimit_passes() {
            when(usageProvider.getUsedBytes(StorageType.LOCAL, "bucket")).thenReturn(1000L);
            assertDoesNotThrow(() -> checker.check(StorageType.LOCAL, "bucket", 0));
        }
    }

    @Nested
    class GetUsage {

        @Test
        void returnsCorrectUsage() {
            when(usageProvider.getUsedBytes(StorageType.LOCAL, "bucket")).thenReturn(300L);

            QuotaUsage usage = checker.getUsage(StorageType.LOCAL, "bucket");

            assertEquals(300, usage.usedBytes());
            assertEquals(1000, usage.maxBytes());
            assertEquals(700, usage.remainingBytes());
        }
    }
}
