package com.fileharbor.backend.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fileharbor.backend.dto.FileMetadata;
import com.fileharbor.backend.exception.FileStorageException;
import com.fileharbor.backend.exception.InvalidFileIdException;
import com.fileharbor.backend.exception.StoredFileNotFoundException;
import com.fileharbor.backend.service.FileDownload;
import com.fileharbor.backend.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(FileController.class)
class FileDownloadControllerTest {

    private static final UUID ID = UUID.fromString("3f2b8c1e-6d4a-4b7e-9a51-0c2f7d9e1a44");
    private static final Instant UPLOADED_AT = Instant.parse("2026-11-16T10:15:30Z");
    private static final String SECRET = "SECRET-azure-account-details";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    // ------------------------------------------------------------ success

    @Test
    void download_returnsBinaryContentWithStatus200() throws Exception {
        byte[] bytes = {0, 1, 2, (byte) 0xFF, (byte) 0xFE, 5};
        stubDownload("report.pdf", "application/pdf", new TrackedStream(bytes), bytes.length);

        MvcResult result = mockMvc.perform(get("/api/files/{id}", ID))
                .andExpect(status().isOk())
                .andReturn();

        assertArrayEquals(bytes, result.getResponse().getContentAsByteArray());
        verify(fileService).download(ID.toString());
    }

