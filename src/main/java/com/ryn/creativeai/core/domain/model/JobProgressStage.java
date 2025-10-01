package com.ryn.creativeai.core.domain.model;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hitos de progreso para los Jobs de generación.
 */
public enum JobProgressStage {

    QUEUED(0),
    STARTING(5),
    SENDING_TO_PROVIDER(20),
    STORING_RESULTS(70),
    COMPLETED(100);

    private static final List<Map<String, Object>> DEFINITIONS = Arrays.stream(values())
            .map(stage -> Map.of(
                    "stage", stage.name(),
                    "progress", stage.progress))
            .toList();

    private final int progress;

    JobProgressStage(int progress) {
        this.progress = progress;
    }

    public int progressValue() {
        return progress;
    }

    public static Optional<JobProgressStage> fromProgress(int progress) {
        return Arrays.stream(values())
                .filter(stage -> stage.progress == progress)
                .findFirst();
    }

    public static List<Map<String, Object>> definitions() {
        return DEFINITIONS;
    }
}
