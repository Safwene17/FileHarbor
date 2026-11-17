# FileHarbor — API Specification

This document is the authoritative API contract for FileHarbor.

Base path: `/api`

Stack: Java 21, Spring Boot 4.1.2, Angular 21, Azure Blob Storage, Azure App Service.

Implementation status:

| Part | Status |
|---|---|
| `GET /api/health` | Implemented |
| Storage operations (`FileService`: upload, list, download, delete) | Implemented and unit-tested with mocked Azure clients; not yet run against a real storage account |
| `/api/files` HTTP endpoints, global `ProblemDetail` handler | Not implemented |
| Legacy `/api/blobs` endpoints | Present, temporary (see [Legacy API](#6-legacy-api)) |

## 1. Storage design

The MVP uses Azure Blob Storage as its storage and metadata source. No database is required.

- The Azure Blob container must remain private.
- Each uploaded file receives a server-generated UUID as its blob name.
- Original filenames are never used as blob identifiers. Duplicate original filenames are allowed.
- Existing blobs must never be overwritten. Uploads use an atomic Azure conditional upload with
  `If-None-Match: *`. An exists-then-upload check must not be used.
- The original filename is stored in Azure blob metadata under the key `originalFilename`, encoded as
  **URL-safe Base64 (RFC 4648 §5) over its UTF-8 bytes**. The same encoding and decoding convention is
  applied everywhere.
- The content type is stored in the blob's HTTP `Content-Type` property.
- File size and creation time are obtained from Azure blob properties. `uploadedAt` is **not** stored
  as custom metadata.
- No PostgreSQL or other database is introduced.

### What qualifies as a FileHarbor file

A blob is a FileHarbor file only when **both** conditions hold:

1. Its name is a canonical UUID.
2. It has a valid, decodable `originalFilename` metadata value.

Legacy blobs and malformed blobs are never exposed as valid FileHarbor files: they are omitted from
listings and treated as not found for download and delete.

## 2. API endpoints

Base path: `/api/files`

### File metadata object

Returned by upload and list.

```json
{
  "id": "3f2b8c1e-6d4a-4b7e-9a51-0c2f7d9e1a44",
  "originalFilename": "Raport final – ședință.pdf",
  "contentType": "application/pdf",
  "size": 482113,
  "uploadedAt": "2026-11-16T10:15:30Z"
}
```

| Field | Source |
|---|---|
| `id` | Blob name (canonical lowercase UUID) |
| `originalFilename` | Decoded `originalFilename` blob metadata |
| `contentType` | Blob `Content-Type` property |
| `size` | Blob content length, in bytes |
| `uploadedAt` | Azure blob creation-time property (ISO-8601, UTC) |

### POST `/api/files`

- Accepts `multipart/form-data`; required field: `file`.
- Rejects a missing or empty file, and a missing filename.
- Maximum size: 20 MB.
- Generates a UUID server-side; duplicate filenames are allowed; never overwrites an existing blob.
- Success: `201 Created` with the file metadata object.

### GET `/api/files`

- Success: `200 OK` with an array of file metadata objects (id, originalFilename, contentType, size, uploadedAt).
- Includes only valid FileHarbor files.
- Size and creation time are derived from Azure properties.

### GET `/api/files/{fileId}`

- Accepts only canonical UUID identifiers.
- Success: `200 OK` with the file contents and these headers:
  - `Content-Type`: set appropriately from the stored content type.
  - `Content-Disposition: attachment` always, with a safe `filename` and an RFC 5987 `filename*`
    for international filenames.
  - `X-Content-Type-Options: nosniff`.
- `404 Not Found` for missing files and for blobs that are not valid FileHarbor files.

### DELETE `/api/files/{fileId}`

- Accepts only canonical UUID identifiers.
- Deletes only valid FileHarbor files.
- Success: `204 No Content`.
- `404 Not Found` for nonexistent files and for blobs that are not valid FileHarbor files.
- Uses ETag-based conditional deletion where supported, so a blob that changed after verification is
  not deleted by accident.

### GET `/api/health`

Application health endpoint, already implemented: `200 OK` → `{"status":"UP","application":"FileHarbor"}`.
It is separate from Spring Boot Actuator's `/actuator/health`.

## 3. Validation and security

- Reject empty uploads, missing files and missing filenames.
- Enforce the 20 MB maximum.
- Validate UUIDs strictly, using the canonical textual representation.
- Do not trust browser-provided MIME types as proof of file safety.
- Sanitize filenames before including them in response headers.
- Do not expose Azure exception messages or credentials to clients.
- Keep the storage container private.
- Do not expose Azure credentials to Angular.

## 4. Error responses

All API errors use Spring `ProblemDetail` with `Content-Type: application/problem+json`. Detail
messages are safe and consistent, and never include internal exception details.

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "File not found."
}
```

| Status | Meaning |
|---|---|
| `400 Bad Request` | Invalid identifier or invalid upload |
| `404 Not Found` | Missing file, or a blob that is not a valid FileHarbor file |
| `409 Conflict` | UUID collision / existing blob |
| `413 Payload Too Large` | Upload exceeds 20 MB |
| `500 Internal Server Error` | Unexpected failure |
| `503 Service Unavailable` | Appropriate temporary Azure service failures |

## 5. Authentication and Azure identity

Application-user authentication is separate from Azure authentication.

| Environment | Azure authentication |
|---|---|
| Local development | `DefaultAzureCredential` with developer credentials |
| Azure App Service | Managed Identity |
| Angular | No Azure credentials |

- Use **Storage Blob Data Contributor** with the narrowest practical scope, ideally the target
  container where that is sufficient.
- Managed Identity authenticates the backend to Azure. It does not authenticate application users.
- Do not deploy publicly until application-user authentication and file ownership are implemented.

## 6. Legacy API

The existing `/api/blobs` endpoints are temporary. They use client-selected blob names, allow
overwriting, and do not enforce the FileHarbor file rules.

After `/api/files` is implemented and tested, remove the `/api/blobs` endpoints and their obsolete
tests. Legacy blobs must remain untouched; migrating them is outside the MVP scope.

## 7. Architecture

Keep the architecture simple:

```text
Controller → Service → Azure Blob SDK
```

- Use concrete services and constructor injection.
- Do not introduce service interfaces without a genuine need, `FileServiceImpl` classes, unnecessary
  storage abstraction layers, or a database or repository layer.
- DTOs and centralized exception handling are appropriate where required.

## 8. Implementation questions to verify

Verify the actual APIs and behavior of the Azure Storage Blob SDK version resolved by the project's
Maven BOM. Never invent SDK behavior.

- Conditional upload options and `If-None-Match` behavior.
- The exception raised when a blob already exists.
- Metadata key/value restrictions and size limits.
- Listing with metadata and the required properties.
- Availability of creation time, content type and size.
- Download streaming and metadata retrieval.
- Missing-blob behavior.
- ETag-based conditional deletion.
- The Spring exception raised for oversized multipart requests.
- Required Azure RBAC permissions.

Current state: the Azure Storage Blob SDK resolved by the project is `azure-storage-blob` 12.36.0 (Azure
SDK BOM 1.3.9). The existence and signatures of the APIs used by the storage service were inspected in
that version, and the service is unit-tested with mocked clients. Runtime behavior against a real storage
account (error codes for existing and missing blobs, whether list results include creation time,
`If-Match` behavior on streaming reads) and the multipart and RBAC questions remain **unverified** and
must be confirmed before this contract is considered fully validated.

## 9. Project scope

The initial MVP supports upload, list, download and delete.

No database, sharing, version history or public access is required.
