package io.github.dornol.filekit.spring.upload;

import io.github.dornol.filekit.domain.FileMetadata;
import io.github.dornol.filekit.upload.FileUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactiveFileUploadServiceTest {

    @Test
    void upload_delegatesAndCompletes() throws Exception {
        FilePart part = mock(FilePart.class);
        when(part.filename()).thenReturn("file.txt");
        when(part.transferTo(any(Path.class))).thenAnswer(invocation -> {
            Files.write(invocation.getArgument(0), "data".getBytes());
            return Mono.empty();
        });
        FileUploadService delegate = mock(FileUploadService.class);
        FileMetadata metadata = mock(FileMetadata.class);
        when(delegate.upload(any(), any(), any())).thenReturn(metadata);

        FileMetadata result = new ReactiveFileUploadService(delegate, 100)
                .upload(part, TestStorage.LOCAL, "bucket")
                .block();

        assertThat(result).isSameAs(metadata);
    }

    enum TestStorage { LOCAL }
}
