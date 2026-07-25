package com.agentscope.codewiki.web.enterprise;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent/conversations")
public class ConversationController {
    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @PostMapping
    public ConversationCreateResponse create(@Valid @RequestBody ChatRequest request) {
        ConversationExecution execution = service.start(request);
        return new ConversationCreateResponse(
                execution.conversationId(),
                execution.requestId(),
                execution.status(),
                "/api/agent/conversations/" + execution.conversationId() + "/events");
    }

    @GetMapping("/{conversationId}")
    public ConversationSnapshotResponse get(@PathVariable String conversationId) {
        ConversationExecution execution =
                service.find(conversationId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "conversation not found"));
        return new ConversationSnapshotResponse(
                execution.conversationId(),
                execution.requestId(),
                execution.status(),
                execution.answer(),
                execution.snapshotEvents());
    }

    @GetMapping("/{conversationId}/events")
    public SseEmitter events(@PathVariable String conversationId) {
        ConversationExecution execution =
                service.find(conversationId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "conversation not found"));
        SseEmitter emitter = new SseEmitter(0L);
        try {
            for (AgentProgressEvent event : execution.snapshotEvents()) {
                emitter.send(
                        SseEmitter.event()
                                .name(event.type().name())
                                .id(event.eventId())
                                .data(event));
            }
            execution.subscribe(emitter);
            emitter.send(
                    SseEmitter.event()
                            .name(AgentEventType.HEARTBEAT.name())
                            .data(java.util.Map.of("conversationId", conversationId)));
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }
}
