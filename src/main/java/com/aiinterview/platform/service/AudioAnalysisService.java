package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.response.AudioAnalysisResult;

public interface AudioAnalysisService {

    AudioAnalysisResult analyzeAudio(byte[] audioData, String fileName, String questionText, Integer durationSeconds);
}