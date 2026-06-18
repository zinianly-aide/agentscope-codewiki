package com.agentscope.codewiki.agent;

/** In-memory content for a wiki page before it is written to disk. */
record WikiPageContent(WikiPage page, String markdown, String fingerprint) {

    WikiPageContent {
        if (page == null) {
            throw new IllegalArgumentException("page must not be null");
        }
        markdown = markdown == null ? "" : markdown;
        fingerprint = fingerprint == null ? "" : fingerprint;
    }
}
