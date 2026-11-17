package com.fileharbor.backend.service;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

import com.fileharbor.backend.dto.FileMetadata;

/**
 * Result of a download: the file's metadata and a streaming content body.
 * The caller owns the stream and must close it (use try-with-resources).
 */
public record FileDownload(FileMetadata metadata, InputStream content) implements Closeable {

    @Override
    public void close() throws IOException {
        content.close();
    }
}

