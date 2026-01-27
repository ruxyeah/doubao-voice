package com.doubao.voice.protocol.constants;

/**
 * 序列化和压缩类型常量
 */
public final class SerializationType {

    private SerializationType() {
    }

    // ==================== 序列化方式 ====================

    /**
     * 原始二进制（无特殊序列化，主要用于音频数据）
     */
    public static final int RAW = 0x00;

    /**
     * JSON序列化（主要用于文本类型消息）
     */
    public static final int JSON = 0x01;

    // ==================== 压缩方式 ====================

    /**
     * 无压缩
     */
    public static final int COMPRESSION_NONE = 0x00;

    /**
     * Gzip压缩
     */
    public static final int COMPRESSION_GZIP = 0x01;

    // ==================== 协议版本 ====================

    /**
     * 协议版本V1
     */
    public static final int PROTOCOL_VERSION = 0x01;

    /**
     * 默认Header大小（4字节）
     */
    public static final int DEFAULT_HEADER_SIZE = 0x01;

    /**
     * 获取序列化方式名称
     */
    public static String getSerializationName(int type) {
        return switch (type) {
            case RAW -> "RAW";
            case JSON -> "JSON";
            default -> "UNKNOWN(" + type + ")";
        };
    }

    /**
     * 获取压缩方式名称
     */
    public static String getCompressionName(int type) {
        return switch (type) {
            case COMPRESSION_NONE -> "NONE";
            case COMPRESSION_GZIP -> "GZIP";
            default -> "UNKNOWN(" + type + ")";
        };
    }
}
