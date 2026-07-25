package com.agentscope.codewiki.cli;

import com.agentscope.codewiki.dify.DifyChatModel;
import com.agentscope.codewiki.retriever.CodeContext;
import com.agentscope.codewiki.retriever.CodeDocument;
import com.agentscope.codewiki.retriever.CodeDocumentChunker;
import com.agentscope.codewiki.retriever.ContextPack;
import com.agentscope.codewiki.retriever.HybridRetriever;
import com.agentscope.codewiki.retriever.RetrievalCorpus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Runs repository Q&A over TopK retrieved chunks. */
final class AskRunner {

    private static final int TOKEN_CHARS = 4;

    AskResult run(AskCommandOptions options) throws IOException {
        List<CodeDocument> documents = loadDocuments(options.repoPath());
        ContextPack contextPack =
                new HybridRetriever(RetrievalCorpus.of(CodeDocumentChunker.chunkAll(documents)))
                        .retrieve(options.question(), options.topK());
        List<CodeContext> topContexts =
                contextPack.relevantCode().stream().limit(options.topK()).toList();
        String prompt =
                fitPrompt(
                        buildPrompt(options.question(), topContexts),
                        options.maxContextTokens() * TOKEN_CHARS);
        String answer;
        if (options.difyEnabled()) {
            if (options.difyBaseUrl() == null || options.difyApiKey() == null) {
                throw new IllegalArgumentException(
                        "Ask Dify mode requires --dify-base-url and --dify-api-key or DIFY_BASE_URL/DIFY_API_KEY");
            }
            DifyChatModel model =
                    new DifyChatModel(
                            options.difyBaseUrl(),
                            options.difyApiKey(),
                            options.difyUser(),
                            options.sessionId(),
                            Duration.ofSeconds(options.difyTimeoutSeconds()),
                            options.difyResponseMode());
            answer = model.chat(prompt, difyInputs(options, topContexts)).getAnswer();
        } else {
            answer = mockAnswer(options.question(), topContexts, prompt);
        }
        return new AskResult(answer, prompt, topContexts.size(), estimateTokens(prompt));
    }

    private List<CodeDocument> loadDocuments(Path repo) throws IOException {
        try (Stream<Path> stream = Files.walk(repo)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(repo, path))
                    .filter(this::isSupportedSource)
                    .sorted()
                    .map(path -> toDocument(repo, path))
                    .toList();
        }
    }

    private CodeDocument toDocument(Path repo, Path file) {
        String relative = repo.relativize(file).toString().replace('\\', '/');
        String content = read(file);
        return CodeDocument.builder()
                .id(relative)
                .module(module(relative))
                .path(relative)
                .language(language(file))
                .content(content)
                .summary("Repository Q&A source file " + relative)
                .dependencies(List.of())
                .build();
    }

    private String buildPrompt(String question, List<CodeContext> contexts) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Answer the repository question using only the TopK retrieved context.\n");
        prompt.append("Question: ").append(question).append("\n\n");
        prompt.append("TopK context:\n");
        for (int index = 0; index < contexts.size(); index++) {
            CodeContext context = contexts.get(index);
            prompt.append("\n[")
                    .append(index + 1)
                    .append("] path=")
                    .append(context.path())
                    .append(" score=")
                    .append(context.score())
                    .append('\n')
                    .append(context.content())
                    .append('\n');
        }
        return prompt.toString();
    }

    private Map<String, Object> difyInputs(AskCommandOptions options, List<CodeContext> contexts) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("question", options.question());
        inputs.put("top_k", options.topK());
        inputs.put("context_paths", contexts.stream().map(CodeContext::path).toList());
        inputs.put("max_context_tokens", options.maxContextTokens());
        return inputs;
    }

    private String mockAnswer(String question, List<CodeContext> contexts, String prompt) {
        return "Mock repository answer for question: "
                + question
                + "\nTopK contexts used: "
                + contexts.stream().map(CodeContext::path).toList()
                + "\nPrompt tokens: "
                + estimateTokens(prompt);
    }

    private String fitPrompt(String prompt, int maxChars) {
        if (prompt.length() <= maxChars) {
            return prompt;
        }
        String marker = "\n... [trimmed for Dify prompt budget]";
        return prompt.substring(0, Math.max(0, maxChars - marker.length())) + marker;
    }

    private int estimateTokens(String text) {
        return text == null || text.isBlank()
                ? 0
                : Math.max(1, (int) Math.ceil(text.length() / (double) TOKEN_CHARS));
    }

    private boolean isSupportedSource(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".java")
                || name.endsWith(".py")
                || name.endsWith(".ts")
                || name.endsWith(".tsx");
    }

    private boolean isIgnored(Path repo, Path path) {
        Path relative = repo.relativize(path);
        for (Path part : relative) {
            String name = part.toString();
            if (name.equals(".git")
                    || name.equals("target")
                    || name.equals("build")
                    || name.equals("node_modules")) {
                return true;
            }
        }
        return false;
    }

    private String module(String relativePath) {
        int slash = relativePath.indexOf('/');
        return slash < 0 ? "root" : relativePath.substring(0, slash);
    }

    private String language(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) {
            return "Java";
        }
        if (name.endsWith(".py")) {
            return "Python";
        }
        if (name.endsWith(".ts") || name.endsWith(".tsx")) {
            return "TypeScript";
        }
        return "unknown";
    }

    private String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    record AskResult(String answer, String prompt, int contextCount, int promptTokens) {}
}
