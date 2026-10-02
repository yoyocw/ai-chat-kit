package io.github.yoyocw.aichatkit.module.ai.framework.citation;

import io.github.yoyocw.aichatkit.module.ai.framework.json.AiEngineJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 累积文档引用白名单；不保留切片、文档 ID、检索输入或临时下载凭据。 */
public final class AiCitationSupport {
    private AiCitationSupport() { }

    /** 合并跨 SSE 分片的引用；编号由上游提供，不自行重新编号，最多保留 100 项。 */
    public static ArrayNode merge(JsonNode previous, JsonNode current) {
        Map<String, ObjectNode> sources = new LinkedHashMap<>();
        append(sources, previous);
        append(sources, current);
        ArrayNode result = AiEngineJson.createArrayNode();
        sources.values().forEach(result::add);
        return result;
    }

    /** 只读取正式 doc_references 的展示字段，忽略异常类型和超限文本。 */
    private static void append(Map<String, ObjectNode> sources, JsonNode references) {
        if (references == null || !references.isArray()) { return; }
        for (JsonNode reference : references) {
            String id = reference.path("index_id").asText("");
            if (!id.matches("[0-9]{1,6}") || (sources.size() >= 100 && !sources.containsKey(id))) { continue; }
            String title = reference.path("title").isTextual() ? reference.path("title").textValue() : "";
            if (title.trim().isEmpty()) { title = "参考资料 " + id; }
            if (title.length() > 500) { title = title.substring(0, 500); }
            ObjectNode source = AiEngineJson.createObjectNode();
            source.put("index_id", id);
            source.put("title", title);
            String url = safeUrl(reference.path("doc_url").asText(""));
            if (url != null) { source.put("doc_url", url); }
            // 重复分片中缺失字段不抹掉此前已经取得的链接。
            if (sources.containsKey(id) && url == null && sources.get(id).has("doc_url")) {
                source.set("doc_url", sources.get(id).get("doc_url"));
            }
            sources.put(id, source);
        }
    }

    /** 仅允许无凭据的 HTTP(S) 绝对链接；不修补畸形 Markdown，不暴露临时签名下载地址。 */
    private static String safeUrl(String value) {
        if (value.isEmpty() || value.length() > 2048) { return null; }
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || host == null || uri.getRawUserInfo() != null) { return null; }
            host = host.toLowerCase(Locale.ROOT);
            if (!host.contains(".") || host.matches("[0-9.]+") || host.contains(":")
                    || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".localhost")) { return null; }
            String query = uri.getRawQuery();
            if (query != null && query.toLowerCase(Locale.ROOT)
                    .matches(".*(signature|accesskey|credential|token|expires|%[0-9a-f]{2}).*")) { return null; }
            return uri.toASCIIString();
        } catch (Exception ignored) {
            return null;
        }
    }
}
