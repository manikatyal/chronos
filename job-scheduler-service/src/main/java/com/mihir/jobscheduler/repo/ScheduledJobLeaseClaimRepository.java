package com.mihir.jobscheduler.repo;

import java.time.Duration;
import java.util.List;

public interface ScheduledJobLeaseClaimRepository {

    List<Long> claimDueJobs(String workerId, Duration leaseDuration, int batchSize);
}
