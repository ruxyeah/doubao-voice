package com.doubao.voice.api.rest;

import com.doubao.voice.api.rest.dto.SessionRequest;
import com.doubao.voice.api.rest.dto.SessionResponse;
import com.doubao.voice.api.rest.dto.TextQueryRequest;
import com.doubao.voice.service.VoiceService;
import com.doubao.voice.session.VoiceSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 语音服务REST控制器
 *
 * 提供会话管理和文本对话的HTTP接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceService voiceService;

    /**
     * 创建语音会话
     *
     * POST /api/v1/voice/sessions
     */
    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> createSession(@RequestBody(required = false) SessionRequest request) {
        log.info("创建语音会话请求");

        // 1. 创建会话
        String sessionId = voiceService.createSession();

        // 2. 连接到豆包API
        voiceService.connectSession(sessionId);

        // 3. 获取会话信息
        VoiceSession session = voiceService.getSession(sessionId);

        SessionResponse response = buildSessionResponse(session);
        return ResponseEntity.ok(response);
    }

    /**
     * 启动语音会话
     *
     * POST /api/v1/voice/sessions/{sessionId}/start
     */
    @PostMapping("/sessions/{sessionId}/start")
    public ResponseEntity<SessionResponse> startSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) SessionRequest request) {
        log.info("启动语音会话: {}", sessionId);

        voiceService.startSession(sessionId, request != null ? request.toSessionConfig() : null);

        VoiceSession session = voiceService.getSession(sessionId);
        return ResponseEntity.ok(buildSessionResponse(session));
    }

    /**
     * 获取会话状态
     *
     * GET /api/v1/voice/sessions/{sessionId}
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable String sessionId) {
        log.debug("获取会话状态: {}", sessionId);

        VoiceSession session = voiceService.getSession(sessionId);
        return ResponseEntity.ok(buildSessionResponse(session));
    }

    /**
     * 结束语音会话
     *
     * POST /api/v1/voice/sessions/{sessionId}/end
     */
    @PostMapping("/sessions/{sessionId}/end")
    public ResponseEntity<SessionResponse> endSession(@PathVariable String sessionId) {
        log.info("结束语音会话: {}", sessionId);

        voiceService.endSession(sessionId);

        VoiceSession session = voiceService.getSession(sessionId);
        return ResponseEntity.ok(buildSessionResponse(session));
    }

    /**
     * 断开会话连接
     *
     * POST /api/v1/voice/sessions/{sessionId}/disconnect
     */
    @PostMapping("/sessions/{sessionId}/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectSession(@PathVariable String sessionId) {
        log.info("断开会话连接: {}", sessionId);

        voiceService.disconnectSession(sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("message", "会话已断开");
        return ResponseEntity.ok(response);
    }

    /**
     * 删除会话
     *
     * DELETE /api/v1/voice/sessions/{sessionId}
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        log.info("删除会话: {}", sessionId);

        voiceService.deleteSession(sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("message", "会话已删除");
        return ResponseEntity.ok(response);
    }

    /**
     * 发送文本查询
     *
     * POST /api/v1/voice/sessions/{sessionId}/text
     */
    @PostMapping("/sessions/{sessionId}/text")
    public ResponseEntity<Map<String, Object>> sendTextQuery(
            @PathVariable String sessionId,
            @Valid @RequestBody TextQueryRequest request) {
        log.info("发送文本查询: sessionId={}, text={}", sessionId, request.getText());

        voiceService.sendTextQuery(sessionId, request.getText(), request.getQuestionId());

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("text", request.getText());
        response.put("questionId", request.getQuestionId());
        response.put("message", "文本查询已发送");
        return ResponseEntity.ok(response);
    }

    /**
     * 获取服务状态
     *
     * GET /api/v1/voice/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "running");
        status.put("sessionCount", voiceService.getSessionCount());
        return ResponseEntity.ok(status);
    }

    /**
     * 构建会话响应
     */
    private SessionResponse buildSessionResponse(VoiceSession session) {
        return SessionResponse.builder()
                .sessionId(session.getSessionId())
                .dialogId(session.getDialogId())
                .state(session.getState())
                .stateDescription(session.getState().getDescription())
                .createdAt(session.getCreatedAt())
                .lastActiveAt(session.getLastActiveAt())
                .errorMessage(session.getErrorMessage())
                // 诊断信息
                .doubaoConnected(session.isDoubaoConnected())
                .doubaoConnectionStarted(session.isDoubaoConnectionStarted())
                .doubaoSessionId(session.getDoubaoSessionId())
                .build();
    }
}
