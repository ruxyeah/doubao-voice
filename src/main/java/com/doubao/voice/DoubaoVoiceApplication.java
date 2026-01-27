package com.doubao.voice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 豆包实时语音对话API服务启动类
 *
 * 提供实时语音对话能力的REST和WebSocket API
 */
@SpringBootApplication
@EnableConfigurationProperties
@EnableAsync
@EnableScheduling
public class DoubaoVoiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoubaoVoiceApplication.class, args);
    }
}
