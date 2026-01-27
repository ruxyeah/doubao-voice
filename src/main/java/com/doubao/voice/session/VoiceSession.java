package com.doubao.voice.session;

import com.doubao.voice.client.DoubaoClientListener;
import com.doubao.voice.client.DoubaoWebSocketClient;
import com.doubao.voice.config.DoubaoProperties;
import com.doubao.voice.protocol.message.DoubaoMessage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 语音会话实体
 *
 * 封装一个用户的语音对话会话，包括：
 * - 客户端WebSocket连接
 * - 豆包API WebSocket客户端
 * - 会话状态管理
 * - 事件转发
 */
@Slf4j
@Getter
public class VoiceSession implements DoubaoClientListener {

    /**
     * 会话ID（本地生成）
     */
    private final String sessionId;

    /**
     * 豆包对话ID（用于续接对话）
     */
    @Setter
    private String dialogId;

    /**
     * 会话状态
     */
    @Setter
    private volatile SessionState state;

    /**
     * 豆包WebSocket客户端
     */
    private final DoubaoWebSocketClient doubaoClient;

    /**
     * 客户端WebSocket会话
     */
    @Setter
    private WebSocketSession clientSession;

    /**
     * 会话配置
     */
    @Setter
    private SessionConfig config;

    /**
     * 创建时间
     */
    private final Instant createdAt;

    /**
     * 最后活动时间
     */
    @Setter
    private volatile Instant lastActiveAt;

    /**
     * 错误信息
     */
    @Setter
    private String errorMessage;

    /**
     * 事件监听器列表
     */
    private final CopyOnWriteArrayList<Consumer<VoiceSessionEvent>> eventListeners;

    public VoiceSession(DoubaoProperties properties) {
        this.sessionId = UUID.randomUUID().toString();
        this.state = SessionState.CREATED;
        this.createdAt = Instant.now();
        this.lastActiveAt = Instant.now();
        this.eventListeners = new CopyOnWriteArrayList<>();

        // 创建豆包客户端
        this.doubaoClient = new DoubaoWebSocketClient(properties);
        this.doubaoClient.addListener(this);
    }

    /**
     * 添加事件监听器
     */
    public void addEventListener(Consumer<VoiceSessionEvent> listener) {
        eventListeners.add(listener);
    }

    /**
     * 移除事件监听器
     */
    public void removeEventListener(Consumer<VoiceSessionEvent> listener) {
        eventListeners.remove(listener);
    }

