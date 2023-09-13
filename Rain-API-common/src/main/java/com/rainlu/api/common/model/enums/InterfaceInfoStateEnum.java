package com.rainlu.api.common.model.enums;

/**
 * 接口状态枚举类
 */
public enum InterfaceInfoStateEnum {

    ONLINE("已发布", (byte) 1),
    OFFLINE("已下线", (byte) 0);

    private final String text;

    private final Byte value;

    InterfaceInfoStateEnum(String text, Byte value) {
        this.text = text;
        this.value = value;
    }


    public byte getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
