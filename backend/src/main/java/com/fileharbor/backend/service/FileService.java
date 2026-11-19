
package com.fileharbor.backend.service;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.fileharbor.backend.exception.FileNotFoundException;
import com.fileharbor.backend.exception.FileStorageException;
import com.fileharbor.backend.exception.InvalidFileException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileService {

    private final BlobContainerClient containerClient;

    public FileService(BlobContainerClient containerClient) {
        this.containerClient = containerClient;
    }

    // Upload a file
    public void upload(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File cannot be empty.");
        }

        String filename = file.getOriginalFilename();

        if (filename == null || filename.isBlank()) {
            throw new InvalidFileException("Invalid filename.");
        }

        try {
            containerClient
                    .getBlobClient(filename)
                    .upload(file.getInputStream(), file.getSize(), true);

        } catch (IOException | RuntimeException ex) {
            throw new FileStorageException("Failed to upload file.", ex);
        }
    }

    // List all files
    public List<String> list() {

        try {
            List<String> files = new ArrayList<>();

            for (BlobItem blob : containerClient.listBlobs()) {
                files.add(blob.getName());
            }

            return files;

        } catch (RuntimeException ex) {
            throw new FileStorageException("Failed to retrieve files.", ex);
        }
    }

    // Download a file
    public byte[] download(String filename) {

        BlockBlobClient blob = containerClient
                .getBlobClient(filename)
                .getBlockBlobClient();

        try {
            if (!blob.exists()) {
                throw new FileNotFoundException("File not found: " + filename);
            }

            return blob.downloadContent().toBytes();

        } catch (FileNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new FileStorageException("Failed to download file.", ex);
        }
    }

    // Delete a file
    public void delete(String filename) {

        try {
            var blob = containerClient.getBlobClient(filename);

            if (!blob.exists()) {
                throw new FileNotFoundException("File not found: " + filename);
            }

            blob.delete();

        } catch (FileNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new FileStorageException("Failed to delete file.", ex);
        }
    }
}
