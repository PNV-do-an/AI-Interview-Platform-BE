package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.model.enums.AiScreeningTrack;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.WorkplaceModel;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_descriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private User employer;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String companyName;

    private String position;

    private String location;

    private String salaryRange;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    /** Comma-separated list of required skills, used for matching suggestions */
    @Column(columnDefinition = "TEXT")
    private String requiredSkills;

    /** Wizard step 1 — all nullable so ddl-auto can add them without touching existing rows. */
    private String department;

    private String experienceLevel;

    private String employmentType;

    @Enumerated(EnumType.STRING)
    private WorkplaceModel workplaceModel;

    private String timezoneRequirement;

    /** Wizard step 3 — structured salary. {@code salaryRange} stays the display string. */
    private String salaryCurrency;

    private Long salaryMin;

    private Long salaryMax;

    private String equityBand;

    private String annualBonus;

    /** Wizard step 4 — AI screening configuration, stored only (no engine behind it yet). */
    private Boolean aiScreeningEnabled;

    @Enumerated(EnumType.STRING)
    private AiScreeningTrack aiScreeningTrack;

    /** Passing score threshold, 50–95; only meaningful when aiScreeningEnabled = true. */
    private Integer aiScreeningPassScore;

    /** Comma-separated focus areas, same convention as requiredSkills. */
    @Column(columnDefinition = "TEXT")
    private String aiQuestionFocusAreas;

    /** Reason written by the reviewer when rejecting; cleared on approval. */
    @Column(columnDefinition = "TEXT")
    private String reviewNote;

    /** Set when the employer submits for review; cleared again when the job is rejected. */
    private LocalDateTime submittedAt;

    /** Set when an admin/staff approves or rejects. */
    private LocalDateTime decidedAt;

    /** AI pre-screening result placeholder — stored only, no engine writes it yet (spec 004 FR-005). */
    private Integer aiSafetyScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.OPEN;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
