package com.fileharbor.backend.exception;

/** The supplied file ID is not a canonical UUID. */
public class InvalidFileIdException extends RuntimeException {

    public InvalidFileIdException() {
        super("File ID must be a valid UUID.");
    }
}

