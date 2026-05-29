package com.mihir.jobscheduler.controller;

import com.mihir.jobscheduler.dto.FailedJobResponse;
import com.mihir.jobscheduler.dto.JobDetailResponse;
import com.mihir.jobscheduler.dto.JobRequest;
import com.mihir.jobscheduler.dto.JobResponse;
import com.mihir.jobscheduler.dto.JobStatusResponse;
import com.mihir.jobscheduler.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody JobRequest request) {
        JobResponse response = jobService.createJob(request);
        HttpStatus status = response.alreadyExists() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable Long jobId) {
        return ResponseEntity.ok(jobService.getJobStatus(jobId));
    }

    @GetMapping("/{jobId}/detail")
    public ResponseEntity<JobDetailResponse> getJobDetail(@PathVariable Long jobId) {
        return ResponseEntity.ok(jobService.getJobDetail(jobId));
    }

    @GetMapping
    public ResponseEntity<List<JobStatusResponse>> getJobs(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(jobService.getJobs(page, size));
    }

    @GetMapping("/failed")
    public ResponseEntity<List<FailedJobResponse>> getFailedJobs(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(jobService.getFailedJobs(page, size));
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<JobResponse> updateJob(@PathVariable Long jobId, @Valid @RequestBody JobRequest request) {
        return ResponseEntity.ok(jobService.updateJob(jobId, request));
    }

    @DeleteMapping("/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(@PathVariable Long jobId) {
        jobService.deleteJob(jobId);
    }
}
