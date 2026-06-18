package com.agentscope.codewiki.core;

import java.util.Optional;

/**
 * Persistence layer for summaries.
 *
 * <p>Implementations may use local files, embedded DB, or remote APIs.
 */
public interface SummaryStorage {

    /**
     * Save a repository-level summary.
     *
     * @param summary the summary to persist
     */
    void save(RepositorySummary summary);

    /**
     * Load a repository summary by name.
     *
     * @param repositoryName the repository name
     * @return the summary, or empty if not found
     */
    Optional<RepositorySummary> load(String repositoryName);

    /**
     * Delete a stored summary.
     *
     * @param repositoryName the repository to delete
     * @return true if deleted, false if not found
     */
    boolean delete(String repositoryName);

    /** Check whether a summary exists. */
    default boolean exists(String repositoryName) {
        return load(repositoryName).isPresent();
    }
}
