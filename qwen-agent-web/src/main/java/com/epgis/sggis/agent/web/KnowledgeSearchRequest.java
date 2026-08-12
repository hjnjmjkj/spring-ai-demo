package com.epgis.sggis.agent.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeSearchRequest(
        @NotBlank(message = "检索问题不能为空")
        @Size(max = 4000, message = "检索问题不能超过 4000 个字符")
        String query,
        @Min(value = 1, message = "topK 不能小于 1")
        @Max(value = 20, message = "topK 不能大于 20")
        Integer topK,
        @DecimalMin(value = "0.0", message = "相似度阈值不能小于 0")
        @DecimalMax(value = "1.0", message = "相似度阈值不能大于 1")
        Double similarityThreshold) {

    public int effectiveTopK() {
        return topK == null ? 5 : topK;
    }

    public double effectiveSimilarityThreshold() {
        return similarityThreshold == null ? 0.65 : similarityThreshold;
    }
}
