package com.fileharbor.backend.controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fileharbor.backend.dto.FileMetadata;
import com.fileharbor.backend.service.FileDownload;
import com.fileharbor.backend.service.FileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/** HTTP endpoints of the FileHarbor file API. All storage logic lives in {@link FileService}. */
@RestController
@RequestMapping(FileController.BASE_PATH)
public class FileController {

    static final String BASE_PATH = "/api/files";

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileMetadata> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty.");
        }
        FileMetadata metadata;
        try (InputStream content = file.getInputStream()) {
            metadata = fileService.upload(file.getOriginalFilename(), file.getContentType(), content, file.getSize());
        }
        URI location = UriComponentsBuilder.fromPath(BASE_PATH + "/{id}").buildAndExpand(metadata.id()).toUri();
        return ResponseEntity.created(location).body(metadata);
    }

    @GetMapping
    public List<FileMetadata> list() {
        return fileService.list();
    }

    /**
     * Streams the file as an attachment. The body is an {@link InputStreamResource}: Spring copies it
     * to the response without buffering the file and closes the stream when writing finishes or fails.
     *
     * <p>A successful {@code FileService.download} only means the file was verified and the stream was
     * opened; bytes are fetched from Azure lazily while the response is written. A failure at that point
     * happens after the status and headers may already be sent, so a {@code ProblemDetail} can no
     * longer be returned and the client sees a truncated response.
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId) {
        FileDownload download = fileService.download(fileId);
        try {
            FileMetadata metadata = download.metadata();
            return ResponseEntity.ok()
                    .contentType(parseMediaType(metadata.contentType()))
                    .contentLength(metadata.size())
                    .header(HttpHeaders.CONTENT_DISPOSITION, attachment(metadata.originalFilename()).toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new InputStreamResource(download.content()));
        } catch (RuntimeException e) {
            // Building the response failed before Spring took ownership of the stream.
            try {
                download.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    /** All validation and eligibility checks live in {@link FileService#delete}; errors map to ProblemDetail. */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(@PathVariable String fileId) {
        fileService.delete(fileId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Built with Spring's {@link ContentDisposition}, never by string concatenation. Plain printable
     * ASCII names use {@code filename="..."} (quotes and backslashes escaped by Spring); anything else is
     * sent as RFC 5987/6266 {@code filename*=UTF-8''...}, which percent-encodes non-ASCII and control characters.
     */
    private static ContentDisposition attachment(String filename) {
        boolean printableAscii = filename.chars().allMatch(c -> c >= 0x20 && c <= 0x7E);
        ContentDisposition.Builder builder = ContentDisposition.attachment();
        return (printableAscii ? builder.filename(filename) : builder.filename(filename, StandardCharsets.UTF_8)).build();
    }

    private static MediaType parseMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

