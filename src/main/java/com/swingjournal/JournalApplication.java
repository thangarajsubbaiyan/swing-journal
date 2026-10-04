package com.swingjournal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JournalApplication {

    public static void main(String[] args) throws IOException {
        // SQLite creates the file but not its folder, so make sure the folder exists.
        String dir = System.getProperty("journal.db.dir",
                System.getProperty("user.home") + "/swing-journal");
        Files.createDirectories(Path.of(dir));
        SpringApplication.run(JournalApplication.class, args);
    }
}
