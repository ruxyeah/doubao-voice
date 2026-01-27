package com.doubao.voice.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文本查询请求DTO
 */
@Data
public class TextQueryRequest {

    /**
     * 文本内容
     */
    @NotBlank(message = "文本内容不能为空")
    private String text;

    /**
     * 问题ID（可选，用于追踪）
     */
    private String questionId;
}
