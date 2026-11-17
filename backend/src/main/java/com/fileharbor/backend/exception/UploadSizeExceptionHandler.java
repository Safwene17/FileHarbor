package com.fileharbor.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Maps oversized multipart requests to a 413 {@link ProblemDetail}.
 *
 * <p>This advice is deliberately <em>not</em> restricted to {@code FileController}: the container
 * rejects an oversized request while parsing it, before any controller has been chosen, and a
 * controller-scoped advice does not apply at that point. Only this single exception type is handled
 * here, so no other (for example legacy {@code /api/blobs}) error behavior changes.
 */
@RestControllerAdvice
public class UploadSizeExceptionHandler {

    static final String DETAIL = "The uploaded file exceeds the maximum allowed size.";

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, DETAIL);
        body.setTitle(HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }
}

