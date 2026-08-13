package io.github.dornol.filekit.scan;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Streaming {@link VirusScanner} implementation for the ClamAV daemon's
 * {@code INSTREAM} protocol.
 *
 * <p>The input is sent in bounded chunks and is never buffered in memory by
 * this implementation. A scanner error or malformed daemon response returns
 * {@link ScanResult.Status#ERROR}; upload services using this result therefore
 * reject the file by default.</p>
 *
 * <p>ClamAV's TCP endpoint must be protected by network policy. Do not expose
 * it directly to untrusted networks.</p>
 */
public final class ClamAvVirusScanner implements VirusScanner {

    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final int BUFFER_SIZE = 32 * 1024;
    private static final int MAX_RESPONSE_BYTES = 4096;

    private final String host;
    private final int port;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    /**
     * Creates a scanner using the standard ClamAV port and five-second timeouts.
     *
     * @param host ClamAV daemon host
     */
    public ClamAvVirusScanner(String host) {
        this(host, 3310, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    /**
     * Creates a scanner with explicit network settings.
     *
     * @param host            ClamAV daemon host
     * @param port            ClamAV daemon TCP port
     * @param connectTimeout  maximum time to establish a connection
     * @param readTimeout     maximum socket read wait between daemon responses
     */
    public ClamAvVirusScanner(String host, int port,
                              Duration connectTimeout, Duration readTimeout) {
        this.host = Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535: " + port);
        }
        this.connectTimeoutMillis = timeoutMillis(connectTimeout, "connectTimeout");
        this.readTimeoutMillis = timeoutMillis(readTimeout, "readTimeout");
        this.port = port;
    }

    @Override
    public ScanResult scan(byte[] fileBytes) {
        Objects.requireNonNull(fileBytes, "fileBytes");
        return scan(new ByteArrayInputStream(fileBytes));
    }

    @Override
    public ScanResult scan(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);

            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.write(INSTREAM_COMMAND);

            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                output.writeInt(read);
                output.write(buffer, 0, read);
            }
            output.writeInt(0);
            output.flush();

            return parseResponse(readResponse(socket.getInputStream()));
        } catch (IOException e) {
            return ScanResult.error("ClamAV scan failed: " + e.getClass().getSimpleName());
        }
    }

    private static int timeoutMillis(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        try {
            long millis = duration.toMillis();
            if (millis < 1 || millis > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(name + " is outside the supported range");
            }
            return (int) millis;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(name + " is outside the supported range", e);
        }
    }

    private static String readResponse(InputStream input) throws IOException {
        byte[] response = new byte[MAX_RESPONSE_BYTES];
        int length = 0;
        int value;
        while (length < response.length && (value = input.read()) != -1) {
            if (value == 0 || value == '\n') {
                break;
            }
            response[length++] = (byte) value;
        }
        if (length == 0) {
            throw new IOException("ClamAV returned an empty response");
        }
        if (length == response.length) {
            throw new IOException("ClamAV response is too long");
        }
        return new String(response, 0, length, StandardCharsets.US_ASCII);
    }

    private static ScanResult parseResponse(String response) {
        if (response.endsWith("OK")) {
            return ScanResult.clean();
        }
        if (response.endsWith("FOUND")) {
            return ScanResult.infected(response);
        }
        return ScanResult.error("Unexpected ClamAV response");
    }
}
