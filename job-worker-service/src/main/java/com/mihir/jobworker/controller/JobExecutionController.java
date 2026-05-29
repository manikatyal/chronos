package com.mihir.jobworker.controller;

import com.mihir.jobworker.dto.JobExecutionResponse;
import com.mihir.jobworker.service.JobExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/executions")
@RequiredArgsConstructor
public class JobExecutionController {

    private final JobExecutionService jobExecutionService;

    @GetMapping
    public ResponseEntity<List<JobExecutionResponse>> getAll() {
        return ResponseEntity.ok(jobExecutionService.getAll());
    }

    @GetMapping("/failed")
    public ResponseEntity<List<JobExecutionResponse>> getFailed() {
        return ResponseEntity.ok(jobExecutionService.getFailed());
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobExecutionResponse> getByJobId(@PathVariable Long jobId) {
        return ResponseEntity.ok(jobExecutionService.getByJobId(jobId));
    }
}
