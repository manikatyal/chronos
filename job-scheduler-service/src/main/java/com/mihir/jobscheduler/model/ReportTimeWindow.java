package com.mihir.jobscheduler.model;

import java.util.Set;

public enum ReportTimeWindow {
    LAST_30_DAYS,
    LAST_15_DAYS,
    LAST_10_DAYS,
    LAST_5_DAYS,
    LAST_1_DAY,
    LAST_12_HOURS,
    LAST_6_HOURS,
    LAST_30_MINUTES,
    LAST_5_MINUTES;

    public static final Set<String> SUPPORTED_VALUES = Set.of(
            LAST_30_DAYS.name(),
            LAST_15_DAYS.name(),
            LAST_10_DAYS.name(),
            LAST_5_DAYS.name(),
            LAST_1_DAY.name(),
            LAST_12_HOURS.name(),
            LAST_6_HOURS.name(),
            LAST_30_MINUTES.name(),
            LAST_5_MINUTES.name());
}
