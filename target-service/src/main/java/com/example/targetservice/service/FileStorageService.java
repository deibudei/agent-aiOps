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

    /** Reads only files inside the configured storage directory. */
    public String readFile(String fileName) throws IOException {
        Path filePath = BASE_DIR.resolve(fileName).toAbsolutePath().normalize();
        if (!filePath.startsWith(BASE_DIR)) {
            throw new IllegalArgumentException("path traversal denied: " + fileName);
        }
        return Files.readString(filePath);
    }

    public Path baseDir() {
        return BASE_DIR;
    }
}
