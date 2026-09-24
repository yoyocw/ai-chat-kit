package io.github.yoyocw.aichatkit.ai.adapter.web;

import java.util.HashSet;
import java.util.List;

/** HTTP边界上无法用单个字段注解表达的输入校验。 */
final class AiWebInputs {
    /** 无实例状态。 */
    private AiWebInputs() { }
    /** @param codes 有序成员编码，重复不能在绑定成Set时静默丢弃 */
    static void members(List<String> codes) {
        if (codes == null || codes.size() < 2 || codes.size() > 3
                || new HashSet<String>(codes).size() != codes.size()) {
            throw new AiWebInputException();
        }
    }
    /** @param title 群聊标题；单聊允许100字符，群聊只允许30字符 */
    static void groupTitle(String title) {
        if (title == null || title.trim().isEmpty() || title.length() > 30) {
            throw new AiWebInputException();
        }
    }
    /** @param response 所有公开分享响应均禁止缓存、索引及传递来源 */
    static void privateHeaders(javax.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Robots-Tag", "noindex");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
    /** @param code 32位小写十六进制分享码 */
    static void shareCode(String code) {
        if (code == null || !code.matches("[0-9a-f]{32}")) {
            throw new AiWebInputException();
        }
    }
}
