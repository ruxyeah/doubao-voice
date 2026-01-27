package com.doubao.voice.protocol.constants;

/**
 * 事件类型常量
 *
 * 定义客户端请求事件和服务端响应事件
 */
public final class EventType {

    private EventType() {
    }

    // ==================== 客户端事件 ====================

    /**
     * 开始连接
     */
    public static final int START_CONNECTION = 1;

    /**
     * 结束连接
     */
    public static final int FINISH_CONNECTION = 2;

    /**
     * 开始会话
     */
    public static final int START_SESSION = 100;

    /**
     * 结束会话
     */
    public static final int FINISH_SESSION = 102;

    /**
     * 音频数据请求
     */
    public static final int TASK_REQUEST = 200;

    /**
     * 打招呼文本
     */
    public static final int SAY_HELLO = 300;

    /**
     * 文本查询
     */
    public static final int CHAT_TEXT_QUERY = 501;

    /**
     * RAG文本
     */
    public static final int CHAT_RAG_TEXT = 502;

    /**
     * TTS文本（指定文本合成音频）
     */
    public static final int CHAT_TTS_TEXT = 500;

    /**
     * 创建上下文
     */
    public static final int CONVERSATION_CREATE = 560;

    /**
     * 更新上下文
     */
    public static final int CONVERSATION_UPDATE = 561;

    /**
     * 查询上下文
     */
    public static final int CONVERSATION_RETRIEVE = 562;

    /**
     * 删除上下文
     */
    public static final int CONVERSATION_DELETE = 563;

    // ==================== 服务端事件 ====================

    /**
     * 连接已启动
     */
    public static final int CONNECTION_STARTED = 50;

    /**
     * 会话已启动
     */
    public static final int SESSION_STARTED = 150;

    /**
     * 会话已结束
     */
    public static final int SESSION_FINISHED = 152;

    /**
     * 会话失败
     */
    public static final int SESSION_FAILED = 153;

    /**
     * 用量响应
     */
    public static final int USAGE_RESPONSE = 154;

    /**
     * TTS句子开始
     */
    public static final int TTS_SENTENCE_START = 350;

    /**
     * TTS句子结束
     */
    public static final int TTS_SENTENCE_END = 351;

    /**
     * TTS音频响应
     */
    public static final int TTS_RESPONSE = 352;

    /**
     * TTS播放结束
     */
    public static final int TTS_ENDED = 359;

    /**
     * 用户开始说话（ASR检测到首字）
     */
    public static final int ASR_INFO = 450;

    /**
     * ASR识别结果
     */
    public static final int ASR_RESPONSE = 451;

    /**
     * 用户停止说话
     */
    public static final int ASR_ENDED = 459;

    /**
     * AI文本回复
     */
    public static final int CHAT_RESPONSE = 550;

    /**
     * 文本查询确认
     */
    public static final int CHAT_TEXT_QUERY_CONFIRMED = 553;

    /**
     * AI回复结束
     */
    public static final int CHAT_ENDED = 559;

    /**
     * 上下文创建确认
     */
    public static final int CONVERSATION_CREATED = 567;

    /**
     * 上下文更新确认
     */
    public static final int CONVERSATION_UPDATED = 568;

    /**
     * 上下文查询结果
     */
    public static final int CONVERSATION_RETRIEVED = 569;

    /**
     * 上下文删除确认
     */
    public static final int CONVERSATION_DELETED = 571;

    /**
     * 对话通用错误
     */
    public static final int DIALOG_COMMON_ERROR = 599;

    /**
     * 判断是否为客户端事件
     */
    public static boolean isClientEvent(int event) {
        return event < 50 || (event >= 100 && event < 150) ||
               (event >= 200 && event < 300) || (event >= 500 && event < 600);
    }

    /**
     * 判断是否为服务端事件
     */
    public static boolean isServerEvent(int event) {
        return !isClientEvent(event);
    }

    /**
     * 获取事件名称
     */
    public static String getName(int event) {
        return switch (event) {
            // 客户端事件
            case START_CONNECTION -> "START_CONNECTION";
            case FINISH_CONNECTION -> "FINISH_CONNECTION";
            case START_SESSION -> "START_SESSION";
            case FINISH_SESSION -> "FINISH_SESSION";
            case TASK_REQUEST -> "TASK_REQUEST";
            case SAY_HELLO -> "SAY_HELLO";
            case CHAT_TEXT_QUERY -> "CHAT_TEXT_QUERY";
            case CHAT_RAG_TEXT -> "CHAT_RAG_TEXT";
            case CHAT_TTS_TEXT -> "CHAT_TTS_TEXT";
            case CONVERSATION_CREATE -> "CONVERSATION_CREATE";
            case CONVERSATION_UPDATE -> "CONVERSATION_UPDATE";
            case CONVERSATION_RETRIEVE -> "CONVERSATION_RETRIEVE";
            case CONVERSATION_DELETE -> "CONVERSATION_DELETE";
            // 服务端事件
            case CONNECTION_STARTED -> "CONNECTION_STARTED";
            case SESSION_STARTED -> "SESSION_STARTED";
            case SESSION_FINISHED -> "SESSION_FINISHED";
            case SESSION_FAILED -> "SESSION_FAILED";
            case USAGE_RESPONSE -> "USAGE_RESPONSE";
            case TTS_SENTENCE_START -> "TTS_SENTENCE_START";
            case TTS_SENTENCE_END -> "TTS_SENTENCE_END";
            case TTS_RESPONSE -> "TTS_RESPONSE";
            case TTS_ENDED -> "TTS_ENDED";
            case ASR_INFO -> "ASR_INFO";
            case ASR_RESPONSE -> "ASR_RESPONSE";
            case ASR_ENDED -> "ASR_ENDED";
            case CHAT_RESPONSE -> "CHAT_RESPONSE";
            case CHAT_TEXT_QUERY_CONFIRMED -> "CHAT_TEXT_QUERY_CONFIRMED";
            case CHAT_ENDED -> "CHAT_ENDED";
            case CONVERSATION_CREATED -> "CONVERSATION_CREATED";
            case CONVERSATION_UPDATED -> "CONVERSATION_UPDATED";
            case CONVERSATION_RETRIEVED -> "CONVERSATION_RETRIEVED";
            case CONVERSATION_DELETED -> "CONVERSATION_DELETED";
            case DIALOG_COMMON_ERROR -> "DIALOG_COMMON_ERROR";
            default -> "UNKNOWN(" + event + ")";
        };
    }
}
