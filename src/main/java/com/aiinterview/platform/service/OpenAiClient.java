package com.aiinterview.platform.service;

import com.aiinterview.platform.common.exception.AIServiceException;
import com.aiinterview.platform.config.AIConfig;
import com.aiinterview.platform.model.dto.response.CvParseResponse;
import com.aiinterview.platform.model.enums.ProficiencyLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OpenAiClient {

    private static final int MAX_ATTEMPTS = 2;

    private final AIConfig aiConfig;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public OpenAiClient(AIConfig aiConfig, ObjectMapper objectMapper) {
        this.aiConfig = aiConfig;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(60_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public CvParseResponse parseCv(String cvText) {
        if (cvText == null || cvText.isBlank()) {
            throw new AIServiceException("CV text trống, không thể phân tích");
        }

        String content = chat(buildCvAnalysisPrompt(cvText));
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return parseAndMap(content);
            } catch (JsonProcessingException e) {
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("AI CV response không phải JSON hợp lệ (attempt {}). Retry...", attempt);
                    content = chat(buildCvRetryPrompt(cvText));
                } else {
                    log.error("AI CV parsing thất bại sau {} lần thử. Raw content: {}", MAX_ATTEMPTS, content);
                    throw new AIServiceException("AI trả dữ liệu không hợp lệ khi phân tích CV", e);
                }
            } catch (AIServiceException e) {
                throw e;
            } catch (Exception e) {
                if (attempt < MAX_ATTEMPTS) {
                    content = chat(buildCvRetryPrompt(cvText));
                } else {
                    log.error("AI CV parsing thất bại sau {} lần thử. Raw content: {}", MAX_ATTEMPTS, content);
                    throw new AIServiceException("AI trả dữ liệu không hợp lệ khi phân tích CV", e);
                }
            }
        }
        throw new AIServiceException("AI trả dữ liệu không hợp lệ khi phân tích CV");
    }

    public String chat(String userPrompt) {
        return chat(userPrompt, aiConfig.getModel());
    }

    public String chat(String userPrompt, String model) {
        assertApiKeyConfigured();
        if (isGemini()) {
            return geminiGenerateContent(userPrompt, model);
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", userPrompt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("max_tokens", aiConfig.getMaxTokens());
        body.put("temperature", aiConfig.getTemperature());
        body.put("response_format", Map.of("type", "json_object"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiConfig.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    aiConfig.getBaseUrl() + "/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class);
            return extractContent(response.getBody());
        } catch (RestClientException e) {
            throw new AIServiceException("Lỗi khi gọi OpenAI: " + e.getMessage(), e);
        }
    }

    public String chatEval(String userPrompt) {
        return chat(userPrompt, aiConfig.getEvalModel());
    }

    public JsonNode chatJson(String userPrompt) {
        return chatJson(userPrompt, aiConfig.getModel());
    }

    public JsonNode chatJson(String userPrompt, String model) {
        String content = chat(userPrompt, model);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return objectMapper.readTree(stripCodeFences(content));
            } catch (JsonProcessingException e) {
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("AI JSON response không hợp lệ (attempt {}). Retry...", attempt);
                    content = chat(userPrompt, model);
                } else {
                    log.error("AI JSON parsing thất bại sau {} lần thử. Raw content: {}", MAX_ATTEMPTS, content);
                    throw new AIServiceException("AI trả dữ liệu không hợp lệ", e);
                }
            }
        }
        throw new AIServiceException("AI trả dữ liệu không hợp lệ");
    }

    public JsonNode chatJsonEval(String userPrompt) {
        return chatJson(userPrompt, aiConfig.getEvalModel());
    }

    private void assertApiKeyConfigured() {
        if (aiConfig.getApiKey() == null || aiConfig.getApiKey().isBlank()) {
            throw new AIServiceException(
                    "OPENAI_API_KEY chưa được cấu hình. Vui lòng đặt OPENAI_API_KEY trong file .env");
        }
    }

    private boolean isGemini() {
        return "gemini".equalsIgnoreCase(aiConfig.getProvider());
    }

    private String geminiGenerateContent(String userPrompt, String model) {
        Map<String, Object> contents = new LinkedHashMap<>();
        contents.put("parts", List.of(Map.of("text", userPrompt)));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("maxOutputTokens", aiConfig.getMaxTokens());
        generationConfig.put("temperature", aiConfig.getTemperature());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(contents));
        body.put("generationConfig", generationConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", aiConfig.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return callGemini(aiConfig.getBaseUrl() + "/models/" + model + ":generateContent", entity);
    }

    private String extractGeminiText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.get("error");
            if (error != null) {
                throw new AIServiceException("Gemini trả lỗi: " + error.path("message").asText());
            }
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                String blockReason = root.path("promptFeedback").path("blockReason").asText(null);
                if (blockReason != null) {
                    throw new AIServiceException("Gemini chặn nội dung: " + blockReason);
                }
                throw new AIServiceException("Gemini không trả về nội dung");
            }
            JsonNode parts = candidates.get(0).path("content").path("parts");
            StringBuilder text = new StringBuilder();
            for (JsonNode part : parts) {
                text.append(part.path("text").asText());
            }
            if (text.toString().isBlank()) {
                throw new AIServiceException("Gemini không trả về nội dung");
            }
            return text.toString().trim();
        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AIServiceException("Không parse được response từ Gemini", e);
        }
    }

    public String transcribeAudio(byte[] audioData, String filename) {
        assertApiKeyConfigured();
        if (isGemini()) {
            return geminiTranscribe(audioData, filename);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(aiConfig.getApiKey());

        ByteArrayResource fileResource = new ByteArrayResource(audioData) {
            @Override
            public String getFilename() {
                return filename == null || filename.isBlank() ? "audio.webm" : filename;
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", aiConfig.getWhisperModel());
        form.add("file", fileResource);
        form.add("response_format", "json");

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(form, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    aiConfig.getBaseUrl() + "/audio/transcriptions",
                    HttpMethod.POST,
                    entity,
                    String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode error = root.get("error");
            if (error != null) {
                throw new AIServiceException("OpenAI transcription lỗi: " + error.path("message").asText());
            }
            String text = root.path("text").asText();
            if (text == null || text.isBlank()) {
                throw new AIServiceException("Whisper không trả về nội dung transcription");
            }
            return text.trim();
        } catch (AIServiceException e) {
            throw e;
        } catch (RestClientException e) {
            throw new AIServiceException("Lỗi khi gọi OpenAI Whisper: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new AIServiceException("Không parse được kết quả transcription từ OpenAI", e);
        }
    }

    private String geminiTranscribe(byte[] audioData, String filename) {
        Map<String, Object> inlineData = new LinkedHashMap<>();
        inlineData.put("mime_type", guessAudioMime(filename));
        inlineData.put("data", Base64.getEncoder().encodeToString(audioData));

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("parts", List.of(
                Map.of("text", "Transcribe the audio provided in the inline_data below. "
                        + "Return ONLY the verbatim transcribed text. No JSON, no quotes, no extra words."),
                Map.of("inline_data", inlineData)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", aiConfig.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            return callGemini(aiConfig.getBaseUrl() + "/models/" + aiConfig.getModel() + ":generateContent", entity);
        } catch (AIServiceException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("400") || msg.contains("INVALID_ARGUMENT"))) {
                throw new AIServiceException(
                        "Gemini từ chối audio: " + guessAudioMime(filename)
                                + " (webm/ogg/mp4 không được hỗ trợ; chỉ nhận WAV/PCM). "
                                + "Với provider openai (whisper-1), ghi âm webm từ trình duyệt vẫn hoạt động bình thường.",
                        e);
            }
            throw e;
        }
    }

    private String callGemini(String url, HttpEntity<?> entity) {
        RestClientException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                return extractGeminiText(response.getBody());
            } catch (HttpServerErrorException.ServiceUnavailable e) {
                lastError = e;
                if (attempt == 3) {
                    throw new AIServiceException("Lỗi khi gọi Gemini: " + e.getMessage(), e);
                }
                log.warn("Gemini quá tải (attempt {}) — retry sau {}s", attempt, attempt * 2L);
                try {
                    Thread.sleep(attempt * 2000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AIServiceException("Retry Gemini bị gián đoạn", ie);
                }
            } catch (RestClientException e) {
                throw new AIServiceException("Lỗi khi gọi Gemini: " + e.getMessage(), e);
            }
        }
        throw new AIServiceException(
                "Lỗi khi gọi Gemini sau khi retry: " + (lastError != null ? lastError.getMessage() : "unknown"), lastError);
    }

    private String guessAudioMime(String filename) {
        if (filename == null || filename.isBlank()) {
            return "audio/pcm";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".wav")) return "audio/pcm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".opus")) return "audio/ogg";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".mp4")) return "audio/mp4";
        return "audio/webm";
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.get("error");
            if (error != null) {
                throw new AIServiceException("OpenAI trả lỗi: " + error.path("message").asText());
            }
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AIServiceException("Không parse được response từ OpenAI", e);
        }
    }

    private CvParseResponse parseAndMap(String content) throws JsonProcessingException {
        String cleaned = stripCodeFences(content);
        JsonNode node = objectMapper.readTree(cleaned);
        validateCvNode(node);
        return mapCvNode(node);
    }

    private void validateCvNode(JsonNode node) {
        if (!node.path("skills").isArray()) {
            throw new IllegalArgumentException("Thiếu field 'skills' (phải là mảng)");
        }
        if (node.path("skills").size() > 30) {
            throw new IllegalArgumentException("Danh sách skills vượt giới hạn 30");
        }
        if (node.path("experience").isArray() && node.path("experience").size() > 20) {
            throw new IllegalArgumentException("Danh sách experience vượt giới hạn 20");
        }
        if (node.path("education").isArray() && node.path("education").size() > 10) {
            throw new IllegalArgumentException("Danh sách education vượt giới hạn 10");
        }
    }

    private CvParseResponse mapCvNode(JsonNode node) {
        List<CvParseResponse.SkillData> skills = new ArrayList<>();
        for (JsonNode item : node.path("skills")) {
            String name = item.path("name").asText().trim();
            if (name.isEmpty()) continue;
            Integer years = item.path("years").isMissingNode() || item.path("years").isNull()
                    ? 0 : item.path("years").asInt();
            skills.add(CvParseResponse.SkillData.builder()
                    .name(name)
                    .proficiency(parseProficiency(item.path("proficiency").asText()))
                    .years(years)
                    .build());
        }

        List<CvParseResponse.ExperienceData> experience = new ArrayList<>();
        for (JsonNode item : node.path("experience")) {
            experience.add(CvParseResponse.ExperienceData.builder()
                    .company(item.path("company").asText())
                    .role(item.path("role").asText())
                    .duration(item.path("duration").asText())
                    .achievements(toStringList(item.path("achievements")))
                    .build());
        }

        List<CvParseResponse.EducationData> education = new ArrayList<>();
        for (JsonNode item : node.path("education")) {
            Integer year = item.path("year").isMissingNode() || item.path("year").isNull()
                    ? null : item.path("year").asInt();
            education.add(CvParseResponse.EducationData.builder()
                    .institution(item.path("institution").asText())
                    .degree(item.path("degree").asText())
                    .year(year)
                    .build());
        }

        return CvParseResponse.builder()
                .skills(skills)
                .experience(experience)
                .education(education)
                .suggestedPositions(toStringList(node.path("suggested_positions")))
                .knowledgeGaps(toStringList(node.path("knowledge_gaps")))
                .build();
    }

    private List<String> toStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = item.asText().trim();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    private ProficiencyLevel parseProficiency(String raw) {
        if (raw == null || raw.isBlank()) {
            return ProficiencyLevel.INTERMEDIATE;
        }
        try {
            return ProficiencyLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Proficiency không hợp lệ '{}', dùng INTERMEDIATE mặc định", raw);
            return ProficiencyLevel.INTERMEDIATE;
        }
    }

    private String stripCodeFences(String content) {
        if (content == null) {
            return "{}";
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline >= 0) {
                text = text.substring(firstNewline + 1);
            }
            int lastFence = text.lastIndexOf("```");
            if (lastFence >= 0) {
                text = text.substring(0, lastFence);
            }
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            text = text.substring(firstBrace, lastBrace + 1);
        }
        return text.trim();
    }

    private String buildCvAnalysisPrompt(String cvText) {
        return """
                Analyze the following CV/resume and extract structured information.

                CV Content:
                %s

                Return a JSON object with exactly these fields:
                {
                  "skills": [
                    {"name": "Java", "proficiency": "ADVANCED", "years": 3},
                    {"name": "Spring Boot", "proficiency": "INTERMEDIATE", "years": 2}
                  ],
                  "experience": [
                    {
                      "company": "ABC Corp",
                      "role": "Backend Developer",
                      "duration": "2022-2024",
                      "achievements": ["Built microservices", "Improved performance 30%"]
                    }
                  ],
                  "education": [
                    {"institution": "University XYZ", "degree": "BSc CS", "year": 2022}
                  ],
                  "suggested_positions": [
                    "Java Backend Developer",
                    "Full Stack Developer",
                    "Software Engineer"
                  ],
                  "knowledge_gaps": [
                    "Cloud Computing",
                    "System Design"
                  ]
                }
                """.replace("%s", cvText);
    }

    private String buildCvRetryPrompt(String cvText) {
        return """
                Kết quả phản hồi trước đó của bạn không phải JSON hợp lệ.
                Hãy chỉ trả về một JSON object hợp lệ (không markdown, không giải thích),
                theo đúng schema đã yêu cầu trước đó.

                CV Content:
                %s
                """.replace("%s", cvText);
    }
}