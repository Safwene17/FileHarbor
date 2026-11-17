package com.fileharbor.backend.service;

import com.azure.core.util.BinaryData;
import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlobServiceTest {

    @Mock
    private BlobContainerClient containerClient;

    @Mock
    private BlobClient blobClient;

    private BlobService blobService;

    @BeforeEach
    void setUp() {
        blobService = new BlobService(containerClient);
    }

    @Test
    void uploadBlob_uploadsBytesWithOverwriteEnabled() throws Exception {
        byte[] data = "hello".getBytes();
        when(containerClient.getBlobClient("doc.txt")).thenReturn(blobClient);

        blobService.uploadBlob("doc.txt", data);

        ArgumentCaptor<InputStream> stream = ArgumentCaptor.forClass(InputStream.class);
        verify(containerClient).getBlobClient("doc.txt");
        verify(blobClient).upload(stream.capture(), eq((long) data.length), eq(true));
        assertArrayEquals(data, stream.getValue().readAllBytes());
    }

    @Test
    void downloadBlob_returnsDownloadedContent() {
        byte[] content = {1, 2, 3};
        when(containerClient.getBlobClient("doc.txt")).thenReturn(blobClient);
        when(blobClient.downloadContent()).thenReturn(BinaryData.fromBytes(content));

        byte[] result = blobService.downloadBlob("doc.txt");

        assertArrayEquals(content, result);
        verify(containerClient).getBlobClient("doc.txt");
        verify(blobClient).downloadContent();
    }

    @Test
    void listBlobs_mapsBlobItemNamesToStrings() {
        PagedIterable<BlobItem> items = pagedIterableOf(
                new BlobItem().setName("a.txt"), new BlobItem().setName("b.pdf"));
        when(containerClient.listBlobs()).thenReturn(items);

        List<String> names = blobService.listBlobs();

        assertEquals(List.of("a.txt", "b.pdf"), names);
    }

    @Test
    void listBlobs_returnsEmptyListForEmptyContainer() {
        PagedIterable<BlobItem> empty = pagedIterableOf();
        when(containerClient.listBlobs()).thenReturn(empty);

        List<String> names = blobService.listBlobs();

        assertTrue(names.isEmpty());
    }

    @Test
    void deleteBlob_deletesTheRequestedBlob() {
        when(containerClient.getBlobClient("doc.txt")).thenReturn(blobClient);

        blobService.deleteBlob("doc.txt");

        verify(containerClient).getBlobClient("doc.txt");
        verify(blobClient).delete();
        verifyNoMoreInteractions(blobClient);
    }

    /**
     * PagedIterable is a concrete SDK class; its iterator() is what a for-each loop uses,
     * so a Mockito mock that stubs iterator() is enough.
     */
    @SuppressWarnings("unchecked")
    private static PagedIterable<BlobItem> pagedIterableOf(BlobItem... items) {
        PagedIterable<BlobItem> paged = org.mockito.Mockito.mock(PagedIterable.class);
        when(paged.iterator()).thenReturn(List.of(items).iterator());
        return paged;
    }
}





