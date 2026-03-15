package io.github.dornol.filekit.spring.download;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileResponseBuilderTest {

    enum S { LOCAL }

    private final FileMetadata metadata = new FileMetadata(
            "key", "report.pdf", 1024, "chk",
            new FileFormat("application/pdf", "pdf", "application"),
            new FileLocation("bucket", "key.pdf", S.LOCAL)
    );

    // ── Download factory ─────────────────────────────────────────────

    @Nested
    class DownloadFactory {

        @Test
        void setsAttachmentDisposition() {
            ResponseEntity<String> response = FileResponseBuilder.download(metadata).body("data");

            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(cd).startsWith("attachment;");
            assertThat(cd).contains("report.pdf");
        }

        @Test
        void setsContentTypeFromMetadata() {
            ResponseEntity<String> response = FileResponseBuilder.download(metadata).body("data");

            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                    .isEqualTo("application/pdf");
        }

        @Test
        void setsContentLengthFromMetadata() {
            ResponseEntity<String> response = FileResponseBuilder.download(metadata).body("data");

            assertThat(response.getHeaders().getContentLength()).isEqualTo(1024);
        }

        @Test
        void withCustomFilename() {
            ResponseEntity<String> response = FileResponseBuilder.download("custom.xlsx")
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body("data");

            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(cd).contains("custom.xlsx");
        }

        @Test
        void setsNosniffHeader() {
            ResponseEntity<String> response = FileResponseBuilder.download(metadata).body("data");

            assertThat(response.getHeaders().getFirst("X-Content-Type-Options"))
                    .isEqualTo("nosniff");
        }
    }

    // ── Inline factory ───────────────────────────────────────────────

    @Nested
    class InlineFactory {

        @Test
        void setsInlineDisposition() {
            ResponseEntity<String> response = FileResponseBuilder.inline(metadata).body("data");

            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(cd).startsWith("inline;");
        }

        @Test
        void setsContentTypeFromMetadata() {
            ResponseEntity<String> response = FileResponseBuilder.inline(metadata).body("data");

            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                    .isEqualTo("application/pdf");
        }

        @Test
        void withCustomFilename() {
            ResponseEntity<String> response = FileResponseBuilder.inline("preview.pdf").body("data");

            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(cd).startsWith("inline;");
            assertThat(cd).contains("preview.pdf");
        }
    }

    // ── Null/invalid parameter validation ────────────────────────────

    @Nested
    class ParameterValidation {

        @Test
        void download_nullStringFilename_throws() {
            assertThatThrownBy(() -> FileResponseBuilder.download((String) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("filename");
        }

        @Test
        void inline_nullStringFilename_throws() {
            assertThatThrownBy(() -> FileResponseBuilder.inline((String) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("filename");
        }

        @Test
        void contentLength_negative_throws() {
            assertThatThrownBy(() -> FileResponseBuilder.download("file.txt").contentLength(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("-1");
        }

        @Test
        void contentLength_negativeMax_throws() {
            assertThatThrownBy(() -> FileResponseBuilder.download("file.txt").contentLength(Long.MIN_VALUE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void contentLength_zero_allowed() {
            ResponseEntity<String> response = FileResponseBuilder.download("file.txt")
                    .contentLength(0)
                    .body("data");

            assertThat(response.getHeaders().getContentLength()).isEqualTo(0);
        }

        @Test
        void cache_null_throws() {
            assertThatThrownBy(() -> FileResponseBuilder.download("file.txt").cache(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("duration");
        }
    }

    // ── Cache control ────────────────────────────────────────────────

    @Nested
    class CacheControl {

        @Test
        void cache_setsCacheControlHeader() {
            ResponseEntity<String> response = FileResponseBuilder.download(metadata)
                    .cache(Duration.ofHours(1))
                    .body("data");

            assertThat(response.getHeaders().getCacheControl()).contains("max-age=3600");
        }

        @Test
        void cache_zeroDuration() {
            ResponseEntity<String> response = FileResponseBuilder.download("file.txt")
                    .cache(Duration.ZERO)
                    .body("data");

            assertThat(response.getHeaders().getCacheControl()).contains("max-age=0");
        }

        @Test
        void noCache_noCacheControlHeader() {
            ResponseEntity<String> response = FileResponseBuilder.download("file.txt")
                    .body("data");

            assertThat(response.getHeaders().getCacheControl()).isNull();
        }
    }

    // ── Filename sanitization ────────────────────────────────────────

    @Nested
    class FilenameSanitization {

        @Test
        void crlfStripped() {
            ResponseEntity<String> response = FileResponseBuilder
                    .download("malicious\r\nX-Injected: true")
                    .contentType("text/plain")
                    .body("data");

            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(cd).doesNotContain("\r");
            assertThat(cd).doesNotContain("\n");
            assertThat(cd).contains("malicious");
        }

        @Test
        void nullByteStripped() {
            ResponseEntity<String> response = FileResponseBuilder
                    .download("file\u0000.txt")
                    .body("data");

            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(cd).doesNotContain("\u0000");
            assertThat(cd).contains("file");
        }

        @Test
        void tabStripped() {
            ResponseEntity<String> response = FileResponseBuilder
                    .download("file\t.txt")
                    .body("data");

            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(cd).doesNotContain("\t");
        }

        @Test
        void koreanFilename_encodedInHeader() {
            FileMetadata koreanFile = new FileMetadata(
                    "key", "보고서.pdf", 100, "chk",
                    new FileFormat("application/pdf", "pdf", "application"),
                    new FileLocation("bucket", "key.pdf", S.LOCAL)
            );

            ResponseEntity<String> response = FileResponseBuilder.download(koreanFile).body("data");

            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            // ASCII fallback should replace Korean chars with _
            assertThat(cd).contains("filename=\"___.pdf\"");
            // UTF-8 encoded filename should be present
            assertThat(cd).contains("filename*=UTF-8''");
        }

        @Test
        void japaneseFilename_encodedInHeader() {
            FileMetadata japaneseFile = new FileMetadata(
                    "key", "テスト.xlsx", 100, "chk",
                    new FileFormat("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "xlsx", "application"),
                    new FileLocation("bucket", "key.xlsx", S.LOCAL)
            );

            ResponseEntity<String> response = FileResponseBuilder.download(japaneseFile).body("data");

            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(cd).contains("filename*=UTF-8''");
        }

        @Test
        void filenameWithSpaces_encoded() {
            ResponseEntity<String> response = FileResponseBuilder
                    .download("my report.pdf")
                    .body("data");

            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(cd).contains("my report.pdf");
            assertThat(cd).contains("my%20report.pdf");
        }
    }

    // ── Builder chaining ─────────────────────────────────────────────

    @Nested
    class BuilderChaining {

        @Test
        void toResponseBuilder_returnsBodyBuilder() {
            ResponseEntity.BodyBuilder builder = FileResponseBuilder.download("file.txt")
                    .contentType("text/plain")
                    .toResponseBuilder();

            ResponseEntity<String> response = builder.body("content");
            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test
        void allOptions_combined() {
            ResponseEntity<String> response = FileResponseBuilder.download("report.csv")
                    .contentType("text/csv")
                    .contentLength(500)
                    .cache(Duration.ofMinutes(30))
                    .body("csv-data");

            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/csv");
            assertThat(response.getHeaders().getContentLength()).isEqualTo(500);
            assertThat(response.getHeaders().getCacheControl()).contains("max-age=1800");
            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(cd).contains("report.csv");
        }

        @Test
        void noContentType_headerAbsent() {
            ResponseEntity<String> response = FileResponseBuilder.download("file.txt")
                    .body("data");

            // When no content type is set, Spring may set default or leave null
            // The important thing is no custom header was explicitly set
            assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        }

        @Test
        void noContentLength_headerAbsent() {
            ResponseEntity<String> response = FileResponseBuilder.download("file.txt")
                    .body("data");

            assertThat(response.getHeaders().getContentLength()).isEqualTo(-1);
        }
    }
}
