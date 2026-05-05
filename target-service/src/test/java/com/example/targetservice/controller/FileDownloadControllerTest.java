package com.example.targetservice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.targetservice.TargetServiceApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TargetServiceApplication.class)
@AutoConfigureMockMvc
class FileDownloadControllerTest {

    private static final Path STORAGE_DIR = Paths.get("files").toAbsolutePath().normalize();

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void setUp() throws Exception {
        Files.createDirectories(STORAGE_DIR);
        Files.writeString(STORAGE_DIR.resolve("welcome.txt"), "hello from storage");
    }

    @Test
    void shouldRejectPathTraversal() throws Exception {
        mockMvc.perform(get("/api/files/download")
                        .param("fileName", "../agent-platform/pom.xml"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void allowsNormalFileDownload() throws Exception {
        mockMvc.perform(get("/api/files/download")
                        .param("fileName", "welcome.txt"))
                .andExpect(status().is2xxSuccessful());
    }
}
