package com.mihir.jobscheduler.controller;

import com.mihir.jobscheduler.dto.FailedJobResponse;
import com.mihir.jobscheduler.dto.WorkerFailureReportRequest;
import com.mihir.jobscheduler.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalWorkerFailureController {

    private final JobService jobService;

    @Value("${chronos.internal.worker-report-key}")
    private String workerReportKey;

    @PostMapping("/worker-failures")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void recordWorkerFailure(
            @RequestHeader(name = "X-Internal-Key", required = false) String providedKey,
            @Valid @RequestBody WorkerFailureReportRequest request) {
        ensureInternalAccess(providedKey);
        jobService.recordWorkerFailure(request);
    }

    @GetMapping("/failed-jobs")
    public List<FailedJobResponse> getFailedJobs(
            @RequestHeader(name = "X-Internal-Key", required = false) String providedKey,
            @RequestParam(name = "fromTime") Instant fromTime) {
        ensureInternalAccess(providedKey);
        return jobService.getFailedJobsSince(fromTime);
    }

    private void ensureInternalAccess(String providedKey) {
        if (providedKey == null || !java.security.MessageDigest.isEqual(
                workerReportKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                providedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("Invalid internal worker report key");
        }
    }
}
