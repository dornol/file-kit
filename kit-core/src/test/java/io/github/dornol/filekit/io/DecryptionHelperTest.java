package io.github.dornol.filekit.io;

import io.github.dornol.filekit.spi.FileEncryptor;
import io.github.dornol.filekit.spi.NoOpFileEncryptor;
import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DecryptionHelperTest {

    @Nested
    class SuccessfulDecryption {

        @Test
        void passThroughEncryptor_returnsOriginalContent() throws IOException {
            FileEncryptor passThrough = new FileEncryptor() {
                @Override
                public void encrypt(InputStream in, OutputStream out) throws IOException {
                    in.transferTo(out);
                }
                @Override
                public void decrypt(InputStream in, OutputStream out) throws IOException {
                    in.transferTo(out);
                }
            };

            byte[] original = "hello world".getBytes();
            try (InputStream result = DecryptionHelper.decryptToStream(
                    new ByteArrayInputStream(original), passThrough)) {
                assertArrayEquals(original, result.readAllBytes());
            }
        }

        @Test
        void xorEncryptor_decryptsCorrectly() throws IOException {
            FileEncryptor xor = new FileEncryptor() {
                @Override
                public void encrypt(InputStream in, OutputStream out) throws IOException {
                    int b;
                    while ((b = in.read()) != -1) out.write(b ^ 0x42);
                }
                @Override
                public void decrypt(InputStream in, OutputStream out) throws IOException {
                    int b;
                    while ((b = in.read()) != -1) out.write(b ^ 0x42);
                }
            };

            byte[] plain = "secret data".getBytes();
            byte[] encrypted = new byte[plain.length];
            for (int i = 0; i < plain.length; i++) encrypted[i] = (byte) (plain[i] ^ 0x42);

            try (InputStream result = DecryptionHelper.decryptToStream(
                    new ByteArrayInputStream(encrypted), xor)) {
                assertArrayEquals(plain, result.readAllBytes());
            }
        }

        @Test
        void emptyContent_returnsEmptyStream() throws IOException {
            FileEncryptor passThrough = new FileEncryptor() {
                @Override
                public void encrypt(InputStream in, OutputStream out) throws IOException {
                    in.transferTo(out);
                }
                @Override
                public void decrypt(InputStream in, OutputStream out) throws IOException {
                    in.transferTo(out);
                }
            };

            try (InputStream result = DecryptionHelper.decryptToStream(
                    new ByteArrayInputStream(new byte[0]), passThrough)) {
                assertEquals(0, result.readAllBytes().length);
            }
        }

        @Test
        void closingReturnedStream_cleansUpTempFile() throws IOException {
            FileEncryptor passThrough = new FileEncryptor() {
                @Override
                public void encrypt(InputStream in, OutputStream out) throws IOException {
                    in.transferTo(out);
                }
                @Override
                public void decrypt(InputStream in, OutputStream out) throws IOException {
                    in.transferTo(out);
                }
            };

            InputStream result = DecryptionHelper.decryptToStream(
                    new ByteArrayInputStream("data".getBytes()), passThrough);

            // Should not throw when closing
            result.readAllBytes();
            result.close();
        }

        @Test
        void inputStreamIsConsumedAndClosed() throws IOException {
            boolean[] closed = {false};
            InputStream tracked = new ByteArrayInputStream("data".getBytes()) {
                @Override
                public void close() throws IOException {
                    closed[0] = true;
                    super.close();
                }
            };

            FileEncryptor passThrough = new FileEncryptor() {
                @Override
                public void encrypt(InputStream in, OutputStream out) throws IOException {
                    in.transferTo(out);
                }
                @Override
                public void decrypt(InputStream in, OutputStream out) throws IOException {
                    in.transferTo(out);
                }
            };

            try (InputStream result = DecryptionHelper.decryptToStream(tracked, passThrough)) {
                result.readAllBytes();
            }

            assertTrue(closed[0], "Original encrypted input stream should be closed");
        }
    }

    @Nested
    class DecryptionFailure {

        @Test
        void ioExceptionDuringDecrypt_throwsDecryptionFailed() {
            FileEncryptor failing = new FileEncryptor() {
                @Override
                public void encrypt(InputStream in, OutputStream out) {}
                @Override
                public void decrypt(InputStream in, OutputStream out) throws IOException {
                    throw new IOException("decryption error");
                }
            };

            FileStorageException ex = assertThrows(FileStorageException.class,
                    () -> DecryptionHelper.decryptToStream(
                            new ByteArrayInputStream("data".getBytes()), failing));
            assertEquals(FileStorageException.DECRYPTION_FAILED, ex.getMessageKey());
        }

        @Test
        void decryptionFailure_closesOriginalStream() {
            boolean[] closed = {false};
            InputStream tracked = new ByteArrayInputStream("data".getBytes()) {
                @Override
                public void close() throws IOException {
                    closed[0] = true;
                    super.close();
                }
            };

            FileEncryptor failing = new FileEncryptor() {
                @Override
                public void encrypt(InputStream in, OutputStream out) {}
                @Override
                public void decrypt(InputStream in, OutputStream out) throws IOException {
                    throw new IOException("fail");
                }
            };

            assertThrows(FileStorageException.class,
                    () -> DecryptionHelper.decryptToStream(tracked, failing));

            assertTrue(closed[0], "Original stream should be closed even on failure");
        }
    }
}
