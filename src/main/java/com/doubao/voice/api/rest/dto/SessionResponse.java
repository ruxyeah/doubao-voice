package com.doubao.voice.api.rest.dto;

import com.doubao.voice.session.SessionState;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 会话响应DTO
 */
@Data
@Builder
public class SessionResponse {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 对话ID（用于续接对话）
     */
    private String dialogId;

    /**
     * 会话状态
     */
    private SessionState state;

    /**
     * 状态描述
     */
    private String stateDescription;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 最后活动时间
     */
    private Instant lastActiveAt;

    /**
     * 错误信息（如有）
     */
    private String errorMessage;
}
