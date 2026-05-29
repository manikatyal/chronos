package com.mihir.jobworker.model;

import java.time.Duration;

public enum ReportTimeWindow {
    LAST_30_DAYS(Duration.ofDays(30)),
    LAST_15_DAYS(Duration.ofDays(15)),
    LAST_10_DAYS(Duration.ofDays(10)),
    LAST_5_DAYS(Duration.ofDays(5)),
    LAST_1_DAY(Duration.ofDays(1)),
    LAST_12_HOURS(Duration.ofHours(12)),
    LAST_6_HOURS(Duration.ofHours(6)),
    LAST_30_MINUTES(Duration.ofMinutes(30)),
    LAST_5_MINUTES(Duration.ofMinutes(5));

    private final Duration duration;

    ReportTimeWindow(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }
}
