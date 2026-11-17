package com.fileharbor.backend.exception;

/** The uploaded file has no usable filename (missing, or blank after sanitizing). */
public class InvalidFilenameException extends RuntimeException {

    public InvalidFilenameException() {
        super("Filename must not be blank.");
    }
}

