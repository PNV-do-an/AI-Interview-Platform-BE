package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.enums.AiScreeningTrack;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.WorkplaceModel;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class JobDetailResponse {

    private Long id;
    private String title;
    private String companyName;
    private String position;
    private String location;
    private String salaryRange;
    private String description;
    private String requirements;
    private String requiredSkills;
    private JobStatus status;
    private Long employerId;
    private String employerName;
    private String employerEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String department;
    private String experienceLevel;
    private String employmentType;
    private WorkplaceModel workplaceModel;
    private String timezoneRequirement;
    private String salaryCurrency;
    private Long salaryMin;
    private Long salaryMax;
    private String equityBand;
    private String annualBonus;
    private Boolean aiScreeningEnabled;
    private AiScreeningTrack aiScreeningTrack;
    private Integer aiScreeningPassScore;
    private String aiQuestionFocusAreas;
    private String reviewNote;

    public static JobDetailResponse from(JobDescription job) {
        return JobDetailResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .companyName(job.getCompanyName())
                .position(job.getPosition())
                .location(job.getLocation())
                .salaryRange(job.getSalaryRange())
                .description(job.getDescription())
                .requirements(job.getRequirements())
                .requiredSkills(job.getRequiredSkills())
                .status(job.getStatus())
                .employerId(job.getEmployer().getId())
                .employerName(job.getEmployer().getFullName())
                .employerEmail(job.getEmployer().getEmail())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .department(job.getDepartment())
                .experienceLevel(job.getExperienceLevel())
                .employmentType(job.getEmploymentType())
                .workplaceModel(job.getWorkplaceModel())
                .timezoneRequirement(job.getTimezoneRequirement())
                .salaryCurrency(job.getSalaryCurrency())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .equityBand(job.getEquityBand())
                .annualBonus(job.getAnnualBonus())
                .aiScreeningEnabled(job.getAiScreeningEnabled())
                .aiScreeningTrack(job.getAiScreeningTrack())
                .aiScreeningPassScore(job.getAiScreeningPassScore())
                .aiQuestionFocusAreas(job.getAiQuestionFocusAreas())
                .reviewNote(job.getReviewNote())
                .build();
    }
}
