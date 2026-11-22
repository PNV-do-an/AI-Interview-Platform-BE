package com.aiinterview.platform.service.document.impl;

import com.aiinterview.platform.model.dto.response.CvParseResponse;
import com.aiinterview.platform.model.dto.response.PositionRecommendationResponse;
import com.aiinterview.platform.model.entity.UserCv;
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
import static org.mockito.Mockito.mock;
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

    @BeforeEach
    void setUp() {
        userCvRepository = mock(UserCvRepository.class);
        UserSkillRepository userSkillRepository = mock(UserSkillRepository.class);
        OpenAiClient openAiClient = mock(OpenAiClient.class);
        positionRecommendationService = mock(PositionRecommendationService.class);
        service = new CvServiceImpl(userCvRepository, userSkillRepository, openAiClient,
                positionRecommendationService, new ObjectMapper());
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
}
