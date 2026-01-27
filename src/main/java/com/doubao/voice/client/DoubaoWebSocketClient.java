package com.doubao.voice.client;

import com.doubao.voice.config.DoubaoProperties;
import com.doubao.voice.protocol.codec.DoubaoProtocolDecoder;
import com.doubao.voice.protocol.codec.DoubaoProtocolEncoder;
import com.doubao.voice.protocol.constants.EventType;
import com.doubao.voice.protocol.constants.MessageType;
import com.doubao.voice.protocol.message.DoubaoMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 豆包WebSocket客户端
 *
 * 负责与豆包API建立WebSocket连接，发送和接收消息
 */
@Slf4j
public class DoubaoWebSocketClient {

    private final DoubaoProperties properties;
    private final DoubaoProtocolEncoder encoder;
    private final DoubaoProtocolDecoder decoder;
    private final OkHttpClient httpClient;
    private final List<DoubaoClientListener> listeners;

    private WebSocket webSocket;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connectionStarted = new AtomicBoolean(false);

    @Getter
    private String sessionId;

    @Getter
    private String dialogId;

    public DoubaoWebSocketClient(DoubaoProperties properties) {
        this.properties = properties;
        this.encoder = new DoubaoProtocolEncoder();
        this.decoder = new DoubaoProtocolDecoder();
        this.listeners = new CopyOnWriteArrayList<>();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getApi().getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getApi().getReadTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.getApi().getWriteTimeout(), TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 添加事件监听器
     */
    public void addListener(DoubaoClientListener listener) {
        listeners.add(listener);
    }

    /**
     * 移除事件监听器
     */
    public void removeListener(DoubaoClientListener listener) {
        listeners.remove(listener);
    }

    /**
     * 连接到豆包API
     */
    public void connect() {
        if (connected.get()) {
            log.warn("已经连接，无需重复连接");
            return;
        }

        // 生成会话ID
        this.sessionId = UUID.randomUUID().toString();

        // 构建请求，添加认证头
        Request request = new Request.Builder()
                .url(properties.getApi().getUrl())
                .addHeader("X-Api-App-ID", properties.getApi().getAppId())
                .addHeader("X-Api-Access-Key", properties.getApi().getAccessKey())
                .addHeader("X-Api-Resource-Id", properties.getApi().getResourceId())
                .addHeader("X-Api-App-Key", properties.getApi().getAppKey())
                .addHeader("X-Api-Connect-Id", sessionId)
                .build();

        log.info("正在连接豆包API: {}", properties.getApi().getUrl());
        log.debug("认证信息: appId={}, resourceId={}",
                properties.getApi().getAppId(),
                properties.getApi().getResourceId());
        webSocket = httpClient.newWebSocket(request, new DoubaoWebSocketListener());
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (webSocket != null) {
            try {
                // 发送结束连接消息
                sendFinishConnection();
                Thread.sleep(100);
            } catch (Exception e) {
                log.warn("发送结束连接消息失败: {}", e.getMessage());
            }
            webSocket.close(1000, "Normal closure");
            webSocket = null;
        }
        connected.set(false);
        connectionStarted.set(false);
    }

    /**
     * 发送开始连接消息
     */
    public void sendStartConnection() throws IOException {
        byte[] data = encoder.encodeStartConnection();
        send(data);
        log.debug("发送START_CONNECTION消息");
    }

    /**
     * 发送结束连接消息
     */
    public void sendFinishConnection() throws IOException {
        byte[] data = encoder.encodeFinishConnection();
        send(data);
        log.debug("发送FINISH_CONNECTION消息");
    }

    /**
     * 发送开始会话消息
     */
    public void sendStartSession(Object sessionConfig) throws IOException {
        byte[] data = encoder.encodeStartSession(sessionId, sessionConfig);
        send(data);
        log.debug("发送START_SESSION消息, sessionId={}", sessionId);
    }

    /**
     * 发送结束会话消息
     */
    public void sendFinishSession() throws IOException {
        byte[] data = encoder.encodeFinishSession(sessionId);
        send(data);
        log.debug("发送FINISH_SESSION消息, sessionId={}", sessionId);
    }

    /**
     * 发送音频数据
     */
    public void sendAudio(byte[] audioData) throws IOException {
        byte[] data = encoder.encodeAudio(sessionId, audioData);
        send(data);
        log.trace("发送音频数据, size={}", audioData.length);
    }

    /**
     * 发送文本查询
     */
    public void sendTextQuery(String text, String questionId) throws IOException {
        byte[] data = encoder.encodeTextQuery(sessionId, text, questionId);
        send(data);
        log.debug("发送文本查询: {}", text);
    }

    /**
     * 发送原始消息
     */
    public void sendMessage(DoubaoMessage message) throws IOException {
        byte[] data = encoder.encode(message);
        send(data);
    }

    /**
     * 发送二进制数据
     */
    private void send(byte[] data) {
        if (webSocket != null && connected.get()) {
            webSocket.send(ByteString.of(data));
        } else {
            log.warn("WebSocket未连接，无法发送数据");
        }
    }

    /**
     * 判断是否已连接
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * 判断连接是否已启动（收到CONNECTION_STARTED事件）
     */
    public boolean isConnectionStarted() {
        return connectionStarted.get();
    }

    /**
     * WebSocket监听器
     */
    private class DoubaoWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
            log.info("WebSocket连接已建立");
            connected.set(true);

            // 通知监听器
            listeners.forEach(DoubaoClientListener::onConnected);

            // 发送开始连接消息
            try {
                sendStartConnection();
            } catch (IOException e) {
                log.error("发送开始连接消息失败", e);
                listeners.forEach(l -> l.onError(e));
            }
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
            try {
                byte[] data = bytes.toByteArray();
                DoubaoMessage message = decoder.decode(data);

                if (message != null) {
                    // 处理特定事件
                    handleEvent(message);

                    // 通知监听器
                    listeners.forEach(l -> l.onMessage(message));

                    // 如果是音频数据，单独通知
                    if (message.getMessageType() == MessageType.SERVER_ACK &&
                        message.getBinaryPayload() != null) {
                        byte[] audioData = message.getBinaryPayload();
                        String sid = message.getSessionId();
                        listeners.forEach(l -> l.onAudioData(audioData, sid));
                    }
                }
            } catch (Exception e) {
                log.error("处理消息失败", e);
                listeners.forEach(l -> l.onError(e));
            }
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
            log.debug("收到文本消息: {}", text);
        }

