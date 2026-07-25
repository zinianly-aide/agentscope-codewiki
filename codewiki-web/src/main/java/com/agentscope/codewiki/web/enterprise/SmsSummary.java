package com.agentscope.codewiki.web.enterprise;

import java.util.List;

/** SMS summary payload returned by query_sms_summary. */
public record SmsSummary(
        String startDate,
        String endDate,
        int totalSubmitted,
        int totalSuccess,
        int sensitiveBlocked,
        List<PlatformMetric> platforms) {
    public record PlatformMetric(
            String platform, int submitted, int success, int sensitiveBlocked) {
        double blockedRate() {
            return submitted == 0 ? 0D : sensitiveBlocked * 1D / submitted;
        }
    }
}
