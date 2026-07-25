package com.agentscope.codewiki.web.enterprise;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class ToolAuditService {
    private final List<ToolAuditRecord> records = new ArrayList<>();
    private final int maxRecords;

    public ToolAuditService() {
        this(1000);
    }

    public ToolAuditService(int maxRecords) {
        this.maxRecords = maxRecords;
    }

    public synchronized ToolAuditRecord record(
            ToolExecutionContext context,
            String tool,
            String source,
            OffsetDateTime start,
            long durationMs,
            boolean success,
            String errorType) {
        ToolAuditRecord record =
                new ToolAuditRecord(
                        context == null ? "" : context.requestId(),
                        context == null ? "" : context.conversationId(),
                        tool,
                        source,
                        start,
                        OffsetDateTime.now(ZoneOffset.ofHours(9)),
                        durationMs,
                        success ? "SUCCESS" : "FAILED",
                        errorType == null ? "" : errorType);
        records.add(record);
        while (records.size() > maxRecords) {
            records.remove(0);
        }
        return record;
    }

    public synchronized List<ToolAuditRecord> recent(int limit) {
        int from = Math.max(0, records.size() - Math.max(0, limit));
        return List.copyOf(records.subList(from, records.size()));
    }
}
