package com.fileharbor.backend.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureBlobStorageConfiguration {

    @Bean
    public BlobContainerClient blobContainerClient(
            @Value("${azure.storage.connection-string}") String connectionString,
            @Value("${azure.storage.blob-container-name}") String containerName) {
        return new BlobContainerClientBuilder()
                .connectionString(connectionString)
                .containerName(containerName)
                .buildClient();
    }
}

