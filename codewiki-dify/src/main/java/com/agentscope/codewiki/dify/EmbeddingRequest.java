package com.agentscope.codewiki.dify;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible embedding request.
 *
 * <p>Serialized as:
 *
 * <pre>{@code
 * {
 *   "model": "text-embedding-3-small",
 *   "input": ["text 1", "text 2"]
 * }
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmbeddingRequest {

    private String model;
    private List<String> input = new ArrayList<>();
    private String user;

    public EmbeddingRequest() {}

    public EmbeddingRequest(String model, String input) {
        this.model = model;
        this.input = List.of(input);
    }

    public EmbeddingRequest(String model, List<String> input) {
        this.model = model;
        this.input = new ArrayList<>(input);
    }

    public static EmbeddingRequest of(String model, String input) {
        return new EmbeddingRequest(model, input);
    }

    public static EmbeddingRequest of(String model, List<String> input) {
        return new EmbeddingRequest(model, input);
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<String> getInput() {
        return input;
    }

    public void setInput(List<String> input) {
        this.input = input;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }
}
