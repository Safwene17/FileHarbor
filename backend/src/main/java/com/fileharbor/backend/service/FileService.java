package com.fileharbor.backend.service;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.azure.core.exception.AzureException;
import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobItemProperties;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.options.BlobInputStreamOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.fileharbor.backend.dto.FileMetadata;
import com.fileharbor.backend.exception.FileIdCollisionException;
import com.fileharbor.backend.exception.FileStorageException;
import com.fileharbor.backend.exception.InvalidFileIdException;
import com.fileharbor.backend.exception.InvalidFilenameException;
import com.fileharbor.backend.exception.StoredFileNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Stores FileHarbor files in Azure Blob Storage.
 *
 * <p>A blob is a FileHarbor file only if its name is a canonical (lowercase) UUID and it carries a
 * decodable {@value #FILENAME_METADATA_KEY} metadata value. Every other blob (for example one
 * created by the legacy {@code /api/blobs} endpoints) is invisible to this service and is never
 * returned, read or deleted by it.
 *
 * <p><b>Timestamp semantics.</b> {@code uploadedAt} always means the moment the blob was first
 * successfully created. Uploads never overwrite, so a blob's creation time never changes. List and
 * download read Azure's creation-time property; upload uses the {@code Last-Modified} value from the
 * upload response, which for a newly created blob is the same moment, so no extra request is needed.
 * Azure reports these at one-second precision. If Azure supplies no timestamp the value is {@code null}.
 * It is never stored as custom metadata.
 */
@Service
@RequiredArgsConstructor
public class FileService {

    static final String FILENAME_METADATA_KEY = "originalFilename";
    static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final int MAX_CONTENT_TYPE_LENGTH = 255;
    private static final String TOKEN = "[!#$%&'*+.^_`|~0-9A-Za-z-]+";
    private static final String QUOTED_STRING = "\"(?:[ !#-\\[\\]-~]|\\\\[ -~])*\"";
    private static final Pattern MEDIA_TYPE = Pattern.compile(
            TOKEN + "/" + TOKEN + "(?: *; *" + TOKEN + "=(?:" + TOKEN + "|" + QUOTED_STRING + "))*");
    private static final Pattern CANONICAL_UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final BlobContainerClient containerClient;


    /**
     * Stores a new file under a freshly generated UUID. Existing blobs are never overwritten:
     * the upload carries {@code If-None-Match: *}, so Azure rejects it atomically.
     *
     * @throws InvalidFilenameException if the filename is missing or blank after sanitizing
     * @throws FileIdCollisionException if a blob with the generated ID already exists
     * @throws FileStorageException     on any other storage failure
     */
    public FileMetadata upload(String originalFilename, String contentType, InputStream content, long size) {
        Objects.requireNonNull(content, "content");
        String filename = sanitizeFilename(originalFilename);
        String type = normalizeContentType(contentType);
        UUID id = UUID.randomUUID();

        BlobParallelUploadOptions options = new BlobParallelUploadOptions(content, size)
                .setHeaders(new BlobHttpHeaders().setContentType(type))
                .setMetadata(Map.of(FILENAME_METADATA_KEY, encodeFilename(filename)))
                .setRequestConditions(new BlobRequestConditions().setIfNoneMatch("*"));

        try {
            Response<BlockBlobItem> response = containerClient.getBlobClient(id.toString())
                    .uploadWithResponse(options, null, Context.NONE);
            // Upload response Last-Modified == creation time of a brand-new, never-overwritten blob.
            OffsetDateTime created = response.getValue() == null ? null : response.getValue().getLastModified();
            return new FileMetadata(id, filename, type, size, toInstant(created));
        } catch (BlobStorageException e) {
            if (isAlreadyExists(e)) {
                throw new FileIdCollisionException();
            }
            throw new FileStorageException(e);
        } catch (AzureException e) {
            throw new FileStorageException(e);
        }
    }

