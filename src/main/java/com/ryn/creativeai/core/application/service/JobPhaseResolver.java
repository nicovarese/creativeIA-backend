package com.ryn.creativeai.core.application.service;

import com.ryn.creativeai.core.domain.model.JobStatus;

public final class JobPhaseResolver {

    private JobPhaseResolver() {}

    public static String resolve(JobStatus status, Integer progress) {
        if (status == null) return "UNKNOWN";
        int p = progress == null ? 0 : Math.max(0, Math.min(progress, 100));

        return switch (status) {
            case QUEUED -> "QUEUED";
            case RUNNING -> {
                if (p < 25) yield "PREPARING";
                if (p < 70) yield "GENERATING";
                yield "STORING";
            }
            case DONE -> "DONE";
            case FAILED -> "FAILED";
        };
    }
}
