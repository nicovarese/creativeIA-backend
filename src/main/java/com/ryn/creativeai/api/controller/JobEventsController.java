package com.ryn.creativeai.api.controller;

import com.ryn.creativeai.core.application.service.JobEventsHub;
import com.ryn.creativeai.infra.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/v1/jobs")
@RequiredArgsConstructor
public class JobEventsController {

    private final JobRepository jobs;
    private final JobEventsHub eventsHub;

    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable UUID id) {
        jobs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return eventsHub.subscribe(id);
    }
}
