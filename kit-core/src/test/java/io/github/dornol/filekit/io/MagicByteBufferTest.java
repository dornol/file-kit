package io.github.dornol.filekit.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MagicByteBufferTest {

    // M1
    @Test
    void defaultCapacity() {
        MagicByteBuffer b = new MagicByteBuffer();
        assertEquals(MagicByteBuffer.DEFAULT_SIZE, b.capacity());
        assertEquals(0, b.size());
    }

    // M2
    @Test
    void customCapacity() {
        MagicByteBuffer b = new MagicByteBuffer(4096);
        assertEquals(4096, b.capacity());
        assertEquals(0, b.size());
    }

    // M3
    @Test
    void belowMinCapacity_throws() {
        assertThrows(IllegalArgumentException.class, () -> new MagicByteBuffer(100));
        assertThrows(IllegalArgumentException.class, () -> new MagicByteBuffer(0));
        assertThrows(IllegalArgumentException.class, () -> new MagicByteBuffer(-1));
    }

    // M4
    @Test
    void observe_growsSize() {
        MagicByteBuffer b = new MagicByteBuffer(MagicByteBuffer.MIN_SIZE);
        byte[] data = new byte[512];
        b.observe(data, 0, data.length);
        assertEquals(512, b.size());
    }

    // M5
    @Test
    void observeOverCapacity_trims() {
        MagicByteBuffer b = new MagicByteBuffer(MagicByteBuffer.MIN_SIZE);
        byte[] data = new byte[MagicByteBuffer.MIN_SIZE + 500];
        b.observe(data, 0, data.length);
        assertEquals(MagicByteBuffer.MIN_SIZE, b.size());

        // Subsequent observes after full are ignored
        b.observe(new byte[]{9, 9, 9}, 0, 3);
        assertEquals(MagicByteBuffer.MIN_SIZE, b.size());
    }

    // M6
    @Test
    void asInputStream_returnsCapturedBytes() throws IOException {
        MagicByteBuffer b = new MagicByteBuffer();
        byte[] data = "hello world magic".getBytes();
        b.observe(data, 0, data.length);

        try (InputStream in = b.asInputStream()) {
            assertArrayEquals(data, in.readAllBytes());
        }
    }

    // M7
    @Test
    void emptyBuffer_streamReturnsEof() throws IOException {
        MagicByteBuffer b = new MagicByteBuffer();
        try (InputStream in = b.asInputStream()) {
            assertEquals(-1, in.read());
        }
    }

    // M8
    @Test
    void fragmentedObserve_concatenates() throws IOException {
        MagicByteBuffer b = new MagicByteBuffer();
        b.observe(new byte[]{'a', 'b', 'c', 'd'}, 0, 4);
        b.observe(new byte[]{'e', 'f', 'g', 'h'}, 0, 4);
        assertEquals(8, b.size());

        try (InputStream in = b.asInputStream()) {
            assertArrayEquals("abcdefgh".getBytes(), in.readAllBytes());
        }
    }

    // Bonus — observe with offset/len
    @Test
    void observe_respectsOffsetAndLen() throws IOException {
        MagicByteBuffer b = new MagicByteBuffer();
        byte[] src = "--HELLO--".getBytes();
        b.observe(src, 2, 5);
        try (InputStream in = b.asInputStream()) {
            assertArrayEquals("HELLO".getBytes(), in.readAllBytes());
        }
    }

    // Bonus — zero-len observe is no-op
    @Test
    void observe_zeroLen_noOp() {
        MagicByteBuffer b = new MagicByteBuffer();
        b.observe(new byte[]{1, 2, 3}, 0, 0);
        assertEquals(0, b.size());
    }
}
