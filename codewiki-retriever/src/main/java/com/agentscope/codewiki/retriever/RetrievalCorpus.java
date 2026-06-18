package com.agentscope.codewiki.retriever;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable collection of searchable code documents. */
public class RetrievalCorpus {

    private final List<CodeDocument> documents;
    private final Map<String, CodeDocument> documentsById;

    public RetrievalCorpus(Collection<CodeDocument> documents) {
        Map<String, CodeDocument> byId = new LinkedHashMap<>();
        for (CodeDocument document : documents == null ? List.<CodeDocument>of() : documents) {
            byId.put(document.getId(), document);
        }
        this.documents = Collections.unmodifiableList(new ArrayList<>(byId.values()));
        this.documentsById = Collections.unmodifiableMap(byId);
    }

    public static RetrievalCorpus of(Collection<CodeDocument> documents) {
        return new RetrievalCorpus(documents);
    }

    public List<CodeDocument> getDocuments() {
        return documents;
    }

    public Optional<CodeDocument> findById(String id) {
        return Optional.ofNullable(documentsById.get(id));
    }
}
