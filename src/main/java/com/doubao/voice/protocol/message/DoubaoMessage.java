package com.doubao.voice.protocol.message;

import com.doubao.voice.protocol.constants.EventType;
import com.doubao.voice.protocol.constants.MessageType;
import com.doubao.voice.protocol.constants.SerializationType;
import lombok.Builder;
import lombok.Data;

/**
 * 豆包协议消息
 *
 * 封装编解码所需的所有字段
 */
@Data
@Builder
public class DoubaoMessage {

    // ==================== Header字段 ====================

    /**
     * 协议版本（默认1）
     */
    @Builder.Default
    private int protocolVersion = SerializationType.PROTOCOL_VERSION;

    /**
     * Header大小（默认1，表示4字节）
     */
    @Builder.Default
    private int headerSize = SerializationType.DEFAULT_HEADER_SIZE;

    /**
     * 消息类型
     * @see MessageType
     */
    private int messageType;

    /**
     * 消息类型特定标志
     * @see MessageType#FLAG_MSG_WITH_EVENT
     */
    @Builder.Default
    private int flags = MessageType.FLAG_MSG_WITH_EVENT;

    /**
     * 序列化方式
     * @see SerializationType#JSON
     * @see SerializationType#RAW
     */
    @Builder.Default
    private int serialization = SerializationType.JSON;

    /**
     * 压缩方式
     * @see SerializationType#COMPRESSION_GZIP
     * @see SerializationType#COMPRESSION_NONE
     */
    @Builder.Default
    private int compression = SerializationType.COMPRESSION_GZIP;

    // ==================== 扩展字段 ====================

    /**
     * 事件ID
     * @see EventType
     */
    private Integer eventId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 序列号
     */
    private Integer sequence;

    // ==================== Payload字段 ====================

    /**
     * JSON负载（用于文本消息）
     */
    private Object payload;

    /**
     * 二进制负载（用于音频数据）
     */
    private byte[] binaryPayload;

    // ==================== 辅助方法 ====================

    /**
     * 判断是否有事件ID
     */
    public boolean hasEvent() {
        return (flags & MessageType.FLAG_MSG_WITH_EVENT) != 0;
    }

    /**
     * 判断是否使用Gzip压缩
     */
    public boolean isGzipCompressed() {
        return compression == SerializationType.COMPRESSION_GZIP;
    }

    /**
     * 判断是否为JSON序列化
     */
    public boolean isJsonSerialization() {
        return serialization == SerializationType.JSON;
    }

    /**
     * 判断是否为客户端消息
     */
    public boolean isClientMessage() {
        return MessageType.isClientMessage(messageType);
    }

    /**
     * 判断是否为服务端消息
     */
    public boolean isServerMessage() {
        return MessageType.isServerMessage(messageType);
    }

    /**
     * 判断是否为音频数据消息
     */
    public boolean isAudioMessage() {
        return messageType == MessageType.CLIENT_AUDIO_ONLY_REQUEST ||
               messageType == MessageType.SERVER_ACK;
    }

    /**
     * 获取事件名称
     */
    public String getEventName() {
        return eventId != null ? EventType.getName(eventId) : "NO_EVENT";
    }

    /**
     * 获取消息类型名称
     */
    public String getMessageTypeName() {
        return MessageType.getName(messageType);
    }

    @Override
    public String toString() {
        return String.format("DoubaoMessage{type=%s, event=%s, sessionId=%s, payloadSize=%d}",
                getMessageTypeName(),
                getEventName(),
                sessionId,
                binaryPayload != null ? binaryPayload.length :
                        (payload != null ? payload.toString().length() : 0));
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建客户端JSON消息
     */
    public static DoubaoMessage createClientMessage(int eventId, String sessionId, Object payload) {
        return DoubaoMessage.builder()
                .messageType(MessageType.CLIENT_FULL_REQUEST)
                .flags(MessageType.FLAG_MSG_WITH_EVENT)
                .serialization(SerializationType.JSON)
                .compression(SerializationType.COMPRESSION_GZIP)
                .eventId(eventId)
                .sessionId(sessionId)
                .payload(payload)
                .build();
    }

    /**
     * 创建客户端音频消息
     */
    public static DoubaoMessage createAudioMessage(String sessionId, byte[] audioData) {
        return DoubaoMessage.builder()
                .messageType(MessageType.CLIENT_AUDIO_ONLY_REQUEST)
                .flags(MessageType.FLAG_MSG_WITH_EVENT)
                .serialization(SerializationType.RAW)
                .compression(SerializationType.COMPRESSION_NONE)
                .eventId(EventType.TASK_REQUEST)
                .sessionId(sessionId)
                .binaryPayload(audioData)
                .build();
    }

    /**
     * 创建开始连接消息
     */
    public static DoubaoMessage createStartConnectionMessage() {
        return createClientMessage(EventType.START_CONNECTION, null, java.util.Collections.emptyMap());
    }

    /**
     * 创建结束连接消息
     */
    public static DoubaoMessage createFinishConnectionMessage() {
        return createClientMessage(EventType.FINISH_CONNECTION, null, java.util.Collections.emptyMap());
    }

    /**
     * 创建开始会话消息
     */
    public static DoubaoMessage createStartSessionMessage(String sessionId, Object config) {
        return createClientMessage(EventType.START_SESSION, sessionId, config);
    }

    /**
     * 创建结束会话消息
     */
    public static DoubaoMessage createFinishSessionMessage(String sessionId) {
        return createClientMessage(EventType.FINISH_SESSION, sessionId, java.util.Collections.emptyMap());
    }

    /**
     * 创建文本查询消息
     */
    public static DoubaoMessage createTextQueryMessage(String sessionId, String text, String questionId) {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("content", text);
        if (questionId != null) {
            payload.put("question_id", questionId);
        }
        return createClientMessage(EventType.CHAT_TEXT_QUERY, sessionId, payload);
    }
}
