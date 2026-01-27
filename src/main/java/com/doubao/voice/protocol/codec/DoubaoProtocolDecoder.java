package com.doubao.voice.protocol.codec;

import com.doubao.voice.protocol.constants.MessageType;
import com.doubao.voice.protocol.constants.SerializationType;
import com.doubao.voice.protocol.message.DoubaoMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 豆包协议解码器
 *
 * 将二进制数据解码为DoubaoMessage
 *
 * 消息格式：
 * [Header 4字节] [Sequence 4字节(可选)] [Event ID 4字节(可选)] [Session ID长度 4字节] [Session ID] [Payload长度 4字节] [Payload]
 */
@Slf4j
public class DoubaoProtocolDecoder {

    private final ObjectMapper objectMapper;

    public DoubaoProtocolDecoder() {
        this.objectMapper = new ObjectMapper();
    }

    public DoubaoProtocolDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解码消息
     *
     * @param data 二进制数据
     * @return 消息对象
     * @throws IOException 解码失败
     */
    public DoubaoMessage decode(byte[] data) throws IOException {
        if (data == null || data.length < 4) {
            throw new IOException("数据长度不足，至少需要4字节Header");
        }

        // 打印原始数据用于调试
        if (log.isDebugEnabled()) {
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(data.length, 64); i++) {
                hex.append(String.format("%02X ", data[i]));
            }
            log.debug("收到原始数据[{}字节]: {}", data.length, hex);
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        DoubaoMessage.DoubaoMessageBuilder builder = DoubaoMessage.builder();

        // 1. 解析Header（4字节）
        parseHeader(buffer, builder);

        // 获取已解析的值用于后续判断
        int messageType = (data[1] >> 4) & 0x0F;
        int flags = data[1] & 0x0F;
        int serialization = (data[2] >> 4) & 0x0F;
        int compression = data[2] & 0x0F;

        // 跳过扩展头（如果Header Size > 1）
        int headerSize = data[0] & 0x0F;
        if (headerSize > 1) {
            int extendedHeaderBytes = (headerSize - 1) * 4;
            if (buffer.remaining() < extendedHeaderBytes) {
                throw new IOException("扩展头数据不足");
            }
            buffer.position(buffer.position() + extendedHeaderBytes);
        }

        // 2. 如果有序列号（flags低2位不为0），读取Sequence（4字节）
        if ((flags & 0x03) != 0) {
            if (buffer.remaining() < 4) {
                throw new IOException("Sequence数据不足");
            }
            int sequence = buffer.getInt();
            builder.sequence(sequence);
        }

        // 3. 如果有MSG_WITH_EVENT标志，读取Event ID
        if ((flags & MessageType.FLAG_MSG_WITH_EVENT) != 0) {
            if (buffer.remaining() < 4) {
                throw new IOException("Event ID数据不足");
            }
            int eventId = buffer.getInt();
            builder.eventId(eventId);
        }

        // 4. 读取Session ID（仅SERVER_FULL_RESPONSE和SERVER_ACK有Session ID）
        if (messageType == MessageType.SERVER_FULL_RESPONSE ||
            messageType == MessageType.SERVER_ACK) {
            String sessionId = parseSessionId(buffer);
            builder.sessionId(sessionId);
        }

        // 4.1 读取Error Code（仅SERVER_ERROR_RESPONSE有）
        if (messageType == MessageType.SERVER_ERROR_RESPONSE) {
            if (buffer.remaining() < 4) {
                throw new IOException("Error Code数据不足");
            }
            int errorCode = buffer.getInt();
            log.debug("SERVER_ERROR_RESPONSE errorCode={}", errorCode);
        }

        // 5. 读取Payload长度和数据
        if (buffer.remaining() >= 4) {
            int payloadLength = buffer.getInt();
            log.debug("读取Payload: position={}, payloadLength={}, remaining={}",
                    buffer.position(), payloadLength, buffer.remaining());
            if (payloadLength > 0) {
                if (buffer.remaining() < payloadLength) {
                    throw new IOException("Payload数据不足，期望" + payloadLength + "字节，实际" + buffer.remaining() + "字节");
                }
                byte[] payloadBytes = new byte[payloadLength];
                buffer.get(payloadBytes);

                // 解压和解析Payload
                parsePayload(payloadBytes, serialization, compression, messageType, builder);
            }
        }

        DoubaoMessage message = builder.build();
        log.debug("解码消息完成: type={}, event={}, sessionId={}",
                message.getMessageTypeName(),
                message.getEventName(),
                message.getSessionId());

        return message;
    }

