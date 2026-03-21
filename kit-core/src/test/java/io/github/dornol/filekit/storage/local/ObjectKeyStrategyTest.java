package io.github.dornol.filekit.storage.local;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectKeyStrategyTest {

    @Test
    void flat_returnsKeyDotExtension() {
        ObjectKeyStrategy strategy = ObjectKeyStrategy.flat();
        assertEquals("my-key.png", strategy.resolve("my-key", "png"));
    }

    @Test
    void dateBased_includesCurrentDate() {
        ObjectKeyStrategy strategy = ObjectKeyStrategy.dateBased();
        String result = strategy.resolve("abc", "txt");

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        assertEquals(today + "/abc.txt", result);
    }

    @Test
    void hashPrefixed_depth1() {
        ObjectKeyStrategy strategy = ObjectKeyStrategy.hashPrefixed(1);
        // key "abcd-1234..." → normalized "abcd1234..." → prefix "ab/"
        String result = strategy.resolve("abcd-1234", "jpg");
        assertTrue(result.startsWith("ab/"));
        assertTrue(result.endsWith("abcd-1234.jpg"));
    }

    @Test
    void hashPrefixed_depth2() {
        ObjectKeyStrategy strategy = ObjectKeyStrategy.hashPrefixed(2);
        String result = strategy.resolve("abcd-ef12", "png");
        // normalized: "abcdef12" → "ab/cd/"
        assertTrue(result.startsWith("ab/cd/"));
        assertTrue(result.endsWith("abcd-ef12.png"));
    }

    @Test
    void hashPrefixed_invalidDepth_throws() {
        assertThrows(IllegalArgumentException.class, () -> ObjectKeyStrategy.hashPrefixed(0));
        assertThrows(IllegalArgumentException.class, () -> ObjectKeyStrategy.hashPrefixed(5));
    }

    @Test
    void hashPrefixed_shortKey_throws() {
        ObjectKeyStrategy strategy = ObjectKeyStrategy.hashPrefixed(2);
        // "ab" after removing hyphens is only 2 chars, need 4 for depth 2
        assertThrows(IllegalArgumentException.class,
                () -> strategy.resolve("ab", "txt"));
    }

    @Test
    void hashPrefixed_exactMinimumKeyLength_works() {
        ObjectKeyStrategy strategy = ObjectKeyStrategy.hashPrefixed(2);
        // "abcd" is exactly 4 chars, enough for depth 2
        String result = strategy.resolve("abcd", "txt");
        assertTrue(result.startsWith("ab/cd/"));
        assertTrue(result.endsWith("abcd.txt"));
    }

}
