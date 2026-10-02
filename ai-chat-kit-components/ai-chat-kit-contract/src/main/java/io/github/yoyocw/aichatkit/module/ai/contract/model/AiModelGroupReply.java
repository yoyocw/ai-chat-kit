package io.github.yoyocw.aichatkit.module.ai.contract.model;

/** Provider-independent validated group reply. */
public final class AiModelGroupReply {
    private final String speakerCode;
    private final String content;

    public AiModelGroupReply(String speakerCode, String content) {
        this.speakerCode = speakerCode;
        this.content = content;
    }

    public String getSpeakerCode() {
        return speakerCode;
    }

    public String getContent() {
        return content;
    }
}
