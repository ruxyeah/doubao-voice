package com.doubao.voice.client;

import com.doubao.voice.config.DoubaoProperties;
import com.doubao.voice.protocol.codec.DoubaoProtocolDecoder;
import com.doubao.voice.protocol.codec.DoubaoProtocolEncoder;
import com.doubao.voice.protocol.constants.EventType;
import com.doubao.voice.protocol.constants.MessageType;
import com.doubao.voice.protocol.message.DoubaoMessage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 豆包WebSocket客户端
 *
 * 负责与豆包API建立WebSocket连接，发送和接收消息
 * 支持断线自动重连
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

    // ========== 重连相关字段 ==========

    /**
     * 是否启用自动重连
     */
    @Setter
    @Getter
    private boolean autoReconnect = true;

    /**
     * 最大重连次数
     */
    @Setter
    private int maxReconnectAttempts = 5;

    /**
     * 初始重连延迟（毫秒）
     */
    @Setter
    private long initialReconnectDelay = 1000;

    /**
     * 最大重连延迟（毫秒）
     */
    @Setter
    private long maxReconnectDelay = 30000;

    /**
     * 当前重连次数
     */
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    /**
     * 是否正在重连中
     */
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    /**
     * 是否手动断开（手动断开不触发重连）
     */
    private final AtomicBoolean manualDisconnect = new AtomicBoolean(false);

    /**
     * 重连调度器
     */
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "doubao-reconnect");
        t.setDaemon(true);
        return t;
    });

    /**
     * 当前重连任务
     */
    private ScheduledFuture<?> reconnectTask;

    // ========== 连接健康检测相关字段 ==========

    /**
     * 最后发送消息的时间戳（毫秒）
     */
    private final AtomicLong lastSendTime = new AtomicLong(0);

    /**
     * 最后接收消息的时间戳（毫秒）
     */
    private final AtomicLong lastReceiveTime = new AtomicLong(0);

    /**
     * 等待响应的请求数
     */
    private final AtomicInteger pendingRequests = new AtomicInteger(0);

    /**
     * 健康检查调度器
     */
    private final ScheduledExecutorService healthCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "doubao-health-check");
        t.setDaemon(true);
        return t;
    });

    /**
     * 健康检查任务
     */
    private ScheduledFuture<?> healthCheckTask;

    /**
     * 连接空闲超时时间（毫秒）- 超过此时间没有收到任何消息则主动重连
     * 设置为90秒，比服务端的recv_timeout（120秒）稍短，确保在服务端超时前重连
     */
    private static final long CONNECTION_IDLE_TIMEOUT = 90000; // 90秒

    /**
     * 请求响应超时时间（毫秒）- 发送请求后超过此时间没收到响应
     */
    private static final long REQUEST_RESPONSE_TIMEOUT = 30000; // 30秒

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
        doConnect(false);
    }

    /**
     * 执行连接
     *
     * @param isReconnect 是否是重连
     */
    private void doConnect(boolean isReconnect) {
        if (connected.get()) {
            log.warn("已经连接，无需重复连接");
            return;
        }

        // 重连时不重新生成sessionId，保持对话连续性
        if (!isReconnect || this.sessionId == null) {
            this.sessionId = UUID.randomUUID().toString();
        }

        // 重置手动断开标志
        manualDisconnect.set(false);

        // 构建请求，添加认证头
        Request request = new Request.Builder()
                .url(properties.getApi().getUrl())
                .addHeader("X-Api-App-ID", properties.getApi().getAppId())
                .addHeader("X-Api-Access-Key", properties.getApi().getAccessKey())
                .addHeader("X-Api-Resource-Id", properties.getApi().getResourceId())
                .addHeader("X-Api-App-Key", properties.getApi().getAppKey())
                .addHeader("X-Api-Connect-Id", sessionId)
                .build();

        log.info("{}豆包API: {}, sessionId={}",
                isReconnect ? "重连" : "连接",
                properties.getApi().getUrl(),
                sessionId);
        log.debug("认证信息: appId={}, resourceId={}",
                properties.getApi().getAppId(),
                properties.getApi().getResourceId());
        webSocket = httpClient.newWebSocket(request, new DoubaoWebSocketListener());
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        // 标记为手动断开，不触发重连
        manualDisconnect.set(true);
        cancelReconnect();
        stopHealthCheck();

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
        reconnectAttempts.set(0);
        pendingRequests.set(0);
    }

    /**
     * 取消重连任务
     */
    private void cancelReconnect() {
        if (reconnectTask != null && !reconnectTask.isDone()) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
        reconnecting.set(false);
    }

    /**
     * 调度重连
     */
    private void scheduleReconnect() {
        if (!autoReconnect || manualDisconnect.get()) {
            log.debug("自动重连已禁用或手动断开，不进行重连");
            return;
        }

        if (reconnecting.getAndSet(true)) {
            log.debug("已在重连中，跳过");
            return;
        }

        int attempts = reconnectAttempts.incrementAndGet();
        if (attempts > maxReconnectAttempts) {
            log.error("已达到最大重连次数({})，停止重连", maxReconnectAttempts);
            reconnecting.set(false);
            listeners.forEach(l -> l.onError(new IOException("重连失败：已达到最大重连次数")));
            return;
        }

        // 计算延迟（指数退避）
        long delay = Math.min(initialReconnectDelay * (1L << (attempts - 1)), maxReconnectDelay);
        log.info("将在 {}ms 后进行第 {}/{} 次重连", delay, attempts, maxReconnectAttempts);

        // 通知监听器重连开始
        listeners.forEach(l -> {
            if (l instanceof ReconnectListener rl) {
                rl.onReconnecting(attempts, maxReconnectAttempts, delay);
            }
        });

        reconnectTask = reconnectScheduler.schedule(() -> {
            reconnecting.set(false);
            log.info("开始第 {}/{} 次重连", attempts, maxReconnectAttempts);
            doConnect(true);
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 重连成功后的处理
     */
    private void onReconnectSuccess() {
        int attempts = reconnectAttempts.getAndSet(0);
        if (attempts > 0) {
            log.info("重连成功，已重置重连计数器");
            listeners.forEach(l -> {
                if (l instanceof ReconnectListener rl) {
                    rl.onReconnected(attempts);
                }
            });
        }
    }

    /**
     * 获取当前重连次数
     */
    public int getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    /**
     * 是否正在重连
     */
    public boolean isReconnecting() {
        return reconnecting.get();
    }

    /**
     * 手动触发重连
     */
    public void reconnect() {
        if (connected.get()) {
            log.warn("当前已连接，无需重连");
            return;
        }
        manualDisconnect.set(false);
        reconnectAttempts.set(0);
        doConnect(true);
    }

    /**
     * 关闭客户端，释放资源
     */
    public void shutdown() {
        disconnect();
        stopHealthCheck();
        reconnectScheduler.shutdown();
        healthCheckScheduler.shutdown();
        try {
            if (!reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                reconnectScheduler.shutdownNow();
            }
            if (!healthCheckScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                healthCheckScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            reconnectScheduler.shutdownNow();
            healthCheckScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ========== 连接健康检测方法 ==========

    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        stopHealthCheck();
        healthCheckTask = healthCheckScheduler.scheduleWithFixedDelay(
                this::checkConnectionHealth,
                10000, // 首次延迟10秒
                10000, // 每10秒检查一次
                TimeUnit.MILLISECONDS
        );
        log.debug("连接健康检查已启动");
    }

    /**
     * 停止健康检查
     */
    private void stopHealthCheck() {
        if (healthCheckTask != null && !healthCheckTask.isDone()) {
            healthCheckTask.cancel(false);
            healthCheckTask = null;
        }
    }

    /**
     * 检查连接健康状态
     */
    private void checkConnectionHealth() {
        if (!connected.get() || manualDisconnect.get()) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastRecv = lastReceiveTime.get();
        long lastSend = lastSendTime.get();
        int pending = pendingRequests.get();

        long lastRecvAgo = lastRecv > 0 ? now - lastRecv : -1;
        long lastSendAgo = lastSend > 0 ? now - lastSend : -1;

        // 检查连接空闲超时 - 即使没有pending请求，也要检查
        // 豆包API会话可能在空闲后超时，需要主动重连
        if (lastRecv > 0 && lastRecvAgo > CONNECTION_IDLE_TIMEOUT) {
            log.warn("连接空闲超时！最后接收消息: {}ms前，触发预防性重连", lastRecvAgo);

            // 通知监听器连接超时
            final long finalLastSendAgo = lastSendAgo;
            final long finalLastRecvAgo = lastRecvAgo;
            listeners.forEach(l -> {
                if (l instanceof ReconnectListener rl) {
                    rl.onConnectionTimeout(pending, finalLastSendAgo, finalLastRecvAgo);
                }
            });

            // 主动重连
            if (autoReconnect && !manualDisconnect.get()) {
                log.info("空闲超时，主动重连以保持会话活跃");
                connected.set(false);
                connectionStarted.set(false);
                pendingRequests.set(0);
                // 关闭当前连接
                if (webSocket != null) {
                    webSocket.close(1000, "Idle timeout reconnect");
                }
                scheduleReconnect();
            }
            return;
        }

        // 检查请求响应超时
        if (pending > 0 && lastSend > 0 && lastSendAgo > REQUEST_RESPONSE_TIMEOUT) {
            log.error("请求响应超时！pending={}, 最后发送: {}ms前, 最后接收: {}ms前",
                    pending, lastSendAgo, lastRecvAgo > 0 ? lastRecvAgo : "无");

            // 通知监听器连接超时
            final long finalLastSendAgo = lastSendAgo;
            final long finalLastRecvAgo = lastRecvAgo;
            listeners.forEach(l -> {
                if (l instanceof ReconnectListener rl) {
                    rl.onConnectionTimeout(pending, finalLastSendAgo, finalLastRecvAgo);
                }
            });

            // 可能是静默断连，触发重连
            if (autoReconnect && !manualDisconnect.get()) {
                log.warn("检测到可能的静默断连，准备重连");
                connected.set(false);
                connectionStarted.set(false);
                pendingRequests.set(0);
                scheduleReconnect();
            }
            return;
        }

        // 定期输出连接状态（每次都输出，便于调试）
        log.info("连接健康检查: connected={}, started={}, pending={}, 空闲{}秒",
                connected.get(), connectionStarted.get(), pending,
                lastRecvAgo > 0 ? lastRecvAgo / 1000 : 0);
    }

    /**
     * 获取最后接收消息的时间
     */
    public long getLastReceiveTime() {
        return lastReceiveTime.get();
    }

    /**
     * 获取最后发送消息的时间
     */
    public long getLastSendTime() {
        return lastSendTime.get();
    }

    /**
     * 获取等待响应的请求数
     */
    public int getPendingRequests() {
        return pendingRequests.get();
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
        log.info("准备发送文本查询: text={}, questionId={}, sessionId={}, connected={}, connectionStarted={}, webSocket={}",
                text, questionId, sessionId, connected.get(), connectionStarted.get(),
                webSocket != null ? "exists" : "null");

        if (!connected.get()) {
            log.error("WebSocket未连接，尝试重连...");
            if (autoReconnect && !manualDisconnect.get()) {
                scheduleReconnect();
            }
            throw new IOException("WebSocket未连接，无法发送文本查询");
        }
        if (!connectionStarted.get()) {
            throw new IOException("连接未启动，无法发送文本查询");
        }

        byte[] data = encoder.encodeTextQuery(sessionId, text, questionId);
        sendWithTracking(data, true); // 文本查询需要追踪响应
        log.info("文本查询已发送: text={}, dataSize={}, pendingRequests={}", text, data.length, pendingRequests.get());
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
        sendWithTracking(data, false);
    }

    /**
     * 发送二进制数据（带追踪）
     *
     * @param data        要发送的数据
     * @param trackResponse 是否追踪响应（等待服务端回复的请求）
     */
    private void sendWithTracking(byte[] data, boolean trackResponse) {
        if (webSocket != null && connected.get()) {
            lastSendTime.set(System.currentTimeMillis());
            if (trackResponse) {
                pendingRequests.incrementAndGet();
            }

            boolean success = webSocket.send(ByteString.of(data));
            if (!success) {
                log.error("WebSocket发送失败，连接可能已断开，当前状态: connected={}", connected.get());
                if (trackResponse) {
                    pendingRequests.decrementAndGet();
                }
                // 标记连接已断开
                connected.set(false);
                connectionStarted.set(false);
                listeners.forEach(l -> l.onError(new IOException("WebSocket发送失败，连接已断开")));
            }
        } else {
            log.warn("WebSocket未连接，无法发送数据, webSocket={}, connected={}",
                    webSocket != null ? "exists" : "null", connected.get());
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
            lastReceiveTime.set(System.currentTimeMillis());
            pendingRequests.set(0);

            // 启动健康检查
            startHealthCheck();

            // 处理重连成功
            onReconnectSuccess();

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
            // 更新最后接收时间
            lastReceiveTime.set(System.currentTimeMillis());

            try {
                byte[] data = bytes.toByteArray();
                log.debug("收到WebSocket消息，大小: {} 字节", data.length);
                DoubaoMessage message = decoder.decode(data);

                if (message != null) {
                    log.debug("解码消息: type={}, event={}, sessionId={}",
                            message.getMessageTypeName(),
                            message.getEventName(),
                            message.getSessionId());

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
                } else {
                    log.warn("消息解码返回null");
                }
            } catch (Exception e) {
                log.error("处理消息失败: {}", e.getMessage(), e);
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
            stopHealthCheck();
            listeners.forEach(l -> l.onDisconnected(code, reason));

            // 非正常关闭时触发重连（1000是正常关闭码）
            if (code != 1000 && !manualDisconnect.get()) {
                log.info("检测到非正常断开，准备重连");
                scheduleReconnect();
            }
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
            stopHealthCheck();
            listeners.forEach(l -> l.onError(t));

            // 触发重连
            if (!manualDisconnect.get()) {
                log.info("连接失败，准备重连");
                scheduleReconnect();
            }
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

            case EventType.USAGE_RESPONSE -> {
                log.info("收到用量响应: {}", payloadMap);
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

            case EventType.TTS_RESPONSE -> {
                // TTS音频数据响应 - 音频数据在binaryPayload中
                log.trace("收到TTS_RESPONSE音频数据");
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

            case EventType.CHAT_TEXT_QUERY_CONFIRMED -> {
                if (payloadMap != null) {
                    String questionId = (String) payloadMap.get("question_id");
                    log.info("文本查询已确认, questionId={}", questionId);
                    // 收到确认，说明请求已被处理
                }
            }

            case EventType.CHAT_ENDED -> {
                if (payloadMap != null) {
                    String questionId = (String) payloadMap.get("question_id");
                    String replyId = (String) payloadMap.get("reply_id");
                    log.debug("AI回复结束");
                    // AI回复结束，减少pending计数
                    int remaining = pendingRequests.decrementAndGet();
                    if (remaining < 0) {
                        pendingRequests.set(0);
                    }
                    log.debug("pendingRequests减少，当前值: {}", pendingRequests.get());
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

            default -> log.debug("未处理的事件: eventId={}, name={}, payload={}",
                    eventId, EventType.getName(eventId), payloadMap);
        }
    }
}
