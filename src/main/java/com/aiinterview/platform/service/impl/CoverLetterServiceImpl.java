package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.CoverLetterGenerateRequest;
import com.aiinterview.platform.model.dto.response.CoverLetterResponse;
import com.aiinterview.platform.model.entity.CoverLetter;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.UserCv;
import com.aiinterview.platform.model.entity.UserSkill;
import com.aiinterview.platform.model.repository.CandidateProfileRepository;
import com.aiinterview.platform.model.repository.CoverLetterRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.UserCvRepository;
import com.aiinterview.platform.model.repository.UserSkillRepository;
import com.aiinterview.platform.service.CoverLetterService;
import com.aiinterview.platform.service.ai.CvAiEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CoverLetterServiceImpl implements CoverLetterService {

    private final CoverLetterRepository coverLetterRepository;
    private final UserCvRepository userCvRepository;
    private final UserSkillRepository userSkillRepository;
    private final JobDescriptionRepository jobRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CvAiEngine cvAiEngine;

    @Override
    @Transactional
    public CoverLetterResponse generate(User candidate, CoverLetterGenerateRequest request) {
        List<String> skills = List.of();
        Integer experienceYears = null;
        String cvContent = null;

        if (request.getCvId() != null) {
            UserCv cv = userCvRepository.findById(request.getCvId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy CV"));
            if (!cv.getUser().getId().equals(candidate.getId())) {
                throw new BadRequestException("Bạn không có quyền sử dụng CV này");
            }
            cvContent = cv.getRawText();
            skills = userSkillRepository.findByCvId(cv.getId()).stream()
                    .map(UserSkill::getSkillName)
                    .toList();
        }

        JobDescription job = null;
        if (request.getJobId() != null) {
            job = jobRepository.findByIdAndDeletedAtIsNull(request.getJobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin tuyển dụng"));
        }

        if (cvContent == null) {
            CvAiEngine.CvAnalysis analysis = cvAiEngine.analyze(
                    candidate.getFullName() + "\n" + (candidate.getEmail() != null ? candidate.getEmail() : ""));
            skills = analysis.skills();
        }

        List<String> matched = cvAiEngine.matchJob(skills, job);
        Integer years = candidateProfileRepository.findByUser(candidate)
                .map(p -> p.getExperienceYears() > 0 ? p.getExperienceYears() : null)
                .orElse(null);
        String content = cvAiEngine.generateCoverLetter(candidate, skills, years, job, matched);

        CoverLetter letter = CoverLetter.builder()
                .candidate(candidate)
                .job(job)
                .title(request.getTitle())
                .content(content)
                .build();
        return CoverLetterResponse.from(coverLetterRepository.save(letter));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CoverLetterResponse> getMyLetters(User candidate) {
        return coverLetterRepository.findByCandidateOrderByUpdatedAtDesc(candidate).stream()
                .map(CoverLetterResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CoverLetterResponse getLetter(User candidate, Long id) {
        return CoverLetterResponse.from(ownLetter(candidate, id));
    }

    @Override
    @Transactional
    public CoverLetterResponse updateContent(User candidate, Long id, String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Nội dung thư không được để trống");
        }
        CoverLetter letter = ownLetter(candidate, id);
        letter.setContent(content);
        return CoverLetterResponse.from(coverLetterRepository.save(letter));
    }

    @Override
    @Transactional
    public void delete(User candidate, Long id) {
        coverLetterRepository.delete(ownLetter(candidate, id));
    }

    private CoverLetter ownLetter(User candidate, Long id) {
        CoverLetter letter = coverLetterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thư xin việc"));
        if (!letter.getCandidate().getId().equals(candidate.getId())) {
            throw new BadRequestException("Bạn không có quyền truy cập thư này");
        }
        return letter;
    }
}
