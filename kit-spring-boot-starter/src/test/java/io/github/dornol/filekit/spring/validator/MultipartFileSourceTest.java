package io.github.dornol.filekit.spring.validator;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultipartFileSourceTest {

    @Test
    void delegatesGetOriginalFilename() {
        MultipartFile mock = mock(MultipartFile.class);
        when(mock.getOriginalFilename()).thenReturn("photo.jpg");

        MultipartFileSource source = new MultipartFileSource(mock);
        assertEquals("photo.jpg", source.getOriginalFilename());
    }

    @Test
    void delegatesGetOriginalFilename_null() {
        MultipartFile mock = mock(MultipartFile.class);
        when(mock.getOriginalFilename()).thenReturn(null);

        MultipartFileSource source = new MultipartFileSource(mock);
        assertNull(source.getOriginalFilename());
    }

    @Test
    void delegatesGetInputStream() throws IOException {
        MultipartFile mock = mock(MultipartFile.class);
        InputStream expected = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(mock.getInputStream()).thenReturn(expected);

        MultipartFileSource source = new MultipartFileSource(mock);
        assertSame(expected, source.getInputStream());
    }

    @Test
    void delegatesGetSize() {
        MultipartFile mock = mock(MultipartFile.class);
        when(mock.getSize()).thenReturn(12345L);

        MultipartFileSource source = new MultipartFileSource(mock);
        assertEquals(12345L, source.getSize());
    }

    @Test
    void delegatesIsEmpty_true() {
        MultipartFile mock = mock(MultipartFile.class);
        when(mock.isEmpty()).thenReturn(true);

        MultipartFileSource source = new MultipartFileSource(mock);
        assertTrue(source.isEmpty());
    }

    @Test
    void delegatesIsEmpty_false() {
        MultipartFile mock = mock(MultipartFile.class);
        when(mock.isEmpty()).thenReturn(false);

        MultipartFileSource source = new MultipartFileSource(mock);
        assertFalse(source.isEmpty());
    }

}
