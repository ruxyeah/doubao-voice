package com.doubao.voice.service;

import com.doubao.voice.config.DoubaoProperties;
import com.doubao.voice.exception.DoubaoException;
import com.doubao.voice.session.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * 语音服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceServiceImpl implements VoiceService {

    private final VoiceSessionManager sessionManager;
    private final DoubaoProperties properties;

    @Override
    public String createSession() {
        VoiceSession session = sessionManager.createSession();
        return session.getSessionId();
    }

    @Override
    public void connectSession(String sessionId) {
        VoiceSession session = sessionManager.getSessionOrThrow(sessionId);
        session.connect();
        log.info("会话[{}]开始连接", sessionId);
    }

    @Override
    public void startSession(String sessionId, SessionConfig config) {
        VoiceSession session = sessionManager.getSessionOrThrow(sessionId);

        // 如果配置为空，使用默认配置
        if (config == null) {
            config = buildDefaultConfig();
        }

        try {
            session.startSession(config);
            log.info("会话[{}]启动中", sessionId);
        } catch (IOException e) {
            throw new DoubaoException("启动会话失败", e);
        }
    }

    @Override
    public void endSession(String sessionId) {
        VoiceSession session = sessionManager.getSessionOrThrow(sessionId);
        try {
            session.endSession();
            log.info("会话[{}]结束中", sessionId);
        } catch (IOException e) {
            throw new DoubaoException("结束会话失败", e);
        }
    }

    @Override
    public void disconnectSession(String sessionId) {
        VoiceSession session = sessionManager.getSessionOrThrow(sessionId);
        session.disconnect();
        log.info("会话[{}]已断开", sessionId);
    }

    @Override
    public void deleteSession(String sessionId) {
        sessionManager.removeSession(sessionId);
        log.info("会话[{}]已删除", sessionId);
    }

    @Override
    public void sendAudio(String sessionId, byte[] audioData) {
        VoiceSession session = sessionManager.getSessionOrThrow(sessionId);
        try {
            session.sendAudio(audioData);
        } catch (IOException e) {
            throw new DoubaoException("发送音频失败", e);
        }
    }

    @Override
    public void sendTextQuery(String sessionId, String text, String questionId) {
        VoiceSession session = sessionManager.getSessionOrThrow(sessionId);
        try {
            session.sendTextQuery(text, questionId);
            log.debug("会话[{}]发送文本查询: {}", sessionId, text);
        } catch (IOException e) {
            throw new DoubaoException("发送文本查询失败", e);
        }
    }

    @Override
    public SessionState getSessionState(String sessionId) {
        VoiceSession session = sessionManager.getSessionOrThrow(sessionId);
        return session.getState();
    }

    @Override
    public VoiceSession getSession(String sessionId) {
        return sessionManager.getSessionOrThrow(sessionId);
    }

    @Override
    public void addSessionListener(String sessionId, Consumer<VoiceSessionEvent> listener) {
        VoiceSession session = sessionManager.getSessionOrThrow(sessionId);
        session.addEventListener(listener);
    }

    @Override
    public void removeSessionListener(String sessionId, Consumer<VoiceSessionEvent> listener) {
        VoiceSession session = sessionManager.getSessionOrThrow(sessionId);
        session.removeEventListener(listener);
    }

    @Override
    public int getSessionCount() {
        return sessionManager.getSessionCount();
    }

    /**
     * 构建默认会话配置
     */
    private SessionConfig buildDefaultConfig() {
        DoubaoProperties.Tts tts = properties.getTts();
        DoubaoProperties.Asr asr = properties.getAsr();
        DoubaoProperties.Dialog dialog = properties.getDialog();

        return SessionConfig.builder()
                // ASR配置
                .endSmoothWindowMs(asr.getEndSmoothWindowMs())
                .enableCustomVad(asr.isEnableCustomVad())
                // TTS配置
                .speaker(tts.getDefaultSpeaker())
                .ttsSampleRate(tts.getSampleRate())
                .audioFormat(tts.getFormat())
                .channel(tts.getChannel())
                // Dialog配置
                .botName(dialog.getDefaultBotName())
                .systemRole(dialog.getDefaultSystemRole())
                .speakingStyle(dialog.getDefaultSpeakingStyle())
                .model(dialog.getDefaultModel())
                .enableWebSearch(dialog.isEnableWebSearch())
                .recvTimeout(dialog.getRecvTimeout())
                .strictAudit(dialog.isStrictAudit())
                .build();
    }
}
