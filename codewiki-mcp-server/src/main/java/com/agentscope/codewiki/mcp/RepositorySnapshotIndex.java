package com.agentscope.codewiki.mcp;

import com.agentscope.codewiki.indexer.graph.CodeGraph;
import com.agentscope.codewiki.indexer.graph.CodeGraphBuilder;
import com.agentscope.codewiki.parser.CodeSymbol;
import com.agentscope.codewiki.parser.TreeSitterSymbolExtractor;
import com.agentscope.codewiki.retriever.CodeDocument;
import com.agentscope.codewiki.retriever.CodeDocumentChunker;
import com.agentscope.codewiki.retriever.HybridRetriever;
import com.agentscope.codewiki.retriever.RetrievalCorpus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lazy content-addressed repository snapshot. Reads current source bytes on each refresh to detect
 * same-size/same-mtime edits, but only parses changed files and rebuilds retrieval on actual source
 * changes. Optional persisted symbol cache is explicitly enabled by path.
 */
final class RepositorySnapshotIndex {
    private static final int FORMAT_VERSION = 2;
    private static final int MAX_SYMBOLS = 3500;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path root;
    private final Path bridgeScript;
    private final Path cachePath;
    private Map<String, CachedFile> cached;
    private Snapshot snapshot;
    private int lastParsedFiles;
    private long totalParseBatches;

    RepositorySnapshotIndex(Path root, Path bridgeScript, Path cachePath) {
        this.root = root;
        this.bridgeScript = bridgeScript;
        this.cachePath = cachePath;
        this.cached = load();
    }

    synchronized Snapshot refresh(List<Path> files) throws IOException {
        Map<String, CachedFile> next = new LinkedHashMap<>();
        Map<String, String> source = new LinkedHashMap<>();
        List<Path> changed = new ArrayList<>();
        for (Path file : files) {
            String relative = relative(file);
            // Restrict writes by the same scanner in CodewikiRepositoryTools. No linked input.
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String sha = sha256(text.getBytes(StandardCharsets.UTF_8));
            source.put(relative, text);
            CachedFile old = cached.get(relative);
            if (old != null
                    && old.hash() != null
                    && old.hash().equals(sha)
                    && old.symbols() != null
                    && old.symbols().stream()
                            .allMatch(symbol -> relative.equals(symbol.getFilePath()))) {
                next.put(relative, old);
            } else {
                next.put(relative, new CachedFile(sha, List.of()));
                changed.add(file);
            }
        }
        if (snapshot != null && changed.isEmpty() && cached.keySet().equals(next.keySet())) {
            lastParsedFiles = 0;
            return snapshot;
        }
        if (!changed.isEmpty()) {
            TreeSitterSymbolExtractor extractor =
                    bridgeScript == null
                            ? new TreeSitterSymbolExtractor()
                            : new TreeSitterSymbolExtractor(bridgeScript);
            List<CodeSymbol> symbols = extractor.extractBatch(changed);
            Map<String, List<CodeSymbol>> byFile = new HashMap<>();
            for (CodeSymbol symbol : symbols) {
                Path absolute = Path.of(symbol.getFilePath()).normalize();
                if (!absolute.startsWith(root) || !next.containsKey(relative(absolute))) {
                    throw new IOException("bridge returned symbol outside indexed scope");
                }
                String relative = relative(absolute);
                symbol.setFilePath(relative);
                byFile.computeIfAbsent(relative, key -> new ArrayList<>()).add(symbol);
            }
            for (Path file : changed) {
                String relative = relative(file);
                next.put(
                        relative,
                        new CachedFile(
                                next.get(relative).hash(),
                                List.copyOf(byFile.getOrDefault(relative, List.of()))));
            }
            totalParseBatches++;
        }
        List<CodeSymbol> allSymbols =
                next.values().stream().flatMap(entry -> entry.symbols().stream()).toList();
        if (allSymbols.size() > MAX_SYMBOLS) {
            throw new IllegalArgumentException("symbol limit exceeded; narrow repository scope");
        }
        List<CodeDocument> documents = new ArrayList<>();
        for (Map.Entry<String, String> file : source.entrySet()) {
            String relative = file.getKey();
            String module =
                    relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : "root";
            String ext =
                    relative.contains(".") ? relative.substring(relative.lastIndexOf('.')) : "";
            documents.add(
                    CodeDocument.builder()
                            .id(relative)
                            .module(module)
                            .path(relative)
                            .language(ext)
                            .content(file.getValue())
                            .summary("")
                            .dependencies(List.of())
                            .build());
        }
        Snapshot built = new Snapshot(documents, allSymbols);
        boolean changedSnapshot = !cached.equals(next);
        if (changedSnapshot) {
            save(next);
        }
        this.cached = next;
        this.snapshot = built;
        this.lastParsedFiles = changed.size();
        return built;
    }

