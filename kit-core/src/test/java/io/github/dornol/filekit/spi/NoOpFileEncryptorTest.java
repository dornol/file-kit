package io.github.dornol.filekit.spi;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoOpFileEncryptorTest {

    NoOpFileEncryptor encryptor = new NoOpFileEncryptor();

    @Test
    void encrypt_copiesInputToOutput() throws IOException {
        byte[] data = "hello world".getBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        encryptor.encrypt(new ByteArrayInputStream(data), out);

        assertArrayEquals(data, out.toByteArray());
    }

    @Test
    void decrypt_copiesInputToOutput() throws IOException {
        byte[] data = "encrypted content".getBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        encryptor.decrypt(new ByteArrayInputStream(data), out);

        assertArrayEquals(data, out.toByteArray());
    }

    @Test
    void encrypt_emptyInput() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        encryptor.encrypt(new ByteArrayInputStream(new byte[0]), out);

        assertArrayEquals(new byte[0], out.toByteArray());
    }

    @Test
    void decrypt_emptyInput() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        encryptor.decrypt(new ByteArrayInputStream(new byte[0]), out);

        assertArrayEquals(new byte[0], out.toByteArray());
    }

    @Test
    void isEnabled_returnsFalse() {
        assertFalse(encryptor.isEnabled());
    }

    @Test
    void defaultIsEnabled_returnsTrue() {
        // A custom FileEncryptor (not NoOp) should return true by default
        FileEncryptor custom = new FileEncryptor() {
            @Override
            public void encrypt(InputStream plainInput, java.io.OutputStream cipherOutput) {}
            @Override
            public void decrypt(InputStream cipherInput, java.io.OutputStream plainOutput) {}
        };
        assertTrue(custom.isEnabled());
    }

    @Test
    void encrypt_largeData() throws IOException {
        byte[] data = new byte[1024 * 1024]; // 1MB
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 256);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        encryptor.encrypt(new ByteArrayInputStream(data), out);

        assertArrayEquals(data, out.toByteArray());
    }
}
