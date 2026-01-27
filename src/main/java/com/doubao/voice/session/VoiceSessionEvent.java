package com.doubao.voice.session;

import lombok.Builder;
import lombok.Data;

/**
 * 语音会话事件
 */
@Data
@Builder
public class VoiceSessionEvent {

    /**
     * 事件类型
     */
    private EventType type;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 对话ID
     */
    private String dialogId;

    /**
     * 问题ID
     */
    private String questionId;

    /**
     * 回复ID
     */
    private String replyId;

    /**
     * 文本内容
     */
    private String text;

    /**
     * TTS类型
     */
    private String ttsType;

    /**
     * 音频数据
     */
    private byte[] audioData;

    /**
     * 是否为临时结果
     */
    private Boolean isInterim;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 状态码
     */
    private String statusCode;

    /**
     * 关闭代码
     */
    private Integer closeCode;

    /**
     * 关闭原因
     */
    private String closeReason;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        // 连接相关
        CONNECTION_STARTED,
        DISCONNECTED,
        ERROR,

        // 会话相关
        SESSION_STARTED,
        SESSION_FINISHED,
        SESSION_FAILED,

        // ASR相关
        USER_SPEECH_STARTED,
        ASR_RESULT,
        USER_SPEECH_ENDED,

        // TTS相关
        TTS_SENTENCE_START,
        TTS_SENTENCE_END,
        TTS_ENDED,
        AUDIO_DATA,

        // Chat相关
        CHAT_RESPONSE,
        CHAT_ENDED,

        // 错误
        DIALOG_ERROR
    }

    // ==================== 工厂方法 ====================

    public static VoiceSessionEvent connectionStarted(String sessionId) {
        return VoiceSessionEvent.builder()
                .type(EventType.CONNECTION_STARTED)
                .sessionId(sessionId)
                .build();
    }

    public static VoiceSessionEvent disconnected(String sessionId, int code, String reason) {
        return VoiceSessionEvent.builder()
                .type(EventType.DISCONNECTED)
                .sessionId(sessionId)
                .closeCode(code)
                .closeReason(reason)
                .build();
    }

    public static VoiceSessionEvent error(String sessionId, String error) {
        return VoiceSessionEvent.builder()
                .type(EventType.ERROR)
                .sessionId(sessionId)
                .error(error)
                .build();
    }

    public static VoiceSessionEvent sessionStarted(String sessionId, String dialogId) {
        return VoiceSessionEvent.builder()
                .type(EventType.SESSION_STARTED)
                .sessionId(sessionId)
                .dialogId(dialogId)
                .build();
    }

    public static VoiceSessionEvent sessionFinished(String sessionId) {
        return VoiceSessionEvent.builder()
                .type(EventType.SESSION_FINISHED)
                .sessionId(sessionId)
                .build();
    }

    public static VoiceSessionEvent sessionFailed(String sessionId, String error) {
        return VoiceSessionEvent.builder()
                .type(EventType.SESSION_FAILED)
                .sessionId(sessionId)
                .error(error)
                .build();
    }

    public static VoiceSessionEvent userSpeechStarted(String sessionId, String questionId) {
        return VoiceSessionEvent.builder()
                .type(EventType.USER_SPEECH_STARTED)
                .sessionId(sessionId)
                .questionId(questionId)
                .build();
    }

    public static VoiceSessionEvent asrResult(String sessionId, String text, boolean isInterim) {
        return VoiceSessionEvent.builder()
                .type(EventType.ASR_RESULT)
                .sessionId(sessionId)
                .text(text)
                .isInterim(isInterim)
                .build();
    }

    public static VoiceSessionEvent userSpeechEnded(String sessionId) {
        return VoiceSessionEvent.builder()
                .type(EventType.USER_SPEECH_ENDED)
                .sessionId(sessionId)
                .build();
    }

    public static VoiceSessionEvent ttsSentenceStart(String sessionId, String text, String ttsType,
                                                     String questionId, String replyId) {
        return VoiceSessionEvent.builder()
                .type(EventType.TTS_SENTENCE_START)
                .sessionId(sessionId)
                .text(text)
                .ttsType(ttsType)
                .questionId(questionId)
                .replyId(replyId)
                .build();
    }

    public static VoiceSessionEvent ttsSentenceEnd(String sessionId, String questionId, String replyId) {
        return VoiceSessionEvent.builder()
                .type(EventType.TTS_SENTENCE_END)
                .sessionId(sessionId)
                .questionId(questionId)
                .replyId(replyId)
                .build();
    }

    public static VoiceSessionEvent ttsEnded(String sessionId, String questionId, String replyId) {
        return VoiceSessionEvent.builder()
                .type(EventType.TTS_ENDED)
                .sessionId(sessionId)
                .questionId(questionId)
                .replyId(replyId)
                .build();
    }

    public static VoiceSessionEvent audioData(String sessionId, byte[] audioData) {
        return VoiceSessionEvent.builder()
                .type(EventType.AUDIO_DATA)
                .sessionId(sessionId)
                .audioData(audioData)
                .build();
    }

    public static VoiceSessionEvent chatResponse(String sessionId, String content,
                                                 String questionId, String replyId) {
        return VoiceSessionEvent.builder()
                .type(EventType.CHAT_RESPONSE)
                .sessionId(sessionId)
                .text(content)
                .questionId(questionId)
                .replyId(replyId)
                .build();
    }

    public static VoiceSessionEvent chatEnded(String sessionId, String questionId, String replyId) {
        return VoiceSessionEvent.builder()
                .type(EventType.CHAT_ENDED)
                .sessionId(sessionId)
                .questionId(questionId)
                .replyId(replyId)
                .build();
    }

    public static VoiceSessionEvent dialogError(String sessionId, String statusCode, String message) {
        return VoiceSessionEvent.builder()
                .type(EventType.DIALOG_ERROR)
                .sessionId(sessionId)
                .statusCode(statusCode)
                .error(message)
                .build();
    }
}