        @Override
        public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            log.info("WebSocket正在关闭: code={}, reason={}", code, reason);
        }

        @Override
        public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            log.info("WebSocket已关闭: code={}, reason={}", code, reason);
            connected.set(false);
            connectionStarted.set(false);
            listeners.forEach(l -> l.onDisconnected(code, reason));
        }

        @Override
        public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
            log.error("WebSocket连接失败: {}", t.getMessage());
            if (response != null) {
                log.error("响应状态: {} {}", response.code(), response.message());
                try {
                    if (response.body() != null) {
                        log.error("响应内容: {}", response.body().string());
                    }
                } catch (Exception e) {
                    log.trace("读取响应内容失败", e);
                }
            }
            connected.set(false);
            connectionStarted.set(false);
            listeners.forEach(l -> l.onError(t));
        }
    }

    /**
     * 处理服务端事件
     */
    @SuppressWarnings("unchecked")
    private void handleEvent(DoubaoMessage message) {
        if (message.getEventId() == null) {
            return;
        }

        int eventId = message.getEventId();
        Object payload = message.getPayload();
        Map<String, Object> payloadMap = null;
        if (payload instanceof Map) {
            payloadMap = (Map<String, Object>) payload;
        }

        switch (eventId) {
            case EventType.CONNECTION_STARTED -> {
                connectionStarted.set(true);
                log.info("连接已启动");
                listeners.forEach(DoubaoClientListener::onConnectionStarted);
            }

            case EventType.SESSION_STARTED -> {
                if (payloadMap != null) {
                    dialogId = (String) payloadMap.get("dialog_id");
                }
                log.info("会话已启动, dialogId={}", dialogId);
                listeners.forEach(l -> l.onSessionStarted(dialogId));
            }

            case EventType.SESSION_FINISHED -> {
                log.info("会话已结束");
                listeners.forEach(DoubaoClientListener::onSessionFinished);
            }

            case EventType.SESSION_FAILED -> {
                String error = payloadMap != null ? (String) payloadMap.get("error") : "Unknown error";
                log.error("会话失败: {}", error);
                listeners.forEach(l -> l.onSessionFailed(error));
            }

            case EventType.ASR_INFO -> {
                String questionId = payloadMap != null ? (String) payloadMap.get("question_id") : null;
                log.debug("用户开始说话, questionId={}", questionId);
                listeners.forEach(l -> l.onUserSpeechStarted(questionId));
            }

            case EventType.ASR_RESPONSE -> {
                if (payloadMap != null) {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) payloadMap.get("results");
                    if (results != null && !results.isEmpty()) {
                        Map<String, Object> result = results.get(0);
                        String text = (String) result.get("text");
                        Boolean isInterim = (Boolean) result.get("is_interim");
                        log.debug("ASR识别结果: text={}, isInterim={}", text, isInterim);
                        listeners.forEach(l -> l.onAsrResult(text, Boolean.TRUE.equals(isInterim)));
                    }
                }
            }

            case EventType.ASR_ENDED -> {
                log.debug("用户停止说话");
                listeners.forEach(DoubaoClientListener::onUserSpeechEnded);
            }

            case EventType.TTS_SENTENCE_START -> {
                if (payloadMap != null) {
                    String text = (String) payloadMap.get("text");
                    String ttsType = (String) payloadMap.get("tts_type");
                    String questionId = (String) payloadMap.get("question_id");
                    String replyId = (String) payloadMap.get("reply_id");
                    log.debug("TTS句子开始: text={}, type={}", text, ttsType);
                    listeners.forEach(l -> l.onTtsSentenceStart(text, ttsType, questionId, replyId));
                }
            }

            case EventType.TTS_SENTENCE_END -> {
                if (payloadMap != null) {
                    String questionId = (String) payloadMap.get("question_id");
                    String replyId = (String) payloadMap.get("reply_id");
                    log.debug("TTS句子结束");
                    listeners.forEach(l -> l.onTtsSentenceEnd(questionId, replyId));
                }
            }

            case EventType.TTS_ENDED -> {
                if (payloadMap != null) {
                    String questionId = (String) payloadMap.get("question_id");
                    String replyId = (String) payloadMap.get("reply_id");
                    log.debug("TTS播放结束");
                    listeners.forEach(l -> l.onTtsEnded(questionId, replyId));
                }
            }

            case EventType.CHAT_RESPONSE -> {
                if (payloadMap != null) {
                    String content = (String) payloadMap.get("content");
                    String questionId = (String) payloadMap.get("question_id");
                    String replyId = (String) payloadMap.get("reply_id");
                    log.debug("AI回复: {}", content);
                    listeners.forEach(l -> l.onChatResponse(content, questionId, replyId));
                }
            }

            case EventType.CHAT_ENDED -> {
                if (payloadMap != null) {
                    String questionId = (String) payloadMap.get("question_id");
                    String replyId = (String) payloadMap.get("reply_id");
                    log.debug("AI回复结束");
                    listeners.forEach(l -> l.onChatEnded(questionId, replyId));
                }
            }

            case EventType.DIALOG_COMMON_ERROR -> {
                if (payloadMap != null) {
                    String statusCode = String.valueOf(payloadMap.get("status_code"));
                    String errorMessage = (String) payloadMap.get("message");
                    log.error("对话错误: code={}, message={}", statusCode, errorMessage);
                    listeners.forEach(l -> l.onDialogError(statusCode, errorMessage));
                }
            }

            default -> log.trace("未处理的事件: {}", EventType.getName(eventId));
        }
    }
}
