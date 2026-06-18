package com.agentscope.codewiki.retriever;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for the hybrid retrieval engine. */
class HybridRetrieverTest {

    @Test
    void shouldReturnCodeSummariesDependencyChainsAndModules() {
        RetrievalCorpus corpus =
                RetrievalCorpus.of(
                        List.of(
                                CodeDocument.builder()
                                        .id("order-service")
                                        .module("order")
                                        .path("src/main/java/OrderService.java")
                                        .language("java")
                                        .content(
                                                "class OrderService { PaymentClient paymentClient; }")
                                        .summary(
                                                "Handles order checkout and coordinates payment authorization.")
                                        .dependencies(List.of("payment-client"))
                                        .build(),
                                CodeDocument.builder()
                                        .id("payment-client")
                                        .module("payment")
                                        .path("src/main/java/PaymentClient.java")
                                        .language("java")
                                        .content("class PaymentClient { void authorize() {} }")
                                        .summary(
                                                "Calls the payment gateway to authorize checkout transactions.")
                                        .dependencies(List.of("gateway-config"))
                                        .build(),
                                CodeDocument.builder()
                                        .id("gateway-config")
                                        .module("payment")
                                        .path("src/main/java/GatewayConfig.java")
                                        .language("java")
                                        .content("class GatewayConfig { String endpoint; }")
                                        .summary("Stores payment gateway endpoint configuration.")
                                        .build(),
                                CodeDocument.builder()
                                        .id("user-profile")
                                        .module("user")
                                        .path("src/main/java/UserProfile.java")
                                        .language("java")
                                        .content("class UserProfile { String name; }")
                                        .summary("Maintains user profile settings.")
                                        .build()));

        ContextPack pack =
                new HybridRetriever(corpus)
                        .retrieve("How does checkout payment authorization work?");

        assertEquals("How does checkout payment authorization work?", pack.question());
        assertFalse(pack.relevantCode().isEmpty());
        assertFalse(pack.relevantSummaries().isEmpty());
        assertFalse(pack.relevantDependencyChains().isEmpty());
        assertFalse(pack.relevantModules().isEmpty());
        assertEquals("order-service", pack.relevantCode().getFirst().documentId());
        assertTrue(
                pack.relevantModules().stream()
                        .anyMatch(module -> "order".equals(module.module())));
        assertTrue(
                pack.relevantDependencyChains().stream()
                        .anyMatch(chain -> chain.nodes().contains("payment-client")));
    }

    @Test
    void shouldSupportVectorOnlySignals() {
        RetrievalCorpus corpus =
                RetrievalCorpus.of(
                        List.of(
                                CodeDocument.builder()
                                        .id("auth-token")
                                        .module("security")
                                        .path("TokenService.java")
                                        .language("java")
                                        .content("issues credentials")
                                        .summary("security credential issuing")
                                        .embedding(new double[] {1D, 0D})
                                        .build(),
                                CodeDocument.builder()
                                        .id("reporting")
                                        .module("reporting")
                                        .path("ReportService.java")
                                        .language("java")
                                        .content("renders report")
                                        .summary("report rendering")
                                        .embedding(new double[] {0D, 1D})
                                        .build()));
        EmbeddingModel embeddingModel = question -> new double[] {1D, 0D};

        ContextPack pack =
                new HybridRetriever(corpus, embeddingModel).retrieve("unseen semantic query");

        assertEquals("auth-token", pack.relevantCode().getFirst().documentId());
        assertEquals("security", pack.relevantModules().getFirst().module());
    }
}
