package com.agentscope.codewiki.retriever;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Deterministic local embedding model for tests and offline bootstrap usage. */
public class HashingEmbeddingModel implements EmbeddingModel {

    private static final int DEFAULT_DIMENSIONS = 64;

    private final int dimensions;

    public HashingEmbeddingModel() {
        this(DEFAULT_DIMENSIONS);
    }

    public HashingEmbeddingModel(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.dimensions = dimensions;
    }

    @Override
    public double[] embed(String text) {
        double[] vector = new double[dimensions];
        List<String> tokens = TextTokenizer.tokenize(text);
        for (String token : tokens) {
            int bucket = Math.floorMod(hash(token), dimensions);
            vector[bucket] += 1D;
        }
        return vector;
    }

    private int hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return ((bytes[0] & 0xFF) << 24)
                    | ((bytes[1] & 0xFF) << 16)
                    | ((bytes[2] & 0xFF) << 8)
                    | (bytes[3] & 0xFF);
        } catch (NoSuchAlgorithmException exception) {
            return token.hashCode();
        }
    }
}
