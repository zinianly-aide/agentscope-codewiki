package com.agentscope.codewiki.agent;

/** One evidence-backed finding in a diff review report. */
public class ReviewFinding {

    private ReviewSeverity severity;
    private String section;
    private String message;
    private String filePath;
    private String className;
    private String methodName;
    private String evidence;

    public ReviewFinding() {
        this(ReviewSeverity.INFO, "Potential Risks", "", "", "", "", "");
    }

    public ReviewFinding(
            ReviewSeverity severity,
            String section,
            String message,
            String filePath,
            String className,
            String methodName,
            String evidence) {
        this.severity = severity == null ? ReviewSeverity.INFO : severity;
        this.section = blankToDefault(section, "Potential Risks");
        this.message = nullToEmpty(message);
        this.filePath = nullToEmpty(filePath);
        this.className = nullToEmpty(className);
        this.methodName = nullToEmpty(methodName);
        this.evidence = nullToEmpty(evidence);
    }

    public ReviewSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(ReviewSeverity severity) {
        this.severity = severity == null ? ReviewSeverity.INFO : severity;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = blankToDefault(section, "Potential Risks");
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = nullToEmpty(message);
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = nullToEmpty(filePath);
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = nullToEmpty(className);
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = nullToEmpty(methodName);
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = nullToEmpty(evidence);
    }

    public String symbolReference() {
        if (!className.isBlank() && !methodName.isBlank()) {
            return className + "." + methodName;
        }
        if (!className.isBlank()) {
            return className;
        }
        return methodName;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
