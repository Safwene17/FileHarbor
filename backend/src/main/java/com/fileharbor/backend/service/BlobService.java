package com.fileharbor.backend.service;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlobService {

    private final BlobContainerClient blobContainerClient;


    // Create or Update Blob
    public void uploadBlob(String blobName, byte[] data) {
        InputStream dataStream = new ByteArrayInputStream(data);
        blobContainerClient.getBlobClient(blobName).upload(dataStream, data.length, true);
    }
    // Read Blob
    public byte[] downloadBlob(String blobName) {
        return blobContainerClient.getBlobClient(blobName).downloadContent().toBytes();
    }
    // List Blobs
    public List<String> listBlobs() {
        List<String> blobNames = new ArrayList<>();
        for (BlobItem blobItem : blobContainerClient.listBlobs()) {
            blobNames.add(blobItem.getName());
        }
        return blobNames;
    }
    // Delete Blob
    public void deleteBlob(String blobName) {
        blobContainerClient.getBlobClient(blobName).delete();
    }
}