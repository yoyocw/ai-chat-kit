package io.github.yoyocw.aichatkit.testnative.framework.common.enums;

/** Test-only native enum value used to reject non-admin token subjects. */
public enum UserTypeEnum {
    ADMIN(2), MEMBER(1);
    private final int value;
    UserTypeEnum(int value) { this.value = value; }
    public Integer getValue() { return value; }
}
