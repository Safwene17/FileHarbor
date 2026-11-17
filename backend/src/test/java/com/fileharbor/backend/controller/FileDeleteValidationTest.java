package com.fileharbor.backend.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.azure.storage.blob.BlobContainerClient;
import com.fileharbor.backend.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UUID validation lives in {@link FileService} (single source of truth), not in the controller. This test
 * uses the <em>real</em> service over a mocked Azure container client to prove that an invalid ID is rejected
 * with a 400 {@code ProblemDetail} before any storage operation happens.
 */
@WebMvcTest(FileController.class)
@Import(FileService.class)
class FileDeleteValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlobContainerClient containerClient;

    @Test
    void invalidIds_return400ProblemDetailAndNeverTouchStorage() throws Exception {
        for (String invalid : new String[]{
                "not-a-uuid",
                "1-1-1-1-1",
                "3f2b8c1e6d4a4b7e9a510c2f7d9e1a44",
                "3f2b8c1e-6d4a-4b7e-9a51-0c2f7d9e1a4",
                "3f2b8c1e-6d4a-4b7e-9a51-0c2f7d9e1a44x",
                "..%20"}) {
            mockMvc.perform(delete("/api/files/{id}", invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("The file ID must be a valid UUID."));
        }

        verifyNoInteractions(containerClient);
    }
}

