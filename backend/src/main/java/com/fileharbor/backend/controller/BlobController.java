package com.fileharbor.backend.controller;

import com.fileharbor.backend.service.BlobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/blobs")
public class BlobController {

    private final BlobService blobService;

    @PostMapping("/{blobName}")
    public ResponseEntity<String> uploadBlob(@PathVariable String blobName, @RequestBody byte[] data) {
        blobService.uploadBlob(blobName, data);
        return ResponseEntity.status(HttpStatus.CREATED).body("Blob uploaded successfully.");
    }
    @GetMapping("/{blobName}")
    public ResponseEntity<byte[]> downloadBlob(@PathVariable String blobName) {
        byte[] data = blobService.downloadBlob(blobName);
        return ResponseEntity.ok(data);
    }
    @GetMapping
    public ResponseEntity<List<String>> listBlobs() {
        List<String> blobs = blobService.listBlobs();
        return ResponseEntity.ok(blobs);
    }
    @DeleteMapping("/{blobName}")
    public ResponseEntity<String> deleteBlob(@PathVariable String blobName) {
        blobService.deleteBlob(blobName);
        return ResponseEntity.ok("Blob deleted successfully.");
    }
}