package io.github.dornol.filekit.transfer;

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

class BatchTransferResultTest {

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
            BatchTransferResult result = new BatchTransferResult(
                    List.of(meta1, meta2), Map.of("missing", "not found"));

            assertEquals(2, result.succeeded().size());
            assertEquals(1, result.failed().size());
        }

        @Test
        void emptyResult() {
            BatchTransferResult result = new BatchTransferResult(List.of(), Map.of());

            assertEquals(0, result.succeeded().size());
            assertEquals(0, result.failed().size());
        }

        @Test
        void nullSucceeded_throws() {
            assertThrows(NullPointerException.class,
                    () -> new BatchTransferResult(null, Map.of()));
        }

        @Test
        void nullFailed_throws() {
            assertThrows(NullPointerException.class,
                    () -> new BatchTransferResult(List.of(), null));
        }
    }

    @Nested
    class ConvenienceMethods {

        @Test
        void totalRequested_sumOfBoth() {
            BatchTransferResult result = new BatchTransferResult(
                    List.of(meta1), Map.of("missing", "error"));

            assertEquals(2, result.totalRequested());
        }

        @Test
        void allSucceeded_trueWhenNoFailures() {
            BatchTransferResult result = new BatchTransferResult(
                    List.of(meta1, meta2), Map.of());

            assertTrue(result.allSucceeded());
        }

        @Test
        void allSucceeded_falseWhenFailuresExist() {
            BatchTransferResult result = new BatchTransferResult(
                    List.of(meta1), Map.of("missing", "error"));

            assertFalse(result.allSucceeded());
        }

        @Test
        void allSucceeded_trueWhenEmpty() {
            assertTrue(new BatchTransferResult(List.of(), Map.of()).allSucceeded());
        }

        @Test
        void totalRequested_zeroWhenEmpty() {
            assertEquals(0, new BatchTransferResult(List.of(), Map.of()).totalRequested());
        }
    }

    @Nested
    class DefensiveCopy {

        @Test
        void succeededIsDefensivelyCopied() {
            List<FileMetadata> mutable = new ArrayList<>();
            mutable.add(meta1);

            BatchTransferResult result = new BatchTransferResult(mutable, Map.of());
            mutable.add(meta2);

            assertEquals(1, result.succeeded().size());
        }

        @Test
        void succeededIsUnmodifiable() {
            BatchTransferResult result = new BatchTransferResult(List.of(meta1), Map.of());

            assertThrows(UnsupportedOperationException.class,
                    () -> result.succeeded().add(meta2));
        }

        @Test
        void failedIsDefensivelyCopied() {
            Map<String, String> mutable = new LinkedHashMap<>();
            mutable.put("k1", "error");

            BatchTransferResult result = new BatchTransferResult(List.of(), mutable);
            mutable.put("k2", "error2");

            assertEquals(1, result.failed().size());
        }

        @Test
        void failedIsUnmodifiable() {
            BatchTransferResult result = new BatchTransferResult(
                    List.of(), Map.of("k1", "error"));

            assertThrows(UnsupportedOperationException.class,
                    () -> result.failed().put("k2", "error2"));
        }
    }
}
