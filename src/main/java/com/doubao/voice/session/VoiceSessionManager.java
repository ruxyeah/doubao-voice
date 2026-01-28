package com.doubao.voice.session;

import com.doubao.voice.config.DoubaoProperties;
import com.doubao.voice.exception.DoubaoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语音会话管理器
 *
 * 负责管理所有语音会话的生命周期
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceSessionManager {

    private final DoubaoProperties properties;

    /**
     * 会话存储
     */
    private final Map<String, VoiceSession> sessions = new ConcurrentHashMap<>();

    /**
     * 创建新会话
     *
     * @return 新创建的会话
     * @throws DoubaoException 如果超过最大会话数
     */
    public VoiceSession createSession() {
        // 检查会话数限制
        if (sessions.size() >= properties.getSession().getMaxSessions()) {
            throw new DoubaoException("超过最大会话数限制: " + properties.getSession().getMaxSessions());
        }

        VoiceSession session = new VoiceSession(properties);
        sessions.put(session.getSessionId(), session);

        log.info("创建会话: {}, 当前会话数: {}", session.getSessionId(), sessions.size());
        return session;
    }

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return 会话对象
     */
    public Optional<VoiceSession> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * 获取会话，不存在则抛出异常
     *
     * @param sessionId 会话ID
     * @return 会话对象
     * @throws DoubaoException 如果会话不存在
     */
    public VoiceSession getSessionOrThrow(String sessionId) {
        return getSession(sessionId)
                .orElseThrow(() -> new DoubaoException("会话不存在: " + sessionId));
    }

    /**
     * 移除会话
     *
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId) {
        VoiceSession session = sessions.remove(sessionId);
        if (session != null) {
            try {
                // 使用shutdown方法释放所有资源
                session.shutdown();
            } catch (Exception e) {
                log.warn("关闭会话失败: {}", e.getMessage());
            }
            log.info("移除会话: {}, 当前会话数: {}", sessionId, sessions.size());
        }
    }

    /**
     * 获取所有会话
     *
     * @return 所有会话
     */
    public Collection<VoiceSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * 获取当前会话数
     *
     * @return 会话数量
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * 判断会话是否存在
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * 定时清理过期会话
     */
    @Scheduled(fixedDelayString = "${doubao.session.cleanup-interval:60000}")
    public void cleanupExpiredSessions() {
        long timeout = properties.getSession().getTimeout();
        Instant now = Instant.now();
        int cleanedCount = 0;

        for (Map.Entry<String, VoiceSession> entry : sessions.entrySet()) {
            VoiceSession session = entry.getValue();
            Duration idleDuration = Duration.between(session.getLastActiveAt(), now);

            if (idleDuration.toMillis() > timeout) {
                log.info("清理过期会话: {}, 空闲时间: {}ms", entry.getKey(), idleDuration.toMillis());
                removeSession(entry.getKey());
                cleanedCount++;
            }
        }

        if (cleanedCount > 0) {
            log.info("清理了 {} 个过期会话, 当前会话数: {}", cleanedCount, sessions.size());
        }
    }

    /**
     * 关闭所有会话
     */
    public void closeAllSessions() {
        log.info("关闭所有会话, 数量: {}", sessions.size());
        for (String sessionId : sessions.keySet()) {
            removeSession(sessionId);
        }
    }
}
