package com.aiinterview.platform.service.ai;

import com.aiinterview.platform.common.exception.AIServiceException;
import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.model.dto.request.EvaluateFreeInterviewRequest;
import com.aiinterview.platform.model.dto.request.FreeInterviewTurn;
import com.aiinterview.platform.model.dto.request.GenerateQuestionRequest;
import com.aiinterview.platform.model.dto.response.EvaluateFreeInterviewResponse;
import com.aiinterview.platform.model.dto.response.GenerateQuestionResponse;
import com.aiinterview.platform.model.dto.response.TranscribeAudioResponse;
import com.aiinterview.platform.service.infra.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreeInterviewAssistantService {

    public static final String RUBRIC_VERSION = "free-mvp-2026-11";
    private static final List<String> RUBRIC_DIMENSIONS =
            List.of("chuyenMon", "duAn", "tuDuy", "truyenDat", "mucKhop");

    private final OpenAiClient openAiClient;

    public GenerateQuestionResponse generateQuestion(GenerateQuestionRequest request) {
        String prompt = buildQuestionPrompt(request);
        JsonNode node = openAiClient.chatJson(prompt);
        String questionText = node.path("questionText").asText("").trim();
        if (questionText.isBlank()) {
            throw new AIServiceException("AI không sinh được câu hỏi phỏng vấn");
        }
        return new GenerateQuestionResponse(questionText);
    }

    public TranscribeAudioResponse transcribe(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File âm thanh không được để trống");
        }
        String text = openAiClient.transcribeAudio(file.getBytes(),
                file.getOriginalFilename() == null ? "audio.webm" : file.getOriginalFilename());
        if (text == null || text.isBlank()) {
            throw new AIServiceException("Không nhận dạng được nội dung nói");
        }
        return new TranscribeAudioResponse(text.trim());
    }

    public EvaluateFreeInterviewResponse evaluate(EvaluateFreeInterviewRequest request) {
        String prompt = buildEvaluatePrompt(request);
        JsonNode node = openAiClient.chatJson(prompt);

        Map<String, Integer> rubric = new LinkedHashMap<>();
        for (String dim : RUBRIC_DIMENSIONS) {
            int score = node.path(dim).asInt(-1);
            if (score < 0 || score > 100) {
                throw new AIServiceException("AI trả điểm không hợp lệ cho chiều " + dim);
            }
            rubric.put(dim, score);
        }

        String strengths = node.path("strengths").asText("").trim();
        String improvements = node.path("improvements").asText("").trim();
        return new EvaluateFreeInterviewResponse(rubric, strengths, improvements, RUBRIC_VERSION);
    }

    private String buildQuestionPrompt(GenerateQuestionRequest request) {
        StringBuilder history = new StringBuilder();
        if (request.history() != null) {
            for (FreeInterviewTurn turn : request.history()) {
                if (turn.questionText() != null && !turn.questionText().isBlank()) {
                    history.append("Interviewer: ").append(turn.questionText().trim()).append("\n");
                }
                if (turn.answerText() != null && !turn.answerText().isBlank()) {
                    history.append("Candidate: ").append(turn.answerText().trim()).append("\n");
                }
            }
        }
        return """
                You are an experienced, friendly human interviewer conducting a real job interview.
                Job title: %s
                Job description: %s
                Candidate CV: %s
                Conversation so far:
                %s

                Task: Generate the next interview question.
                Rules:
                - Base the question on the job description and candidate CV.
                - Adapt to the candidate's previous answer: if it is short or vague, ask a clarifying follow-up;
                  if it is strong, ask a deeper or scenario-based question.
                - Sound natural and conversational, like a human interviewer.
                - Do not repeat or rephrase a previous question.
                - Keep it concise: one question, at most two sentences.
                - If the interview already has enough information, ask a final closing question.
                Return JSON only with exactly one field:
                {"questionText":"..."}
                No markdown, no extra fields.
                """.formatted(
                safe(request.jobTitle()),
                safe(request.jdText()),
                safe(request.cvText()),
                history.toString().isBlank() ? "(none)" : history.toString());
    }

    private String buildEvaluatePrompt(EvaluateFreeInterviewRequest request) {
        StringBuilder transcript = new StringBuilder();
        if (request.transcript() != null) {
            for (FreeInterviewTurn turn : request.transcript()) {
                transcript.append("Q: ").append(safe(turn.questionText())).append("\n");
                transcript.append("A: ").append(safe(turn.answerText())).append("\n\n");
            }
        }
        return """
                Evaluate the candidate's interview answers and return JSON only:
                {
                  "chuyenMon": 0-100 integer,
                  "duAn": 0-100 integer,
                  "tuDuy": 0-100 integer,
                  "truyenDat": 0-100 integer,
                  "mucKhop": 0-100 integer,
                  "strengths": "short text",
                  "improvements": "short text"
                }
                Job title: %s
                Job description: %s
                Transcript:
                %s
                Rules: scores must be integers 0..100; no markdown; no extra fields.
                """.formatted(
                safe(request.jobTitle()),
                safe(request.jdText()),
                transcript.toString().isBlank() ? "(empty)" : transcript.toString());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}