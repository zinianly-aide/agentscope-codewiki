package com.agentscope.codewiki.web.enterprise;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** Single minimal ReAct-style enterprise agent with one toolkit and local skill loading. */
public class EnterpriseReActAgent {
    static final String SYSTEM_PROMPT =
            """
            你是企业内部智能助手。

            执行规则：

            1. 涉及企业实时数据时，使用已注册的查询工具。
            2. 涉及精确统计、比例、日期、金额或批量数据聚合时，才运行 Python。
            3. 简单问答不得为了展示能力而运行代码。
            4. 当可用 Skill 与用户任务匹配时，先读取对应 Skill 的完整说明。
            5. 工具失败、参数不完整或没有数据时，明确说明，不得编造。
            6. 最终结果应说明数据时间范围、数据来源和计算依据。
            7. 当前所有能力均为只读，不执行发信、数据库修改、代码提交或生产发布。
            8. 不允许尝试访问宿主机文件、Docker Socket、企业密钥或未授权接口。
            """;

    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern CHINESE_RANGE =
            Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日?至(\\d{1,2})月(\\d{1,2})日?");
    private final EnterpriseAgentProperties properties;
    private final EnterpriseTools tools;
    private final SkillRepository skillRepository;
    private final AgentToolRegistry agentToolRegistry;
    private final LlmReActAgent llmReActAgent;

    public EnterpriseReActAgent(
            EnterpriseAgentProperties properties,
            EnterpriseTools tools,
            SkillRepository skillRepository,
            AgentToolRegistry agentToolRegistry,
            LlmReActAgent llmReActAgent) {
        this.properties = properties;
        this.tools = tools;
        this.skillRepository = skillRepository;
        this.agentToolRegistry = agentToolRegistry;
        this.llmReActAgent = llmReActAgent;
    }

    public AgentChatResult chat(List<SessionTurn> history, String message) {
        return chat(history, message, null);
    }

    public AgentChatResult chat(
            List<SessionTurn> history, String message, ToolExecutionContext context) {
        ExecutionMode mode = properties.executionMode();
        if (mode == ExecutionMode.LLM_REACT) {
            return llmReActAgent.chat(history, message, context, MDC.get("userId"));
        }
        return new AgentChatResult(
                deterministicChat(history, message, context), ExecutionMode.DETERMINISTIC);
    }

    public int maxIters() {
        return properties.getMaxIters();
    }

