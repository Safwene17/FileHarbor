
package com.fileharbor.backend.service;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.fileharbor.backend.exception.FileNotFoundException;
import com.fileharbor.backend.exception.InvalidFileException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private BlobContainerClient containerClient;

    @Mock
    private BlobClient blobClient;

    @Mock
    private BlockBlobClient blockBlobClient;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(containerClient);
    }

    @Test
    void uploadFileSuccessfully() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Hello FileHarbor".getBytes()
        );

        when(containerClient.getBlobClient("test.txt"))
                .thenReturn(blobClient);

        fileService.upload(file);

        verify(blobClient).upload(
                any(java.io.InputStream.class),
                eq((long) file.getSize()),
                eq(true)
        );
    }

    @Test
    void uploadEmptyFileThrowsException() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileService.upload(file)
        );

        verifyNoInteractions(containerClient);
    }

    @Test
    void listFilesSuccessfully() {

        BlobItem firstFile = mock(BlobItem.class);
        BlobItem secondFile = mock(BlobItem.class);

        when(firstFile.getName()).thenReturn("first.txt");
        when(secondFile.getName()).thenReturn("second.pdf");

        PagedIterable<BlobItem> blobs = mock(PagedIterable.class);

        when(blobs.iterator()).thenReturn(
                List.of(firstFile, secondFile).iterator()
        );

        when(containerClient.listBlobs()).thenReturn(blobs);

        List<String> result = fileService.list();

        assertEquals(2, result.size());
        assertTrue(result.contains("first.txt"));
        assertTrue(result.contains("second.pdf"));
    }

    @Test
    void downloadFileSuccessfully() {

        byte[] expectedContent = "Hello FileHarbor".getBytes();

        when(containerClient.getBlobClient("test.txt"))
                .thenReturn(blobClient);

        when(blobClient.getBlockBlobClient())
                .thenReturn(blockBlobClient);

        when(blockBlobClient.exists()).thenReturn(true);

        when(blockBlobClient.downloadContent())
                .thenReturn(BinaryData.fromBytes(expectedContent));

        byte[] result = fileService.download("test.txt");

        assertArrayEquals(expectedContent, result);
    }

    @Test
    void downloadMissingFileThrowsException() {

        when(containerClient.getBlobClient("missing.txt"))
                .thenReturn(blobClient);

        when(blobClient.getBlockBlobClient())
                .thenReturn(blockBlobClient);

        when(blockBlobClient.exists()).thenReturn(false);

        assertThrows(
                FileNotFoundException.class,
                () -> fileService.download("missing.txt")
        );
    }

    @Test
    void deleteFileSuccessfully() {

        when(containerClient.getBlobClient("test.txt"))
                .thenReturn(blobClient);

        when(blobClient.exists()).thenReturn(true);

        fileService.delete("test.txt");

        verify(blobClient).delete();
    }

    @Test
    void deleteMissingFileThrowsException() {

        when(containerClient.getBlobClient("missing.txt"))
                .thenReturn(blobClient);

        when(blobClient.exists()).thenReturn(false);

        assertThrows(
                FileNotFoundException.class,
                () -> fileService.delete("missing.txt")
        );

        verify(blobClient, never()).delete();
    }
}
