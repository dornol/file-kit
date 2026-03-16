package io.github.dornol.filekit.spring.validator;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FilePartSourceTest {

    private FilePart mockFilePart(String filename, byte[] content) {
        FilePart filePart = mock(FilePart.class);
        when(filePart.filename()).thenReturn(filename);
        when(filePart.transferTo(any(Path.class))).thenAnswer(invocation -> {
            Path target = invocation.getArgument(0);
            Files.write(target, content);
            return Mono.empty();
        });
        return filePart;
    }

    // ── Factory method ──────────────────────────────────────────────

    @Nested
    class From {

        @Test
        void createsSourceWithCorrectFilenameAndSize() throws IOException {
            byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
            FilePart filePart = mockFilePart("test.txt", content);

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();
                assertThat(source.getOriginalFilename()).isEqualTo("test.txt");
                assertThat(source.getSize()).isEqualTo(content.length);
                assertThat(source.isEmpty()).isFalse();
            }
        }

        @Test
        void emptyFile() throws IOException {
            FilePart filePart = mockFilePart("empty.txt", new byte[0]);

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();
                assertThat(source.isEmpty()).isTrue();
                assertThat(source.getSize()).isZero();
            }
        }

        @Test
        void koreanFilename() throws IOException {
            byte[] content = "data".getBytes(StandardCharsets.UTF_8);
            FilePart filePart = mockFilePart("보고서.pdf", content);

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();
                assertThat(source.getOriginalFilename()).isEqualTo("보고서.pdf");
            }
        }

        @Test
        void filenameWithSpaces() throws IOException {
            byte[] content = "data".getBytes(StandardCharsets.UTF_8);
            FilePart filePart = mockFilePart("my report.pdf", content);

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();
                assertThat(source.getOriginalFilename()).isEqualTo("my report.pdf");
            }
        }

        @Test
        void largeContent() throws IOException {
            byte[] content = new byte[1024 * 1024]; // 1MB
            java.util.Arrays.fill(content, (byte) 'A');
            FilePart filePart = mockFilePart("large.bin", content);

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();
                assertThat(source.getSize()).isEqualTo(1024 * 1024);
                assertThat(source.isEmpty()).isFalse();
            }
        }
    }

    // ── getInputStream ──────────────────────────────────────────────

    @Nested
    class GetInputStream {

        @Test
        void returnsCorrectContent() throws IOException {
            byte[] content = "file content".getBytes(StandardCharsets.UTF_8);
            FilePart filePart = mockFilePart("data.bin", content);

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();
                try (InputStream is = source.getInputStream()) {
                    assertThat(is.readAllBytes()).isEqualTo(content);
                }
            }
        }

        @Test
        void replayable_multipleCallsReturnSameContent() throws IOException {
            byte[] content = "replayable".getBytes(StandardCharsets.UTF_8);
            FilePart filePart = mockFilePart("replay.txt", content);

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();

                try (InputStream is1 = source.getInputStream()) {
                    assertThat(is1.readAllBytes()).isEqualTo(content);
                }
                try (InputStream is2 = source.getInputStream()) {
                    assertThat(is2.readAllBytes()).isEqualTo(content);
                }
                try (InputStream is3 = source.getInputStream()) {
                    assertThat(is3.readAllBytes()).isEqualTo(content);
                }
            }
        }

        @Test
        void emptyFileReturnsEmptyStream() throws IOException {
            FilePart filePart = mockFilePart("empty.txt", new byte[0]);

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();
                try (InputStream is = source.getInputStream()) {
                    assertThat(is.readAllBytes()).isEmpty();
                }
            }
        }
    }

    // ── close ───────────────────────────────────────────────────────

    @Nested
    class Close {

        @Test
        void deletesTempFile() throws IOException {
            byte[] content = "temporary".getBytes(StandardCharsets.UTF_8);
            FilePart filePart = mockFilePart("temp.txt", content);

            FilePartSource source = FilePartSource.from(filePart).block();
            assertThat(source).isNotNull();

            // verify file exists and is readable
            try (InputStream is = source.getInputStream()) {
                assertThat(is.readAllBytes()).isEqualTo(content);
            }

            source.close();

            // after close, temp file is deleted — getInputStream should throw
            assertThatThrownBy(source::getInputStream)
                    .isInstanceOf(java.nio.file.NoSuchFileException.class);
        }

        @Test
        void idempotent_multipleClosesDoNotThrow() throws IOException {
            FilePart filePart = mockFilePart("idem.txt", "data".getBytes(StandardCharsets.UTF_8));

            FilePartSource source = FilePartSource.from(filePart).block();
            assertThat(source).isNotNull();

            source.close();
            source.close();
            source.close();
        }
    }

    // ── FileSource contract ─────────────────────────────────────────

    @Nested
    class FileSourceContract {

        @Test
        void implementsFileSource() {
            byte[] content = "check".getBytes(StandardCharsets.UTF_8);
            FilePart filePart = mockFilePart("contract.txt", content);

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();
                assertThat(source).isInstanceOf(io.github.dornol.filekit.domain.FileSource.class);
                assertThat(source).isInstanceOf(java.io.Closeable.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void getOriginalFilename_returnsExactFilename() throws IOException {
            FilePart filePart = mockFilePart("photo.jpg", new byte[]{1, 2, 3});

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();
                assertThat(source.getOriginalFilename()).isEqualTo("photo.jpg");
            }
        }

        @Test
        void getSize_matchesActualContentLength() throws IOException {
            byte[] content = "exactly 26 bytes of data!!".getBytes(StandardCharsets.UTF_8);
            FilePart filePart = mockFilePart("sized.txt", content);

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();
                assertThat(source.getSize()).isEqualTo(26);
            }
        }

        @Test
        void isEmpty_falseForNonEmptyFile() throws IOException {
            FilePart filePart = mockFilePart("notempty.txt", new byte[]{1});

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();
                assertThat(source.isEmpty()).isFalse();
            }
        }

        @Test
        void isEmpty_trueForEmptyFile() throws IOException {
            FilePart filePart = mockFilePart("empty.txt", new byte[0]);

            try (FilePartSource source = FilePartSource.from(filePart).block()) {
                assertThat(source).isNotNull();
                assertThat(source.isEmpty()).isTrue();
            }
        }
    }

}
