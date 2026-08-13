package io.github.dornol.filekit.scan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClamAvVirusScannerTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void cleanResponse_streamsInputAndReturnsClean() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Future<byte[]> received = executor.submit(() -> receiveAndRespond(server, "stream: OK\0"));
            ClamAvVirusScanner scanner = scanner(server);
            byte[] content = "large-ish content".repeat(1000).getBytes(StandardCharsets.UTF_8);

            ScanResult result = scanner.scan(content);

            assertEquals(ScanResult.Status.CLEAN, result.status());
            assertArrayEquals(content, received.get());
        }
    }

    @Test
    void infectedResponse_returnsInfected() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Future<byte[]> received = executor.submit(
                    () -> receiveAndRespond(server, "stream: Eicar-Test-Signature FOUND\0"));

            ScanResult result = scanner(server).scan("file".getBytes(StandardCharsets.UTF_8));

            assertEquals(ScanResult.Status.INFECTED, result.status());
            assertTrue(result.message().contains("Eicar-Test-Signature"));
            received.get();
        }
    }

    @Test
    void unexpectedResponse_returnsError() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Future<byte[]> received = executor.submit(
                    () -> receiveAndRespond(server, "unexpected\0"));

            ScanResult result = scanner(server).scan(new byte[]{1, 2, 3});

            assertEquals(ScanResult.Status.ERROR, result.status());
            received.get();
        }
    }

    @Test
    void unavailableDaemon_returnsError() throws IOException {
        try (ServerSocket server = new ServerSocket(0)) {
            ScanResult result = scanner(server).scan(new byte[]{1});
            assertEquals(ScanResult.Status.ERROR, result.status());
        }
    }

    @Test
    void invalidConfiguration_throws() {
        assertThrows(IllegalArgumentException.class, () -> new ClamAvVirusScanner(" "));
        assertThrows(IllegalArgumentException.class, () -> new ClamAvVirusScanner("localhost", 0,
                Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new ClamAvVirusScanner("localhost", 3310,
                Duration.ZERO, Duration.ofSeconds(1)));
    }

    private static ClamAvVirusScanner scanner(ServerSocket server) {
        return new ClamAvVirusScanner("localhost", server.getLocalPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private static byte[] receiveAndRespond(ServerSocket server, String response) throws Exception {
        try (Socket socket = server.accept(); DataInputStream input = new DataInputStream(socket.getInputStream())) {
            byte[] command = input.readNBytes("zINSTREAM\0".length());
            assertEquals("zINSTREAM\0", new String(command, StandardCharsets.US_ASCII));

            ByteArrayOutputStream received = new ByteArrayOutputStream();
            int length;
            while ((length = input.readInt()) != 0) {
                received.write(input.readNBytes(length));
            }
            socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return received.toByteArray();
        }
    }
}
