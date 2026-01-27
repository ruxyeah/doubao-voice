package com.doubao.voice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 豆包API配置属性
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "doubao")
public class DoubaoProperties {

    /**
     * API配置
     */
    private Api api = new Api();

    /**
     * 会话配置
     */
    private Session session = new Session();

    /**
     * TTS配置
     */
    private Tts tts = new Tts();

    /**
     * ASR配置
     */
    private Asr asr = new Asr();

    /**
     * 对话配置
     */
    private Dialog dialog = new Dialog();

    @Data
    public static class Api {
        /**
         * 豆包WebSocket API地址
         */
        private String url = "wss://openspeech.bytedance.com/api/v3/realtime/dialogue";

        /**
         * 应用ID
         */
        @NotBlank(message = "appId不能为空")
        private String appId;

        /**
         * 访问密钥
         */
        @NotBlank(message = "accessKey不能为空")
        private String accessKey;

        /**
         * 资源ID
         */
        private String resourceId = "volc.speech.dialog";

        /**
         * 应用密钥（固定值）
         */
        private String appKey = "PlgvMymc7f3tQnJ6";

        /**
         * 连接超时时间（毫秒）
         */
        private int connectTimeout = 10000;

        /**
         * 读取超时时间（毫秒）
         */
        private int readTimeout = 30000;

        /**
         * 写入超时时间（毫秒）
         */
        private int writeTimeout = 10000;
    }

    @Data
    public static class Session {
        /**
         * 会话超时时间（毫秒）
         */
        private long timeout = 300000;

        /**
         * 最大并发会话数
         */
        private int maxSessions = 100;

        /**
         * 会话清理间隔（毫秒）
         */
        private long cleanupInterval = 60000;
    }

    @Data
    public static class Tts {
        /**
         * 默认音色
         */
        private String defaultSpeaker = "zh_female_vv_jupiter_bigtts";

        /**
         * 采样率
         */
        private int sampleRate = 24000;

        /**
         * 音频格式
         */
        private String format = "pcm";

        /**
         * 声道数
         */
        private int channel = 1;
    }

    @Data
    public static class Asr {
        /**
         * 采样率
         */
        private int sampleRate = 16000;

        /**
         * 停止说话后延迟判定时间（毫秒）
         */
        private int endSmoothWindowMs = 1500;

        /**
         * 是否启用自定义VAD
         */
        private boolean enableCustomVad = false;

        /**
         * 是否启用ASR二次校验
         */
        private boolean enableAsrTwopass = false;
    }

    @Data
    public static class Dialog {
        /**
         * 默认机器人名称
         */
        private String defaultBotName = "豆包";

        /**
         * 默认系统角色
         */
        private String defaultSystemRole = "";

        /**
         * 默认说话风格
         */
        private String defaultSpeakingStyle = "";

        /**
         * 默认模型版本
         * O - 支持精品音色
         * SC - 支持声音复刻
         * 1.2.1.0 - O2.0版本
         * 2.2.0.0 - SC2.0版本
         */
        private String defaultModel = "O";

        /**
         * 是否启用联网搜索
         */
        private boolean enableWebSearch = false;

        /**
         * 接收超时时间（秒）
         */
        private int recvTimeout = 10;

        /**
         * 是否严格审核
         */
        private boolean strictAudit = false;
    }
}
