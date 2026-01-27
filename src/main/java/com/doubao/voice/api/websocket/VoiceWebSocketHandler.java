package com.doubao.voice.api.websocket;

import com.doubao.voice.service.VoiceService;
import com.doubao.voice.session.SessionConfig;
import com.doubao.voice.session.SessionState;
import com.doubao.voice.session.VoiceSession;
import com.doubao.voice.session.VoiceSessionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 语音WebSocket处理器
 *
 * 处理客户端WebSocket连接，转发音频数据和事件
 *
 * 连接URL: ws://host/ws/voice?sessionId={sessionId}
 *
 * 客户端消息格式:
 * {
 *   "type": "audio|text|control",
 *   "data": "base64音频数据",
 *   "text": "文本内容",
 *   "action": "start|stop|end"
 * }
 *
 * 服务端消息格式:
 * {
 *   "type": "asr|tts|chat|status|audio|error",
 *   "text": "文本内容",
 *   "isInterim": true/false,
 *   "audioData": "base64音频数据",
 *   "status": "connected|session_started|...",
 *   "error": "错误信息"
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceWebSocketHandler extends AbstractWebSocketHandler {

    private final VoiceService voiceService;
    private final ObjectMapper objectMapper;

    /**
     * 客户端会话 -> 语音会话ID映射
     */
    private final Map<String, String> sessionMapping = new ConcurrentHashMap<>();

    /**
     * 客户端会话 -> 事件监听器映射
     */
    private final Map<String, Consumer<VoiceSessionEvent>> listenerMapping = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        String sessionId = extractSessionId(wsSession);

        if (sessionId == null || sessionId.isEmpty()) {
            // 如果没有提供sessionId，创建新会话
            sessionId = voiceService.createSession();
            voiceService.connectSession(sessionId);
            log.info("WebSocket连接已建立，创建新会话: {}", sessionId);
        } else {
            // 使用已有会话
            log.info("WebSocket连接已建立，使用已有会话: {}", sessionId);
        }

        // 保存映射关系
        sessionMapping.put(wsSession.getId(), sessionId);

        // 注册事件监听器
        Consumer<VoiceSessionEvent> listener = event -> handleVoiceSessionEvent(wsSession, event);
        listenerMapping.put(wsSession.getId(), listener);
        voiceService.addSessionListener(sessionId, listener);

        // 发送连接成功消息
        sendStatusMessage(wsSession, "connected", sessionId);

        // 设置WebSocket会话到VoiceSession
        VoiceSession voiceSession = voiceService.getSession(sessionId);
        voiceSession.setClientSession(wsSession);
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        String sessionId = sessionMapping.get(wsSession.getId());
        if (sessionId == null) {
            sendErrorMessage(wsSession, "会话未初始化");
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");

            switch (type) {
                case "audio" -> handleAudioMessage(wsSession, sessionId, payload);
                case "text" -> handleTextMessage(wsSession, sessionId, payload);
                case "control" -> handleControlMessage(wsSession, sessionId, payload);
                default -> sendErrorMessage(wsSession, "未知消息类型: " + type);
            }
        } catch (Exception e) {
            log.error("处理消息失败", e);
            sendErrorMessage(wsSession, "处理消息失败: " + e.getMessage());
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession wsSession, BinaryMessage message) throws Exception {
        String sessionId = sessionMapping.get(wsSession.getId());
        if (sessionId == null) {
            sendErrorMessage(wsSession, "会话未初始化");
            return;
        }

        // 直接发送二进制音频数据
        ByteBuffer buffer = message.getPayload();
        byte[] audioData = new byte[buffer.remaining()];
        buffer.get(audioData);

        voiceService.sendAudio(sessionId, audioData);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) throws Exception {
        String sessionId = sessionMapping.remove(wsSession.getId());
        Consumer<VoiceSessionEvent> listener = listenerMapping.remove(wsSession.getId());

        if (sessionId != null && listener != null) {
            voiceService.removeSessionListener(sessionId, listener);
            log.info("WebSocket连接已关闭: sessionId={}, status={}", sessionId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) throws Exception {
        log.error("WebSocket传输错误: {}", exception.getMessage(), exception);
        String sessionId = sessionMapping.get(wsSession.getId());
        if (sessionId != null) {
            sendErrorMessage(wsSession, "传输错误: " + exception.getMessage());
        }
    }

    /**
     * 处理音频消息
     */
    private void handleAudioMessage(WebSocketSession wsSession, String sessionId, Map<String, Object> payload) {
        String data = (String) payload.get("data");
        if (data == null || data.isEmpty()) {
            sendErrorMessage(wsSession, "音频数据为空");
            return;
        }

        try {
            byte[] audioData = Base64.getDecoder().decode(data);
            voiceService.sendAudio(sessionId, audioData);
        } catch (Exception e) {
            log.error("发送音频失败", e);
            sendErrorMessage(wsSession, "发送音频失败: " + e.getMessage());
        }
    }

    /**
     * 处理文本消息
     */
    private void handleTextMessage(WebSocketSession wsSession, String sessionId, Map<String, Object> payload) {
        String text = (String) payload.get("text");
        String questionId = (String) payload.get("questionId");

        if (text == null || text.isEmpty()) {
            sendErrorMessage(wsSession, "文本内容为空");
            return;
        }

        try {
            voiceService.sendTextQuery(sessionId, text, questionId);
        } catch (Exception e) {
            log.error("发送文本失败", e);
            sendErrorMessage(wsSession, "发送文本失败: " + e.getMessage());
        }
    }

    /**
     * 处理控制消息
     */
    @SuppressWarnings("unchecked")
    private void handleControlMessage(WebSocketSession wsSession, String sessionId, Map<String, Object> payload) {
        String action = (String) payload.get("action");

        try {
            switch (action) {
                case "start" -> {
                    // 启动会话
                    Map<String, Object> configMap = (Map<String, Object>) payload.get("config");
                    SessionConfig config = parseSessionConfig(configMap);
                    voiceService.startSession(sessionId, config);
                }
                case "end" -> {
                    // 结束会话
                    voiceService.endSession(sessionId);
                }
                case "disconnect" -> {
                    // 断开连接
                    voiceService.disconnectSession(sessionId);
                }
                default -> sendErrorMessage(wsSession, "未知控制命令: " + action);
            }
        } catch (Exception e) {
            log.error("执行控制命令失败", e);
            sendErrorMessage(wsSession, "执行控制命令失败: " + e.getMessage());
        }
    }

    /**
     * 处理语音会话事件
     */
    private void handleVoiceSessionEvent(WebSocketSession wsSession, VoiceSessionEvent event) {
        if (!wsSession.isOpen()) {
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();

            switch (event.getType()) {
                case CONNECTION_STARTED -> {
                    message.put("type", "status");
                    message.put("status", "connection_started");
                }
                case SESSION_STARTED -> {
                    message.put("type", "status");
                    message.put("status", "session_started");
                    message.put("dialogId", event.getDialogId());
                }
                case SESSION_FINISHED -> {
                    message.put("type", "status");
                    message.put("status", "session_finished");
                }
                case SESSION_FAILED -> {
                    message.put("type", "error");
                    message.put("error", event.getError());
                }
                case DISCONNECTED -> {
                    message.put("type", "status");
                    message.put("status", "disconnected");
                }
                case ERROR -> {
                    message.put("type", "error");
                    message.put("error", event.getError());
                }
                case USER_SPEECH_STARTED -> {
                    message.put("type", "asr");
                    message.put("event", "speech_started");
                    message.put("questionId", event.getQuestionId());
                }
                case ASR_RESULT -> {
                    message.put("type", "asr");
                    message.put("event", "result");
                    message.put("text", event.getText());
                    message.put("isInterim", event.getIsInterim());
                }
                case USER_SPEECH_ENDED -> {
                    message.put("type", "asr");
                    message.put("event", "speech_ended");
                }
                case TTS_SENTENCE_START -> {
                    message.put("type", "tts");
                    message.put("event", "sentence_start");
                    message.put("text", event.getText());
                    message.put("ttsType", event.getTtsType());
                    message.put("questionId", event.getQuestionId());
                    message.put("replyId", event.getReplyId());
                }
                case TTS_SENTENCE_END -> {
                    message.put("type", "tts");
                    message.put("event", "sentence_end");
                    message.put("questionId", event.getQuestionId());
                    message.put("replyId", event.getReplyId());
                }
                case TTS_ENDED -> {
                    message.put("type", "tts");
                    message.put("event", "ended");
                    message.put("questionId", event.getQuestionId());
                    message.put("replyId", event.getReplyId());
                }
                case AUDIO_DATA -> {
                    // 发送二进制音频数据
                    if (event.getAudioData() != null) {
                        wsSession.sendMessage(new BinaryMessage(event.getAudioData()));
                    }
                    return;
                }
                case CHAT_RESPONSE -> {
                    message.put("type", "chat");
                    message.put("event", "response");
                    message.put("text", event.getText());
                    message.put("questionId", event.getQuestionId());
                    message.put("replyId", event.getReplyId());
                }
                case CHAT_ENDED -> {
                    message.put("type", "chat");
                    message.put("event", "ended");
                    message.put("questionId", event.getQuestionId());
                    message.put("replyId", event.getReplyId());
                }
                case DIALOG_ERROR -> {
                    message.put("type", "error");
                    message.put("statusCode", event.getStatusCode());
                    message.put("error", event.getError());
                }
            }

            String json = objectMapper.writeValueAsString(message);
            wsSession.sendMessage(new TextMessage(json));

        } catch (Exception e) {
            log.error("发送事件消息失败", e);
        }
    }

    /**
     * 发送状态消息
     */
    private void sendStatusMessage(WebSocketSession wsSession, String status, String sessionId) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "status");
            message.put("status", status);
            message.put("sessionId", sessionId);
            String json = objectMapper.writeValueAsString(message);
            wsSession.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送状态消息失败", e);
        }
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(WebSocketSession wsSession, String error) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "error");
            message.put("error", error);
            String json = objectMapper.writeValueAsString(message);
            wsSession.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送错误消息失败", e);
        }
    }

    /**
     * 从WebSocket会话中提取sessionId参数
     */
    private String extractSessionId(WebSocketSession wsSession) {
        URI uri = wsSession.getUri();
        if (uri == null) {
            return null;
        }
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && "sessionId".equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }

    /**
     * 解析会话配置
     */
    private SessionConfig parseSessionConfig(Map<String, Object> configMap) {
        if (configMap == null) {
            return SessionConfig.defaultConfig();
        }

        SessionConfig.SessionConfigBuilder builder = SessionConfig.builder();

        if (configMap.containsKey("speaker")) {
            builder.speaker((String) configMap.get("speaker"));
        }
        if (configMap.containsKey("botName")) {
            builder.botName((String) configMap.get("botName"));
        }
        if (configMap.containsKey("systemRole")) {
            builder.systemRole((String) configMap.get("systemRole"));
        }
        if (configMap.containsKey("speakingStyle")) {
            builder.speakingStyle((String) configMap.get("speakingStyle"));
        }
        if (configMap.containsKey("model")) {
            builder.model((String) configMap.get("model"));
        }
        if (configMap.containsKey("enableWebSearch")) {
            builder.enableWebSearch((Boolean) configMap.get("enableWebSearch"));
        }

        return builder.build();
    }
}
