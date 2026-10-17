package com.mihir.jobscheduler.service;

import com.mihir.jobscheduler.dto.FailedJobResponse;
import com.mihir.jobscheduler.dto.JobDetailResponse;
import com.mihir.jobscheduler.dto.JobRequest;
import com.mihir.jobscheduler.dto.JobFieldMismatch;
import com.mihir.jobscheduler.dto.JobResponse;
import com.mihir.jobscheduler.dto.JobStatusResponse;
import com.mihir.jobscheduler.dto.WorkerFailureReportRequest;
import com.mihir.jobscheduler.entity.JobEntity;
import com.mihir.jobscheduler.entity.ScheduledJobLeaseEntity;
import com.mihir.jobscheduler.entity.ScheduledJobRetryExhaustionEntity;
import com.mihir.jobscheduler.exception.JobConflictException;
import com.mihir.jobscheduler.exception.JobDuplicateConflictException;
import com.mihir.jobscheduler.exception.JobNotFoundException;
import com.mihir.jobscheduler.exception.JobValidationException;
import com.mihir.jobscheduler.model.FailureSource;
import com.mihir.jobscheduler.manager.JobManager;
import com.mihir.jobscheduler.manager.ScheduledJobLeaseManager;
import com.mihir.jobscheduler.manager.ScheduledJobRetryExhaustionManager;
import com.mihir.jobscheduler.model.JobName;
import com.mihir.jobscheduler.model.ScheduleType;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobManager jobManager;
    private final ScheduledJobLeaseManager scheduledJobLeaseManager;
    private final ScheduledJobRetryExhaustionManager scheduledJobRetryExhaustionManager;
    private final CurrentUserService currentUserService;
    private final JobHandlerRegistry registry;
    private final JobEntityFactory jobEntityFactory;
    private final JobSchedulePlanner schedulePlanner;
    private final ObjectMapper objectMapper;

    // Users have no verified email address, so by default only admins may send email (prevents relay abuse).
    @org.springframework.beans.factory.annotation.Value("${chronos.security.user-email-enabled:false}")
    private boolean userEmailEnabled;

    @Transactional
    public JobResponse createJob(JobRequest request) {
        registry.get(request.jobName()).validatePayload(request.payload());
        validateRecipientPermissions(request);
        String currentUserId = currentUserService.currentUserId();
        JobEntity candidate = jobEntityFactory.create(request, currentUserId);
        JobEntity existing = jobManager.findDuplicate(
                currentUserId,
                candidate.getJobName(),
                candidate.getScheduleType(),
                candidate.getScheduleJson(),
                candidate.getPayloadJson()
        ).orElse(null);
        if (existing != null) {
            return toExistingResponse(existing, request);
        }
        try {
            JobEntity savedJob = jobManager.save(candidate);
            scheduledJobLeaseManager.createLease(savedJob, schedulePlanner.initialNextRunAt(request));
            return toResponse(savedJob);
        } catch (DataIntegrityViolationException exception) {
            JobEntity duplicate = jobManager.findDuplicate(
                    currentUserId,
                    candidate.getJobName(),
                    candidate.getScheduleType(),
                    candidate.getScheduleJson(),
                    candidate.getPayloadJson()
            ).orElseThrow(() -> exception);
            return toExistingResponse(duplicate, request);
        }
    }

    @Transactional(readOnly = true)
    public JobStatusResponse getJobStatus(Long jobId) {
        JobEntity job = jobManager.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId));
        currentUserService.ensureCanAccess(job);
        ScheduledJobLeaseEntity lease = scheduledJobLeaseManager.findByJobId(jobId)
                .orElseThrow(() -> new JobNotFoundException("Lease not found for job: " + jobId));
        return toStatusResponse(job, lease);
    }

    @Transactional(readOnly = true)
    public JobDetailResponse getJobDetail(Long jobId) {
        JobEntity job = jobManager.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId));
        currentUserService.ensureCanAccess(job);
        return new JobDetailResponse(
                job.getJobId(),
                job.getJobName(),
                job.getScheduleType(),
                job.getStatus(),
                job.getMaxRetries(),
                job.getCreatedBy(),
                job.getCreatedAt(),
                readJson(job.getScheduleJson()),
                readJson(job.getPayloadJson()));
    }

    @Transactional(readOnly = true)
    public List<JobStatusResponse> getJobs(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(safePage, safeSize);

        List<JobEntity> jobs = (currentUserService.isAdmin()
                ? jobManager.findPageForAll(pageable)
                : jobManager.findPageForUser(currentUserService.currentUserId(), pageable))
                .getContent();

        if (jobs.isEmpty()) {
            return List.of();
        }

        Map<Long, ScheduledJobLeaseEntity> leasesByJobId = scheduledJobLeaseManager.findAllByJobIds(
                        jobs.stream().map(JobEntity::getJobId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(ScheduledJobLeaseEntity::getJobId, lease -> lease));

        return jobs.stream()
                .map(job -> {
                    ScheduledJobLeaseEntity lease = leasesByJobId.get(job.getJobId());
                    if (lease == null) {
                        return null;
                    }
                    return toStatusResponse(job, lease);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FailedJobResponse> getFailedJobs(int page, int size) {
        currentUserService.ensureAdmin();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<ScheduledJobRetryExhaustionEntity> failures = scheduledJobRetryExhaustionManager
                .findPage(PageRequest.of(safePage, safeSize))
                .getContent();
        if (failures.isEmpty()) {
            return List.of();
        }

        Map<Long, JobEntity> jobsById = jobManager.findAllByIds(failures.stream()
                        .map(ScheduledJobRetryExhaustionEntity::getJobId)
                        .distinct()
                        .toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(JobEntity::getJobId, job -> job));

        return failures.stream()
                .map(failure -> {
                    JobEntity job = jobsById.get(failure.getJobId());
                    if (job == null) {
                        return null;
                    }
                    return toFailedJobResponse(job, failure);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FailedJobResponse> getFailedJobsSince(Instant fromTime) {
        List<ScheduledJobRetryExhaustionEntity> failures = scheduledJobRetryExhaustionManager.findAllSince(fromTime);
        if (failures.isEmpty()) {
            return List.of();
        }

        Map<Long, JobEntity> jobsById = jobManager.findAllByIds(failures.stream()
                        .map(ScheduledJobRetryExhaustionEntity::getJobId)
                        .distinct()
                        .toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(JobEntity::getJobId, job -> job));

        return failures.stream()
                .map(failure -> {
                    JobEntity job = jobsById.get(failure.getJobId());
                    if (job == null) {
                        return null;
                    }
                    return toFailedJobResponse(job, failure);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public void recordWorkerFailure(WorkerFailureReportRequest request) {
        JobEntity job = jobManager.findById(request.jobId())
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + request.jobId()));

        scheduledJobRetryExhaustionManager.save(ScheduledJobRetryExhaustionEntity.builder()
                .jobId(job.getJobId())
                .workerId(request.workerId())
                .failureSource(FailureSource.WORKER)
                .executionId(request.executionId())
                .logTag(request.logTag())
                .retryCount(0)
                .failureReason(request.failureReason())
                .exhaustedAt(request.failedAt())
                .build());
        org.slf4j.LoggerFactory.getLogger(JobService.class).error(
                "Worker failure recorded logTag={} jobId={} executionId={} workerId={} reason={}",
                request.logTag(), request.jobId(), request.executionId(), request.workerId(), request.failureReason());
    }

    @Transactional
    public JobResponse updateJob(Long jobId, JobRequest request) {
        registry.get(request.jobName()).validatePayload(request.payload());
        validateRecipientPermissions(request);
        JobEntity job = jobManager.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId));
        currentUserService.ensureCanAccess(job);
        ScheduledJobLeaseEntity lease = scheduledJobLeaseManager.findByJobId(jobId)
                .orElseThrow(() -> new JobNotFoundException("Lease not found for job: " + jobId));
        ensureEditable(job, lease);

        JobEntity candidate = jobEntityFactory.create(request, job.getCreatedBy());
        JobEntity duplicate = jobManager.findDuplicate(
                job.getCreatedBy(),
                candidate.getJobName(),
                candidate.getScheduleType(),
                candidate.getScheduleJson(),
                candidate.getPayloadJson()
        ).filter(existing -> !existing.getJobId().equals(jobId)).orElse(null);
        if (duplicate != null) {
            throw new JobDuplicateConflictException(
                    "Duplicate job already exists for the requested payload",
                    toExistingResponse(duplicate, request));
        }

        jobEntityFactory.apply(job, request);
        JobEntity updated = jobManager.save(job);
        lease.setNextRunAt(schedulePlanner.initialNextRunAt(request));
        lease.setRetryCount(0);
        lease.setLockedAt(null);
        lease.setLockedBy(null);
        lease.setLockedUntil(null);
        scheduledJobLeaseManager.save(lease);
        return toResponse(updated);
    }

    @Transactional
    public void deleteJob(Long jobId) {
        JobEntity job = jobManager.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId));
        currentUserService.ensureCanAccess(job);
        ScheduledJobLeaseEntity lease = scheduledJobLeaseManager.findByJobId(jobId)
                .orElseThrow(() -> new JobNotFoundException("Lease not found for job: " + jobId));
        ensureEditable(job, lease);
        scheduledJobLeaseManager.deleteByJobId(jobId);
        jobManager.delete(job);
    }

    private JobResponse toResponse(JobEntity job) {
        return new JobResponse(
                job.getJobId(),
                job.getJobName(),
                job.getScheduleType(),
                job.getStatus(),
                job.getMaxRetries(),
                job.getCreatedBy(),
                job.getCreatedAt(),
                false,
                List.of());
    }

    private JobResponse toExistingResponse(JobEntity job, JobRequest request) {
        return new JobResponse(
                job.getJobId(),
                job.getJobName(),
                job.getScheduleType(),
                job.getStatus(),
                job.getMaxRetries(),
                job.getCreatedBy(),
                job.getCreatedAt(),
                true,
                mismatches(job, request));
    }

    private JobStatusResponse toStatusResponse(JobEntity job, ScheduledJobLeaseEntity lease) {
        boolean editable = isEditable(job, lease);
        return new JobStatusResponse(
                job.getJobId(),
                job.getJobName(),
                job.getScheduleType(),
                job.getStatus(),
                job.getMaxRetries(),
                job.getCreatedBy(),
                job.getCreatedAt(),
                lease.getNextRunAt(),
                lease.getRetryCount(),
                lease.getFirstClaimedAt(),
                lease.getLockedBy(),
                lease.getLockedUntil(),
                editable,
                editable);
    }

    private FailedJobResponse toFailedJobResponse(JobEntity job, ScheduledJobRetryExhaustionEntity exhaustion) {
        return new FailedJobResponse(
                job.getJobId(),
                job.getJobName(),
                job.getScheduleType(),
                job.getStatus(),
                job.getMaxRetries(),
                job.getCreatedBy(),
                job.getCreatedAt(),
                exhaustion != null ? exhaustion.getFailureSource() : null,
                exhaustion != null ? exhaustion.getExecutionId() : null,
                exhaustion != null ? exhaustion.getLogTag() : null,
                exhaustion != null ? exhaustion.getWorkerId() : null,
                exhaustion != null ? exhaustion.getRetryCount() : null,
                exhaustion != null ? exhaustion.getFailureReason() : null,
                exhaustion != null ? exhaustion.getExhaustedAt() : null);
    }

    private void ensureEditable(JobEntity job, ScheduledJobLeaseEntity lease) {
        if (!isEditable(job, lease)) {
            throw new JobConflictException("Job can no longer be modified or deleted in its current state: " + job.getJobId());
        }
    }

    private boolean isEditable(JobEntity job, ScheduledJobLeaseEntity lease) {
        if (job.getScheduleType() == ScheduleType.CRON) {
            return !isCurrentlyLocked(lease);
        }
        return lease.getFirstClaimedAt() == null;
    }

    private boolean isCurrentlyLocked(ScheduledJobLeaseEntity lease) {
        return lease.getLockedBy() != null
                && lease.getLockedUntil() != null
                && lease.getLockedUntil().isAfter(Instant.now());
    }

    private List<JobFieldMismatch> mismatches(JobEntity job, JobRequest request) {
        List<JobFieldMismatch> mismatches = new ArrayList<>();
        if (job.getMaxRetries() != request.maxRetries()) {
            mismatches.add(new JobFieldMismatch("maxRetries", request.maxRetries(), job.getMaxRetries()));
        }
        return mismatches;
    }

    private void validateRecipientPermissions(JobRequest request) {
        if (request.payload() == null) {
            return;
        }

        if (request.jobName() == JobName.SEND_EMAIL && !userEmailEnabled) {
            ensureAdminCanUseBulkMode("Only admins can create email jobs");
        }

        if (request.jobName() == JobName.SEND_EMAIL && request.payload().has("emails")) {
            ensureAdminCanUseBulkMode("Only admins can create email jobs for multiple recipients");
        }

        if (request.jobName() == JobName.SEND_NOTIFICATION) {
            if (request.payload().path("sendToAll").asBoolean(false)) {
                ensureAdminCanUseBulkMode("Only admins can send reminders to all users");
                return;
            }

            if (request.payload().has("userIds")) {
                ensureAdminCanUseBulkMode("Only admins can create notification jobs for multiple recipients");
            }

            if (!currentUserService.isAdmin()
                    && request.payload().hasNonNull("userId")
                    && !currentUserService.currentUserId().equals(request.payload().get("userId").asText())) {
                throw new JobValidationException("Users can create notification jobs only for themselves");
            }
        }

        if (request.jobName() == JobName.GENERATE_REPORT) {
            ensureAdminCanUseBulkMode("Only admins can create failed jobs report requests");
        }
    }

    private void ensureAdminCanUseBulkMode(String message) {
        if (!currentUserService.isAdmin()) {
            throw new JobValidationException(message);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read stored job JSON", exception);
        }
    }
}
