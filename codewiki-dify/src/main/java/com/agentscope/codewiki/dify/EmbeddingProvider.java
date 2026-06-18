package com.agentscope.codewiki.dify;

import java.io.IOException;

/**
 * Provider interface for generating dense vector embeddings from text.
 *
 * <p>This interface is generation-only. It intentionally does not define storage, retrieval,
 * ranking, vector database, or embedding-index behavior.
 */
public interface EmbeddingProvider {

    /**
     * Generate embeddings for the given request.
     *
     * @param request embedding request containing model and input text(s)
     * @return embedding vectors and usage metadata
     * @throws IOException on transport or serialization failures
     */
    EmbeddingResponse embed(EmbeddingRequest request) throws IOException;
}
