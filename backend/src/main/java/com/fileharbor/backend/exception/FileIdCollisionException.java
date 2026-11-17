package com.fileharbor.backend.exception;

/** A blob with the generated ID already exists; the conditional upload refused to overwrite it. */
public class FileIdCollisionException extends RuntimeException {

    public FileIdCollisionException() {
        super("A file with the generated ID already exists.");
    }
}

