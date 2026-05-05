package com.example.targetservice.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Service;

@Service
public class FileStorageService {

    private static final Path BASE_DIR = Paths.get("files").toAbsolutePath().normalize();

    public FileStorageService() throws IOException {
        Files.createDirectories(BASE_DIR);
    }

    /**
     * Reads a file from the storage directory.
     * BUG: No path traversal protection - "../" sequences allow reading arbitrary files.
     */
    public String readFile(String fileName) throws IOException {
        Path filePath = BASE_DIR.resolve(fileName);
        return Files.readString(filePath);
    }

    public Path baseDir() {
        return BASE_DIR;
    }
}
