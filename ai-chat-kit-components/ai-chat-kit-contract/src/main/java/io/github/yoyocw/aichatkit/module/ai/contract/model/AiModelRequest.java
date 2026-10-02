package io.github.yoyocw.aichatkit.module.ai.contract.model;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMember;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable authorized request; credentials intentionally lack a bean getter and textual representation. */
public final class AiModelRequest {
    private final AiChatMode mode;
    private final Long messageId;
    private final String appId;
    private final String prompt;
    private final String sessionId;
    private final String businessArtifactJson;
    private final String historySummary;
    private final String traceCode;
    private final transient String toolAuthorization;
    private final Boolean mapEnabled;
    private final AiApplicationConfig application;
    private final List<AiGroupMemberSnapshot> members;

    public AiModelRequest(AiChatMode mode, Long messageId, String appId, String prompt, String sessionId,
                          Boolean mapEnabled, String businessArtifactJson, String historySummary, String traceCode,
                          AiApplicationConfig application, String toolAuthorization, List<? extends AiGroupMember> members) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.messageId = messageId;
        this.appId = appId;
        this.prompt = prompt;
        this.sessionId = sessionId;
        this.mapEnabled = mapEnabled;
        this.businessArtifactJson = businessArtifactJson;
        this.historySummary = historySummary;
        this.traceCode = traceCode;
        this.application = application;
        this.toolAuthorization = toolAuthorization;
        List<AiGroupMemberSnapshot> copy = new ArrayList<>();
        if (members != null) {
            for (AiGroupMember member : members) {
                copy.add(new AiGroupMemberSnapshot(member.getCode(), member.getName(), member.getRole()));
            }
        }
        this.members = Collections.unmodifiableList(copy);
    }

    public AiChatMode getMode() {
        return mode;
    }

    public Long getMessageId() {
        return messageId;
    }

    public String getAppId() {
        return appId;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Boolean getMapEnabled() {
        return mapEnabled;
    }

    public String getBusinessArtifactJson() {
        return businessArtifactJson;
    }

    public String getHistorySummary() {
        return historySummary;
    }

    public String getTraceCode() {
        return traceCode;
    }

    public AiApplicationConfig getApplication() {
        return application;
    }

    public List<AiGroupMemberSnapshot> getMembers() {
        return members;
    }

    public String toolAuthorization() {
        return toolAuthorization;
    }

    public AiModelRequest withSessionId(String value) {
        return new AiModelRequest(mode, messageId, appId, prompt, value, mapEnabled, businessArtifactJson,
                historySummary, traceCode, application, toolAuthorization, members);
    }

    @Override

    public String toString() {
        return "AiModelRequest[mode="+mode+", messageId="+messageId+"]";
    }
}
