package com.fileharbor.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.fileharbor.backend.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Runs the real embedded server with the real multipart configuration from {@code application.yaml}
 * (20 MB). MockMvc cannot be used here: it does not enforce the container's multipart limits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Fake, non-secret values: building the Azure client does not contact Azure.
        "azure.storage.connection-string=DefaultEndpointsProtocol=https;AccountName=testaccount;AccountKey=dGVzdA==;EndpointSuffix=core.windows.net",
        "azure.storage.blob-container-name=test-container"
})
class FileApiSizeLimitIntegrationTest {

    private static final String BOUNDARY = "----fileharbor-test-boundary";

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private FileService fileService;

    @Test
    void uploadLargerThanConfiguredLimitReturns413ProblemDetail() throws Exception {
        byte[] content = new byte[21 * 1024 * 1024];
        Arrays.fill(content, (byte) 'a');

        HttpResponse<String> response = post(content);

        assertEquals(413, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"),
                "content type was " + response.headers().firstValue("Content-Type"));
        assertTrue(response.body().contains("\"status\":413"), response.body());
        assertTrue(response.body().contains("The uploaded file exceeds the maximum allowed size."), response.body());
        assertFalse(response.body().contains("Exception"), "must not expose exception details: " + response.body());
        verifyNoInteractions(fileService);
    }

    private HttpResponse<String> post(byte[] content) throws Exception {
        byte[] head = ("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"big.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/files"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, content, tail)))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}

