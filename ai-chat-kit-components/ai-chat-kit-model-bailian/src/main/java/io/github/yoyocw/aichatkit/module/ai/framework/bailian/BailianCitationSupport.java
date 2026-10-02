package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.yoyocw.aichatkit.module.ai.framework.citation.AiCitationSupport;

/** Compatibility entry for the provider; filtering is shared by neutral display adapters. */
public final class BailianCitationSupport {
    private BailianCitationSupport() { }

    public static ArrayNode merge(JsonNode previous, JsonNode current) {
        return AiCitationSupport.merge(previous, current);
    }
}
