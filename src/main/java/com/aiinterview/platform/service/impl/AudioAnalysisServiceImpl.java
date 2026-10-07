package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.model.dto.response.AudioAnalysisResult;
import com.aiinterview.platform.service.AudioAnalysisService;
import com.aiinterview.platform.service.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioAnalysisServiceImpl implements AudioAnalysisService {

    private static final long MAX_AUDIO_SIZE = 8L * 1024 * 1024;
    private static final int MAX_TRANSCRIPTION_LEN = 3000;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "webm", "mp3", "mp4", "mpeg", "mpga", "m4a", "wav", "ogg", "opus");

    private final OpenAiClient openAiClient;

    @Value("${storage.audio-upload-dir:./uploads/audio}")
    private String audioUploadDir;

    @Override
    public AudioAnalysisResult analyzeAudio(byte[] audioData, String fileName, String questionText,
                                            Integer durationSeconds) {
        if (audioData == null || audioData.length == 0) {
            throw new BadRequestException("File audio trống");
        }
        if (audioData.length > MAX_AUDIO_SIZE) {
            throw new BadRequestException("File audio vượt quá giới hạn 8MB");
        }

        String safeFileName = sanitizeFileName(fileName);
        String filePath = persistAudioFile(audioData, safeFileName);

        String transcription = openAiClient.transcribeAudio(audioData, safeFileName);
        JsonNode analysis = openAiClient.chatJsonEval(buildAudioAnalysisPrompt(transcription, questionText));

        return AudioAnalysisResult.builder()
                .audioFilePath(filePath)
                .transcription(transcription)
                .eloquenceScore(numberValue(analysis, "eloquence_score"))
                .coherenceScore(numberValue(analysis, "coherence_score"))
                .durationSeconds(durationSeconds)
                .build();
    }

    private String persistAudioFile(byte[] audioData, String fileName) {
        try {
            Path target = Paths.get(audioUploadDir, fileName).toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            try (InputStream in = new java.io.ByteArrayInputStream(audioData)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target.toString();
        } catch (IOException e) {
            throw new BadRequestException("Không lưu được file audio");
        }
    }

    private String sanitizeFileName(String fileName) {
        String name = fileName == null ? "" : fileName.trim();
        String lower = name.toLowerCase();
        String ext = "";
        int dot = lower.lastIndexOf('.');
        if (dot >= 0) {
            String candidate = lower.substring(dot + 1);
            if (ALLOWED_EXTENSIONS.contains(candidate)) {
                ext = "." + candidate;
            }
        }
        String base = dot >= 0 ? name.substring(0, dot) : name;
        String safeBase = base.isBlank()
                ? "audio_" + System.currentTimeMillis()
                : base.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safeBase + "_" + System.currentTimeMillis() + (ext.isEmpty() ? ".webm" : ext);
    }

    private Double numberValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    private String buildAudioAnalysisPrompt(String transcription, String questionText) {
        String truncated = transcription.length() > MAX_TRANSCRIPTION_LEN
                ? transcription.substring(0, MAX_TRANSCRIPTION_LEN)
                : transcription;
        return """
                Analyze the following audio transcription from an interview answer.

                Transcription: {{transcription}}
                Question Context: {{question}}

                Evaluate the transcription based on:
                1. Eloquence (0-10): fluency, clarity, minimal filler words
                2. Coherence (0-10): logical flow, structured response

                Return a JSON object with exactly these fields:
                {
                  "eloquence_score": 7.5,
                  "coherence_score": 8.0,
                  "analysis": {
                    "filler_words_count": 2,
                    "avg_sentence_length": 12,
                    "has_structure": true,
                    "notes": "Short qualitative note"
                  }
                }

                STRICT: Only return this JSON object. No markdown, no extra text.
                """
                .replace("{{transcription}}", truncated)
                .replace("{{question}}", questionText == null ? "" : questionText);
    }
}