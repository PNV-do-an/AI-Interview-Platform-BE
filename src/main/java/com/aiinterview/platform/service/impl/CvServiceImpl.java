package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.AIServiceException;
import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.response.CvParseResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.UserCv;
import com.aiinterview.platform.model.entity.UserSkill;
import com.aiinterview.platform.model.enums.ProficiencyLevel;
import com.aiinterview.platform.model.repository.UserCvRepository;
import com.aiinterview.platform.model.repository.UserSkillRepository;
import com.aiinterview.platform.service.CvService;
import com.aiinterview.platform.service.OpenAiClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CvServiceImpl implements CvService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final UserCvRepository userCvRepository;
    private final UserSkillRepository userSkillRepository;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    @Value("${storage.cv-upload-dir:./uploads/cv}")
    private String cvUploadDir;

    @Override
    public CvParseResponse uploadCv(MultipartFile file, User user) {
        String contentType = resolveContentType(file);
        if (contentType == null) {
            throw new BadRequestException("Chỉ hỗ trợ file PDF hoặc text (.pdf/.txt)");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File CV vượt quá giới hạn 10MB");
        }

        String rawText = extractText(contentType, file);
        if (rawText == null || rawText.isBlank()) {
            throw new BadRequestException("Không trích xuất được văn bản từ file CV");
        }

        String fileName = persistCvFile(file);
        CvParseResponse parsed = openAiClient.parseCv(rawText);
        parsed.setFileName(fileName);
        return persistParsed(user, rawText, fileName, storageDir(), parsed);
    }

    @Override
    public CvParseResponse parseCvText(String cvText, User user) {
        if (cvText == null || cvText.isBlank()) {
            throw new BadRequestException("CV text không được để trống");
        }
        CvParseResponse parsed = openAiClient.parseCv(cvText);
        return persistParsed(user, cvText, null, null, parsed);
    }

    @Override
    public CvParseResponse getCv(Long userId) {
        return userCvRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> CvParseResponse.builder().build());
    }

    @Override
    public CvParseResponse setPosition(Long userId, String position, String level) {
        UserCv cv = userCvRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Chưa có CV nào. Hãy upload hoặc parse CV trước khi chọn vị trí"));
        cv.setTargetPosition(position);
        cv.setTargetLevel(level);
        return toResponse(userCvRepository.save(cv));
    }

    @Override
    public List<UserSkill> getCvSkills(Long userId) {
        return userCvRepository.findTopByUserIdOrderByUpdatedAtDesc(userId)
                .map(cv -> userSkillRepository.findByCvId(cv.getId()))
                .orElseGet(List::of);
    }

    private CvParseResponse persistParsed(User user, String rawText, String fileName, String filePath,
                                          CvParseResponse parsed) {
        UserCv cv = userCvRepository.findByUserId(user.getId())
                .orElseGet(() -> UserCv.builder().user(user).build());
        cv.setRawText(rawText);
        cv.setFileName(fileName);
        cv.setFilePath(filePath);
        cv.setParsedSkills(toJson(parsed.getSkills()));
        cv.setParsedExperience(toJson(parsed.getExperience()));
        cv.setParsedEducation(toJson(parsed.getEducation()));
        cv.setSuggestedPositions(toJson(parsed.getSuggestedPositions()));
        cv.setKnowledgeGaps(toJson(parsed.getKnowledgeGaps()));
        UserCv saved = userCvRepository.save(cv);

        replaceSkills(saved, parsed.getSkills());
        parsed.setCvId(saved.getId());
        return parsed;
    }

    private void replaceSkills(UserCv cv, List<CvParseResponse.SkillData> skills) {
        userSkillRepository.deleteByCvId(cv.getId());
        if (skills == null) {
            return;
        }
        for (CvParseResponse.SkillData skill : skills) {
            if (skill.getName() == null || skill.getName().isBlank()) {
                continue;
            }
            userSkillRepository.save(UserSkill.builder()
                    .cv(cv)
                    .skillName(skill.getName())
                    .proficiencyLevel(skill.getProficiency() == null
                            ? ProficiencyLevel.INTERMEDIATE : skill.getProficiency())
                    .yearsExperience(skill.getYears())
                    .build());
        }
    }

    private CvParseResponse toResponse(UserCv cv) {
        return CvParseResponse.builder()
                .cvId(cv.getId())
                .fileName(cv.getFileName())
                .skills(readList(cv.getParsedSkills(), new TypeReference<>() {
                }))
                .experience(readList(cv.getParsedExperience(), new TypeReference<>() {
                }))
                .education(readList(cv.getParsedEducation(), new TypeReference<>() {
                }))
                .suggestedPositions(readList(cv.getSuggestedPositions(), new TypeReference<>() {
                }))
                .knowledgeGaps(readList(cv.getKnowledgeGaps(), new TypeReference<>() {
                }))
                .targetPosition(cv.getTargetPosition())
                .targetLevel(cv.getTargetLevel())
                .build();
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.warn("Không parse được JSON cột của CV: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AIServiceException("Không serialize được dữ liệu CV", e);
        }
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if ("application/pdf".equals(contentType) || "text/plain".equals(contentType)) {
            return contentType;
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (name.endsWith(".txt")) {
            return "text/plain";
        }
        return null;
    }

    private String extractText(String contentType, MultipartFile file) {
        try {
            if ("application/pdf".equals(contentType)) {
                try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                    return new PDFTextStripper().getText(document);
                }
            }
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BadRequestException("Không đọc được nội dung file CV");
        }
    }

    private String persistCvFile(MultipartFile file) {
        String original = file.getOriginalFilename();
        String safeOriginal = original == null ? "file" : original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileName = "cv_" + System.currentTimeMillis() + "_" + safeOriginal;
        try {
            Path target = Paths.get(storageDir(), fileName).toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return fileName;
        } catch (IOException e) {
            throw new BadRequestException("Không lưu được file CV");
        }
    }

    private String storageDir() {
        return Paths.get(cvUploadDir).toAbsolutePath().normalize().toString();
    }
}