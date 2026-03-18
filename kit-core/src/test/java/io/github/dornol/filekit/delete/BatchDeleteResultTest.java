package io.github.dornol.filekit.delete;

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

class BatchDeleteResultTest {

    @Nested
    class Validation {

        @Test
        void validResult() {
            BatchDeleteResult result = new BatchDeleteResult(
                    List.of("key1", "key2"), Map.of("key3", "not found"));

            assertEquals(2, result.succeeded().size());
            assertEquals(1, result.failed().size());
        }

        @Test
        void emptyResult() {
            BatchDeleteResult result = new BatchDeleteResult(List.of(), Map.of());

            assertEquals(0, result.succeeded().size());
            assertEquals(0, result.failed().size());
        }

        @Test
        void nullSucceeded_throws() {
            assertThrows(NullPointerException.class,
                    () -> new BatchDeleteResult(null, Map.of()));
        }

        @Test
        void nullFailed_throws() {
            assertThrows(NullPointerException.class,
                    () -> new BatchDeleteResult(List.of(), null));
        }
    }

    @Nested
    class ConvenienceMethods {

        @Test
        void totalRequested_sumOfBoth() {
            BatchDeleteResult result = new BatchDeleteResult(
                    List.of("a", "b"), Map.of("c", "error"));

            assertEquals(3, result.totalRequested());
        }

        @Test
        void allSucceeded_trueWhenNoFailures() {
            BatchDeleteResult result = new BatchDeleteResult(
                    List.of("a", "b"), Map.of());

            assertTrue(result.allSucceeded());
        }

        @Test
        void allSucceeded_falseWhenFailuresExist() {
            BatchDeleteResult result = new BatchDeleteResult(
                    List.of("a"), Map.of("b", "error"));

            assertFalse(result.allSucceeded());
        }

        @Test
        void allSucceeded_trueWhenEmpty() {
            BatchDeleteResult result = new BatchDeleteResult(List.of(), Map.of());

            assertTrue(result.allSucceeded());
        }

        @Test
        void totalRequested_zeroWhenEmpty() {
            BatchDeleteResult result = new BatchDeleteResult(List.of(), Map.of());

            assertEquals(0, result.totalRequested());
        }
    }

    @Nested
    class DefensiveCopy {

        @Test
        void succeededIsDefensivelyCopied() {
            List<String> mutable = new ArrayList<>();
            mutable.add("key1");

            BatchDeleteResult result = new BatchDeleteResult(mutable, Map.of());
            mutable.add("key2");

            assertEquals(1, result.succeeded().size());
        }

        @Test
        void succeededIsUnmodifiable() {
            BatchDeleteResult result = new BatchDeleteResult(List.of("key1"), Map.of());

            assertThrows(UnsupportedOperationException.class,
                    () -> result.succeeded().add("key2"));
        }

        @Test
        void failedIsDefensivelyCopied() {
            Map<String, String> mutable = new LinkedHashMap<>();
            mutable.put("key1", "error");

            BatchDeleteResult result = new BatchDeleteResult(List.of(), mutable);
            mutable.put("key2", "error2");

            assertEquals(1, result.failed().size());
        }

        @Test
        void failedIsUnmodifiable() {
            BatchDeleteResult result = new BatchDeleteResult(
                    List.of(), Map.of("key1", "error"));

            assertThrows(UnsupportedOperationException.class,
                    () -> result.failed().put("key2", "error2"));
        }
    }
}
