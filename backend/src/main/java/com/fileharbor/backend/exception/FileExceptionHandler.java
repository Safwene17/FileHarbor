package com.fileharbor.backend.exception;

import com.fileharbor.backend.controller.FileController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error of the {@code /api/files} API into an {@code application/problem+json}
 * {@link ProblemDetail} with a stable title and a fixed, safe detail message.
 *
 * <p>Exception messages are never copied into responses: they may contain Azure or internal details.
 * The causes of server-side failures are logged instead.
 */
@RestControllerAdvice(assignableTypes = FileController.class)
public class FileExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FileExceptionHandler.class);

    // ---- framework exceptions (customized messages) ----

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ex, HttpStatus.BAD_REQUEST, "A file must be uploaded in the multipart field 'file'.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ex, HttpStatus.PAYLOAD_TOO_LARGE, UploadSizeExceptionHandler.DETAIL, request);
    }

    // ---- application exceptions ----

    @ExceptionHandler(InvalidFilenameException.class)
    public ResponseEntity<Object> handleInvalidFilename(InvalidFilenameException ex, WebRequest request) {
        return problem(ex, HttpStatus.BAD_REQUEST, "The uploaded file must have a valid filename.", request);
    }

    @ExceptionHandler(InvalidFileIdException.class)
    public ResponseEntity<Object> handleInvalidFileId(InvalidFileIdException ex, WebRequest request) {
        return problem(ex, HttpStatus.BAD_REQUEST, "The file ID must be a valid UUID.", request);
    }

    @ExceptionHandler(StoredFileNotFoundException.class)
    public ResponseEntity<Object> handleFileNotFound(StoredFileNotFoundException ex, WebRequest request) {
        return problem(ex, HttpStatus.NOT_FOUND, "File not found.", request);
    }

    @ExceptionHandler(FileIdCollisionException.class)
    public ResponseEntity<Object> handleIdCollision(FileIdCollisionException ex, WebRequest request) {
        log.warn("File ID collision while uploading a file");
        return problem(ex, HttpStatus.CONFLICT, "The file could not be stored because of an ID conflict. Please try again.", request);
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<Object> handleStorageFailure(FileStorageException ex, WebRequest request) {
        log.error("File storage failure", ex);
        return problem(ex, HttpStatus.SERVICE_UNAVAILABLE, "File storage is temporarily unavailable. Please try again later.", request);
    }

    // ---- everything else ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error while handling a file request", ex);
        return problem(ex, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    private ResponseEntity<Object> problem(Exception ex, HttpStatus status, String detail, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        return handleExceptionInternal(ex, body, new HttpHeaders(), status, request);
    }
}


