package io.github.dornol.filekit.upload;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchUploadResultTest {

    enum StorageType { LOCAL }

    private final FileMetadata meta1 = new FileMetadata("k1", "a.txt", 10, "c1",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "k1.txt", StorageType.LOCAL));

    private final FileMetadata meta2 = new FileMetadata("k2", "b.txt", 20, "c2",
            new FileFormat("text/plain", "txt", "text"),
            new FileLocation("bucket", "k2.txt", StorageType.LOCAL));

    @Nested
    class Validation {

        @Test
        void validResult() {
            BatchUploadResult result = new BatchUploadResult(
                    List.of(meta1, meta2), Map.of("bad.txt", "too large"));

            assertEquals(2, result.succeeded().size());
            assertEquals(1, result.failed().size());
        }

        @Test
        void emptyResult() {
            BatchUploadResult result = new BatchUploadResult(List.of(), Map.of());

            assertEquals(0, result.succeeded().size());
            assertEquals(0, result.failed().size());
        }

        @Test
        void nullSucceeded_throws() {
            assertThrows(NullPointerException.class,
                    () -> new BatchUploadResult(null, Map.of()));
        }

        @Test
        void nullFailed_throws() {
            assertThrows(NullPointerException.class,
                    () -> new BatchUploadResult(List.of(), null));
        }
    }

    @Nested
    class ConvenienceMethods {

        @Test
        void totalRequested_sumOfBoth() {
            BatchUploadResult result = new BatchUploadResult(
                    List.of(meta1), Map.of("bad.txt", "error"));

            assertEquals(2, result.totalRequested());
        }

        @Test
        void allSucceeded_trueWhenNoFailures() {
            BatchUploadResult result = new BatchUploadResult(
                    List.of(meta1, meta2), Map.of());

            assertTrue(result.allSucceeded());
        }

        @Test
        void allSucceeded_falseWhenFailuresExist() {
            BatchUploadResult result = new BatchUploadResult(
                    List.of(meta1), Map.of("bad.txt", "error"));

            assertFalse(result.allSucceeded());
        }

        @Test
        void allSucceeded_trueWhenEmpty() {
            assertTrue(new BatchUploadResult(List.of(), Map.of()).allSucceeded());
        }

        @Test
        void totalRequested_zeroWhenEmpty() {
            assertEquals(0, new BatchUploadResult(List.of(), Map.of()).totalRequested());
        }
    }

    @Nested
    class DefensiveCopy {

        @Test
        void succeededIsDefensivelyCopied() {
            List<FileMetadata> mutable = new ArrayList<>();
            mutable.add(meta1);

            BatchUploadResult result = new BatchUploadResult(mutable, Map.of());
            mutable.add(meta2);

            assertEquals(1, result.succeeded().size());
        }

        @Test
        void succeededIsUnmodifiable() {
            BatchUploadResult result = new BatchUploadResult(List.of(meta1), Map.of());

            assertThrows(UnsupportedOperationException.class,
                    () -> result.succeeded().add(meta2));
        }

        @Test
        void failedIsDefensivelyCopied() {
            Map<String, String> mutable = new LinkedHashMap<>();
            mutable.put("a.txt", "error");

            BatchUploadResult result = new BatchUploadResult(List.of(), mutable);
            mutable.put("b.txt", "error2");

            assertEquals(1, result.failed().size());
        }

        @Test
        void failedIsUnmodifiable() {
            BatchUploadResult result = new BatchUploadResult(
                    List.of(), Map.of("a.txt", "error"));

            assertThrows(UnsupportedOperationException.class,
                    () -> result.failed().put("b.txt", "error2"));
        }
    }

    @Nested
    class FailureReasons {

        // F1
        @Test
        void emptyFailed_returnsEmptyMap() {
            BatchUploadResult result = new BatchUploadResult(List.of(meta1), Map.of());
            assertTrue(result.failureReasons().isEmpty());
        }

        // F2
        @Test
        void singleReason_countsAll() {
            BatchUploadResult result = new BatchUploadResult(
                    List.of(),
                    Map.of("a.txt", "storage down",
                           "b.txt", "storage down",
                           "c.txt", "storage down"));
            assertEquals(Map.of("storage down", 3), result.failureReasons());
        }

        // F3
        @Test
        void mixedReasons_perReasonCounts() {
            BatchUploadResult result = new BatchUploadResult(
                    List.of(),
                    Map.of("a.txt", "storage down",
                           "b.txt", "invalid filename",
                           "c.txt", "storage down",
                           "d.txt", "quota exceeded"));
            Map<String, Integer> reasons = result.failureReasons();
            assertEquals(2, reasons.get("storage down"));
            assertEquals(1, reasons.get("invalid filename"));
            assertEquals(1, reasons.get("quota exceeded"));
        }

        // F4
        @Test
        void returnedMapIsImmutable() {
            BatchUploadResult result = new BatchUploadResult(
                    List.of(), Map.of("a.txt", "error"));
            assertThrows(UnsupportedOperationException.class,
                    () -> result.failureReasons().put("other", 1));
        }
    }
}
