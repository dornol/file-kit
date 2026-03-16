package io.github.dornol.filekit.io;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedInputStreamTest {

    @Nested
    class ReadExactBytes {

        @Test
        void readsUpToMaxBytes() throws IOException {
            byte[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 5);

            byte[] result = bis.readAllBytes();

            assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, result);
        }

        @Test
        void readsAllWhenMaxExceedsData() throws IOException {
            byte[] data = {1, 2, 3};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 10);

            byte[] result = bis.readAllBytes();

            assertArrayEquals(new byte[]{1, 2, 3}, result);
        }

        @Test
        void readSingleByte() throws IOException {
            byte[] data = {42, 43, 44};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 2);

            assertEquals(42, bis.read());
            assertEquals(43, bis.read());
            assertEquals(-1, bis.read());
        }

        @Test
        void readExactlyMaxBytes() throws IOException {
            byte[] data = {1, 2, 3, 4, 5};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 5);

            byte[] result = bis.readAllBytes();

            assertArrayEquals(data, result);
        }

        @Test
        void readOneByte() throws IOException {
            byte[] data = {42, 43, 44};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 1);

            assertEquals(42, bis.read());
            assertEquals(-1, bis.read());
        }
    }

    @Nested
    class BufferedReads {

        @Test
        void readWithOffset() throws IOException {
            byte[] data = {1, 2, 3, 4, 5};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 3);

            byte[] buf = new byte[10];
            int n = bis.read(buf, 2, 5);

            assertEquals(3, n);
            assertEquals(1, buf[2]);
            assertEquals(2, buf[3]);
            assertEquals(3, buf[4]);
        }

        @Test
        void multipleBufferedReads() throws IOException {
            byte[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 7);

            byte[] buf1 = new byte[3];
            int n1 = bis.read(buf1, 0, 3);
            assertEquals(3, n1);
            assertArrayEquals(new byte[]{1, 2, 3}, buf1);

            byte[] buf2 = new byte[3];
            int n2 = bis.read(buf2, 0, 3);
            assertEquals(3, n2);
            assertArrayEquals(new byte[]{4, 5, 6}, buf2);

            byte[] buf3 = new byte[3];
            int n3 = bis.read(buf3, 0, 3);
            assertEquals(1, n3); // only 1 byte remaining
            assertEquals(7, buf3[0]);
        }

        @Test
        void readLargeBufferLimitedByRemaining() throws IOException {
            byte[] data = {1, 2, 3};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 2);

            byte[] buf = new byte[100];
            int n = bis.read(buf, 0, 100);

            assertEquals(2, n);
            assertEquals(1, buf[0]);
            assertEquals(2, buf[1]);
        }
    }

    @Nested
    class EarlyEof {

        @Test
        void returnsMinusOneWhenLimitReached() throws IOException {
            byte[] data = {1, 2, 3};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 2);

            byte[] buf = new byte[10];
            int n1 = bis.read(buf, 0, 10);
            assertEquals(2, n1);

            int n2 = bis.read(buf, 0, 10);
            assertEquals(-1, n2);
        }

        @Test
        void singleByteReadReturnsMinusOneAfterLimit() throws IOException {
            byte[] data = {1, 2};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 1);

            assertEquals(1, bis.read());
            assertEquals(-1, bis.read());
            assertEquals(-1, bis.read()); // repeated calls still return -1
        }

        @Test
        void underlyingStreamExhaustedBeforeLimit() throws IOException {
            byte[] data = {1, 2};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 10);

            assertEquals(1, bis.read());
            assertEquals(2, bis.read());
            assertEquals(-1, bis.read()); // underlying stream exhausted
        }
    }

    @Nested
    class ZeroLength {

        @Test
        void zeroMaxBytes_readsNothing() throws IOException {
            byte[] data = {1, 2, 3};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 0);

            assertEquals(-1, bis.read());
            assertEquals(-1, bis.read(new byte[10], 0, 10));
        }

        @Test
        void zeroMaxBytes_readAllBytesReturnsEmpty() throws IOException {
            byte[] data = {1, 2, 3};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 0);

            byte[] result = bis.readAllBytes();
            assertEquals(0, result.length);
        }
    }

    @Nested
    class Validation {

        @Test
        void negativeMaxBytes_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedInputStream(new ByteArrayInputStream(new byte[0]), -1));
        }

        @Test
        void negativeMaxValue_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedInputStream(new ByteArrayInputStream(new byte[0]), Long.MIN_VALUE));
        }
    }

    @Nested
    class SkipAndAvailable {

        @Test
        void skip_respectsLimit() throws IOException {
            byte[] data = {1, 2, 3, 4, 5};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 3);

            long skipped = bis.skip(2);
            assertEquals(2, skipped);
            assertEquals(3, bis.read()); // reads third byte
            assertEquals(-1, bis.read()); // limit reached
        }

        @Test
        void skip_moreThanRemaining() throws IOException {
            byte[] data = {1, 2, 3, 4, 5};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 3);

            long skipped = bis.skip(10);
            assertEquals(3, skipped); // limited to remaining
            assertEquals(-1, bis.read());
        }

        @Test
        void skip_zero() throws IOException {
            byte[] data = {1, 2, 3};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 3);

            long skipped = bis.skip(0);
            assertEquals(0, skipped);
            assertEquals(1, bis.read()); // still reads from start
        }

        @Test
        void skip_afterPartialRead() throws IOException {
            byte[] data = {1, 2, 3, 4, 5};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 4);

            assertEquals(1, bis.read()); // read 1 byte
            long skipped = bis.skip(2); // skip 2 bytes
            assertEquals(2, skipped);
            assertEquals(4, bis.read()); // read 4th byte
            assertEquals(-1, bis.read()); // limit reached
        }

        @Test
        void available_respectsLimit() throws IOException {
            byte[] data = {1, 2, 3, 4, 5};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 3);

            assertEquals(3, bis.available());
        }

        @Test
        void available_afterPartialRead() throws IOException {
            byte[] data = {1, 2, 3, 4, 5};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 3);

            bis.read();
            assertEquals(2, bis.available());
        }

        @Test
        void available_afterExhausted() throws IOException {
            byte[] data = {1, 2, 3};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 2);

            bis.readAllBytes();
            assertEquals(0, bis.available());
        }

        @Test
        void available_zero() throws IOException {
            byte[] data = {1, 2, 3};
            BoundedInputStream bis = new BoundedInputStream(new ByteArrayInputStream(data), 0);

            assertEquals(0, bis.available());
        }
    }
}
