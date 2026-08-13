package io.github.dornol.filekit.example.config;

import io.github.dornol.filekit.storage.FileStorageException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void clientError_doesNotExposeInternalMessage() {
        FileStorageException exception = new FileStorageException(
                FileStorageException.INVALID_FILENAME,
                "internal path /srv/private/secret.txt");

        ResponseEntity<Map<String, Object>> response = handler.handleFileStorage(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("messageKey", FileStorageException.INVALID_FILENAME);
        assertThat(response.getBody()).containsEntry("message", "File request is invalid");
        assertThat(response.getBody()).doesNotContainValue("internal path /srv/private/secret.txt");
    }

    @Test
    void notFound_mapsTo404() {
        ResponseEntity<Map<String, Object>> response = handler.handleFileStorage(
                new FileStorageException(FileStorageException.FILE_NOT_FOUND, "secret key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unexpectedFailure_mapsTo500WithGenericMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleFileStorage(
                new FileStorageException(FileStorageException.DOWNLOAD_FAILED, "backend details"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "File operation failed");
        assertThat(response.getBody()).doesNotContainValue("backend details");
    }
}
