# PDF Metadata Extraction

Extract metadata from PDF documents via Apache PDFBox. Auto-configured when PDFBox is on the classpath.

## When to use

- Display page count / author / title in a file preview.
- Filter PDFs by creation date, creator application, etc.

## Setup

Add PDFBox to your dependencies:

```groovy
implementation 'org.apache.pdfbox:pdfbox:3.0.4'
```

The Spring Boot starter auto-registers `PdfBoxMetadataExtractor` as the default `PdfMetadataExtractor` bean when PDFBox is present.

## Usage

```java
@Autowired PdfMetadataExtractor pdfExtractor;

byte[] pdfBytes = Files.readAllBytes(Path.of("document.pdf"));
PdfMetadata metadata = pdfExtractor.extract(pdfBytes);

metadata.pageCount();    // int
metadata.title();        // String, nullable
metadata.author();       // String, nullable
metadata.creator();      // String, nullable
metadata.creationDate(); // Instant, nullable

// InputStream variant
try (InputStream is = Files.newInputStream(Path.of("document.pdf"))) {
    PdfMetadata meta = pdfExtractor.extract(is);
}
```

## Memory note

The `PdfMetadataExtractor.extract(InputStream)` default reads the full stream
into memory and delegates to `extract(byte[])`. The built-in
`PdfBoxMetadataExtractor` implements byte-array extraction, so use it for files
that are already bounded by your upload limits. For very large or untrusted PDFs,
provide a custom extractor that controls buffering and parser limits explicitly.

## Custom implementation

`PdfMetadataExtractor` is an SPI — override with your own `@Bean`:

```java
@Bean
public PdfMetadataExtractor pdfMetadataExtractor() {
    return new MyCustomPdfExtractor();  // e.g., iText-based
}
```

## Errors

Extraction failures throw `FileStorageException(PDF_PROCESSING_FAILED)`. See [validation-and-errors.md](validation-and-errors.md) for the full message key list.

## Related

- [image-processing.md](image-processing.md) — image metadata, similar shape.
- [standalone.md](standalone.md) — use `PdfBoxMetadataExtractor` without Spring Boot.
