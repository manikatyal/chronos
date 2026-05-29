package com.mihir.jobworker.controller;

import com.mihir.jobworker.config.WorkerProcessingProperties;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/v1/reports")
public class ReportDownloadController {

    private final WorkerProcessingProperties processingProperties;

    public ReportDownloadController(WorkerProcessingProperties processingProperties) {
        this.processingProperties = processingProperties;
    }

    @GetMapping("/failed/{fileName:.+}")
    public ResponseEntity<InputStreamResource> downloadFailedReport(@PathVariable String fileName) {
        Path directory = resolveReportDirectory();
        Path target = directory.resolve(fileName).normalize();

        if (!target.startsWith(directory) || !Files.exists(target) || Files.isDirectory(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }

        try {
            InputStreamResource resource = new InputStreamResource(Files.newInputStream(target));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .contentLength(Files.size(target))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + target.getFileName() + "\"")
                    .body(resource);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read report", exception);
        }
    }

    private Path resolveReportDirectory() {
        String configured = processingProperties.reports() != null
                ? processingProperties.reports().outputDirectory()
                : null;
        if (configured == null || configured.isBlank()) {
            return Path.of("generated-reports").toAbsolutePath();
        }
        return Path.of(configured).toAbsolutePath();
    }
}