    /**
     * 发布事件
     */
    private void publishEvent(VoiceSessionEvent event) {
        eventListeners.forEach(listener -> {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("事件处理失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 连接到豆包API
     */
    public void connect() {
        if (state != SessionState.CREATED && state != SessionState.DISCONNECTED) {
            log.warn("会话状态不允许连接: {}", state);
            return;
        }
        state = SessionState.CONNECTING;
        doubaoClient.connect();
        updateLastActive();
    }

    /**
     * 启动会话
     */
    public void startSession(SessionConfig sessionConfig) throws IOException {
        if (state != SessionState.CONNECTED) {
            throw new IllegalStateException("会话状态不允许启动: " + state);
        }
        this.config = sessionConfig;
        state = SessionState.SESSION_STARTING;

        // 构建会话配置
        Map<String, Object> configMap = buildSessionConfig(sessionConfig);
        doubaoClient.sendStartSession(configMap);
        updateLastActive();
    }

    /**
     * 结束会话
     */
    public void endSession() throws IOException {
        if (state != SessionState.SESSION_ACTIVE) {
            log.warn("会话状态不允许结束: {}", state);
            return;
        }
        state = SessionState.SESSION_ENDING;
        doubaoClient.sendFinishSession();
        updateLastActive();
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        doubaoClient.disconnect();
        state = SessionState.DISCONNECTED;
    }

    /**
     * 发送音频数据
     */
    public void sendAudio(byte[] audioData) throws IOException {
        if (state != SessionState.SESSION_ACTIVE) {
            log.warn("会话状态不允许发送音频: {}", state);
            return;
        }
        doubaoClient.sendAudio(audioData);
        updateLastActive();
    }

    /**
     * 发送文本查询
     */
    public void sendTextQuery(String text, String questionId) throws IOException {
        if (state != SessionState.SESSION_ACTIVE) {
            throw new IllegalStateException("会话状态不允许发送文本: " + state);
        }
        doubaoClient.sendTextQuery(text, questionId);
        updateLastActive();
    }

    /**
     * 更新最后活动时间
     */
    private void updateLastActive() {
        this.lastActiveAt = Instant.now();
    }

    /**
     * 构建会话配置
     */
    private Map<String, Object> buildSessionConfig(SessionConfig config) {
        Map<String, Object> result = new HashMap<>();

        // ASR配置
        Map<String, Object> asr = new HashMap<>();
        Map<String, Object> asrExtra = new HashMap<>();
        asrExtra.put("end_smooth_window_ms", config.getEndSmoothWindowMs());
        if (config.isEnableCustomVad()) {
            asrExtra.put("enable_custom_vad", true);
        }
        asr.put("extra", asrExtra);
        result.put("asr", asr);

        // TTS配置
        Map<String, Object> tts = new HashMap<>();
        tts.put("speaker", config.getSpeaker());
        Map<String, Object> audioConfig = new HashMap<>();
        audioConfig.put("channel", config.getChannel());
        audioConfig.put("format", config.getAudioFormat());
        audioConfig.put("sample_rate", config.getTtsSampleRate());
        tts.put("audio_config", audioConfig);
        result.put("tts", tts);

        // Dialog配置
        Map<String, Object> dialog = new HashMap<>();
        dialog.put("bot_name", config.getBotName());
        if (config.getSystemRole() != null && !config.getSystemRole().isEmpty()) {
            dialog.put("system_role", config.getSystemRole());
        }
        if (config.getSpeakingStyle() != null && !config.getSpeakingStyle().isEmpty()) {
            dialog.put("speaking_style", config.getSpeakingStyle());
        }
        if (dialogId != null && !dialogId.isEmpty()) {
            dialog.put("dialog_id", dialogId);
        }

        Map<String, Object> dialogExtra = new HashMap<>();
        dialogExtra.put("strict_audit", config.isStrictAudit());
        dialogExtra.put("recv_timeout", config.getRecvTimeout());
        dialogExtra.put("input_mod", "audio");
        dialogExtra.put("model", config.getModel());
        if (config.isEnableWebSearch()) {
            dialogExtra.put("enable_volc_websearch", true);
        }
        dialog.put("extra", dialogExtra);
        result.put("dialog", dialog);

        return result;
    }

    // ==================== DoubaoClientListener 实现 ====================

    @Override
    public void onConnected() {
        log.info("会话[{}] 已连接到豆包API", sessionId);
    }

    @Override
    public void onConnectionStarted() {
        state = SessionState.CONNECTED;
        log.info("会话[{}] 连接已启动", sessionId);
        publishEvent(VoiceSessionEvent.connectionStarted(sessionId));
    }

    @Override
    public void onSessionStarted(String dialogId) {
        this.dialogId = dialogId;
        state = SessionState.SESSION_ACTIVE;
        log.info("会话[{}] 已启动, dialogId={}", sessionId, dialogId);
        publishEvent(VoiceSessionEvent.sessionStarted(sessionId, dialogId));
    }

    @Override
    public void onSessionFinished() {
        state = SessionState.CONNECTED;
        log.info("会话[{}] 已结束", sessionId);
        publishEvent(VoiceSessionEvent.sessionFinished(sessionId));
    }

    @Override
    public void onSessionFailed(String error) {
        state = SessionState.ERROR;
        this.errorMessage = error;
        log.error("会话[{}] 失败: {}", sessionId, error);
        publishEvent(VoiceSessionEvent.sessionFailed(sessionId, error));
    }

    @Override
    public void onDisconnected(int code, String reason) {
        state = SessionState.DISCONNECTED;
        log.info("会话[{}] 已断开: code={}, reason={}", sessionId, code, reason);
        publishEvent(VoiceSessionEvent.disconnected(sessionId, code, reason));
    }

    @Override
    public void onError(Throwable error) {
        state = SessionState.ERROR;
        this.errorMessage = error.getMessage();
        log.error("会话[{}] 发生错误", sessionId, error);
        publishEvent(VoiceSessionEvent.error(sessionId, error.getMessage()));
    }

    @Override
    public void onMessage(DoubaoMessage message) {
        updateLastActive();
    }

    @Override
    public void onAudioData(byte[] audioData, String sessionId) {
        publishEvent(VoiceSessionEvent.audioData(this.sessionId, audioData));
    }

    @Override
    public void onUserSpeechStarted(String questionId) {
        publishEvent(VoiceSessionEvent.userSpeechStarted(sessionId, questionId));
    }

    @Override
    public void onAsrResult(String text, boolean isInterim) {
        publishEvent(VoiceSessionEvent.asrResult(sessionId, text, isInterim));
    }

    @Override
    public void onUserSpeechEnded() {
        publishEvent(VoiceSessionEvent.userSpeechEnded(sessionId));
    }

    @Override
    public void onTtsSentenceStart(String text, String ttsType, String questionId, String replyId) {
        publishEvent(VoiceSessionEvent.ttsSentenceStart(sessionId, text, ttsType, questionId, replyId));
    }

    @Override
    public void onTtsSentenceEnd(String questionId, String replyId) {
        publishEvent(VoiceSessionEvent.ttsSentenceEnd(sessionId, questionId, replyId));
    }

    @Override
    public void onTtsEnded(String questionId, String replyId) {
        publishEvent(VoiceSessionEvent.ttsEnded(sessionId, questionId, replyId));
    }

    @Override
    public void onChatResponse(String content, String questionId, String replyId) {
        publishEvent(VoiceSessionEvent.chatResponse(sessionId, content, questionId, replyId));
    }

    @Override
    public void onChatEnded(String questionId, String replyId) {
        publishEvent(VoiceSessionEvent.chatEnded(sessionId, questionId, replyId));
    }

    @Override
    public void onDialogError(String statusCode, String message) {
        publishEvent(VoiceSessionEvent.dialogError(sessionId, statusCode, message));
    }
}
