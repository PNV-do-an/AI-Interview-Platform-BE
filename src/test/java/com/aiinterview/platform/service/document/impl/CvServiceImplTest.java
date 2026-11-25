package com.aiinterview.platform.service.document.impl;

import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.response.CvParseResponse;
import com.aiinterview.platform.model.dto.response.PositionRecommendationResponse;
import com.aiinterview.platform.model.entity.PositionCatalog;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.UserCv;
import com.aiinterview.platform.model.repository.PositionCatalogRepository;
import com.aiinterview.platform.model.repository.UserCvRepository;
import com.aiinterview.platform.model.repository.UserSkillRepository;
import com.aiinterview.platform.service.document.impl.CvServiceImpl;
import com.aiinterview.platform.service.infra.OpenAiClient;
import com.aiinterview.platform.service.job.PositionRecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GET CV có kèm gợi ý vị trí: phần gợi ý là "phụ" nên không được phép làm hỏng cả response.
 * Test ghi lại đúng ranh giới này — lỗi của bộ gợi ý bị nuốt, CV vẫn trả bình thường.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class CvServiceImplTest {

    private static final Long USER_ID = 7L;

    private CvServiceImpl service;
    private UserCvRepository userCvRepository;
    private PositionRecommendationService positionRecommendationService;
    private PositionCatalogRepository positionCatalogRepository;
    private OpenAiClient openAiClient;

    @BeforeEach
    void setUp() {
        userCvRepository = mock(UserCvRepository.class);
        UserSkillRepository userSkillRepository = mock(UserSkillRepository.class);
        openAiClient = mock(OpenAiClient.class);
        positionRecommendationService = mock(PositionRecommendationService.class);
        positionCatalogRepository = mock(PositionCatalogRepository.class);
        service = new CvServiceImpl(userCvRepository, userSkillRepository, openAiClient,
                positionRecommendationService, positionCatalogRepository, new ObjectMapper());
    }

    @Test
    void getCvEmbedsRecommendedPositions() {
        // Arrange: user có CV, bộ gợi ý trả 1 vị trí khớp 75%
        when(userCvRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(UserCv.builder().id(1L).targetPosition("Backend").build()));
        when(positionRecommendationService.recommend(USER_ID, 5))
                .thenReturn(List.of(PositionRecommendationResponse.builder()
                        .positionId(10L)
                        .title("Backend Java Engineer")
                        .matchedSkillCount(3)
                        .matchPercentage(75)
                        .build()));

        // Act
        CvParseResponse response = service.getCv(USER_ID);

        // Assert: dữ liệu CV gốc và danh sách gợi ý cùng có mặt trong một response
        assertThat(response.getTargetPosition()).isEqualTo("Backend");
        assertThat(response.getRecommendedPositions()).hasSize(1);
        assertThat(response.getRecommendedPositions().get(0).getMatchPercentage()).isEqualTo(75);
    }

    @Test
    void getCvStillReturnsCvWhenRecommendationFails() {
        // Arrange: bộ gợi ý ném lỗi (catalog chưa sẵn sàng)
        when(userCvRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(UserCv.builder().id(1L).build()));
        when(positionRecommendationService.recommend(USER_ID, 5))
                .thenThrow(new IllegalStateException("catalog unavailable"));

        // Act: lỗi ở bước gợi ý không được ném ra ngoài
        CvParseResponse response = service.getCv(USER_ID);

        // Assert: CV vẫn trả về, chỉ mất phần gợi ý
        assertThat(response.getCvId()).isEqualTo(1L);
        assertThat(response.getRecommendedPositions()).isEmpty();
    }

    @Test
    void getCvAttachesTargetPositionWithoutWarningWhenItIsTheBest() {
        // Arrange: user chọn vị trí 10 và nó cũng là gợi ý tốt nhất
        givenCvWithTargetPosition(10L);
        when(positionRecommendationService.recommend(USER_ID, 5))
                .thenReturn(List.of(position(10L, "Backend Java Engineer", 4, 57)));
        when(positionRecommendationService.matchPosition(USER_ID, 10L))
                .thenReturn(position(10L, "Backend Java Engineer", 4, 57));

        // Act
        CvParseResponse response = service.getCv(USER_ID);

        // Assert: vị trí đích có breakdown riêng và không bị cảnh báo vì đã là tốt nhất
        assertThat(response.getTargetPositionId()).isEqualTo(10L);
        assertThat(response.getTargetPositionMatch().getPositionId()).isEqualTo(10L);
        assertThat(response.getWarnings()).isEmpty();
    }

    @Test
    void getCvWarnsWhenTargetPositionIsMuchWorseThanBestMatch() {
        // Arrange: chọn QA 33% trong khi gợi ý tốt nhất là Fullstack 71%
        givenCvWithTargetPosition(8L);
        when(positionRecommendationService.recommend(USER_ID, 5))
                .thenReturn(List.of(position(3L, "Fullstack Engineer", 5, 71)));
        when(positionRecommendationService.matchPosition(USER_ID, 8L))
                .thenReturn(position(8L, "QA Automation Engineer", 2, 33));

        // Act
        CvParseResponse response = service.getCv(USER_ID);

        // Assert: cảnh báo để FE gợi ý đổi vị trí, nhưng vẫn trả điểm của vị trí đã chọn
        assertThat(response.getWarnings()).containsExactly("POSITION_MISMATCH");
        assertThat(response.getTargetPositionMatch().getTitle()).isEqualTo("QA Automation Engineer");
        assertThat(response.getTargetPositionMatch().getMatchPercentage()).isEqualTo(33);
    }

    @Test
    void getCvDoesNotWarnWhenGapIsBelowThreshold() {
        // Arrange: lệch 11 điểm, dưới ngưỡng mặc định 20 nên coi là chấp nhận được
        givenCvWithTargetPosition(2L);
        when(positionRecommendationService.recommend(USER_ID, 5))
                .thenReturn(List.of(position(3L, "Fullstack Engineer", 5, 71)));
        when(positionRecommendationService.matchPosition(USER_ID, 2L))
                .thenReturn(position(2L, "Frontend React Engineer", 4, 60));

        // Act
        CvParseResponse response = service.getCv(USER_ID);

        // Assert
        assertThat(response.getWarnings()).isEmpty();
    }

    @Test
    void parseCvTextRejectsUnknownTargetPositionBeforeCallingAi() {
        // Arrange: id không có trong catalog active
        when(positionCatalogRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        // Act & Assert: 404 ngay, không tốn token AI và không ghi CV
        assertThatThrownBy(() -> service.parseCvText("Java Spring Boot", user(), 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vị trí không tồn tại");

        verify(openAiClient, never()).parseCv(any());
        verify(userCvRepository, never()).save(any(UserCv.class));
    }

    @Test
    void parseCvTextPersistsChosenTargetPosition() {
        // Arrange: user chọn vị trí 3 trước khi parse
        when(positionCatalogRepository.findByIdAndActiveTrue(3L))
                .thenReturn(Optional.of(PositionCatalog.builder().id(3L).title("Fullstack Engineer").build()));
        when(openAiClient.parseCv("Java Spring Boot")).thenReturn(CvParseResponse.builder()
                .skills(List.of(CvParseResponse.SkillData.builder().name("Java").build()))
                .build());
        when(userCvRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(userCvRepository.save(any(UserCv.class))).thenAnswer(invocation -> {
            UserCv cv = invocation.getArgument(0);
            cv.setId(1L);
            return cv;
        });
        when(positionRecommendationService.recommend(USER_ID, 5)).thenReturn(List.of());
        when(positionRecommendationService.matchPosition(USER_ID, 3L))
                .thenReturn(position(3L, "Fullstack Engineer", 1, 14));

        // Act
        CvParseResponse response = service.parseCvText("Java Spring Boot", user(), 3L);

        // Assert: cột cũ target_position giữ đồng bộ với target_position_id
        assertThat(response.getCvId()).isEqualTo(1L);
        assertThat(response.getTargetPositionId()).isEqualTo(3L);
        assertThat(response.getTargetPosition()).isEqualTo("Fullstack Engineer");
    }

    private void givenCvWithTargetPosition(Long positionId) {
        when(userCvRepository.findByUserId(USER_ID)).thenReturn(Optional.of(
                UserCv.builder().id(1L).targetPositionId(positionId).build()));
    }

    private User user() {
        return User.builder().id(USER_ID).email("candidate@example.com").build();
    }

    private PositionRecommendationResponse position(Long id, String title, int matchedCount, int percentage) {
        return PositionRecommendationResponse.builder()
                .positionId(id)
                .title(title)
                .matchedSkillCount(matchedCount)
                .matchPercentage(percentage)
                .build();
    }
}
