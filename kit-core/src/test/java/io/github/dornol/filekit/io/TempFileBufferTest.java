package io.github.dornol.filekit.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempFileBufferTest {

    // T1
    @Test
    void create_returnsPathAndFileExists() throws IOException {
        TempFileBuffer buf = TempFileBuffer.create("test-create-");
        try {
            assertTrue(Files.exists(buf.path()), "temp file should exist after create");
            assertTrue(buf.path().getFileName().toString().startsWith("test-create-"));
        } finally {
            buf.close();
        }
    }

    // T2
    @Test
    void close_deletesFile() throws IOException {
        TempFileBuffer buf = TempFileBuffer.create("test-delete-");
        Path p = buf.path();
        assertTrue(Files.exists(p));
        buf.close();
        assertFalse(Files.exists(p), "file must be deleted after close");
    }

    // T3
    @Test
    void close_idempotent() throws IOException {
        TempFileBuffer buf = TempFileBuffer.create("test-idem-");
        buf.close();
        // Second close must not throw or have side effects
        buf.close();
        assertFalse(Files.exists(buf.path()));
    }

    // T4
    @Test
    void pathReturnsSameInstance_afterClose() throws IOException {
        TempFileBuffer buf = TempFileBuffer.create("test-path-");
        Path before = buf.path();
        buf.close();
        Path after = buf.path();
        assertSame(before, after);
    }

    // T5
    @Test
    void tryWithResources_normalExit_cleansUp() throws IOException {
        Path p;
        try (TempFileBuffer buf = TempFileBuffer.create("test-twr-normal-")) {
            p = buf.path();
            assertTrue(Files.exists(p));
        }
        assertFalse(Files.exists(p));
    }

    // T6
    @Test
    void tryWithResources_exception_cleansUp() {
        Path[] captured = new Path[1];
        assertThrows(RuntimeException.class, () -> {
            try (TempFileBuffer buf = TempFileBuffer.create("test-twr-ex-")) {
                captured[0] = buf.path();
                throw new RuntimeException("boom");
            }
        });
        assertFalse(Files.exists(captured[0]), "temp file must be deleted after exception");
    }

    // T7
    @Test
    void close_whenFileAlreadyDeleted_isNoop() throws IOException {
        TempFileBuffer buf = TempFileBuffer.create("test-already-");
        Files.delete(buf.path()); // pre-delete manually
        // close should swallow/no-op (deleteIfExists handles missing file)
        buf.close();
        assertFalse(Files.exists(buf.path()));
    }

    // T8
    @Test
    void create_nullPrefix_throws() {
        assertThrows(NullPointerException.class, () -> TempFileBuffer.create(null));
    }

    // T9
    @Test
    void createdFile_hasTmpSuffix() throws IOException {
        try (TempFileBuffer buf = TempFileBuffer.create("test-suffix-")) {
            String name = buf.path().getFileName().toString();
            assertTrue(name.endsWith(".tmp"), "expected .tmp suffix, got: " + name);
        }
    }

    // Bonus — create multiple, they get distinct paths
    @Test
    void create_returnsDistinctPaths() throws IOException {
        try (TempFileBuffer a = TempFileBuffer.create("test-distinct-");
             TempFileBuffer b = TempFileBuffer.create("test-distinct-")) {
            assertFalse(a.path().equals(b.path()));
        }
    }

    // Bonus — path survives after close content-wise (same object reference,
    // but file gone).  Match T4 but verify file state.
    @Test
    void closedBuffer_pathStillReadable_butFileGone() throws IOException {
        TempFileBuffer buf = TempFileBuffer.create("test-state-");
        Path p = buf.path();
        assertEquals(p.getFileName(), buf.path().getFileName());
        buf.close();
        assertEquals(p.getFileName(), buf.path().getFileName());
        assertFalse(Files.exists(buf.path()));
    }

    // ── release() ─────────────────────────────────────────────────────

    // R1
    @Test
    void release_returnsSamePathInstance() throws IOException {
        TempFileBuffer buf = TempFileBuffer.create("test-release-");
        try {
            Path p = buf.release();
            assertSame(buf.path(), p);
        } finally {
            Files.deleteIfExists(buf.path());
        }
    }

    // R2
    @Test
    void release_keepsFileAfterTryWithResources() throws IOException {
        Path captured;
        try (TempFileBuffer buf = TempFileBuffer.create("test-release-twr-")) {
            captured = buf.release();
        }
        try {
            assertTrue(Files.exists(captured),
                    "file must survive try-with-resources when released");
        } finally {
            Files.deleteIfExists(captured);
        }
    }

    // R3
    @Test
    void release_thenExplicitClose_isNoop() throws IOException {
        TempFileBuffer buf = TempFileBuffer.create("test-release-close-");
        try {
            Path p = buf.release();
            buf.close();
            assertTrue(Files.exists(p),
                    "close after release must not delete the file");
        } finally {
            Files.deleteIfExists(buf.path());
        }
    }

    // R4
    @Test
    void doubleRelease_throws() throws IOException {
        TempFileBuffer buf = TempFileBuffer.create("test-release-double-");
        try {
            buf.release();
            assertThrows(IllegalStateException.class, buf::release);
        } finally {
            Files.deleteIfExists(buf.path());
        }
    }

    // R5
    @Test
    void releaseAfterClose_throws() throws IOException {
        TempFileBuffer buf = TempFileBuffer.create("test-release-after-close-");
        buf.close();
        assertThrows(IllegalStateException.class, buf::release);
    }
}