    synchronized Map<String, Object> status(List<Path> sourceFiles) throws IOException {
        Snapshot current = refresh(sourceFiles);
        return Map.of(
                "fileCount",
                current.documents().size(),
                "symbolCount",
                current.symbols().size(),
                "parsedFilesLastRefresh",
                lastParsedFiles,
                "totalParseBatches",
                totalParseBatches,
                "persistenceEnabled",
                cachePath != null);
    }

    private String relative(Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private Map<String, CachedFile> load() {
        if (cachePath == null
                || Files.isSymbolicLink(cachePath)
                || !Files.isRegularFile(cachePath)) {
            return Map.of();
        }
        try {
            DiskSnapshot data = MAPPER.readValue(cachePath.toFile(), DiskSnapshot.class);
            if (data.version() != FORMAT_VERSION
                    || !root.toString().equals(data.root())
                    || data.files() == null) {
                return Map.of();
            }
            return new LinkedHashMap<>(data.files());
        } catch (IOException | RuntimeException ignored) {
            // Corrupted/stale index may not stop source analysis; rebuild it safely.
            return Map.of();
        }
    }

    private void save(Map<String, CachedFile> next) throws IOException {
        if (cachePath == null) {
            return;
        }
        if (Files.isSymbolicLink(cachePath) || Files.isSymbolicLink(cachePath.getParent())) {
            throw new IOException("index cache path must not be a symbolic link");
        }
        Path parent = cachePath.getParent();
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("index cache parent directory does not exist");
        }
        Path temp = Files.createTempFile(parent, ".codewiki-index-", ".tmp");
        try {
            try {
                Files.setPosixFilePermissions(
                        temp,
                        EnumSet.of(
                                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Non-POSIX file systems may use filesystem-level access controls.
            }
            MAPPER.writeValue(
                    temp.toFile(), new DiskSnapshot(FORMAT_VERSION, root.toString(), next));
            try {
                Files.move(
                        temp,
                        cachePath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    record CachedFile(String hash, List<CodeSymbol> symbols) {}

    record DiskSnapshot(int version, String root, Map<String, CachedFile> files) {}

    static final class Snapshot {
        private final List<CodeDocument> documents;
        private final List<CodeSymbol> symbols;
        private final HybridRetriever retriever;
        private CodeGraph graph;

        Snapshot(List<CodeDocument> documents, List<CodeSymbol> symbols) {
            this.documents = List.copyOf(documents);
            this.symbols = List.copyOf(symbols);
            this.retriever =
                    new HybridRetriever(
                            RetrievalCorpus.of(CodeDocumentChunker.chunkAll(documents)));
        }

        List<CodeDocument> documents() {
            return documents;
        }

        List<CodeSymbol> symbols() {
            return symbols;
        }

        HybridRetriever retriever() {
            return retriever;
        }

        synchronized CodeGraph graph() {
            if (graph == null) {
                graph = new CodeGraphBuilder().build(symbols);
            }
            return graph;
        }
    }
}
