package com.fileharbor.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fileharbor.backend.exception.FileStorageException;
import com.fileharbor.backend.exception.InvalidFileIdException;
import com.fileharbor.backend.exception.StoredFileNotFoundException;
import com.fileharbor.backend.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(FileController.class)
class FileDeleteControllerTest {

    private static final UUID ID = UUID.fromString("3f2b8c1e-6d4a-4b7e-9a51-0c2f7d9e1a44");
    private static final String SECRET = "SECRET-azure-account-details";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @Test
    void delete_returns204WithEmptyBodyAndCallsServiceExactlyOnce() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/files/{id}", ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        assertEquals(0, result.getResponse().getContentAsByteArray().length);
        assertEquals(null, result.getResponse().getContentType(), "a 204 must not declare a body type");
        verify(fileService, times(1)).delete(ID.toString());
        verifyNoMoreInteractions(fileService);
    }

    @Test
    void delete_passesTheIdExactlyAsSuppliedToTheService() throws Exception {
        String upperCase = ID.toString().toUpperCase();

        mockMvc.perform(delete("/api/files/{id}", upperCase)).andExpect(status().isNoContent());

        verify(fileService).delete(upperCase);
    }

    @Test
    void delete_isNeither200Nor202() throws Exception {
        int status = mockMvc.perform(delete("/api/files/{id}", ID)).andReturn().getResponse().getStatus();

        assertEquals(204, status);
    }

    @Test
    void delete_invalidId_returns400ProblemDetail() throws Exception {
        doThrow(new InvalidFileIdException()).when(fileService).delete("not-a-uuid");

        mockMvc.perform(delete("/api/files/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("The file ID must be a valid UUID."));
    }

    @Test
    void delete_missingFile_returns404ProblemDetail() throws Exception {
        doThrow(new StoredFileNotFoundException()).when(fileService).delete(ID.toString());

        mockMvc.perform(delete("/api/files/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("File not found."));
    }

    @Test
    void delete_storageFailure_returns503ProblemDetailWithoutAzureDetails() throws Exception {
        doThrow(new FileStorageException(new RuntimeException(SECRET))).when(fileService).delete(ID.toString());

        MvcResult result = mockMvc.perform(delete("/api/files/{id}", ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.detail").value("File storage is temporarily unavailable. Please try again later."))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains(SECRET));
    }

    @Test
    void delete_failuresNeverProduceASuccessfulResponse() throws Exception {
        RuntimeException[] failures = {
                new InvalidFileIdException(),
                new StoredFileNotFoundException(),
                new FileStorageException(new RuntimeException(SECRET)),
                new FileStorageException(SECRET),
                new IllegalStateException(SECRET)};

        for (RuntimeException failure : failures) {
            doThrow(failure).when(fileService).delete(ID.toString());

            MvcResult result = mockMvc.perform(delete("/api/files/{id}", ID)).andReturn();

            int status = result.getResponse().getStatus();
            assertNotEquals(204, status, failure.getClass().getSimpleName());
            assertTrue(status >= 400, failure.getClass().getSimpleName() + " -> " + status);
            assertTrue(result.getResponse().getContentType().startsWith("application/problem+json"));
            assertFalse(result.getResponse().getContentAsString().contains(SECRET));
        }
    }
}

