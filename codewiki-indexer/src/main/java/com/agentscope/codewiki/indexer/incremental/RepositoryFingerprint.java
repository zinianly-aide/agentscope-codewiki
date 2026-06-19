package com.agentscope.codewiki.indexer.incremental;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serializable repository fingerprint used to drive incremental indexing. */
public class RepositoryFingerprint {

    public static final String DEFAULT_FILE_NAME = "repository-fingerprint.json";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private List<FileFingerprint> files = new ArrayList<>();

    public RepositoryFingerprint() {}

    public RepositoryFingerprint(List<FileFingerprint> files) {
        setFiles(files);
    }

    public static RepositoryFingerprint scan(Path repositoryRoot) throws IOException {
        List<FileFingerprint> fingerprints = new ArrayList<>();
        try (var stream = Files.walk(repositoryRoot)) {
            List<Path> paths =
                    stream.filter(Files::isRegularFile)
                            .filter(path -> !isGitInternal(repositoryRoot, path))
                            .sorted(Comparator.comparing(path -> normalize(repositoryRoot, path)))
                            .toList();
            for (Path path : paths) {
                fingerprints.add(fingerprint(repositoryRoot, path));
            }
        }
        return new RepositoryFingerprint(fingerprints);
    }

    public static RepositoryFingerprint readJson(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), RepositoryFingerprint.class);
    }

    public List<FileFingerprint> getFiles() {
        return List.copyOf(files);
    }

    public void setFiles(List<FileFingerprint> files) {
        this.files =
                files == null
                        ? new ArrayList<>()
                        : files.stream()
                                .sorted(Comparator.comparing(FileFingerprint::getPath))
                                .toList();
    }

    public Map<String, FileFingerprint> byPath() {
        Map<String, FileFingerprint> index = new LinkedHashMap<>();
        for (FileFingerprint file : files) {
            index.put(file.getPath(), file);
        }
        return index;
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize repository fingerprint", e);
        }
    }

    public void writeJson(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve(DEFAULT_FILE_NAME), toJson());
    }

    private static FileFingerprint fingerprint(Path root, Path path) throws IOException {
        return new FileFingerprint(
                normalize(root, path),
                sha256(path),
                Files.size(path),
                Files.getLastModifiedTime(path).toMillis());
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path);
                    DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                digestInput.transferTo(OutputStream.nullOutputStream());
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static String normalize(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static boolean isGitInternal(Path root, Path path) {
        return normalize(root, path).startsWith(".git/");
    }
}