    @Test
    void download_setsContentTypeFromMetadata() throws Exception {
        stubDownload("report.pdf", "application/pdf", new TrackedStream(new byte[]{1}), 1);

        mockMvc.perform(get("/api/files/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void download_keepsContentTypeParametersFromMetadata() throws Exception {
        stubDownload("notes.txt", "text/plain; charset=utf-8", new TrackedStream(new byte[]{1}), 1);

        mockMvc.perform(get("/api/files/{id}", ID))
                .andExpect(header().string("Content-Type", "text/plain;charset=utf-8"));
    }

    @Test
    void download_ignoresAcceptHeaderAndStillServesTheStoredType() throws Exception {
        stubDownload("report.pdf", "application/pdf", new TrackedStream(new byte[]{1}), 1);

        mockMvc.perform(get("/api/files/{id}", ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void download_setsContentLengthFromVerifiedSize() throws Exception {
        byte[] bytes = new byte[1234];
        stubDownload("big.bin", "application/octet-stream", new TrackedStream(bytes), bytes.length);

        mockMvc.perform(get("/api/files/{id}", ID))
                .andExpect(header().longValue("Content-Length", 1234));
    }

    @Test
    void download_setsNosniffHeader() throws Exception {
        stubDownload("report.pdf", "application/pdf", new TrackedStream(new byte[]{1}), 1);

        mockMvc.perform(get("/api/files/{id}", ID))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    // ------------------------------------------------ Content-Disposition

    @Test
    void download_usesAttachmentDispositionForAsciiFilename() throws Exception {
        stubDownload("report.pdf", "application/pdf", new TrackedStream(new byte[]{1}), 1);

        mockMvc.perform(get("/api/files/{id}", ID))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"report.pdf\""));
    }

    @Test
    void download_encodesNonAsciiFilenameWithRfc5987() throws Exception {
        String original = "Raport final – ședință 😀.pdf";
        stubDownload(original, "application/pdf", new TrackedStream(new byte[]{1}), 1);

        String disposition = mockMvc.perform(get("/api/files/{id}", ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Content-Disposition");

        assertTrue(disposition.startsWith("attachment;"), disposition);
        assertTrue(disposition.contains("filename*=UTF-8''"), disposition);
        assertTrue(disposition.chars().allMatch(c -> c >= 0x20 && c <= 0x7E), "header must be pure ASCII: " + disposition);
        assertEquals(original, ContentDisposition.parse(disposition).getFilename());
    }

    @Test
    void download_escapesQuotesInFilename() throws Exception {
        String original = "my \"quoted\" file.txt";
        stubDownload(original, "text/plain", new TrackedStream(new byte[]{1}), 1);

        String disposition = mockMvc.perform(get("/api/files/{id}", ID))
                .andReturn().getResponse().getHeader("Content-Disposition");

        assertTrue(disposition.startsWith("attachment;"), disposition);
        assertEquals(original, ContentDisposition.parse(disposition).getFilename());
        assertFalse(disposition.contains("\"quoted\""), "quotes must be escaped: " + disposition);
    }

    @Test
    void download_cannotBeUsedForHeaderInjectionThroughTheFilename() throws Exception {
        stubDownload("evil.txt\r\nX-Injected: yes", "text/plain", new TrackedStream(new byte[]{1}), 1);

        MvcResult result = mockMvc.perform(get("/api/files/{id}", ID)).andReturn();

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertFalse(disposition.contains("\r") || disposition.contains("\n"), disposition);
        assertEquals(null, result.getResponse().getHeader("X-Injected"));
        assertEquals("attachment", disposition.substring(0, disposition.indexOf(';')));
    }

    @Test
    void download_neverExposesTheBlobNameOrStorageDetailsInHeaders() throws Exception {
        stubDownload("report.pdf", "application/pdf", new TrackedStream(new byte[]{1}), 1);

        MvcResult result = mockMvc.perform(get("/api/files/{id}", ID)).andReturn();

        for (String name : result.getResponse().getHeaderNames()) {
            for (Object value : result.getResponse().getHeaders(name)) {
                assertFalse(value.toString().contains(ID.toString()), name + ": " + value);
            }
        }
    }

    // ------------------------------------------------------ empty file

    @Test
    void download_supportsEmptyFile() throws Exception {
        TrackedStream stream = new TrackedStream(new byte[0]);
        stubDownload("empty.txt", "text/plain", stream, 0);

        mockMvc.perform(get("/api/files/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", 0))
                .andExpect(content().bytes(new byte[0]));

        assertTrue(stream.closed.get());
    }

    // ------------------------------------------------- resource management

    @Test
    void download_closesTheStreamAfterTheResponseIsWritten() throws Exception {
        TrackedStream stream = new TrackedStream("some content".getBytes(StandardCharsets.UTF_8));
        stubDownload("a.txt", "text/plain", stream, stream.size);

        mockMvc.perform(get("/api/files/{id}", ID)).andExpect(status().isOk());

        assertTrue(stream.closed.get(), "download stream must be closed");
    }

    @Test
    void download_closesTheStreamWhenReadingFailsMidResponse() throws Exception {
        TrackedStream failing = new TrackedStream(new byte[]{1, 2, 3}) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                throw new java.io.UncheckedIOException(new IOException(SECRET));
            }
        };
        stubDownload("a.bin", "application/octet-stream", failing, 3);

        // Once writing has started a ProblemDetail can no longer be guaranteed; only the cleanup is asserted.
        try {
            mockMvc.perform(get("/api/files/{id}", ID));
        } catch (Exception expected) {
            // acceptable: the failure surfaces from the dispatcher
        }

        assertTrue(failing.closed.get(), "stream must be closed even when reading fails");
    }

    @Test
    void download_closesTheStreamWhenBuildingTheResponseFails() throws Exception {
        TrackedStream stream = new TrackedStream(new byte[]{1});
        FileMetadata broken = new FileMetadata(ID, null, "text/plain", 1, UPLOADED_AT); // null filename -> NPE
        when(fileService.download(ID.toString())).thenReturn(new FileDownload(broken, stream));

        try {
            mockMvc.perform(get("/api/files/{id}", ID));
        } catch (Exception expected) {
            // not interesting here
        }

        assertTrue(stream.closed.get(), "stream must be closed when the controller itself fails");
    }

    // -------------------------------------------------------------- errors

    @Test
    void download_invalidId_returns400ProblemDetail() throws Exception {
        when(fileService.download("not-a-uuid")).thenThrow(new InvalidFileIdException());

        mockMvc.perform(get("/api/files/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("The file ID must be a valid UUID."));
    }

    @Test
    void download_missingFile_returns404ProblemDetail() throws Exception {
        when(fileService.download(ID.toString())).thenThrow(new StoredFileNotFoundException());

        mockMvc.perform(get("/api/files/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("File not found."));
    }

    @Test
    void download_storageFailure_returns503ProblemDetailWithoutAzureDetails() throws Exception {
        when(fileService.download(ID.toString())).thenThrow(new FileStorageException(new RuntimeException(SECRET)));

        MvcResult result = mockMvc.perform(get("/api/files/{id}", ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.detail").value("File storage is temporarily unavailable. Please try again later."))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains(SECRET));
    }

    // --------------------------------------------------------------- helpers

    private void stubDownload(String filename, String contentType, InputStream stream, long size) {
        FileMetadata metadata = new FileMetadata(ID, filename, contentType, size, UPLOADED_AT);
        when(fileService.download(ID.toString())).thenReturn(new FileDownload(metadata, stream));
    }

    /** Controlled stream that records whether it was closed. */
    private static class TrackedStream extends ByteArrayInputStream {

        final AtomicBoolean closed = new AtomicBoolean(false);
        final int size;

        TrackedStream(byte[] bytes) {
            super(bytes);
            this.size = bytes.length;
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
            super.close();
        }
    }
}

