package com.ryn.creativeai.core.application.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class JobEventsHub {

    private static final long TIMEOUT_MS = Duration.ofMinutes(10).toMillis();
    private final Map<UUID, List<SseEmitter>> byJob = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID jobId) {
        SseEmitter em = new SseEmitter(TIMEOUT_MS);
        byJob.computeIfAbsent(jobId, k -> Collections.synchronizedList(new ArrayList<>())).add(em);

        em.onCompletion(() -> remove(jobId, em));
        em.onTimeout(() -> remove(jobId, em));
        em.onError(e -> remove(jobId, em));

        // ping inicial (para que el front sepa que está conectado)
        try {
            em.send(SseEmitter.event().name("INIT").data("ok").reconnectTime(2000));
        } catch (IOException ignored) {}
        return em;
    }

    public void send(UUID jobId, String event, Object payload) {
        var list = byJob.get(jobId);
        if (list == null) return;
        synchronized (list) {
            for (var em : new ArrayList<>(list)) {
                try {
                    em.send(SseEmitter.event()
                            .name(event)
                            .data(payload, MediaType.APPLICATION_JSON));
                } catch (IOException e) {
                    remove(jobId, em);
                }
            }
        }
    }

    public void complete(UUID jobId) {
        var list = byJob.remove(jobId);
        if (list == null) return;
        for (var em : list) {
            try { em.complete(); } catch (Exception ignored) {}
        }
    }

    private void remove(UUID jobId, SseEmitter em) {
        var list = byJob.get(jobId);
        if (list != null) {
            list.remove(em);
            if (list.isEmpty()) byJob.remove(jobId);
        }
    }

    @PreDestroy
    public void shutdown() {
        byJob.keySet().forEach(this::complete);
        byJob.clear();
    }
}