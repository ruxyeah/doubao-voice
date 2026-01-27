package com.doubao.voice.protocol.codec;

import com.doubao.voice.protocol.constants.MessageType;
import com.doubao.voice.protocol.constants.SerializationType;
import com.doubao.voice.protocol.message.DoubaoMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 豆包协议编码器
 *
 * 将DoubaoMessage编码为二进制数据
 *
 * 消息格式：
 * [Header 4字节] [Event ID 4字节] [Session ID长度 4字节] [Session ID] [Payload长度 4字节] [Payload]
 */
@Slf4j
public class DoubaoProtocolEncoder {

    private final ObjectMapper objectMapper;

    public DoubaoProtocolEncoder() {
        this.objectMapper = new ObjectMapper();
    }

    public DoubaoProtocolEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 编码消息
     *
     * @param message 消息对象
     * @return 二进制数据
     * @throws IOException 编码失败
     */
    public byte[] encode(DoubaoMessage message) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // 1. 写入Header（4字节）
            byte[] header = encodeHeader(message);
            baos.write(header);

            // 2. 如果有事件ID，写入Event ID（4字节，大端序）
            if (message.hasEvent() && message.getEventId() != null) {
                byte[] eventBytes = encodeInt32BigEndian(message.getEventId());
                baos.write(eventBytes);
            }

            // 3. 写入Session ID（仅当sessionId存在时）
            if (message.getSessionId() != null && !message.getSessionId().isEmpty()) {
                byte[] sessionIdBytes = encodeSessionId(message.getSessionId());
                baos.write(sessionIdBytes);
            }

            // 4. 编码并写入Payload
            byte[] payloadBytes = encodePayload(message);
            // 写入Payload长度（4字节，大端序）
            byte[] payloadLengthBytes = encodeInt32BigEndian(payloadBytes.length);
            baos.write(payloadLengthBytes);
            // 写入Payload数据
            baos.write(payloadBytes);

            byte[] result = baos.toByteArray();

            // 打印编码后的数据用于调试
            if (log.isDebugEnabled()) {
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < Math.min(result.length, 64); i++) {
                    hex.append(String.format("%02X ", result[i]));
                }
                log.debug("编码后数据[{}字节]: {}", result.length, hex);
            }

            log.debug("编码消息完成: type={}, event={}, sessionId={}, totalSize={}",
                    message.getMessageTypeName(),
                    message.getEventName(),
                    message.getSessionId(),
                    result.length);

            return result;
        }
    }

    /**
     * 编码Header（4字节）
     *
     * Byte 0: [protocol_version(4bit)][header_size(4bit)]
     * Byte 1: [message_type(4bit)][flags(4bit)]
     * Byte 2: [serialization(4bit)][compression(4bit)]
     * Byte 3: [reserved(8bit)]
     */
    private byte[] encodeHeader(DoubaoMessage message) {
        byte[] header = new byte[4];

        // Byte 0: protocol_version | header_size
        header[0] = (byte) ((message.getProtocolVersion() << 4) | (message.getHeaderSize() & 0x0F));

        // Byte 1: message_type | flags
        header[1] = (byte) ((message.getMessageType() << 4) | (message.getFlags() & 0x0F));

        // Byte 2: serialization | compression
        header[2] = (byte) ((message.getSerialization() << 4) | (message.getCompression() & 0x0F));

        // Byte 3: reserved
        header[3] = 0x00;

        return header;
    }

    /**
     * 编码Session ID
     *
     * 格式: [长度4字节][UTF-8字节数组]
     */
    private byte[] encodeSessionId(String sessionId) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] sessionIdBytes = sessionId.getBytes(StandardCharsets.UTF_8);
            // 写入长度
            baos.write(encodeInt32BigEndian(sessionIdBytes.length));
            // 写入内容
            baos.write(sessionIdBytes);
            return baos.toByteArray();
        }
    }

    /**
     * 编码Payload
     */
    private byte[] encodePayload(DoubaoMessage message) throws IOException {
        byte[] payloadBytes;

        // 根据消息类型选择Payload来源
        if (message.getBinaryPayload() != null) {
            // 二进制Payload（音频数据）
            payloadBytes = message.getBinaryPayload();
        } else if (message.getPayload() != null) {
            // JSON Payload
            payloadBytes = objectMapper.writeValueAsBytes(message.getPayload());
        } else {
            // 空Payload
            payloadBytes = "{}".getBytes(StandardCharsets.UTF_8);
        }

        // 如果需要Gzip压缩
        if (message.isGzipCompressed() && payloadBytes.length > 0 &&
            message.getSerialization() == SerializationType.JSON) {
            payloadBytes = GzipUtils.compress(payloadBytes);
        }

        return payloadBytes;
    }

    /**
     * 将int编码为4字节大端序
     */
    private byte[] encodeInt32BigEndian(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(value);
        return buffer.array();
    }

    /**
     * 创建开始连接消息的二进制数据
     */
    public byte[] encodeStartConnection() throws IOException {
        return encode(DoubaoMessage.createStartConnectionMessage());
    }

    /**
     * 创建结束连接消息的二进制数据
     */
    public byte[] encodeFinishConnection() throws IOException {
        return encode(DoubaoMessage.createFinishConnectionMessage());
    }

    /**
     * 创建开始会话消息的二进制数据
     */
    public byte[] encodeStartSession(String sessionId, Object config) throws IOException {
        return encode(DoubaoMessage.createStartSessionMessage(sessionId, config));
    }

    /**
     * 创建结束会话消息的二进制数据
     */
    public byte[] encodeFinishSession(String sessionId) throws IOException {
        return encode(DoubaoMessage.createFinishSessionMessage(sessionId));
    }

    /**
     * 创建音频数据消息的二进制数据
     */
    public byte[] encodeAudio(String sessionId, byte[] audioData) throws IOException {
        return encode(DoubaoMessage.createAudioMessage(sessionId, audioData));
    }

    /**
     * 创建文本查询消息的二进制数据
     */
    public byte[] encodeTextQuery(String sessionId, String text, String questionId) throws IOException {
        return encode(DoubaoMessage.createTextQueryMessage(sessionId, text, questionId));
    }
}
