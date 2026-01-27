package com.doubao.voice.client;

import com.doubao.voice.protocol.message.DoubaoMessage;

/**
 * 豆包WebSocket客户端事件监听器
 */
public interface DoubaoClientListener {

    /**
     * 连接成功
     */
    default void onConnected() {
    }

    /**
     * 连接断开
     *
     * @param code   关闭代码
     * @param reason 关闭原因
     */
    default void onDisconnected(int code, String reason) {
    }

    /**
     * 收到消息
     *
     * @param message 解码后的消息
     */
    void onMessage(DoubaoMessage message);

    /**
     * 收到音频数据
     *
     * @param audioData 音频数据
     * @param sessionId 会话ID
     */
    default void onAudioData(byte[] audioData, String sessionId) {
    }

    /**
     * 发生错误
     *
     * @param error 错误信息
     */
    default void onError(Throwable error) {
    }

    /**
     * 连接已启动事件
     */
    default void onConnectionStarted() {
    }

    /**
     * 会话已启动事件
     *
     * @param dialogId 对话ID（用于续接对话）
     */
    default void onSessionStarted(String dialogId) {
    }

    /**
     * 会话已结束事件
     */
    default void onSessionFinished() {
    }

    /**
     * 会话失败事件
     *
     * @param error 错误信息
     */
    default void onSessionFailed(String error) {
    }

    /**
     * 用户开始说话（ASR检测到首字）
     *
     * @param questionId 问题ID
     */
    default void onUserSpeechStarted(String questionId) {
    }

    /**
     * ASR识别结果
     *
     * @param text      识别文本
     * @param isInterim 是否为临时结果
     */
    default void onAsrResult(String text, boolean isInterim) {
    }

    /**
     * 用户停止说话
     */
    default void onUserSpeechEnded() {
    }

    /**
     * TTS句子开始
     *
     * @param text       文本内容
     * @param ttsType    TTS类型
     * @param questionId 问题ID
     * @param replyId    回复ID
     */
    default void onTtsSentenceStart(String text, String ttsType, String questionId, String replyId) {
    }

    /**
     * TTS句子结束
     *
     * @param questionId 问题ID
     * @param replyId    回复ID
     */
    default void onTtsSentenceEnd(String questionId, String replyId) {
    }

    /**
     * TTS播放结束
     *
     * @param questionId 问题ID
     * @param replyId    回复ID
     */
    default void onTtsEnded(String questionId, String replyId) {
    }

    /**
     * AI文本回复
     *
     * @param content    回复内容
     * @param questionId 问题ID
     * @param replyId    回复ID
     */
    default void onChatResponse(String content, String questionId, String replyId) {
    }

    /**
     * AI回复结束
     *
     * @param questionId 问题ID
     * @param replyId    回复ID
     */
    default void onChatEnded(String questionId, String replyId) {
    }

    /**
     * 对话错误
     *
     * @param statusCode 状态码
     * @param message    错误信息
     */
    default void onDialogError(String statusCode, String message) {
    }
}
