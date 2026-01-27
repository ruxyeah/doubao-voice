package com.doubao.voice.service;

import com.doubao.voice.session.SessionConfig;
import com.doubao.voice.session.SessionState;
import com.doubao.voice.session.VoiceSession;
import com.doubao.voice.session.VoiceSessionEvent;

import java.util.function.Consumer;

/**
 * 语音服务接口
 */
public interface VoiceService {

    /**
     * 创建语音会话
     *
     * @return 会话ID
     */
    String createSession();

    /**
     * 连接会话到豆包API
     *
     * @param sessionId 会话ID
     */
    void connectSession(String sessionId);

    /**
     * 启动语音会话
     *
     * @param sessionId 会话ID
     * @param config    会话配置
     */
    void startSession(String sessionId, SessionConfig config);

    /**
     * 结束语音会话
     *
     * @param sessionId 会话ID
     */
    void endSession(String sessionId);

    /**
     * 断开会话连接
     *
     * @param sessionId 会话ID
     */
    void disconnectSession(String sessionId);

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     */
    void deleteSession(String sessionId);

    /**
     * 发送音频数据
     *
     * @param sessionId 会话ID
     * @param audioData 音频数据（PCM16 16kHz 单声道）
     */
    void sendAudio(String sessionId, byte[] audioData);

    /**
     * 发送文本查询
     *
     * @param sessionId  会话ID
     * @param text       文本内容
     * @param questionId 问题ID（可选）
     */
    void sendTextQuery(String sessionId, String text, String questionId);

    /**
     * 获取会话状态
     *
     * @param sessionId 会话ID
     * @return 会话状态
     */
    SessionState getSessionState(String sessionId);

    /**
     * 获取会话信息
     *
     * @param sessionId 会话ID
     * @return 会话对象
     */
    VoiceSession getSession(String sessionId);

    /**
     * 添加会话事件监听器
     *
     * @param sessionId 会话ID
     * @param listener  事件监听器
     */
    void addSessionListener(String sessionId, Consumer<VoiceSessionEvent> listener);

    /**
     * 移除会话事件监听器
     *
     * @param sessionId 会话ID
     * @param listener  事件监听器
     */
    void removeSessionListener(String sessionId, Consumer<VoiceSessionEvent> listener);

    /**
     * 获取当前会话数量
     *
     * @return 会话数量
     */
    int getSessionCount();
}
