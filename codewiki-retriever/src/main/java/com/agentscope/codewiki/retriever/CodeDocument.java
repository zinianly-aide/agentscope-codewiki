package com.agentscope.codewiki.retriever;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Searchable code unit used by the retrieval engine. */
public class CodeDocument {

    private final String id;
    private final String module;
    private final String path;
    private final String language;
    private final String content;
    private final String summary;
    private final List<String> dependencies;
    private final double[] embedding;

    public CodeDocument(
            String id,
            String module,
            String path,
            String language,
            String content,
            String summary,
            List<String> dependencies,
            double[] embedding) {
        this.id = requireText(id, "id");
        this.module = nullToEmpty(module);
        this.path = nullToEmpty(path);
        this.language = nullToEmpty(language);
        this.content = nullToEmpty(content);
        this.summary = nullToEmpty(summary);
        this.dependencies =
                Collections.unmodifiableList(new ArrayList<>(nullToEmptyList(dependencies)));
        this.embedding = embedding == null ? new double[0] : embedding.clone();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getModule() {
        return module;
    }

    public String getPath() {
        return path;
    }

    public String getLanguage() {
        return language;
    }

    public String getContent() {
        return content;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public double[] getEmbedding() {
        return embedding.clone();
    }

    String searchableText() {
        return String.join(
                " ", module, path, language, content, summary, String.join(" ", dependencies));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<String> nullToEmptyList(List<String> values) {
        return values == null ? List.of() : values;
    }

    /** Builder for {@link CodeDocument}. */
    public static final class Builder {
        private String id;
        private String module;
        private String path;
        private String language;
        private String content;
        private String summary;
        private List<String> dependencies = List.of();
        private double[] embedding;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder module(String module) {
            this.module = module;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder embedding(double[] embedding) {
            this.embedding = embedding == null ? null : embedding.clone();
            return this;
        }

        public CodeDocument build() {
            return new CodeDocument(
                    id, module, path, language, content, summary, dependencies, embedding);
        }
    }
}
