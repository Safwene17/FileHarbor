package com.fileharbor.backend.exception;

/** The file does not exist, or the blob is not a valid FileHarbor file (for example a legacy blob). */
public class StoredFileNotFoundException extends RuntimeException {

    public StoredFileNotFoundException() {
        super("File not found.");
    }
}

