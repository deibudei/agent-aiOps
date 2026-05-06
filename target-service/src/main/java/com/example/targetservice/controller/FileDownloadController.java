package com.example.targetservice.controller;

import com.example.targetservice.service.FileStorageService;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileDownloadController {

    private final FileStorageService fileStorageService;

    public FileDownloadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/api/files/download")
    public String download(@RequestParam String fileName) throws IOException {
        return fileStorageService.readFile(fileName);
    }
}
