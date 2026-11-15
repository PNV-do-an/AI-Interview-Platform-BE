package com.aiinterview.platform.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioAnalysisResult {

    private String transcription;
    private Double eloquenceScore;
    private Double coherenceScore;
    private Integer durationSeconds;
    private String detectedLanguage;
    private String audioFilePath;
}