    private String deterministicChat(
            List<SessionTurn> history, String message, ToolExecutionContext context) {
        emit(
                context,
                AgentEventType.AGENT_STARTED,
                "正在理解请求",
                "Agent 开始处理请求",
                AgentEventStatus.RUNNING,
                10);
        emit(
                context,
                AgentEventType.CONTEXT_LOADED,
                "加载会话上下文",
                "已加载最近会话上下文",
                AgentEventStatus.COMPLETED,
                15);
        emit(
                context,
                AgentEventType.SKILL_DISCOVERY_STARTED,
                "检查可用 Skill",
                "正在检查本地 Skill",
                AgentEventStatus.RUNNING,
                20);
        List<SkillDescriptor> skills = skillRepository.listSkills();
        boolean useSmsSkill = skillRepository.matchesSmsSkill(message);
        String loadedSkill = "";
        if (useSmsSkill) {
            MDC.put("skillName", "sms-analysis");
            emit(
                    context,
                    AgentEventType.SKILL_MATCHED,
                    "读取短信分析技能",
                    "已匹配 sms-analysis Skill",
                    AgentEventStatus.COMPLETED,
                    25);
            loadedSkill = skillRepository.readSkill("sms-analysis");
            emit(
                    context,
                    AgentEventType.SKILL_LOADED,
                    "加载 Skill 内容",
                    "已加载 sms-analysis Skill",
                    AgentEventStatus.COMPLETED,
                    30);
        }
        if (!useSmsSkill) {
            return "我是企业内部智能助手。当前可用 Skill："
                    + skills.stream().map(SkillDescriptor::name).toList()
                    + "。请提供要查询的企业只读数据问题；简单问答不会运行 Python。";
        }
        Optional<DateRange> dates = extractDates(message);
        if (dates.isEmpty()) {
            return "请提供查询开始日期和结束日期，格式如 2026-07-01 至 2026-07-07。"
                    + "已发现可用 Skill：sms-analysis，但参数不完整，未查询企业数据。";
        }
        MDC.put("iteration", "1");
        emit(
                context,
                AgentEventType.TOOL_SELECTION_STARTED,
                "选择下一步操作",
                "准备查询短信统计",
                AgentEventStatus.RUNNING,
                35);
        emit(
                context,
                AgentEventType.TOOL_CALL_STARTED,
                "查询短信统计",
                "正在调用 query_sms_summary",
                AgentEventStatus.RUNNING,
                40);
        @SuppressWarnings("unchecked")
        ToolResult<SmsSummary> summaryResult =
                (ToolResult<SmsSummary>)
                        agentToolRegistry.execute(
                                "query_sms_summary",
                                java.util.Map.of(
                                        "startDate", dates.get().startDate(),
                                        "endDate", dates.get().endDate()),
                                context);
        if (!summaryResult.success()) {
            emit(
                    context,
                    AgentEventType.TOOL_CALL_FAILED,
                    "查询短信统计失败",
                    summaryResult.errorMessage(),
                    AgentEventStatus.FAILED,
                    45);
            return "短信查询失败："
                    + summaryResult.errorMessage()
                    + "。错误码："
                    + summaryResult.errorCode()
                    + "。数据来源："
                    + summaryResult.toolName()
                    + "。";
        }
        SmsSummary summary = summaryResult.data();
        emit(
                context,
                AgentEventType.TOOL_CALL_COMPLETED,
                "查询短信统计完成",
                "已获得短信统计结果",
                AgentEventStatus.COMPLETED,
                55,
                java.util.Map.of(
                        "toolName",
                        "query_sms_summary",
                        "toolSource",
                        ToolSource.LOCAL_JAVA.name()));
        SmsSummary.PlatformMetric abnormal =
                summary.platforms().stream()
                        .max(Comparator.comparingDouble(SmsSummary.PlatformMetric::blockedRate))
                        .orElse(new SmsSummary.PlatformMetric("unknown", 0, 0, 0));
        emit(
                context,
                AgentEventType.MODEL_TOOL_SELECTION,
                "发现异常平台",
                "发现 " + abnormal.platform() + " 异常",
                AgentEventStatus.COMPLETED,
                60,
                java.util.Map.of("platform", abnormal.platform()));
        emit(
                context,
                AgentEventType.MCP_DISCOVERY_STARTED,
                "发现 MCP 工具",
                "正在发现 MCP 工具",
                AgentEventStatus.RUNNING,
                62);
        emit(
                context,
                AgentEventType.MCP_DISCOVERY_COMPLETED,
                "发现 MCP 工具完成",
                "已准备 MCP 工具调用",
                AgentEventStatus.COMPLETED,
                64);
        emit(
                context,
                AgentEventType.MCP_TOOL_CALL_STARTED,
                "查询平台负责人",
                "正在通过 MCP 查询负责人",
                AgentEventStatus.RUNNING,
                68,
                java.util.Map.of(
                        "toolName", "get_platform_owner", "toolSource", ToolSource.MCP.name()));
        ToolResult<?> ownerResult =
                agentToolRegistry.execute(
                        "get_platform_owner",
                        java.util.Map.of("platform", abnormal.platform()),
                        context);
        emit(
                context,
                ownerResult.success()
                        ? AgentEventType.MCP_TOOL_CALL_COMPLETED
                        : AgentEventType.TOOL_CALL_FAILED,
                ownerResult.success() ? "负责人查询完成" : "负责人查询失败",
                ownerResult.success() ? "已获得平台负责人" : ownerResult.errorMessage(),
                ownerResult.success() ? AgentEventStatus.COMPLETED : AgentEventStatus.FAILED,
                74,
                java.util.Map.of(
                        "toolName", "get_platform_owner", "toolSource", ToolSource.MCP.name()));
        emit(
                context,
                AgentEventType.MCP_TOOL_CALL_STARTED,
                "生成告警摘要",
                "正在生成告警摘要",
                AgentEventStatus.RUNNING,
                78,
                java.util.Map.of(
                        "toolName",
                        "create_sms_alert_summary",
                        "toolSource",
                        ToolSource.MCP.name()));
        ToolResult<?> alertResult =
                agentToolRegistry.execute(
                        "create_sms_alert_summary",
                        java.util.Map.of(
                                "platform", abnormal.platform(),
                                "blockedRate",
                                        abnormal.sensitiveBlocked() * 100.0 / abnormal.submitted(),
                                "startDate", summary.startDate(),
                                "endDate", summary.endDate()),
                        context);
        emit(
                context,
                alertResult.success()
                        ? AgentEventType.MCP_TOOL_CALL_COMPLETED
                        : AgentEventType.TOOL_CALL_FAILED,
                alertResult.success() ? "告警摘要生成完成" : "告警摘要生成失败",
                alertResult.success() ? "已生成告警摘要" : alertResult.errorMessage(),
                alertResult.success() ? AgentEventStatus.COMPLETED : AgentEventStatus.FAILED,
                82,
                java.util.Map.of(
                        "toolName",
                        "create_sms_alert_summary",
                        "toolSource",
                        ToolSource.MCP.name()));
        ToolResult<String> python = null;
        if (needsCalculation(message)) {
            MDC.put("iteration", "2");
            emit(
                    context,
                    AgentEventType.PYTHON_EXECUTION_STARTED,
                    "执行精确计算",
                    "正在按需运行 Python 计算",
                    AgentEventStatus.RUNNING,
                    85);
            @SuppressWarnings("unchecked")
            ToolResult<String> pythonResult =
                    (ToolResult<String>)
                            agentToolRegistry.execute(
                                    "run_python",
                                    java.util.Map.of("code", pythonAnalysisCode(summary)),
                                    context);
            python = pythonResult;
            emit(
                    context,
                    AgentEventType.PYTHON_EXECUTION_COMPLETED,
                    "精确计算完成",
                    python.success() ? "Python 计算完成" : python.errorMessage(),
                    python.success() ? AgentEventStatus.COMPLETED : AgentEventStatus.FAILED,
                    88);
        }
        emit(
                context,
                AgentEventType.ANSWER_GENERATION_STARTED,
                "整理最终回答",
                "正在整理可审计的最终结果",
                AgentEventStatus.RUNNING,
                92);
        if (python != null && python.success()) {
            String answer =
                    python.data().trim()
                            + "\n数据来源："
                            + summaryResult.toolName()
                            + "（"
                            + summary.startDate()
                            + " 至 "
                            + summary.endDate()
                            + "）。计算依据：按平台 sensitiveBlocked/submitted 计算拦截率，按 totalSuccess/totalSubmitted 计算成功率。"
                            + "\n负责人："
                            + value(
                                    asMap(ownerResult.data()),
                                    "ownerName",
                                    ownerResult.errorMessage())
                            + "，邮箱："
                            + value(asMap(ownerResult.data()), "ownerEmail", "MCP 不可用")
                            + "\n告警等级："
                            + value(asMap(alertResult.data()), "severity", alertResult.errorCode())
                            + "\n建议措施："
                            + value(
                                    asMap(alertResult.data()),
                                    "recommendedActions",
                                    "检查近期新增短信模板；核对运营商回执中的敏感词原因；通知平台负责人评估")
                            + (loadedSkill.isBlank() ? "" : "已按 sms-analysis Skill 执行。")
                            + " 本次能力只读。";
            emit(
                    context,
                    AgentEventType.ANSWER_GENERATION_COMPLETED,
                    "最终回答已生成",
                    "已生成最终答案",
                    AgentEventStatus.COMPLETED,
                    98);
            return answer;
        }
        String answer =
                deterministicAnalysis(
                        summary,
                        summaryResult.source(),
                        loadedSkill,
                        python,
                        ownerResult,
                        alertResult);
        emit(
                context,
                AgentEventType.ANSWER_GENERATION_COMPLETED,
                "最终回答已生成",
                "已生成最终答案",
                AgentEventStatus.COMPLETED,
                98);
        return answer;
    }

