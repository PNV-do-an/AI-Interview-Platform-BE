package com.aiinterview.platform.service;

import com.aiinterview.platform.common.exception.AIServiceException;
import com.aiinterview.platform.config.AIConfig;
import com.aiinterview.platform.model.dto.response.CvParseResponse;
import com.aiinterview.platform.model.enums.ProficiencyLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
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
        if (aiConfig.getApiKey() == null || aiConfig.getApiKey().isBlank()) {
            throw new AIServiceException(
                    "OPENAI_API_KEY chưa được cấu hình. Vui lòng đặt OPENAI_API_KEY trong file .env");
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", userPrompt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiConfig.getModel());
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

    public JsonNode chatJson(String userPrompt) {
        String content = chat(userPrompt);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return objectMapper.readTree(stripCodeFences(content));
            } catch (JsonProcessingException e) {
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("AI JSON response không hợp lệ (attempt {}). Retry...", attempt);
                    content = chat(userPrompt);
                } else {
                    log.error("AI JSON parsing thất bại sau {} lần thử. Raw content: {}", MAX_ATTEMPTS, content);
                    throw new AIServiceException("AI trả dữ liệu không hợp lệ", e);
                }
            }
        }
        throw new AIServiceException("AI trả dữ liệu không hợp lệ");
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