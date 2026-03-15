package io.github.dornol.filekit.spring.download;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.domain.FileLocation;
import io.github.dornol.filekit.domain.FileMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FileResponseBuilderTest {

    enum S { LOCAL }

    private final FileMetadata metadata = new FileMetadata(
            "key", "report.pdf", 1024, "chk",
            new FileFormat("application/pdf", "pdf", "application"),
            new FileLocation("bucket", "key.pdf", S.LOCAL)
    );

    @Test
    void download_setsAttachmentDisposition() {
        ResponseEntity<String> response = FileResponseBuilder.download(metadata)
                .body("data");

        String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(cd).startsWith("attachment;");
        assertThat(cd).contains("report.pdf");
    }

    @Test
    void inline_setsInlineDisposition() {
        ResponseEntity<String> response = FileResponseBuilder.inline(metadata)
                .body("data");

        String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(cd).startsWith("inline;");
    }

    @Test
    void download_setsContentTypeAndLength() {
        ResponseEntity<String> response = FileResponseBuilder.download(metadata)
                .body("data");

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/pdf");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(1024);
    }

    @Test
    void download_withCustomFilename() {
        ResponseEntity<String> response = FileResponseBuilder.download("custom.xlsx")
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body("data");

        String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(cd).contains("custom.xlsx");
    }

    @Test
    void cache_setsCacheControlHeader() {
        ResponseEntity<String> response = FileResponseBuilder.download(metadata)
                .cache(Duration.ofHours(1))
                .body("data");

        String cc = response.getHeaders().getCacheControl();
        assertThat(cc).contains("max-age=3600");
    }

    @Test
    void koreanFilename_encodedInHeader() {
        FileMetadata koreanFile = new FileMetadata(
                "key", "\uBCF4\uACE0\uC11C.pdf", 100, "chk",
                new FileFormat("application/pdf", "pdf", "application"),
                new FileLocation("bucket", "key.pdf", S.LOCAL)
        );

        ResponseEntity<String> response = FileResponseBuilder.download(koreanFile)
                .body("data");

        String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        // ASCII fallback should replace Korean chars with _
        assertThat(cd).contains("filename=\"___.pdf\"");
        // UTF-8 encoded filename should be present
        assertThat(cd).contains("filename*=UTF-8''");
    }

    @Test
    void download_setsNosniffHeader() {
        ResponseEntity<String> response = FileResponseBuilder.download(metadata)
                .body("data");

        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void download_crlfInFilename_stripped() {
        ResponseEntity<String> response = FileResponseBuilder.download("malicious\r\nX-Injected: true")
                .contentType("text/plain")
                .body("data");

        String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(cd).doesNotContain("\r");
        assertThat(cd).doesNotContain("\n");
        assertThat(cd).contains("malicious");
    }

    @Test
    void toResponseBuilder_returnsBodyBuilder() {
        ResponseEntity.BodyBuilder builder = FileResponseBuilder.download("file.txt")
                .contentType("text/plain")
                .toResponseBuilder();

        ResponseEntity<String> response = builder.body("content");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

}