    /**
     * 解析Header（4字节）
     */
    private void parseHeader(ByteBuffer buffer, DoubaoMessage.DoubaoMessageBuilder builder) {
        byte byte0 = buffer.get();
        byte byte1 = buffer.get();
        byte byte2 = buffer.get();
        byte byte3 = buffer.get(); // reserved

        // Byte 0: protocol_version | header_size
        int protocolVersion = (byte0 >> 4) & 0x0F;
        int headerSize = byte0 & 0x0F;

        // Byte 1: message_type | flags
        int messageType = (byte1 >> 4) & 0x0F;
        int flags = byte1 & 0x0F;

        // Byte 2: serialization | compression
        int serialization = (byte2 >> 4) & 0x0F;
        int compression = byte2 & 0x0F;

        builder.protocolVersion(protocolVersion)
               .headerSize(headerSize)
               .messageType(messageType)
               .flags(flags)
               .serialization(serialization)
               .compression(compression);
    }

    /**
     * 解析Session ID
     */
    private String parseSessionId(ByteBuffer buffer) throws IOException {
        if (buffer.remaining() < 4) {
            throw new IOException("Session ID长度数据不足");
        }
        int sessionIdLength = buffer.getInt();
        if (sessionIdLength <= 0) {
            return null;
        }
        if (buffer.remaining() < sessionIdLength) {
            throw new IOException("Session ID数据不足");
        }
        byte[] sessionIdBytes = new byte[sessionIdLength];
        buffer.get(sessionIdBytes);
        return new String(sessionIdBytes, StandardCharsets.UTF_8);
    }

    /**
     * 解析Payload
     */
    private void parsePayload(byte[] payloadBytes, int serialization, int compression,
                              int messageType, DoubaoMessage.DoubaoMessageBuilder builder) throws IOException {
        byte[] decodedBytes = payloadBytes;

        // 如果是Gzip压缩，先解压
        if (compression == SerializationType.COMPRESSION_GZIP) {
            try {
                decodedBytes = GzipUtils.decompress(payloadBytes);
            } catch (IOException e) {
                log.warn("Gzip解压失败，尝试作为原始数据处理: {}", e.getMessage());
                decodedBytes = payloadBytes;
            }
        }

        // 根据消息类型处理Payload
        if (messageType == MessageType.SERVER_ACK) {
            // SERVER_ACK通常是音频数据（二进制）
            builder.binaryPayload(decodedBytes);
        } else if (serialization == SerializationType.JSON) {
            // JSON数据
            try {
                Map<String, Object> payload = objectMapper.readValue(decodedBytes,
                        new TypeReference<Map<String, Object>>() {});
                builder.payload(payload);
            } catch (IOException e) {
                // 解析失败，存储为原始字符串
                String payloadStr = new String(decodedBytes, StandardCharsets.UTF_8);
                builder.payload(payloadStr);
                log.warn("JSON解析失败，存储为原始字符串: {}", e.getMessage());
            }
        } else {
            // RAW二进制数据
            builder.binaryPayload(decodedBytes);
        }
    }

    /**
     * 安全解码，失败返回null而非抛出异常
     */
    public DoubaoMessage decodeSafe(byte[] data) {
        try {
            return decode(data);
        } catch (Exception e) {
            log.error("消息解码失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从Payload中获取字符串字段
     */
    @SuppressWarnings("unchecked")
    public static String getPayloadString(DoubaoMessage message, String key) {
        if (message.getPayload() instanceof Map) {
            Object value = ((Map<String, Object>) message.getPayload()).get(key);
            return value != null ? value.toString() : null;
        }
        return null;
    }

    /**
     * 从Payload中获取整数字段
     */
    @SuppressWarnings("unchecked")
    public static Integer getPayloadInt(DoubaoMessage message, String key) {
        if (message.getPayload() instanceof Map) {
            Object value = ((Map<String, Object>) message.getPayload()).get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return null;
    }

    /**
     * 从Payload中获取布尔字段
     */
    @SuppressWarnings("unchecked")
    public static Boolean getPayloadBoolean(DoubaoMessage message, String key) {
        if (message.getPayload() instanceof Map) {
            Object value = ((Map<String, Object>) message.getPayload()).get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        }
        return null;
    }

    /**
     * 从Payload中获取嵌套Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getPayloadMap(DoubaoMessage message, String key) {
        if (message.getPayload() instanceof Map) {
            Object value = ((Map<String, Object>) message.getPayload()).get(key);
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
        }
        return null;
    }
}