    private String deterministicAnalysis(
            SmsSummary summary,
            String source,
            String loadedSkill,
            ToolResult<String> python,
            ToolResult<?> ownerResult,
            ToolResult<?> alertResult) {
        SmsSummary.PlatformMetric abnormal =
                summary.platforms().stream()
                        .max(Comparator.comparingDouble(SmsSummary.PlatformMetric::blockedRate))
                        .orElse(new SmsSummary.PlatformMetric("unknown", 0, 0, 0));
        java.util.Map<?, ?> owner = asMap(ownerResult == null ? null : ownerResult.data());
        java.util.Map<?, ?> alert = asMap(alertResult == null ? null : alertResult.data());
        return "查询时间范围："
                + summary.startDate()
                + " 至 "
                + summary.endDate()
                + "\n总提交量："
                + summary.totalSubmitted()
                + "\n成功量和成功率："
                + summary.totalSuccess()
                + "，"
                + percent(summary.totalSuccess(), summary.totalSubmitted())
                + "\n敏感词拦截量和拦截率："
                + summary.sensitiveBlocked()
                + "，"
                + percent(summary.sensitiveBlocked(), summary.totalSubmitted())
                + "\n异常平台："
                + abnormal.platform()
                + "，拦截率 "
                + percent(abnormal.sensitiveBlocked(), abnormal.submitted())
                + "，高于其他平台。"
                + "\n负责人："
                + value(
                        owner,
                        "ownerName",
                        ownerResult == null ? "MCP 未调用" : ownerResult.errorMessage())
                + "，邮箱："
                + value(owner, "ownerEmail", "MCP 不可用")
                + "，部门："
                + value(owner, "department", "MCP 不可用")
                + "\n告警等级："
                + value(
                        alert,
                        "severity",
                        alertResult == null ? "MCP 未调用" : alertResult.errorCode())
                + "\n告警摘要："
                + value(
                        alert,
                        "summary",
                        alertResult == null ? "MCP 未调用" : alertResult.errorMessage())
                + "\n建议措施："
                + value(alert, "recommendedActions", "检查近期新增短信模板；核对运营商回执中的敏感词原因；通知平台负责人评估")
                + "\n数据来源：query_sms_summary / "
                + source
                + "；get_platform_owner / create_sms_alert_summary / MCP"
                + "\n计算方式：从 ToolResult.data 的结构化平台指标中选择 blockedRate 最高的平台；按 totalSuccess/totalSubmitted 计算成功率，按 sensitiveBlocked/submitted 计算拦截率。"
                + (python == null || python.success()
                        ? ""
                        : " Python 未使用或不可用：" + python.errorMessage())
                + (loadedSkill.isBlank() ? "" : " 已读取 sms-analysis Skill。")
                + " 本次能力只读。";
    }

