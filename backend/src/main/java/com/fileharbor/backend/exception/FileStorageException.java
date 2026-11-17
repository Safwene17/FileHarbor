package com.fileharbor.backend.exception;

/**
 * Unexpected failure while talking to Azure Blob Storage.
 * The message is deliberately generic; the Azure exception is kept only as the cause for server-side logs.
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(Throwable cause) {
        super("File storage is currently unavailable.", cause);
    }

    public FileStorageException(String message) {
        super(message);
    }
}

