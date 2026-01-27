package com.doubao.voice.session;

import lombok.Builder;
import lombok.Data;

/**
 * 会话配置
 */
@Data
@Builder
public class SessionConfig {

    // ==================== ASR配置 ====================

    /**
     * 停止说话后延迟判定时间（毫秒）
     */
    @Builder.Default
    private int endSmoothWindowMs = 1500;

    /**
     * 是否启用自定义VAD
     */
    @Builder.Default
    private boolean enableCustomVad = false;

    // ==================== TTS配置 ====================

    /**
     * 音色
     */
    @Builder.Default
    private String speaker = "zh_female_vv_jupiter_bigtts";

    /**
     * TTS采样率
     */
    @Builder.Default
    private int ttsSampleRate = 24000;

    /**
     * 音频格式
     */
    @Builder.Default
    private String audioFormat = "pcm";

    /**
     * 声道数
     */
    @Builder.Default
    private int channel = 1;

    // ==================== Dialog配置 ====================

    /**
     * 机器人名称
     */
    @Builder.Default
    private String botName = "豆包";

    /**
     * 系统角色设定
     */
    private String systemRole;

    /**
     * 说话风格
     */
    private String speakingStyle;

    /**
     * 模型版本
     * O - 支持精品音色
     * SC - 支持声音复刻
     * 1.2.1.0 - O2.0版本
     * 2.2.0.0 - SC2.0版本
     */
    @Builder.Default
    private String model = "O";

    /**
     * 是否启用联网搜索
     */
    @Builder.Default
    private boolean enableWebSearch = false;

    /**
     * 接收超时时间（秒）
     */
    @Builder.Default
    private int recvTimeout = 10;

    /**
     * 是否严格审核
     */
    @Builder.Default
    private boolean strictAudit = false;

    /**
     * 创建默认配置
     */
    public static SessionConfig defaultConfig() {
        return SessionConfig.builder().build();
    }
}
