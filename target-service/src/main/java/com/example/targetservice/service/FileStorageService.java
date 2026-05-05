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

    /** Reads a file without checking whether the resolved path leaves BASE_DIR. */
    public String readFile(String fileName) throws IOException {
        Path filePath = BASE_DIR.resolve(fileName).toAbsolutePath().normalize();
        return Files.readString(filePath);
    }

    public Path baseDir() {
        return BASE_DIR;
    }
}
