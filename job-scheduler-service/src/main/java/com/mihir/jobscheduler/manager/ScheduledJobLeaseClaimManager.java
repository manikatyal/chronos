package com.mihir.jobscheduler.manager;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class ScheduledJobLeaseClaimManager {

    private static final String CLAIM_SQL = """
            UPDATE scheduled_job_leases
            SET
                locked_by = :workerId,
                locked_at = now(),
                locked_until = now() + make_interval(secs => :leaseSeconds),
                first_claimed_at = coalesce(first_claimed_at, now())
            WHERE job_id IN (
                SELECT job_id
                FROM scheduled_job_leases
                WHERE next_run_at <= now()
                  AND (locked_until IS NULL OR locked_until < now())
                ORDER BY next_run_at ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            RETURNING job_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ScheduledJobLeaseClaimManager(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> claimDueJobs(String workerId, Duration leaseDuration, int batchSize) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("workerId", workerId)
                .addValue("leaseSeconds", leaseDuration.toSeconds())
                .addValue("batchSize", batchSize);
        return jdbcTemplate.query(CLAIM_SQL, parameters, (rs, rowNum) -> rs.getLong("job_id"));
    }
}
