package com.doubao.voice.api.rest.dto;

import com.doubao.voice.session.SessionConfig;
import lombok.Data;

/**
 * 创建/启动会话请求DTO
 */
@Data
public class SessionRequest {

    /**
     * 音色
     */
    private String speaker;

    /**
     * 机器人名称
     */
    private String botName;

    /**
     * 系统角色设定
     */
    private String systemRole;

    /**
     * 说话风格
     */
    private String speakingStyle;

    /**
     * 模型版本（O, SC, 1.2.1.0, 2.2.0.0）
     */
    private String model;

    /**
     * 是否启用联网搜索
     */
    private Boolean enableWebSearch;

    /**
     * 停止说话后延迟判定时间（毫秒）
     */
    private Integer endSmoothWindowMs;

    /**
     * TTS采样率
     */
    private Integer ttsSampleRate;

    /**
     * 音频格式
     */
    private String audioFormat;

    /**
     * 是否严格审核
     */
    private Boolean strictAudit;

    /**
     * 对话ID（用于续接对话）
     */
    private String dialogId;

    /**
     * 转换为SessionConfig
     */
    public SessionConfig toSessionConfig() {
        SessionConfig.SessionConfigBuilder builder = SessionConfig.builder();

        if (speaker != null) {
            builder.speaker(speaker);
        }
        if (botName != null) {
            builder.botName(botName);
        }
        if (systemRole != null) {
            builder.systemRole(systemRole);
        }
        if (speakingStyle != null) {
            builder.speakingStyle(speakingStyle);
        }
        if (model != null) {
            builder.model(model);
        }
        if (enableWebSearch != null) {
            builder.enableWebSearch(enableWebSearch);
        }
        if (endSmoothWindowMs != null) {
            builder.endSmoothWindowMs(endSmoothWindowMs);
        }
        if (ttsSampleRate != null) {
            builder.ttsSampleRate(ttsSampleRate);
        }
        if (audioFormat != null) {
            builder.audioFormat(audioFormat);
        }
        if (strictAudit != null) {
            builder.strictAudit(strictAudit);
        }

        return builder.build();
    }
}
