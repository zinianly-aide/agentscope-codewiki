package com.agentscope.codewiki.web.enterprise;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class ConversationExecution {
    private final String conversationId;
    private final String requestId;
    private final String question;
    private final long startedAtMs = System.currentTimeMillis();
    private volatile long completedAtMs;
    private final int maxEvents;
    private final AtomicLong sequence = new AtomicLong();
    private final List<AgentProgressEvent> events = new ArrayList<>();
    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();
    private volatile ConversationStatus status = ConversationStatus.RUNNING;
    private volatile String answer = "";

    public ConversationExecution(String conversationId, String requestId, int maxEvents) {
        this(conversationId, requestId, "", maxEvents);
    }

    public ConversationExecution(
            String conversationId, String requestId, String question, int maxEvents) {
        this.conversationId = conversationId;
        this.requestId = requestId;
        this.question = question == null ? "" : question;
        this.maxEvents = maxEvents;
    }

    public synchronized AgentProgressEvent append(
            AgentEventType type,
            String stage,
            String message,
            AgentEventStatus status,
            int progress,
            java.util.Map<String, Object> metadata) {
        long seq = sequence.incrementAndGet();
        AgentProgressEvent event =
                new AgentProgressEvent(
                        seq,
                        String.format(java.util.Locale.ROOT, "evt-%03d", seq),
                        conversationId,
                        requestId,
                        OffsetDateTime.now(ZoneOffset.ofHours(9)),
                        type,
                        stage,
                        message,
                        status,
                        progress,
                        metadata == null ? java.util.Map.of() : metadata);
        events.add(event);
        while (events.size() > maxEvents) {
            events.remove(0);
        }
        broadcast(event);
        return event;
    }

    public synchronized List<AgentProgressEvent> snapshotEvents() {
        return List.copyOf(events);
    }

    public void subscribe(SseEmitter emitter) {
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(error -> subscribers.remove(emitter));
    }

    private void broadcast(AgentProgressEvent event) {
        for (SseEmitter emitter : List.copyOf(subscribers)) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name(event.type().name())
                                .id(event.eventId())
                                .data(event));
            } catch (Exception ex) {
                subscribers.remove(emitter);
            }
        }
    }

    public String conversationId() {
        return conversationId;
    }

    public String requestId() {
        return requestId;
    }

    public String question() {
        return question;
    }

    public ConversationStatus status() {
        return status;
    }

    public void status(ConversationStatus status) {
        this.status = status;
        if (status == ConversationStatus.COMPLETED || status == ConversationStatus.FAILED) {
            this.completedAtMs = System.currentTimeMillis();
        }
    }

    public long durationMs() {
        long end = completedAtMs == 0 ? System.currentTimeMillis() : completedAtMs;
        return Math.max(0, end - startedAtMs);
    }

    public String answer() {
        return answer;
    }

    public void answer(String answer) {
        this.answer = answer;
    }
}