    /** Lists valid FileHarbor files, newest first. Legacy and malformed blobs are skipped. */
    public List<FileMetadata> list() {
        ListBlobsOptions options = new ListBlobsOptions()
                .setDetails(new BlobListDetails().setRetrieveMetadata(true));
        List<FileMetadata> files = new ArrayList<>();
        try {
            for (BlobItem item : containerClient.listBlobs(options, null)) {
                toFileMetadata(item).ifPresent(files::add);
            }
        } catch (AzureException e) {
            throw new FileStorageException(e);
        }
        files.sort(Comparator.comparing(FileMetadata::uploadedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FileMetadata::id));
        return files;
    }

    /**
     * Opens a streaming download. The stream is pinned to the verified blob's ETag, so it fails
     * rather than returning a blob that changed after verification. The caller must close the result.
     *
     * @throws InvalidFileIdException      if {@code fileId} is not a canonical UUID
     * @throws StoredFileNotFoundException if the file is missing or not a valid FileHarbor file
     * @throws FileStorageException        on any other storage failure
     */
    public FileDownload download(String fileId) {
        VerifiedFile file = verify(parseFileId(fileId));
        try {
            InputStream stream = file.blob().openInputStream(new BlobInputStreamOptions()
                    .setRequestConditions(new BlobRequestConditions().setIfMatch(file.etag())));
            return new FileDownload(file.metadata(), stream);
        } catch (AzureException e) {
            throw translate(e);
        }
    }

    /**
     * Deletes a valid FileHarbor file, only if it is still the exact blob that was verified
     * ({@code If-Match} on its ETag).
     *
     * @throws InvalidFileIdException      if {@code fileId} is not a canonical UUID
     * @throws StoredFileNotFoundException if the file is missing, invalid, or changed since verification
     * @throws FileStorageException        on any other storage failure
     */
    public void delete(String fileId) {
        VerifiedFile file = verify(parseFileId(fileId));
        try {
            file.blob().deleteWithResponse(null, new BlobRequestConditions().setIfMatch(file.etag()), null, Context.NONE);
        } catch (AzureException e) {
            throw translate(e);
        }
    }

    // ---- verification ----

    private record VerifiedFile(BlobClient blob, String etag, FileMetadata metadata) {
    }

    private VerifiedFile verify(UUID id) {
        BlobClient blob = containerClient.getBlobClient(id.toString());
        BlobProperties properties;
        try {
            properties = blob.getProperties();
        } catch (AzureException e) {
            throw translate(e);
        }
        Optional<String> filename = decodeFilename(properties.getMetadata());
        if (filename.isEmpty()) {
            throw new StoredFileNotFoundException();
        }
        String etag = properties.getETag();
        if (etag == null || etag.isBlank()) {
            // Without an ETag the If-Match protection cannot be applied, so refuse to continue.
            throw new FileStorageException("Storage did not return an ETag for the blob.");
        }
        FileMetadata metadata = new FileMetadata(id, filename.get(), normalizeContentType(properties.getContentType()),
                properties.getBlobSize(), toInstant(properties.getCreationTime()));
        return new VerifiedFile(blob, etag, metadata);
    }

    private static Optional<FileMetadata> toFileMetadata(BlobItem item) {
        String name = item.getName();
        BlobItemProperties properties = item.getProperties();
        if (item.isDeleted() || name == null || !CANONICAL_UUID.matcher(name).matches()
                || properties == null || properties.getContentLength() == null) {
            return Optional.empty();
        }
        return decodeFilename(item.getMetadata()).map(filename -> new FileMetadata(
                UUID.fromString(name),
                filename,
                normalizeContentType(properties.getContentType()),
                properties.getContentLength(),
                toInstant(properties.getCreationTime())));
    }

    // ---- helpers ----

