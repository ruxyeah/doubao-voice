package com.doubao.voice.session;

import com.doubao.voice.client.DoubaoWebSocketClient;
import com.doubao.voice.client.ReconnectListener;
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
 * - 断线自动重连
 */
@Slf4j
@Getter
public class VoiceSession implements ReconnectListener {

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

        // 应用重连配置
        DoubaoProperties.Api apiConfig = properties.getApi();
        this.doubaoClient.setAutoReconnect(apiConfig.isAutoReconnect());
        this.doubaoClient.setMaxReconnectAttempts(apiConfig.getMaxReconnectAttempts());
        this.doubaoClient.setInitialReconnectDelay(apiConfig.getInitialReconnectDelay());
        this.doubaoClient.setMaxReconnectDelay(apiConfig.getMaxReconnectDelay());
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
        if (!doubaoClient.isConnected()) {
            state = SessionState.DISCONNECTED;
            log.error("会话[{}] 豆包客户端已断开，无法发送音频", sessionId);
            publishEvent(VoiceSessionEvent.error(sessionId, "豆包客户端已断开连接"));
            return;
        }
        doubaoClient.sendAudio(audioData);
        updateLastActive();
    }

    /**
     * 发送文本查询
     */
    public void sendTextQuery(String text, String questionId) throws IOException {
        log.info("会话[{}] 准备发送文本查询: text={}, state={}, doubaoClient.connected={}, doubaoClient.connectionStarted={}",
                sessionId, text, state, doubaoClient.isConnected(), doubaoClient.isConnectionStarted());

        if (state != SessionState.SESSION_ACTIVE) {
            throw new IllegalStateException("会话状态不允许发送文本: " + state);
        }
        if (!doubaoClient.isConnected()) {
            state = SessionState.DISCONNECTED;
            throw new IOException("豆包客户端已断开连接");
        }
        if (!doubaoClient.isConnectionStarted()) {
            throw new IOException("豆包客户端连接未启动");
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
     * 获取豆包客户端连接状态
     */
    public boolean isDoubaoConnected() {
        return doubaoClient.isConnected();
    }

    /**
     * 获取豆包客户端连接启动状态
     */
    public boolean isDoubaoConnectionStarted() {
        return doubaoClient.isConnectionStarted();
    }

    /**
     * 获取豆包协议sessionId
     */
    public String getDoubaoSessionId() {
        return doubaoClient.getSessionId();
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
        SessionState previousState = state;
        state = SessionState.CONNECTED;
        log.info("会话[{}] 连接已启动, 之前状态: {}", sessionId, previousState);
        publishEvent(VoiceSessionEvent.connectionStarted(sessionId));

        // 如果是重连后的连接启动，且之前有会话配置，自动重新启动会话
        if (config != null && (previousState == SessionState.CONNECTING || previousState == SessionState.DISCONNECTED || previousState == SessionState.ERROR)) {
            log.info("会话[{}] 检测到重连，自动重新启动会话", sessionId);
            try {
                // 稍微延迟以确保连接完全建立
                Thread.sleep(100);
                Map<String, Object> configMap = buildSessionConfig(config);
                doubaoClient.sendStartSession(configMap);
                state = SessionState.SESSION_STARTING;
            } catch (Exception e) {
                log.error("会话[{}] 自动重新启动会话失败: {}", sessionId, e.getMessage(), e);
                publishEvent(VoiceSessionEvent.error(sessionId, "重连后自动启动会话失败: " + e.getMessage()));
            }
        }
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

    // ==================== ReconnectListener 实现 ====================

    @Override
    public void onReconnecting(int attempt, int maxAttempts, long delayMs) {
        log.info("会话[{}] 正在重连: 第{}/{}次，延迟{}ms", sessionId, attempt, maxAttempts, delayMs);
        state = SessionState.CONNECTING;
        publishEvent(VoiceSessionEvent.reconnecting(sessionId, attempt, maxAttempts, delayMs));
    }

    @Override
    public void onReconnected(int attempts) {
        log.info("会话[{}] 重连成功，共尝试{}次，准备重新启动会话", sessionId, attempts);
        publishEvent(VoiceSessionEvent.reconnected(sessionId, attempts));

        // 重连成功后，如果之前有会话配置，自动重新启动会话
        if (config != null) {
            try {
                // 需要等待CONNECTION_STARTED事件后才能启动会话
                // 这里只是设置状态，实际启动会话在onConnectionStarted中处理
                log.info("会话[{}] 将在连接启动后自动重新启动会话", sessionId);
            } catch (Exception e) {
                log.error("会话[{}] 准备重新启动会话失败: {}", sessionId, e.getMessage());
            }
        }
    }

    @Override
    public void onReconnectFailed(int attempts) {
        log.error("会话[{}] 重连失败，已尝试{}次", sessionId, attempts);
        state = SessionState.ERROR;
        errorMessage = "重连失败：已达到最大重连次数";
        publishEvent(VoiceSessionEvent.reconnectFailed(sessionId, attempts));
    }

    @Override
    public void onConnectionTimeout(int pendingRequests, long lastSendAgo, long lastReceiveAgo) {
        log.warn("会话[{}] 连接超时: pending={}, lastSend={}ms前, lastRecv={}ms前",
                sessionId, pendingRequests, lastSendAgo, lastReceiveAgo);
        publishEvent(VoiceSessionEvent.connectionTimeout(sessionId, pendingRequests, lastSendAgo, lastReceiveAgo));
    }

    /**
     * 手动触发重连
     */
    public void reconnect() {
        doubaoClient.reconnect();
    }

    /**
     * 是否正在重连
     */
    public boolean isReconnecting() {
        return doubaoClient.isReconnecting();
    }

    /**
     * 获取当前重连次数
     */
    public int getReconnectAttempts() {
        return doubaoClient.getReconnectAttempts();
    }

    /**
     * 关闭会话，释放资源
     */
    public void shutdown() {
        doubaoClient.shutdown();
    }
}
