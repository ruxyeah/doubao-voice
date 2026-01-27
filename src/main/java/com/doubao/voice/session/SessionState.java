package com.doubao.voice.session;

/**
 * 语音会话状态
 */
public enum SessionState {

    /**
     * 已创建，未连接豆包
     */
    CREATED("已创建"),

    /**
     * 正在连接豆包API
     */
    CONNECTING("连接中"),

    /**
     * 已连接豆包API，等待会话启动
     */
    CONNECTED("已连接"),

    /**
     * 正在启动会话
     */
    SESSION_STARTING("会话启动中"),

    /**
     * 会话已启动，可以进行对话
     */
    SESSION_ACTIVE("会话进行中"),

    /**
     * 正在结束会话
     */
    SESSION_ENDING("会话结束中"),

    /**
     * 已断开连接
     */
    DISCONNECTED("已断开"),

    /**
     * 发生错误
     */
    ERROR("错误");

    private final String description;

    SessionState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为活跃状态（可以发送消息）
     */
    public boolean isActive() {
        return this == CONNECTED || this == SESSION_ACTIVE;
    }

    /**
     * 判断是否为终结状态
     */
    public boolean isTerminal() {
        return this == DISCONNECTED || this == ERROR;
    }
}
