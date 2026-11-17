package com.fileharbor.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobItemProperties;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.options.BlobInputStreamOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.fileharbor.backend.dto.FileMetadata;
import com.fileharbor.backend.exception.FileIdCollisionException;
import com.fileharbor.backend.exception.FileStorageException;
import com.fileharbor.backend.exception.InvalidFileIdException;
import com.fileharbor.backend.exception.InvalidFilenameException;
import com.fileharbor.backend.exception.StoredFileNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private static final String UNICODE_NAME = "Raport final – ședință 😀.pdf";
    private static final UUID ID = UUID.fromString("3f2b8c1e-6d4a-4b7e-9a51-0c2f7d9e1a44");
    private static final OffsetDateTime CREATED = OffsetDateTime.of(2026, 11, 16, 10, 15, 30, 0, ZoneOffset.UTC);

    @Mock
    private BlobContainerClient containerClient;

    @Mock
    private BlobClient blobClient;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(containerClient);
    }

    // ---------------------------------------------------------------- upload

    @Test
    void upload_createsBlobNamedWithCanonicalLowercaseUuid() {
        FileMetadata result = upload(UNICODE_NAME, "application/pdf");

        ArgumentCaptor<String> blobName = ArgumentCaptor.forClass(String.class);
        verify(containerClient).getBlobClient(blobName.capture());
        assertEquals(UUID.fromString(blobName.getValue()).toString(), blobName.getValue());
        assertEquals(result.id().toString(), blobName.getValue());
    }

    @Test
    void upload_returnsMetadataDescribingTheStoredFile() {
        stubSuccessfulUpload();

        FileMetadata result = fileService.upload(UNICODE_NAME, "application/pdf",
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 3);

        assertEquals(UNICODE_NAME, result.originalFilename());
        assertEquals("application/pdf", result.contentType());
        assertEquals(3, result.size());
        assertEquals(CREATED.toInstant(), result.uploadedAt());
    }

    @Test
    void upload_encodesOriginalFilenameAsUrlSafeBase64OfUtf8AndItCanBeDecoded() {
        upload(UNICODE_NAME, "application/pdf");
        BlobParallelUploadOptions options = capturedUploadOptions();

        String stored = options.getMetadata().get("originalFilename");
        assertEquals(1, options.getMetadata().size());
        assertTrue(stored.matches("[A-Za-z0-9_-]+"), "must be URL-safe Base64: " + stored);
        assertEquals(UNICODE_NAME, new String(Base64.getUrlDecoder().decode(stored), StandardCharsets.UTF_8));
        assertEquals(UNICODE_NAME, FileService.decodeFilename(options.getMetadata()).orElseThrow());
    }

    @Test
    void upload_setsContentTypeHeaderAndMetadataInTheSameUploadCall() {
        upload("a.txt", "text/plain");
        BlobParallelUploadOptions options = capturedUploadOptions();

        assertEquals("text/plain", options.getHeaders().getContentType());
        assertTrue(options.getMetadata().containsKey("originalFilename"));
    }

    @Test
    void upload_usesAtomicNoOverwriteCondition() {
        upload("a.txt", "text/plain");
        BlobParallelUploadOptions options = capturedUploadOptions();

        assertEquals("*", options.getRequestConditions().getIfNoneMatch());
        assertNull(options.getRequestConditions().getIfMatch());
        verify(blobClient, never()).exists();
    }

    @Test
    void upload_generatesDifferentIdsForDuplicateFilenames() {
        stubSuccessfulUpload();

        FileMetadata first = fileService.upload("same.txt", "text/plain", new ByteArrayInputStream(new byte[]{1}), 1);
        FileMetadata second = fileService.upload("same.txt", "text/plain", new ByteArrayInputStream(new byte[]{1}), 1);

        assertFalse(first.id().equals(second.id()));
    }

    @Test
    void upload_sanitizesFilenameBeforeStoring() {
        upload("..\\..\\evil/dir/na\u0000me.txt", "text/plain");

        assertEquals("name.txt", FileService.decodeFilename(capturedUploadOptions().getMetadata()).orElseThrow());
    }

    @Test
    void upload_rejectsBlankFilenameWithoutCallingAzure() {
        for (String bad : new String[]{"   ", null, "dir/"}) {
            assertThrows(InvalidFilenameException.class,
                    () -> fileService.upload(bad, "text/plain", new ByteArrayInputStream(new byte[]{1}), 1));
        }
        verify(containerClient, never()).getBlobClient(any());
    }

    @Test
    void upload_mapsBlobAlreadyExistsErrorCodeToCollisionException() {
        stubUploadFailure(storageException(409, BlobErrorCode.BLOB_ALREADY_EXISTS, "secret details"));

        assertThrows(FileIdCollisionException.class, this::uploadExpectingFailure);
    }

    @Test
    void upload_mapsFailedIfNoneMatchPreconditionToCollisionException() {
        stubUploadFailure(storageException(412, BlobErrorCode.CONDITION_NOT_MET, "secret details"));

        assertThrows(FileIdCollisionException.class, this::uploadExpectingFailure);
    }

    @Test
    void upload_doesNotTreatUnrelatedConflict409AsCollision() {
        stubUploadFailure(storageException(409, BlobErrorCode.CONTAINER_BEING_DELETED, "secret details"));

        FileStorageException thrown = assertThrows(FileStorageException.class, this::uploadExpectingFailure);

        assertFalse(thrown.getMessage().contains("secret"));
    }

    @Test
    void upload_doesNotTreat409WithoutErrorCodeAsCollision() {
        stubUploadFailure(storageException(409, null, "secret details"));

        assertThrows(FileStorageException.class, this::uploadExpectingFailure);
    }

    @Test
    void upload_doesNotTreatUnexpected412AsCollision() {
        // 412 is only a collision when Azure says the condition was not met.
        stubUploadFailure(storageException(412, BlobErrorCode.fromString("SomethingElse"), "secret details"));
        assertThrows(FileStorageException.class, this::uploadExpectingFailure);

        stubUploadFailure(storageException(412, null, "secret details"));
        assertThrows(FileStorageException.class, this::uploadExpectingFailure);
    }

    @Test
    void upload_wrapsOtherAzureFailuresWithoutExposingAzureMessage() {
        stubUploadFailure(storageException(500, null, "secret account details"));

        FileStorageException thrown = assertThrows(FileStorageException.class, this::uploadExpectingFailure);

        assertFalse(thrown.getMessage().contains("secret"));
    }

    // ----------------------------------------------------- content type

    @Test
    void upload_keepsValidContentTypes() {
        for (String valid : new String[]{
                "application/pdf",
                "text/plain; charset=utf-8",
                "text/plain;charset=UTF-8",
                "image/svg+xml",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "multipart/form-data; boundary=\"a b\"",
                "  application/json  "}) {
            assertEquals(valid.strip(), upload("a.txt", valid).contentType(), "type: " + valid);
            assertEquals(valid.strip(), capturedUploadOptions().getHeaders().getContentType());
        }
    }

    @Test
    void upload_usesOctetStreamForMissingOrBlankContentType() {
        assertEquals("application/octet-stream", upload("a.bin", null).contentType());
        assertEquals("application/octet-stream", upload("a.bin", "").contentType());
        assertEquals("application/octet-stream", upload("a.bin", "   ").contentType());
    }

    @Test
    void upload_usesOctetStreamForMalformedContentType() {
        for (String malformed : new String[]{
                "pdf", "text/", "/plain", "text//plain", "text/plain;", "text/plain; charset", "text/plain; =utf-8",
                "text plain/x", "text/pl ain", "text/plain; charset=\"unterminated", "*/*", "text/*", "*/plain",
                "text/plain, text/html", "a".repeat(300) + "/b"}) {
            assertEquals("application/octet-stream", upload("a.bin", malformed).contentType(), "type: " + malformed);
            assertEquals("application/octet-stream", capturedUploadOptions().getHeaders().getContentType());
        }
    }

    @Test
    void upload_usesOctetStreamForContentTypeWithControlCharacters() {
        for (String bad : new String[]{"text/plain\r\nX-Injected: 1", "text/plain\n", "text/pl\u0000ain",
                "text/plain;\tcharset=utf-8", "text/plain\u007f"}) {
            assertEquals("application/octet-stream", upload("a.bin", bad).contentType(), "type: " + bad);
        }
    }

    // ------------------------------------------------------- timestamps

    @Test
    void upload_usesUploadResponseTimestampWithoutExtraRequest() {
        FileMetadata result = upload("a.txt", "text/plain");

        assertEquals(CREATED.toInstant(), result.uploadedAt());
        verify(blobClient, never()).getProperties();
        verify(blobClient, never()).getPropertiesWithResponse(any(), any(), any());
    }

    @Test
    void upload_keepsNullTimestampWhenAzureProvidesNone() {
        BlockBlobItem item = mock(BlockBlobItem.class);
        when(item.getLastModified()).thenReturn(null);
        stubUploadResponse(item);

        FileMetadata result = fileService.upload("a.txt", "text/plain", new ByteArrayInputStream(new byte[]{1}), 1);

        assertNull(result.uploadedAt());
    }

    @Test
    void uploadAndListExposeTheSameTimestampSemantics() {
        // Both are the blob's creation moment as reported by Azure, as an Instant.
        FileMetadata uploaded = upload("a.txt", "text/plain");
        stubListing(blobItem(uploaded.id().toString(), encode("a.txt"), "text/plain", 1L, CREATED));

        assertEquals(uploaded.uploadedAt(), fileService.list().get(0).uploadedAt());
    }

    // ------------------------------------------------------------------ list

    @Test
    void list_returnsValidFileHarborBlobs() {
        stubListing(blobItem(ID.toString(), encode(UNICODE_NAME), "application/pdf", 42L, CREATED));

        assertEquals(List.of(new FileMetadata(ID, UNICODE_NAME, "application/pdf", 42, CREATED.toInstant())),
                fileService.list());
    }

    @Test
    void list_requestsBlobMetadata() {
        stubListing();

        fileService.list();

        ArgumentCaptor<ListBlobsOptions> options = ArgumentCaptor.forClass(ListBlobsOptions.class);
        verify(containerClient).listBlobs(options.capture(), isNull());
        assertTrue(options.getValue().getDetails().getRetrieveMetadata());
    }

    @Test
    void list_excludesLegacyBlobsWithNonUuidNames() {
        stubListing(
                blobItem("report.pdf", encode("report.pdf"), "application/pdf", 1L, CREATED),
                blobItem("folder/" + ID, encode("x"), "text/plain", 1L, CREATED),
                blobItem("3F2B8C1E-6D4A-4B7E-9A51-0C2F7D9E1A44", encode("upper"), "text/plain", 1L, CREATED),
                blobItem("3f2b8c1e6d4a4b7e9a510c2f7d9e1a44", encode("nohyphens"), "text/plain", 1L, CREATED));

        assertTrue(fileService.list().isEmpty());
    }

    @Test
    void list_excludesMalformedFileHarborBlobs() {
        BlobItem noMetadata = blobItem(UUID.randomUUID().toString(), null, "text/plain", 1L, CREATED);
        BlobItem emptyMetadata = new BlobItem().setName(UUID.randomUUID().toString())
                .setMetadata(Map.of()).setProperties(properties("text/plain", 1L, CREATED));
        BlobItem notBase64 = blobItem(UUID.randomUUID().toString(), "***not base64***", "text/plain", 1L, CREATED);
        BlobItem invalidUtf8 = blobItem(UUID.randomUUID().toString(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{(byte) 0xC3, (byte) 0x28}),
                "text/plain", 1L, CREATED);
        BlobItem blankName = blobItem(UUID.randomUUID().toString(), encode("   "), "text/plain", 1L, CREATED);
        BlobItem noLength = blobItem(UUID.randomUUID().toString(), encode("a.txt"), "text/plain", null, CREATED);
        stubListing(noMetadata, emptyMetadata, notBase64, invalidUtf8, blankName, noLength);

        assertTrue(fileService.list().isEmpty());
    }

    @Test
    void list_handlesEmptyContainer() {
        stubListing();

        assertTrue(fileService.list().isEmpty());
    }

    @Test
    void list_handlesMissingCreationTimeAndContentType() {
        stubListing(blobItem(ID.toString(), encode("a.txt"), null, 5L, null));

        FileMetadata file = fileService.list().get(0);

        assertNull(file.uploadedAt());
        assertEquals("application/octet-stream", file.contentType());
    }

    @Test
    void list_acceptsMetadataKeyWithDifferentCase() {
        stubListing(new BlobItem().setName(ID.toString())
                .setMetadata(Map.of("OriginalFilename", encode("a.txt")))
                .setProperties(properties("text/plain", 1L, CREATED)));

        assertEquals("a.txt", fileService.list().get(0).originalFilename());
    }

    @Test
    void list_sortsNewestFirstWithMissingCreationTimeLast() {
        UUID older = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID newer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID undated = UUID.fromString("00000000-0000-0000-0000-000000000003");
        stubListing(
                blobItem(undated.toString(), encode("c"), "t/t", 1L, null),
                blobItem(older.toString(), encode("a"), "t/t", 1L, CREATED),
                blobItem(newer.toString(), encode("b"), "t/t", 1L, CREATED.plusHours(1)));

        assertEquals(List.of(newer, older, undated), fileService.list().stream().map(FileMetadata::id).toList());
    }

    @Test
    void list_wrapsAzureFailure() {
        BlobStorageException failure = storageException(500, "secret");
        doThrow(failure).when(containerClient).listBlobs(any(ListBlobsOptions.class), isNull());

        FileStorageException thrown = assertThrows(FileStorageException.class, () -> fileService.list());

        assertFalse(thrown.getMessage().contains("secret"));
    }

    // -------------------------------------------------------------- download

    @Test
    void download_rejectsInvalidIdsWithoutCallingAzure() {
        for (String bad : new String[]{null, "", "not-a-uuid", "1-1-1-1-1", ID.toString().replace("-", ""),
                "../" + ID, ID + "/x"}) {
            assertThrows(InvalidFileIdException.class, () -> fileService.download(bad), "id: " + bad);
        }
        verify(containerClient, never()).getBlobClient(any());
    }

    @Test
    void download_rejectsMissingBlob() {
        when(containerClient.getBlobClient(ID.toString())).thenReturn(blobClient);
        BlobStorageException notFound = storageException(404, "BlobNotFound");
        doThrow(notFound).when(blobClient).getProperties();

        assertThrows(StoredFileNotFoundException.class, () -> fileService.download(ID.toString()));
    }

    @Test
    void download_rejectsLegacyBlobWithoutMetadataAndNeverOpensIt() {
        when(containerClient.getBlobClient(ID.toString())).thenReturn(blobClient);
        BlobProperties legacy = blobProperties(Map.of(), "\"etag\"", "text/plain", 3, CREATED);
        doReturn(legacy).when(blobClient).getProperties();

        assertThrows(StoredFileNotFoundException.class, () -> fileService.download(ID.toString()));
        verify(blobClient, never()).openInputStream(any(BlobInputStreamOptions.class));
    }

    @Test
    void download_rejectsBlobWithMalformedFilenameMetadata() {
        when(containerClient.getBlobClient(ID.toString())).thenReturn(blobClient);
        BlobProperties malformed = blobProperties(Map.of("originalFilename", "***"), "\"etag\"", "text/plain", 3, CREATED);
        doReturn(malformed).when(blobClient).getProperties();

        assertThrows(StoredFileNotFoundException.class, () -> fileService.download(ID.toString()));
        verify(blobClient, never()).openInputStream(any(BlobInputStreamOptions.class));
    }

    @Test
    void download_returnsMetadataAndStreamPinnedToVerifiedEtag() throws Exception {
        when(containerClient.getBlobClient(ID.toString())).thenReturn(blobClient);
        BlobProperties valid = blobProperties(Map.of("originalFilename", encode(UNICODE_NAME)),
                "\"etag-1\"", "application/pdf", 42, CREATED);
        doReturn(valid).when(blobClient).getProperties();
        BlobInputStream stream = mock(BlobInputStream.class);
        doReturn(stream).when(blobClient).openInputStream(any(BlobInputStreamOptions.class));

        try (FileDownload download = fileService.download(ID.toString().toUpperCase())) {
            assertEquals(new FileMetadata(ID, UNICODE_NAME, "application/pdf", 42, CREATED.toInstant()),
                    download.metadata());
            assertSame(stream, download.content());
        }

        ArgumentCaptor<BlobInputStreamOptions> options = ArgumentCaptor.forClass(BlobInputStreamOptions.class);
        verify(blobClient).openInputStream(options.capture());
        assertEquals("\"etag-1\"", options.getValue().getRequestConditions().getIfMatch());
        verify(stream).close();
    }

    @Test
    void download_treatsBlobChangedAfterVerificationAsNotFound() {
        when(containerClient.getBlobClient(ID.toString())).thenReturn(blobClient);
        BlobProperties valid = blobProperties(Map.of("originalFilename", encode("a.txt")), "\"etag-1\"", "text/plain", 1, CREATED);
        doReturn(valid).when(blobClient).getProperties();
        BlobStorageException changed = storageException(412, "changed");
        doThrow(changed).when(blobClient).openInputStream(any(BlobInputStreamOptions.class));

        assertThrows(StoredFileNotFoundException.class, () -> fileService.download(ID.toString()));
    }

    @Test
    void download_refusesToContinueWithoutEtag() {
        when(containerClient.getBlobClient(ID.toString())).thenReturn(blobClient);
        BlobProperties noEtag = blobProperties(Map.of("originalFilename", encode("a.txt")), null, "text/plain", 1, CREATED);
        doReturn(noEtag).when(blobClient).getProperties();

        assertThrows(FileStorageException.class, () -> fileService.download(ID.toString()));
        verify(blobClient, never()).openInputStream(any(BlobInputStreamOptions.class));
    }

    // ---------------------------------------------------------------- delete

    @Test
    void delete_rejectsInvalidIdsWithoutCallingAzure() {
        assertThrows(InvalidFileIdException.class, () -> fileService.delete("report.pdf"));
        assertThrows(InvalidFileIdException.class, () -> fileService.delete(null));
        verify(containerClient, never()).getBlobClient(any());
    }

    @Test
    void delete_rejectsMissingFileWithoutDeleting() {
        when(containerClient.getBlobClient(ID.toString())).thenReturn(blobClient);
        BlobStorageException notFound = storageException(404, "BlobNotFound");
        doThrow(notFound).when(blobClient).getProperties();

        assertThrows(StoredFileNotFoundException.class, () -> fileService.delete(ID.toString()));
        verify(blobClient, never()).deleteWithResponse(any(), any(), any(), any());
    }

    @Test
    void delete_rejectsNonFileHarborBlobWithoutDeleting() {
        when(containerClient.getBlobClient(ID.toString())).thenReturn(blobClient);
        BlobProperties legacy = blobProperties(Map.of(), "\"etag\"", "text/plain", 1, CREATED);
        doReturn(legacy).when(blobClient).getProperties();

        assertThrows(StoredFileNotFoundException.class, () -> fileService.delete(ID.toString()));
        verify(blobClient, never()).deleteWithResponse(any(), any(), any(), any());
    }

    @Test
    void delete_usesEtagIfMatchCondition() {
        when(containerClient.getBlobClient(ID.toString())).thenReturn(blobClient);
        BlobProperties valid = blobProperties(Map.of("originalFilename", encode("a.txt")), "\"etag-9\"", "text/plain", 1, CREATED);
        doReturn(valid).when(blobClient).getProperties();

        fileService.delete(ID.toString());

        ArgumentCaptor<BlobRequestConditions> conditions = ArgumentCaptor.forClass(BlobRequestConditions.class);
        verify(blobClient).deleteWithResponse(isNull(), conditions.capture(), isNull(), eq(Context.NONE));
        assertEquals("\"etag-9\"", conditions.getValue().getIfMatch());
        verify(blobClient, never()).delete();
        verify(blobClient, never()).deleteIfExists();
        verify(blobClient, never()).exists();
    }

    @Test
    void delete_treatsBlobChangedAfterVerificationAsNotFound() {
        when(containerClient.getBlobClient(ID.toString())).thenReturn(blobClient);
        BlobProperties valid = blobProperties(Map.of("originalFilename", encode("a.txt")), "\"etag-1\"", "text/plain", 1, CREATED);
        doReturn(valid).when(blobClient).getProperties();
        BlobStorageException changed = storageException(412, "ConditionNotMet");
        doThrow(changed).when(blobClient).deleteWithResponse(any(), any(), any(), any());

        assertThrows(StoredFileNotFoundException.class, () -> fileService.delete(ID.toString()));
    }

    @Test
    void delete_wrapsUnexpectedAzureFailure() {
        when(containerClient.getBlobClient(ID.toString())).thenReturn(blobClient);
        BlobStorageException unavailable = storageException(503, "secret");
        doThrow(unavailable).when(blobClient).getProperties();

        FileStorageException thrown = assertThrows(FileStorageException.class, () -> fileService.delete(ID.toString()));

        assertFalse(thrown.getMessage().contains("secret"));
    }

    // ------------------------------------------------------------- utilities

    @Test
    void sanitizeFilename_capsLengthWithoutSplittingSurrogatePairs() {
        String sanitized = FileService.sanitizeFilename("😀".repeat(300));

        assertEquals("😀".repeat(255), sanitized);
    }

    // --------------------------------------------------------------- helpers

    private FileMetadata upload(String filename, String contentType) {
        stubSuccessfulUpload();
        return fileService.upload(filename, contentType, new ByteArrayInputStream(new byte[]{1}), 1);
    }

    private BlobParallelUploadOptions capturedUploadOptions() {
        ArgumentCaptor<BlobParallelUploadOptions> options = ArgumentCaptor.forClass(BlobParallelUploadOptions.class);
        verify(blobClient, atLeastOnce()).uploadWithResponse(options.capture(), isNull(), any(Context.class));
        return options.getValue();
    }

    private void stubSuccessfulUpload() {
        BlockBlobItem item = mock(BlockBlobItem.class);
        lenient().when(item.getLastModified()).thenReturn(CREATED);
        stubUploadResponse(item);
    }

    private void stubUploadResponse(BlockBlobItem item) {
        @SuppressWarnings("unchecked")
        Response<BlockBlobItem> response = mock(Response.class);
        lenient().when(response.getValue()).thenReturn(item);
        lenient().when(containerClient.getBlobClient(any())).thenReturn(blobClient);
        lenient().when(blobClient.uploadWithResponse(any(BlobParallelUploadOptions.class), isNull(), any(Context.class)))
                .thenReturn(response);
    }

    private void stubUploadFailure(BlobStorageException failure) {
        lenient().when(containerClient.getBlobClient(any())).thenReturn(blobClient);
        doThrow(failure).when(blobClient)
                .uploadWithResponse(any(BlobParallelUploadOptions.class), isNull(), any(Context.class));
    }

    private void uploadExpectingFailure() {
        fileService.upload("a.txt", "text/plain", new ByteArrayInputStream(new byte[]{1}), 1);
    }

    private void stubListing(BlobItem... items) {
        @SuppressWarnings("unchecked")
        PagedIterable<BlobItem> paged = mock(PagedIterable.class);
        when(paged.iterator()).thenReturn(List.of(items).iterator());
        when(containerClient.listBlobs(any(ListBlobsOptions.class), isNull())).thenReturn(paged);
    }

    private static BlobItem blobItem(String name, String encodedFilename, String contentType, Long length,
            OffsetDateTime created) {
        Map<String, String> metadata = null;
        if (encodedFilename != null) {
            metadata = new HashMap<>();
            metadata.put("originalFilename", encodedFilename);
        }
        return new BlobItem().setName(name).setMetadata(metadata).setProperties(properties(contentType, length, created));
    }

    private static BlobItemProperties properties(String contentType, Long length, OffsetDateTime created) {
        return new BlobItemProperties().setContentType(contentType).setContentLength(length).setCreationTime(created);
    }

    private static BlobProperties blobProperties(Map<String, String> metadata, String etag, String contentType,
            long size, OffsetDateTime created) {
        BlobProperties properties = mock(BlobProperties.class);
        lenient().when(properties.getMetadata()).thenReturn(metadata);
        lenient().when(properties.getETag()).thenReturn(etag);
        lenient().when(properties.getContentType()).thenReturn(contentType);
        lenient().when(properties.getBlobSize()).thenReturn(size);
        lenient().when(properties.getCreationTime()).thenReturn(created);
        return properties;
    }

    private static String encode(String filename) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(filename.getBytes(StandardCharsets.UTF_8));
    }

    private static BlobStorageException storageException(int status, String message) {
        return storageException(status, null, message);
    }

    private static BlobStorageException storageException(int status, BlobErrorCode code, String message) {
        BlobStorageException exception = mock(BlobStorageException.class);
        lenient().when(exception.getStatusCode()).thenReturn(status);
        lenient().when(exception.getErrorCode()).thenReturn(code);
        lenient().when(exception.getMessage()).thenReturn(message);
        return exception;
    }
}

