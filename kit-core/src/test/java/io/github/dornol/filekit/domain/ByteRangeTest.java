package io.github.dornol.filekit.domain;

import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteRangeTest {

    @Nested
    class Construction {

        @Test
        void validRange() {
            ByteRange range = new ByteRange(0, 499, 1000);

            assertEquals(0, range.start());
            assertEquals(499, range.end());
            assertEquals(1000, range.totalSize());
        }

        @Test
        void length() {
            ByteRange range = new ByteRange(0, 499, 1000);
            assertEquals(500, range.length());
        }

        @Test
        void singleByte() {
            ByteRange range = new ByteRange(0, 0, 1);
            assertEquals(1, range.length());
        }

        @Test
        void singleByteMiddle() {
            ByteRange range = new ByteRange(500, 500, 1000);
            assertEquals(1, range.length());
        }

        @Test
        void lastByte() {
            ByteRange range = new ByteRange(999, 999, 1000);
            assertEquals(1, range.length());
        }

        @Test
        void fullFile() {
            ByteRange range = new ByteRange(0, 999, 1000);
            assertEquals(1000, range.length());
        }

        @Test
        void largeFile() {
            long size = 5_000_000_000L; // 5GB
            ByteRange range = new ByteRange(0, size - 1, size);
            assertEquals(size, range.length());
        }
    }

    @Nested
    class ContentRangeHeader {

        @Test
        void basicRange() {
            ByteRange range = new ByteRange(0, 499, 1000);
            assertEquals("bytes 0-499/1000", range.toContentRangeHeader());
        }

        @Test
        void singleByte() {
            ByteRange range = new ByteRange(0, 0, 1);
            assertEquals("bytes 0-0/1", range.toContentRangeHeader());
        }

        @Test
        void middleRange() {
            ByteRange range = new ByteRange(100, 200, 500);
            assertEquals("bytes 100-200/500", range.toContentRangeHeader());
        }

        @Test
        void lastByte() {
            ByteRange range = new ByteRange(999, 999, 1000);
            assertEquals("bytes 999-999/1000", range.toContentRangeHeader());
        }

        @Test
        void largeOffsets() {
            long size = 10_000_000_000L;
            ByteRange range = new ByteRange(5_000_000_000L, 9_999_999_999L, size);
            assertEquals("bytes 5000000000-9999999999/10000000000", range.toContentRangeHeader());
        }
    }

    @Nested
    class ValidationErrors {

        @Test
        void negativeStart_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ByteRange(-1, 499, 1000));
        }

        @Test
        void endBeforeStart_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ByteRange(500, 499, 1000));
        }

        @Test
        void endEqualsTotalSize_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ByteRange(0, 1000, 1000));
        }

        @Test
        void endExceedsTotalSize_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ByteRange(0, 1500, 1000));
        }

        @Test
        void zeroTotalSize_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ByteRange(0, 0, 0));
        }

        @Test
        void negativeTotalSize_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ByteRange(0, 0, -1));
        }
    }

    @Nested
    class Parsing {

        @Test
        void fullRange() {
            ByteRange range = ByteRange.parse("bytes=0-499", 1000);

            assertEquals(0, range.start());
            assertEquals(499, range.end());
            assertEquals(1000, range.totalSize());
        }

        @Test
        void openEndedRange() {
            ByteRange range = ByteRange.parse("bytes=500-", 1000);

            assertEquals(500, range.start());
            assertEquals(999, range.end());
            assertEquals(1000, range.totalSize());
        }

        @Test
        void openEndedRange_fromStart() {
            ByteRange range = ByteRange.parse("bytes=0-", 1000);

            assertEquals(0, range.start());
            assertEquals(999, range.end());
        }

        @Test
        void openEndedRange_lastByte() {
            ByteRange range = ByteRange.parse("bytes=999-", 1000);

            assertEquals(999, range.start());
            assertEquals(999, range.end());
            assertEquals(1, range.length());
        }

        @Test
        void suffixRange() {
            ByteRange range = ByteRange.parse("bytes=-200", 1000);

            assertEquals(800, range.start());
            assertEquals(999, range.end());
            assertEquals(1000, range.totalSize());
        }

        @Test
        void suffixRange_singleByte() {
            ByteRange range = ByteRange.parse("bytes=-1", 1000);

            assertEquals(999, range.start());
            assertEquals(999, range.end());
            assertEquals(1, range.length());
        }

        @Test
        void suffixLargerThanFile() {
            ByteRange range = ByteRange.parse("bytes=-2000", 1000);

            assertEquals(0, range.start());
            assertEquals(999, range.end());
        }

        @Test
        void endClampedToTotalSize() {
            ByteRange range = ByteRange.parse("bytes=0-5000", 1000);

            assertEquals(0, range.start());
            assertEquals(999, range.end());
        }

        @Test
        void singleByteRange() {
            ByteRange range = ByteRange.parse("bytes=0-0", 1000);

            assertEquals(0, range.start());
            assertEquals(0, range.end());
            assertEquals(1, range.length());
        }

        @Test
        void twoBytesRange() {
            ByteRange range = ByteRange.parse("bytes=0-1", 1000);

            assertEquals(0, range.start());
            assertEquals(1, range.end());
            assertEquals(2, range.length());
        }

        @Test
        void middleRange() {
            ByteRange range = ByteRange.parse("bytes=100-200", 500);

            assertEquals(100, range.start());
            assertEquals(200, range.end());
            assertEquals(101, range.length());
        }

        @Test
        void whitespaceTrimmed() {
            ByteRange range = ByteRange.parse("bytes= 0-499 ", 1000);

            // Leading space in range spec should be handled
            assertEquals(0, range.start());
            assertEquals(499, range.end());
        }
    }

    @Nested
    class ParsingErrors {

        @Test
        void nullHeader_throws() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> ByteRange.parse(null, 1000));
            assertEquals(FileStorageException.RANGE_NOT_SATISFIABLE, ex.getMessageKey());
        }

        @Test
        void emptyHeader_throws() {
            assertThrows(FileStorageException.class,
                    () -> ByteRange.parse("", 1000));
        }

        @Test
        void invalidPrefix_throws() {
            assertThrows(FileStorageException.class,
                    () -> ByteRange.parse("items=0-499", 1000));
        }

        @Test
        void multiRange_throws() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> ByteRange.parse("bytes=0-100,200-300", 1000));
            assertEquals(FileStorageException.RANGE_NOT_SATISFIABLE, ex.getMessageKey());
        }

        @Test
        void startBeyondFileSize_throws() {
            assertThrows(FileStorageException.class,
                    () -> ByteRange.parse("bytes=1000-", 1000));
        }

        @Test
        void startEqualToFileSize_throws() {
            assertThrows(FileStorageException.class,
                    () -> ByteRange.parse("bytes=1000-", 1000));
        }

        @Test
        void invalidFormat_throws() {
            assertThrows(FileStorageException.class,
                    () -> ByteRange.parse("bytes=abc-def", 1000));
        }

        @Test
        void floatingPoint_throws() {
            assertThrows(FileStorageException.class,
                    () -> ByteRange.parse("bytes=0.5-499.5", 1000));
        }

        @Test
        void onlyPrefix_throws() {
            assertThrows(FileStorageException.class,
                    () -> ByteRange.parse("bytes=", 1000));
        }

        @Test
        void suffixZero_throws() {
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> ByteRange.parse("bytes=-0", 1000));
            assertEquals(FileStorageException.RANGE_NOT_SATISFIABLE, ex.getMessageKey());
        }

        @Test
        void suffixNegative_throws() {
            // "bytes=--5" is parsed as suffix with substring(1) = "-5", Long.parseLong("-5") = -5, which is <= 0
            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> ByteRange.parse("bytes=--5", 1000));
            assertEquals(FileStorageException.RANGE_NOT_SATISFIABLE, ex.getMessageKey());
        }
    }
}