    /** Accepts upper or lower case hex, but only the canonical 8-4-4-4-12 form; normalizes to lowercase. */
    static UUID parseFileId(String fileId) {
        if (fileId == null) {
            throw new InvalidFileIdException();
        }
        String normalized = fileId.toLowerCase(Locale.ROOT);
        if (!CANONICAL_UUID.matcher(normalized).matches()) {
            throw new InvalidFileIdException();
        }
        return UUID.fromString(normalized);
    }

    static String encodeFilename(String filename) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(filename.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes the filename metadata (key matched case-insensitively); empty if absent, blank or malformed. */
    static Optional<String> decodeFilename(Map<String, String> metadata) {
        if (metadata == null) {
            return Optional.empty();
        }
        return metadata.entrySet().stream()
                .filter(entry -> FILENAME_METADATA_KEY.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .flatMap(FileService::decodeBase64Utf8);
    }

    private static Optional<String> decodeBase64Utf8(String encoded) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return decoded.isBlank() ? Optional.empty() : Optional.of(decoded);
        } catch (IllegalArgumentException | CharacterCodingException e) {
            return Optional.empty();
        }
    }

    /** Display label only: drops directory parts and control characters and caps the length. */
    static String sanitizeFilename(String filename) {
        if (filename == null) {
            throw new InvalidFilenameException();
        }
        String base = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1)
                .replaceAll("\\p{Cc}", "")
                .strip();
        int[] codePoints = base.codePoints().limit(MAX_FILENAME_LENGTH).toArray();
        String result = new String(codePoints, 0, codePoints.length).strip();
        if (result.isEmpty()) {
            throw new InvalidFilenameException();
        }
        return result;
    }

    /**
     * The client-supplied type is only an untrusted label. It is kept only if it is a syntactically
     * valid, concrete media type ({@code type/subtype} with optional {@code ; name=value} parameters,
     * RFC 9110 tokens); anything else (null, blank, control characters, malformed, wildcard, too
     * long) falls back to {@code application/octet-stream}. No claim is made that the content
     * actually matches the type.
     */
    static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.chars().anyMatch(Character::isISOControl)) {
            return DEFAULT_CONTENT_TYPE; // checked on the raw value: strip() would hide a trailing CR/LF
        }
        String stripped = contentType.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_CONTENT_TYPE_LENGTH
                || !MEDIA_TYPE.matcher(stripped).matches()) {
            return DEFAULT_CONTENT_TYPE;
        }
        String essence = stripped.split(";", 2)[0].strip();
        if (essence.startsWith("*/") || essence.endsWith("/*")) {
            return DEFAULT_CONTENT_TYPE;
        }
        return stripped;
    }

    private static Instant toInstant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }

    /**
     * True only when Azure reports that the blob already exists. The upload sends nothing but
     * {@code If-None-Match: *}, so the two outcomes that mean "the blob exists" are:
     * <ul>
     *   <li>error code {@code BlobAlreadyExists} (Azure answers 409 for this), or</li>
     *   <li>HTTP 412 with error code {@code ConditionNotMet}, i.e. the precondition failed.</li>
     * </ul>
     * Any other 409 (for example {@code ContainerBeingDeleted}) or any 412 without
     * {@code ConditionNotMet} is an unrelated storage failure and is not reported as a collision.
     */
    private static boolean isAlreadyExists(BlobStorageException e) {
        BlobErrorCode code = e.getErrorCode();
        if (BlobErrorCode.BLOB_ALREADY_EXISTS.equals(code)) {
            return true;
        }
        return e.getStatusCode() == 412 && BlobErrorCode.CONDITION_NOT_MET.equals(code);
    }

    /** 404 (missing) and 412 (changed since verification) both mean "nothing safe to act on". */
    private static RuntimeException translate(AzureException e) {
        if (e instanceof BlobStorageException storageException
                && (storageException.getStatusCode() == 404 || storageException.getStatusCode() == 412)) {
            return new StoredFileNotFoundException();
        }
        return new FileStorageException(e);
    }
}

