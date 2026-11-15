package com.aiinterview.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AIConfig {

    private String provider = "openai";
    private String apiKey;
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-4o-mini";
    private String evalModel = "gpt-4o-mini";
    private String whisperModel = "whisper-1";
    private String ttsModel = "tts-1";
    private String ttsVoice = "alloy";
    private int maxTokens = 4096;
    private double temperature = 0.3;
}