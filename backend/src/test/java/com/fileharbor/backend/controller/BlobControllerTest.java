package com.fileharbor.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fileharbor.backend.service.BlobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BlobController.class)
class BlobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlobService blobService;

    @Test
    void uploadBlob_returns201AndPassesNameAndBytesToService() throws Exception {
        byte[] data = {10, 20, 30, 40};

        mockMvc.perform(post("/api/blobs/{blobName}", "report.pdf")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(data))
                .andExpect(status().isCreated())
                .andExpect(content().string("Blob uploaded successfully."));

        verify(blobService).uploadBlob("report.pdf", data);
    }

    @Test
    void downloadBlob_returns200WithBytesAndPassesNameToService() throws Exception {
        byte[] data = {1, 2, 3, 4, 5};
        when(blobService.downloadBlob("report.pdf")).thenReturn(data);

        mockMvc.perform(get("/api/blobs/{blobName}", "report.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(data));

        verify(blobService).downloadBlob("report.pdf");
    }

    @Test
    void listBlobs_returns200WithFileNamesAsJson() throws Exception {
        when(blobService.listBlobs()).thenReturn(List.of("a.txt", "b.pdf", "c.png"));

        mockMvc.perform(get("/api/blobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]").value("a.txt"))
                .andExpect(jsonPath("$[1]").value("b.pdf"))
                .andExpect(jsonPath("$[2]").value("c.png"));

        verify(blobService).listBlobs();
    }

    @Test
    void deleteBlob_returns200AndPassesNameToService() throws Exception {
        mockMvc.perform(delete("/api/blobs/{blobName}", "report.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().string("Blob deleted successfully."));

        verify(blobService).deleteBlob("report.pdf");
    }
}

