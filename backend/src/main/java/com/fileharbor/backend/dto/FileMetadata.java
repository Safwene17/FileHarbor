package com.fileharbor.backend.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata of a stored FileHarbor file.
 *
 * @param uploadedAt derived from Azure's blob creation time; {@code null} if Azure did not report it
 */
public record FileMetadata(
        UUID id,
        String originalFilename,
        String contentType,
        long size,
        Instant uploadedAt) {
}

