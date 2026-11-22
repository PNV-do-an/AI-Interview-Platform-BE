package com.aiinterview.platform.service.document.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.CoverLetterGenerateRequest;
import com.aiinterview.platform.model.dto.response.CoverLetterResponse;
import com.aiinterview.platform.model.entity.CandidateProfile;
import com.aiinterview.platform.model.entity.CoverLetter;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.UserCv;
import com.aiinterview.platform.model.entity.UserSkill;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.CandidateProfileRepository;
import com.aiinterview.platform.model.repository.CoverLetterRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.UserCvRepository;
import com.aiinterview.platform.model.repository.UserSkillRepository;
import com.aiinterview.platform.service.ai.CvAiEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cover letter rules (Postman folder 09): a candidate may only build or edit letters from their
 * own CV, and the AI engine is the only source of generated text. The engine is mocked so the
 * tests stay deterministic and offline.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class CoverLetterServiceImplTest {

    private static final Long LETTER_ID = 3L;
    private static final Long CV_ID = 11L;
    private static final Long JOB_ID = 7L;

    private CoverLetterServiceImpl service;
    private CoverLetterRepository coverLetterRepository;
    private UserCvRepository userCvRepository;
    private UserSkillRepository userSkillRepository;
    private JobDescriptionRepository jobRepository;
    private CandidateProfileRepository candidateProfileRepository;
    private CvAiEngine cvAiEngine;

    private User candidate;
    private User otherCandidate;

    @BeforeEach
    void setUp() {
        coverLetterRepository = mock(CoverLetterRepository.class);
        userCvRepository = mock(UserCvRepository.class);
        userSkillRepository = mock(UserSkillRepository.class);
        jobRepository = mock(JobDescriptionRepository.class);
        candidateProfileRepository = mock(CandidateProfileRepository.class);
        // AI engine luon duoc mock -> khong goi mang, test chay offline va on dinh
        cvAiEngine = mock(CvAiEngine.class);
        service = new CoverLetterServiceImpl(coverLetterRepository, userCvRepository, userSkillRepository,
                jobRepository, candidateProfileRepository, cvAiEngine);

        // 2 ung vien de test quyen so huu CV / thu
        candidate = user(20L, "candidate@example.com", "Candidate User");
        otherCandidate = user(21L, "other-candidate@example.com", "Other Candidate");

        when(coverLetterRepository.save(any(CoverLetter.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Khong tim thay CV thi tra 404 truoc khi goi AI")
    void generate_unknownCv_returnsNotFound() {
        // Arrange: cvId khong co trong DB
        when(userCvRepository.findById(CV_ID)).thenReturn(Optional.empty());

        // Act & Assert: 404 som, khong ton chi phi goi AI
        assertThatThrownBy(() -> service.generate(candidate, request(CV_ID, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy CV");

        // Assert: chua he goi AI va khong tao thu
        verifyNoInteractions(cvAiEngine, coverLetterRepository);
    }

    @Test
    @DisplayName("Dung CV cua nguoi khac bi chan 400")
    void generate_cvOfAnotherUser_returnsBadRequest() {
        // Arrange: CV ton tai nhung thuoc otherCandidate
        UserCv cv = cv(CV_ID, otherCandidate);
        when(userCvRepository.findById(CV_ID)).thenReturn(Optional.of(cv));

        // Act & Assert: khong duoc dung CV cua nguoi khac lam dau vao
        assertThatThrownBy(() -> service.generate(candidate, request(CV_ID, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn không có quyền sử dụng CV này");

        // Assert: khong tao thu
        verify(coverLetterRepository, never()).save(any(CoverLetter.class));
    }

    @Test
    @DisplayName("Tin tuyen dung khong ton tai thi tra 404")
    void generate_unknownJob_returnsNotFound() {
        // Arrange: jobId khong ton tai (hoac tin da xoa mem)
        when(jobRepository.findByIdAndDeletedAtIsNull(JOB_ID)).thenReturn(Optional.empty());

        // Act & Assert: 404 truoc khi sinh noi dung
        assertThatThrownBy(() -> service.generate(candidate, request(null, JOB_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy tin tuyển dụng");

        // Assert: khong tao thu
        verify(coverLetterRepository, never()).save(any(CoverLetter.class));
    }

    @Test
    @DisplayName("Co CV thi lay ky nang tu CV da phan tich, khong goi analyze lai")
    void generate_withCv_reusesParsedSkills() {
        // Arrange: CV cua chinh candidate da tung duoc phan tich -> co san user_skill
        UserCv cv = cv(CV_ID, candidate);
        when(userCvRepository.findById(CV_ID)).thenReturn(Optional.of(cv));
        when(userSkillRepository.findByCvId(CV_ID))
                .thenReturn(List.of(skill("Java"), skill("Spring Boot")));
        when(cvAiEngine.matchJob(anyList(), any())).thenReturn(List.of());
        when(cvAiEngine.generateCoverLetter(eq(candidate), anyList(), any(), any(), anyList()))
                .thenReturn("Noi dung thu");

        // Act
        CoverLetterResponse response = service.generate(candidate, request(CV_ID, null));

        // Assert: noi dung + title lay tu tien trinh sinh
        assertThat(response.getContent()).isEqualTo("Noi dung thu");
        assertThat(response.getTitle()).isEqualTo("Thu ung tuyen");

        // Assert: tai su dung ky nang da luu (khong goi analyze = khong ton token AI)
        verify(cvAiEngine, never()).analyze(any());
        verify(userSkillRepository).findByCvId(CV_ID);
    }

    @Test
    @DisplayName("Khong chon CV thi AI tu phan tich ho so va dung ky nang do tra ve")
    void generate_withoutCv_fallsBackToAnalysis() {
        // Arrange: khong truyen cvId -> engine tu analyze ho so
        when(cvAiEngine.analyze(any())).thenReturn(new CvAiEngine.CvAnalysis(
                List.of("Python", "Docker"), 70, List.of()));
        when(cvAiEngine.matchJob(anyList(), any())).thenReturn(List.of("Python"));
        when(cvAiEngine.generateCoverLetter(eq(candidate), anyList(), any(), any(), anyList()))
                .thenReturn("Thu tu sinh");

        // Act
        service.generate(candidate, request(null, null));

        // Assert: ky nang truyen vao matchJob chinh la ket qua analyze
        ArgumentCaptor<List<String>> skills = ArgumentCaptor.forClass(List.class);
        verify(cvAiEngine).matchJob(skills.capture(), isNull());
        assertThat(skills.getValue()).containsExactly("Python", "Docker");

        // Assert: khong dung duong CV nen khong cham vao 2 repository lien quan
        verifyNoInteractions(userCvRepository, userSkillRepository);
    }

    @Test
    @DisplayName("Kinh nghiem 0 nam duoc gui sang AI la null thay vi 0")
    void generate_zeroExperienceYears_isPassedAsNull() {
        // Arrange: profile ghi experienceYears = 0 (sinh vien / fresher)
        when(cvAiEngine.analyze(any())).thenReturn(new CvAiEngine.CvAnalysis(List.of(), 0, List.of()));
        when(cvAiEngine.matchJob(anyList(), any())).thenReturn(List.of());
        when(candidateProfileRepository.findByUser(candidate)).thenReturn(Optional.of(
                CandidateProfile.builder().user(candidate).experienceYears(0).build()));
        when(cvAiEngine.generateCoverLetter(any(), anyList(), any(), any(), anyList())).thenReturn("Thu");

        // Act
        service.generate(candidate, request(null, null));

        // Assert: 0 duoc doi thanh null de prompt khong viet "0 nam kinh nghiem"
        verify(cvAiEngine).generateCoverLetter(eq(candidate), anyList(), isNull(), isNull(), anyList());
    }

    @Test
    @DisplayName("Sinh thu voi tin tuyen dung gan jobId va ten cong ty vao response")
    void generate_attachesJobToSavedLetter() {
        // Arrange: co job hop le de lam ngu canh
        JobDescription job = JobDescription.builder()
                .id(JOB_ID).title("Backend Engineer").companyName("Acme").build();
        when(jobRepository.findByIdAndDeletedAtIsNull(JOB_ID)).thenReturn(Optional.of(job));
        when(cvAiEngine.analyze(any())).thenReturn(new CvAiEngine.CvAnalysis(List.of(), 0, List.of()));
        when(cvAiEngine.matchJob(anyList(), any())).thenReturn(List.of());
        when(cvAiEngine.generateCoverLetter(any(), anyList(), any(), any(), anyList())).thenReturn("Thu");

        // Act
        CoverLetterResponse response = service.generate(candidate, request(null, JOB_ID));

        // Assert: response tra kem thong tin tin tuyen dung de FE hien thi
        assertThat(response.getJobId()).isEqualTo(JOB_ID);
        assertThat(response.getJobTitle()).isEqualTo("Backend Engineer");
        assertThat(response.getCompanyName()).isEqualTo("Acme");

        // Assert: dong duoc luu gan dung candidate va job (khong phai job khac)
        ArgumentCaptor<CoverLetter> saved = ArgumentCaptor.forClass(CoverLetter.class);
        verify(coverLetterRepository).save(saved.capture());
        assertThat(saved.getValue().getCandidate()).isEqualTo(candidate);
        assertThat(saved.getValue().getJob()).isEqualTo(job);
    }

    @Test
    @DisplayName("GET /cover-letters/{id} voi id la cua nguoi khac bi chan 400")
    void getLetter_ofAnotherCandidate_returnsBadRequest() {
        // Arrange: thu ton tai nhung thuoc otherCandidate
        when(coverLetterRepository.findById(LETTER_ID))
                .thenReturn(Optional.of(letter(otherCandidate)));

        // Act & Assert: chan doc cheo
        assertThatThrownBy(() -> service.getLetter(candidate, LETTER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn không có quyền truy cập thư này");
    }

    @Test
    @DisplayName("GET /cover-letters/{id} khong ton tai tra 404")
    void getLetter_unknownId_returnsNotFound() {
        // Arrange: id khong co trong DB
        when(coverLetterRepository.findById(LETTER_ID)).thenReturn(Optional.empty());

        // Act & Assert: 404 (khac message voi 400 sai quyen)
        assertThatThrownBy(() -> service.getLetter(candidate, LETTER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy thư xin việc");
    }

    @Test
    @DisplayName("PUT /cover-letters/{id}/content voi noi dung rong bi chan 400")
    void updateContent_blank_returnsBadRequest() {
        // Arrange: noi dung chi co khoang trang

        // Act & Assert: validate chay truoc khi doc DB
        assertThatThrownBy(() -> service.updateContent(candidate, LETTER_ID, "   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Nội dung thư không được để trống");

        // Assert: khong ton cong query
        verifyNoInteractions(coverLetterRepository);
    }

    @Test
    @DisplayName("Sua noi dung luu lai dung van ban moi")
    void updateContent_ownedLetter_persistsNewContent() {
        // Arrange: thu cua chinh candidate
        when(coverLetterRepository.findById(LETTER_ID)).thenReturn(Optional.of(letter(candidate)));

        // Act
        CoverLetterResponse response = service.updateContent(candidate, LETTER_ID, "Thu da sua");

        // Assert: noi dung moi duoc tra ve va luu lai
        assertThat(response.getContent()).isEqualTo("Thu da sua");
        verify(coverLetterRepository).save(any(CoverLetter.class));
    }

    @Test
    @DisplayName("DELETE /cover-letters/{id} xoa dung thu cua minh")
    void delete_ownedLetter_removesIt() {
        // Arrange: thu cua chinh candidate
        CoverLetter letter = letter(candidate);
        when(coverLetterRepository.findById(LETTER_ID)).thenReturn(Optional.of(letter));

        // Act
        service.delete(candidate, LETTER_ID);

        // Assert: xoa dung dong vua doc (khong xoa theo id mo ho)
        verify(coverLetterRepository).delete(letter);
    }

    @Test
    @DisplayName("DELETE /cover-letters/{id} cua nguoi khac khong duoc xoa")
    void delete_foreignLetter_returnsBadRequest() {
        // Arrange: thu thuoc otherCandidate
        when(coverLetterRepository.findById(LETTER_ID)).thenReturn(Optional.of(letter(otherCandidate)));

        // Act & Assert: 400 va khong xoa gi
        assertThatThrownBy(() -> service.delete(candidate, LETTER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn không có quyền truy cập thư này");
        verify(coverLetterRepository, never()).delete(any(CoverLetter.class));
    }

    @Test
    @DisplayName("GET /cover-letters/my chi tra thu cua nguoi dang dang nhap")
    void getMyLetters_queriesCurrentCandidate() {
        // Arrange: candidate co 1 thu
        when(coverLetterRepository.findByCandidateOrderByUpdatedAtDesc(candidate))
                .thenReturn(List.of(letter(candidate)));

        // Act
        List<CoverLetterResponse> letters = service.getMyLetters(candidate);

        // Assert: query loc theo dung candidate va tra ve dung so luong
        assertThat(letters).hasSize(1);
        assertThat(letters.get(0).getId()).isEqualTo(LETTER_ID);
        verify(coverLetterRepository).findByCandidateOrderByUpdatedAtDesc(candidate);
    }

    private CoverLetterGenerateRequest request(Long cvId, Long jobId) {
        CoverLetterGenerateRequest request = new CoverLetterGenerateRequest();
        request.setCvId(cvId);
        request.setJobId(jobId);
        request.setTitle("Thu ung tuyen");
        return request;
    }

    private UserCv cv(Long id, User owner) {
        return UserCv.builder()
                .id(id)
                .user(owner)
                .fileName("cv.pdf")
                .rawText("Noi dung CV")
                .build();
    }

    private UserSkill skill(String name) {
        return UserSkill.builder().skillName(name).build();
    }

    private CoverLetter letter(User owner) {
        return CoverLetter.builder()
                .id(LETTER_ID)
                .candidate(owner)
                .title("Thu ung tuyen")
                .content("Noi dung cu")
                .build();
    }

    private User user(Long id, String email, String fullName) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName(fullName)
                .password("secret")
                .role(Role.ROLE_USER)
                .build();
    }
}
