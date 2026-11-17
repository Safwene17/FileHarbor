package com.fileharbor.backend.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

import com.fileharbor.backend.exception.FileIdCollisionException;
import com.fileharbor.backend.exception.FileStorageException;
import com.fileharbor.backend.exception.InvalidFileIdException;
import com.fileharbor.backend.exception.InvalidFilenameException;
import com.fileharbor.backend.exception.StoredFileNotFoundException;
import com.fileharbor.backend.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Verifies that every error of the files API is an {@code application/problem+json}
 * {@code ProblemDetail} with a fixed, safe message. Oversized requests that are rejected by the
 * real container are covered by {@link FileApiSizeLimitIntegrationTest}.
 */
@WebMvcTest(FileController.class)
class FileExceptionHandlingTest {

    private static final String SECRET = "SECRET-azure-account-details";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @Test
    void missingMultipartPart_returns400ProblemDetail() throws Exception {
        ResultActions result = mockMvc.perform(multipart("/api/files"));

        assertProblem(result, 400, "Bad Request", "A file must be uploaded in the multipart field 'file'.");
        verifyNoInteractions(fileService);
    }

    @Test
    void emptyFile_returns400ProblemDetail() throws Exception {
        ResultActions result = mockMvc.perform(multipart("/api/files")
                .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])));

        assertProblem(result, 400, "Bad Request", "File must not be empty.");
        verifyNoInteractions(fileService);
    }

    @Test
    void invalidFilename_returns400ProblemDetail() throws Exception {
        whenUploadThrows(new InvalidFilenameException());

        assertProblem(uploadSomething(), 400, "Bad Request", "The uploaded file must have a valid filename.");
    }

    @Test
    void invalidFileId_returns400ProblemDetail() throws Exception {
        whenUploadThrows(new InvalidFileIdException());

        assertProblem(uploadSomething(), 400, "Bad Request", "The file ID must be a valid UUID.");
    }

    @Test
    void oversizedMultipartRequest_returns413ProblemDetail() throws Exception {
        whenUploadThrows(new MaxUploadSizeExceededException(20L * 1024 * 1024));

        assertProblem(uploadSomething(), 413, "Payload Too Large", "The uploaded file exceeds the maximum allowed size.");
    }

    @Test
    void idCollision_returns409ProblemDetail() throws Exception {
        whenUploadThrows(new FileIdCollisionException());

        assertProblem(uploadSomething(), 409, "Conflict",
                "The file could not be stored because of an ID conflict. Please try again.");
    }

    @Test
    void fileNotFound_returns404ProblemDetail() throws Exception {
        whenUploadThrows(new StoredFileNotFoundException());

        assertProblem(uploadSomething(), 404, "Not Found", "File not found.");
    }

    @Test
    void storageFailure_returns503ProblemDetailWithoutAzureDetails() throws Exception {
        whenUploadThrows(new FileStorageException(new RuntimeException(SECRET)));

        ResultActions result = uploadSomething();

        assertProblem(result, 503, "Service Unavailable",
                "File storage is temporarily unavailable. Please try again later.");
        assertDoesNotExpose(result, SECRET);
    }

    @Test
    void storageFailureWithCustomMessage_doesNotExposeTheMessage() throws Exception {
        whenUploadThrows(new FileStorageException(SECRET));

        ResultActions result = uploadSomething();

        assertProblem(result, 503, "Service Unavailable",
                "File storage is temporarily unavailable. Please try again later.");
        assertDoesNotExpose(result, SECRET);
    }

    @Test
    void unexpectedException_returns500ProblemDetailWithoutInternalDetails() throws Exception {
        whenUploadThrows(new IllegalStateException(SECRET));

        ResultActions result = uploadSomething();

        assertProblem(result, 500, "Internal Server Error", "An unexpected error occurred.");
        assertDoesNotExpose(result, SECRET);
        assertDoesNotExpose(result, "IllegalStateException");
    }

    @Test
    void unexpectedExceptionWhileListing_returns500ProblemDetail() throws Exception {
        when(fileService.list()).thenThrow(new NullPointerException(SECRET));

        ResultActions result = mockMvc.perform(get("/api/files"));

        assertProblem(result, 500, "Internal Server Error", "An unexpected error occurred.");
        assertDoesNotExpose(result, SECRET);
    }

    @Test
    void storageFailureWhileListing_returns503ProblemDetail() throws Exception {
        when(fileService.list()).thenThrow(new FileStorageException(new RuntimeException(SECRET)));

        ResultActions result = mockMvc.perform(get("/api/files"));

        assertProblem(result, 503, "Service Unavailable",
                "File storage is temporarily unavailable. Please try again later.");
        assertDoesNotExpose(result, SECRET);
    }

    // --------------------------------------------------------------- helpers

    private void whenUploadThrows(RuntimeException exception) {
        when(fileService.upload(any(), any(), any(InputStream.class), anyLong())).thenThrow(exception);
    }

    private ResultActions uploadSomething() throws Exception {
        return mockMvc.perform(multipart("/api/files")
                .file(new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1, 2, 3})));
    }

    private static void assertProblem(ResultActions result, int status, String title, String detail) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    private static void assertDoesNotExpose(ResultActions result, String internal) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(internal), "response must not contain '" + internal + "': " + body);
    }
}


