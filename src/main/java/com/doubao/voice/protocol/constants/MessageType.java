package com.doubao.voice.protocol.constants;

/**
 * 消息类型常量
 *
 * 定义客户端和服务端消息类型
 */
public final class MessageType {

    private MessageType() {
    }

    // ==================== 客户端消息类型 ====================

    /**
     * 客户端完整请求（JSON + 可选Gzip）
     */
    public static final int CLIENT_FULL_REQUEST = 0x01;

    /**
     * 客户端仅音频请求（二进制音频数据）
     */
    public static final int CLIENT_AUDIO_ONLY_REQUEST = 0x02;

    // ==================== 服务端消息类型 ====================

    /**
     * 服务端完整响应（事件消息，JSON + 可选Gzip）
     */
    public static final int SERVER_FULL_RESPONSE = 0x09;

    /**
     * 服务端ACK响应（通常携带音频数据）
     */
    public static final int SERVER_ACK = 0x0B;

    /**
     * 服务端错误响应
     */
    public static final int SERVER_ERROR_RESPONSE = 0x0F;

    // ==================== 消息类型特定标志 ====================

    /**
     * 无序列号
     */
    public static final int FLAG_NO_SEQUENCE = 0x00;

    /**
     * 正序列号
     */
    public static final int FLAG_POS_SEQUENCE = 0x01;

    /**
     * 负序列号
     */
    public static final int FLAG_NEG_SEQUENCE = 0x02;

    /**
     * 包含事件ID
     */
    public static final int FLAG_MSG_WITH_EVENT = 0x04;

    /**
     * 判断是否为客户端消息类型
     */
    public static boolean isClientMessage(int type) {
        return type == CLIENT_FULL_REQUEST || type == CLIENT_AUDIO_ONLY_REQUEST;
    }

    /**
     * 判断是否为服务端消息类型
     */
    public static boolean isServerMessage(int type) {
        return type == SERVER_FULL_RESPONSE || type == SERVER_ACK || type == SERVER_ERROR_RESPONSE;
    }

    /**
     * 获取消息类型名称
     */
    public static String getName(int type) {
        return switch (type) {
            case CLIENT_FULL_REQUEST -> "CLIENT_FULL_REQUEST";
            case CLIENT_AUDIO_ONLY_REQUEST -> "CLIENT_AUDIO_ONLY_REQUEST";
            case SERVER_FULL_RESPONSE -> "SERVER_FULL_RESPONSE";
            case SERVER_ACK -> "SERVER_ACK";
            case SERVER_ERROR_RESPONSE -> "SERVER_ERROR_RESPONSE";
            default -> "UNKNOWN(" + type + ")";
        };
    }
}
