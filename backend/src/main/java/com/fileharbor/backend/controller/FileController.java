
package com.fileharbor.backend.controller;

import com.fileharbor.backend.service.FileService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    // Upload a file
    @PostMapping
    public ResponseEntity<String> upload(
            @RequestParam("file") MultipartFile file) throws IOException {

        fileService.upload(file);

        return ResponseEntity
                .status(201)
                .body("File uploaded successfully.");
    }

    // List all files
    @GetMapping
    public ResponseEntity<List<String>> list() {

        return ResponseEntity.ok(fileService.list());
    }

    // Download a file
    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> download(
            @PathVariable String filename) {

        byte[] file = fileService.download(filename);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file);
    }

    // Delete a file
    @DeleteMapping("/{filename}")
    public ResponseEntity<String> delete(
            @PathVariable String filename) {

        fileService.delete(filename);

        return ResponseEntity.ok("File deleted successfully.");
    }
}
