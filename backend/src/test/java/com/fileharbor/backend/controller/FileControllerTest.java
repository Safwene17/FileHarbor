
package com.fileharbor.backend.controller;

import com.fileharbor.backend.exception.FileNotFoundException;
import com.fileharbor.backend.exception.FileStorageException;
import com.fileharbor.backend.exception.GlobalExceptionHandler;
import com.fileharbor.backend.exception.InvalidFileException;
import com.fileharbor.backend.service.FileService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileControllerTest {

    private FileService fileService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        fileService = mock(FileService.class);

        FileController fileController = new FileController(fileService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(fileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadFileSuccessfully() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Hello FileHarbor".getBytes()
        );

        mockMvc.perform(multipart("/api/files").file(file))
                .andExpect(status().isCreated())
                .andExpect(content().string("File uploaded successfully."));

        verify(fileService).upload(any());
    }

    @Test
    void listFilesSuccessfully() throws Exception {

        when(fileService.list())
                .thenReturn(List.of("first.txt", "second.pdf"));

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("first.txt"))
                .andExpect(jsonPath("$[1]").value("second.pdf"));
    }

    @Test
    void downloadFileSuccessfully() throws Exception {

        byte[] content = "Hello FileHarbor".getBytes();

        when(fileService.download("test.txt"))
                .thenReturn(content);

        mockMvc.perform(get("/api/files/test.txt"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content))
                .andExpect(content().contentType(
                        MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"test.txt\""));
    }

    @Test
    void deleteFileSuccessfully() throws Exception {

        mockMvc.perform(delete("/api/files/test.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string("File deleted successfully."));

        verify(fileService).delete("test.txt");
    }

    @Test
    void missingFileReturns404() throws Exception {

        when(fileService.download("missing.txt"))
                .thenThrow(new FileNotFoundException("File not found."));

        mockMvc.perform(get("/api/files/missing.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("File not found."));
    }

    @Test
    void invalidFileReturns400() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        doThrow(new InvalidFileException("File cannot be empty."))
                .when(fileService).upload(any());

        mockMvc.perform(multipart("/api/files").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("File cannot be empty."));
    }

    @Test
    void storageErrorReturns500() throws Exception {

        when(fileService.list())
                .thenThrow(new FileStorageException("Storage unavailable."));

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error")
                        .value("Storage unavailable."));
    }
}
