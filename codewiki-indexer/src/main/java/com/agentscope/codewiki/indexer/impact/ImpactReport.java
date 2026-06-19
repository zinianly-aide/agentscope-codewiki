package com.agentscope.codewiki.indexer.impact;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** JSON report emitted by the impact analysis engine. */
public class ImpactReport {

    public static final String DEFAULT_FILE_NAME = "impact-report.json";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private List<String> changedSymbols = new ArrayList<>();
    private List<ImpactEdge> directImpacts = new ArrayList<>();
    private List<ImpactEdge> transitiveImpacts = new ArrayList<>();
    private String riskLevel = RiskLevel.LOW.name();
    private List<String> affectedModules = new ArrayList<>();

    public List<String> getChangedSymbols() {
        return List.copyOf(changedSymbols);
    }

    public void setChangedSymbols(List<String> changedSymbols) {
        this.changedSymbols =
                changedSymbols == null ? new ArrayList<>() : new ArrayList<>(changedSymbols);
    }

    public List<ImpactEdge> getDirectImpacts() {
        return List.copyOf(directImpacts);
    }

    public void setDirectImpacts(List<ImpactEdge> directImpacts) {
        this.directImpacts =
                directImpacts == null ? new ArrayList<>() : new ArrayList<>(directImpacts);
    }

    public List<ImpactEdge> getTransitiveImpacts() {
        return List.copyOf(transitiveImpacts);
    }

    public void setTransitiveImpacts(List<ImpactEdge> transitiveImpacts) {
        this.transitiveImpacts =
                transitiveImpacts == null ? new ArrayList<>() : new ArrayList<>(transitiveImpacts);
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public List<String> getAffectedModules() {
        return List.copyOf(affectedModules);
    }

    public void setAffectedModules(List<String> affectedModules) {
        this.affectedModules =
                affectedModules == null ? new ArrayList<>() : new ArrayList<>(affectedModules);
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize impact report", e);
        }
    }

    public void writeJson(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve(DEFAULT_FILE_NAME), toJson());
    }

    /** Supported risk levels ordered from least to most severe. */
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
