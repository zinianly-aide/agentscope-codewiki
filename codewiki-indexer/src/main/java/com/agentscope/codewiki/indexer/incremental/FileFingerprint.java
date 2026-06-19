package com.agentscope.codewiki.indexer.incremental;

import java.util.Objects;

/** Fingerprint for a repository file at a point in time. */
public class FileFingerprint {

    private String path;
    private String hash;
    private long size;
    private long modifiedTime;

    public FileFingerprint() {}

    public FileFingerprint(String path, String hash, long size, long modifiedTime) {
        this.path = normalize(path);
        this.hash = hash;
        this.size = size;
        this.modifiedTime = modifiedTime;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = normalize(path);
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(long modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public boolean sameContent(FileFingerprint other) {
        return other != null && size == other.size && Objects.equals(hash, other.hash);
    }

    public boolean sameFingerprint(FileFingerprint other) {
        return sameContent(other)
                && modifiedTime == other.modifiedTime
                && Objects.equals(path, other.path);
    }

    private static String normalize(String path) {
        return path == null ? null : path.replace('\\', '/');
    }
}
