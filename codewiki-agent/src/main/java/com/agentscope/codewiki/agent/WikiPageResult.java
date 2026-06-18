package com.agentscope.codewiki.agent;

import java.nio.file.Path;

/** Result for one generated or skipped wiki page. */
public record WikiPageResult(WikiPage page, Path path, boolean written, String fingerprint) {

    public WikiPageResult {
        if (page == null) {
            throw new IllegalArgumentException("page must not be null");
        }
        fingerprint = fingerprint == null ? "" : fingerprint;
    }
}
