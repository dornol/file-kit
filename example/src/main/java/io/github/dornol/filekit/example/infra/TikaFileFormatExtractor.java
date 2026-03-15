package io.github.dornol.filekit.example.infra;

import io.github.dornol.filekit.domain.FileFormat;
import io.github.dornol.filekit.spi.FileFormatExtractor;
import org.apache.tika.Tika;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class TikaFileFormatExtractor implements FileFormatExtractor {

    private final Tika tika = new Tika();

    @Override
    public FileFormat extract(InputStream inputStream) {
        try {
            String mimeType = tika.detect(inputStream);
            String primaryType = mimeType.split("/")[0];
            String extension = resolveExtension(mimeType);
            return new FileFormat(mimeType, extension, primaryType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String resolveExtension(String mimeType) {
        try {
            MimeType type = MimeTypes.getDefaultMimeTypes().forName(mimeType);
            String ext = type.getExtension();
            return ext.startsWith(".") ? ext.substring(1) : ext;
        } catch (MimeTypeException e) {
            return "bin";
        }
    }

}