    private static java.util.Map<?, ?> asMap(Object value) {
        if (value instanceof java.util.Map<?, ?> map) {
            return map;
        }
        return java.util.Map.of();
    }

    private static String value(java.util.Map<?, ?> map, String key, Object fallback) {
        Object value = map.get(key);
        if (value instanceof java.util.List<?> list) {
            return String.join("；", list.stream().map(Object::toString).toList());
        }
        if (value == null) {
            return fallback == null ? "" : fallback.toString();
        }
        return value.toString();
    }

    private static void emit(
            ToolExecutionContext context,
            AgentEventType type,
            String stage,
            String message,
            AgentEventStatus status,
            int progress) {
        emit(context, type, stage, message, status, progress, java.util.Map.of());
    }

    private static void emit(
            ToolExecutionContext context,
            AgentEventType type,
            String stage,
            String message,
            AgentEventStatus status,
            int progress,
            java.util.Map<String, Object> metadata) {
        if (context != null && context.progressPublisher() != null) {
            context.progressPublisher().publish(type, stage, message, status, progress, metadata);
        }
    }

    private static String pythonAnalysisCode(SmsSummary summary) {
        StringBuilder platforms = new StringBuilder("[");
        for (SmsSummary.PlatformMetric platform : summary.platforms()) {
            if (platforms.length() > 1) {
                platforms.append(',');
            }
            platforms
                    .append("{'platform':'")
                    .append(platform.platform())
                    .append("','submitted':")
                    .append(platform.submitted())
                    .append(",'success':")
                    .append(platform.success())
                    .append(",'sensitiveBlocked':")
                    .append(platform.sensitiveBlocked())
                    .append('}');
        }
        platforms.append(']');
        return "total="
                + summary.totalSubmitted()
                + "\nsuccess="
                + summary.totalSuccess()
                + "\nblocked="
                + summary.sensitiveBlocked()
                + "\nplatforms="
                + platforms
                + "\nfor p in platforms: p['blockedRate']=p['sensitiveBlocked']/p['submitted']\n"
                + "ab=max(platforms,key=lambda p:p['blockedRate'])\n"
                + "print('查询时间范围："
                + summary.startDate()
                + " 至 "
                + summary.endDate()
                + "')\n"
                + "print(f'总提交量：{total}')\n"
                + "print(f'成功量和成功率：{success}，{success/total:.2%}')\n"
                + "print(f'敏感词拦截量和拦截率：{blocked}，{blocked/total:.2%}')\n"
                + "print(f\"异常平台：{ab['platform']}，拦截率 {ab['blockedRate']:.2%}\")";
    }

    private static boolean needsCalculation(String message) {
        return message.contains("比例")
                || message.contains("率")
                || message.contains("排序")
                || message.contains("校验")
                || message.contains("统计");
    }

    private static Optional<DateRange> extractDates(String message) {
        Matcher iso = ISO_DATE.matcher(message);
        List<String> dates = new ArrayList<>();
        while (iso.find()) {
            dates.add(iso.group(1));
        }
        if (dates.size() >= 2) {
            return Optional.of(new DateRange(dates.get(0), dates.get(1)));
        }
        Matcher chinese = CHINESE_RANGE.matcher(message);
        if (chinese.find()) {
            int year = Integer.parseInt(chinese.group(1));
            LocalDate start =
                    LocalDate.of(
                            year,
                            Integer.parseInt(chinese.group(2)),
                            Integer.parseInt(chinese.group(3)));
            LocalDate end =
                    LocalDate.of(
                            year,
                            Integer.parseInt(chinese.group(4)),
                            Integer.parseInt(chinese.group(5)));
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
            return Optional.of(new DateRange(formatter.format(start), formatter.format(end)));
        }
        return Optional.empty();
    }

    private static String percent(int numerator, int denominator) {
        if (denominator == 0) {
            return "0.00%";
        }
        return String.format(java.util.Locale.ROOT, "%.2f%%", numerator * 100.0 / denominator);
    }

    private record DateRange(String startDate, String endDate) {}
}
