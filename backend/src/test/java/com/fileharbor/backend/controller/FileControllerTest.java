package com.fileharbor.backend.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.fileharbor.backend.dto.FileMetadata;
import com.fileharbor.backend.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FileController.class)
class FileControllerTest {

    private static final UUID ID = UUID.fromString("3f2b8c1e-6d4a-4b7e-9a51-0c2f7d9e1a44");
    private static final Instant UPLOADED_AT = Instant.parse("2026-11-16T10:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    // ---------------------------------------------------------------- upload

    @Test
    void upload_returns201WithMetadataAndLocationHeader() throws Exception {
        byte[] bytes = "hello".getBytes();
        when(fileService.upload(eq("report.pdf"), eq("application/pdf"), any(InputStream.class), eq(5L)))
                .thenReturn(new FileMetadata(ID, "report.pdf", "application/pdf", 5, UPLOADED_AT));

        mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "report.pdf", "application/pdf", bytes)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/files/" + ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.originalFilename").value("report.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.uploadedAt").value("2026-11-16T10:15:30Z"));
    }

    @Test
    void upload_passesFilenameContentTypeSizeAndContentToService() throws Exception {
        byte[] bytes = "Conținut ș ă 😀".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        AtomicReference<byte[]> receivedContent = new AtomicReference<>();
        when(fileService.upload(eq("Raport ședință.txt"), eq("text/plain"), any(InputStream.class), eq((long) bytes.length)))
                .thenAnswer(invocation -> {
                    // The stream is closed after the call, so read it while the service is "running".
                    receivedContent.set(invocation.<InputStream>getArgument(2).readAllBytes());
                    return new FileMetadata(ID, "Raport ședință.txt", "text/plain", bytes.length, UPLOADED_AT);
                });

        mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "Raport ședință.txt", "text/plain", bytes)))
                .andExpect(status().isCreated());

        verify(fileService).upload(eq("Raport ședință.txt"), eq("text/plain"), any(InputStream.class), eq((long) bytes.length));
        assertArrayEquals(bytes, receivedContent.get());
    }

    @Test
    void upload_closesTheMultipartInputStream() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1, 2, 3}) {
            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(getBytes()) {
                    @Override
                    public void close() {
                        closed.set(true);
                    }
                };
            }
        };
        when(fileService.upload(any(), any(), any(InputStream.class), eq(3L)))
                .thenReturn(new FileMetadata(ID, "a.txt", "text/plain", 3, UPLOADED_AT));

        mockMvc.perform(multipart("/api/files").file(file)).andExpect(status().isCreated());

        assertTrue(closed.get(), "multipart input stream must be closed");
    }

    @Test
    void upload_returns400WhenFilePartIsMissing() throws Exception {
        mockMvc.perform(multipart("/api/files"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fileService);
    }

    @Test
    void upload_returns400WhenPartIsNotNamedFile() throws Exception {
        mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("document", "a.txt", "text/plain", new byte[]{1})))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fileService);
    }

    @Test
    void upload_returns400ForEmptyFile() throws Exception {
        mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fileService);
    }

    // ------------------------------------------------------------------ list

    @Test
    void list_returns200WithFileMetadata() throws Exception {
        UUID otherId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(fileService.list()).thenReturn(List.of(
                new FileMetadata(ID, "report.pdf", "application/pdf", 42, UPLOADED_AT),
                new FileMetadata(otherId, "notes.txt", "text/plain", 7, null)));

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(ID.toString()))
                .andExpect(jsonPath("$[0].originalFilename").value("report.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$[0].size").value(42))
                .andExpect(jsonPath("$[0].uploadedAt").value("2026-11-16T10:15:30Z"))
                .andExpect(jsonPath("$[1].id").value(otherId.toString()))
                .andExpect(jsonPath("$[1].originalFilename").value("notes.txt"));

        verify(fileService).list();
    }

    @Test
    void list_returns200WithEmptyArrayWhenThereAreNoFiles() throws Exception {
        when(fileService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));

        verify(fileService).list();
    }
}

