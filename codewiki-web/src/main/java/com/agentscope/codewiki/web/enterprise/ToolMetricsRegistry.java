package com.agentscope.codewiki.web.enterprise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ToolMetricsRegistry {
    private final Map<String, Metric> metrics = new ConcurrentHashMap<>();

    public void record(String toolName, boolean success, long durationMs) {
        metrics.computeIfAbsent(toolName, ignored -> new Metric()).record(success, durationMs);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        metrics.forEach((name, metric) -> out.put(name, metric.snapshot()));
        return out;
    }

    static class Metric {
        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong successes = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final List<Long> durations = Collections.synchronizedList(new ArrayList<>());

        void record(boolean success, long durationMs) {
            calls.incrementAndGet();
            if (success) {
                successes.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
            durations.add(Math.max(0, durationMs));
            if (durations.size() > 1000) {
                durations.remove(0);
            }
        }

        Map<String, Object> snapshot() {
            List<Long> copy;
            synchronized (durations) {
                copy = new ArrayList<>(durations);
            }
            Collections.sort(copy);
            long sum = copy.stream().mapToLong(Long::longValue).sum();
            long count = calls.get();
            return Map.of(
                    "calls", count,
                    "successes", successes.get(),
                    "failures", failures.get(),
                    "avgDurationMs", count == 0 ? 0.0 : sum * 1.0 / count,
                    "p95DurationMs", percentile(copy, 95),
                    "p99DurationMs", percentile(copy, 99));
        }

        private static long percentile(List<Long> values, int percentile) {
            if (values.isEmpty()) {
                return 0;
            }
            int index = (int) Math.ceil(values.size() * percentile / 100.0) - 1;
            return values.get(Math.max(0, Math.min(index, values.size() - 1)));
        }
    }
}
