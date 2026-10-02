package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import io.github.yoyocw.aichatkit.module.ai.framework.json.AiEngineJson;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.AGENT_ORCHESTRATOR;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.MAX_MEMBER_COUNT;

/**
 * 百炼群聊工作流输出解析器，校验执行成员归属、最终总控汇总和正文边界后交给持久化层。
 */
public class BailianGroupOutputParser {

    /**
     * 解析并校验工作流 output.text JSON。
     *
     * @param content 工作流完整文本输出
     * @param memberCodes 当前群聊允许参与编排的成员编码
     * @return 已规范化成员编码的工作流输出
     * @throws BailianGroupOutputException JSON、成员或正文不符合契约时抛出
     */
    public BailianGroupOutput parse(String content, List<String> memberCodes) {
        // 目录必须提供规范编码；不静默归一化，以免大小写不同的成员产生冲突或绕过保留码。
        if (memberCodes == null || memberCodes.stream().anyMatch(code -> code == null
                || !code.matches("[A-Z0-9_-]{1,128}") || AGENT_ORCHESTRATOR.equals(code))) {
            throw new BailianGroupOutputException("群聊目录成员编码必须为规范大写编码且不得使用ORCHESTRATOR");
        }
        BailianGroupOutput output;
        try {
            output = AiEngineJson.parseObject(content, BailianGroupOutput.class);
        } catch (RuntimeException ex) {
            throw new BailianGroupOutputException("工作流 output.text 不是合法 JSON");
        }
        if (output == null || output.getReplies() == null || output.getReplies().isEmpty()
                || output.getReplies().size() > MAX_MEMBER_COUNT + 1) {
            throw new BailianGroupOutputException("工作流 replies 数量必须为 1 至 "
                    + (MAX_MEMBER_COUNT + 1) + " 条");
        }
        Set<String> allowedCodes = new HashSet<String>(memberCodes);
        for (int i = 0; i < output.getReplies().size(); i++) {
            BailianGroupReply reply = output.getReplies().get(i);
            normalizeReply(reply, allowedCodes);
            boolean lastReply = i == output.getReplies().size() - 1;
            if (AGENT_ORCHESTRATOR.equals(reply.getSpeakerCode()) != lastReply) {
                throw new BailianGroupOutputException("工作流最后一条 reply 必须且只能由 ORCHESTRATOR 汇总");
            }
        }
        return output;
    }

    private void normalizeReply(BailianGroupReply reply, Set<String> allowedCodes) {
        if (reply == null || !StringUtils.hasText(reply.getSpeakerCode())
                || !StringUtils.hasText(reply.getContent())) {
            throw new BailianGroupOutputException("工作流回复缺少 speakerCode 或 content");
        }
        String speakerCode = reply.getSpeakerCode().trim().toUpperCase(Locale.ROOT);
        if (AGENT_ORCHESTRATOR.equals(speakerCode)) {
            reply.setSpeakerCode(speakerCode);
            reply.setContent(reply.getContent().trim());
            return;
        }
        if (!allowedCodes.contains(speakerCode)) {
            throw new BailianGroupOutputException("工作流返回了未加入群聊的智能体");
        }
        reply.setSpeakerCode(speakerCode);
        reply.setContent(reply.getContent().trim());
    }
}
