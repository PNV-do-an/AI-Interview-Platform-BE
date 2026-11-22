package com.aiinterview.platform.service.job.impl;

import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.common.util.SkillMatcher;
import com.aiinterview.platform.model.dto.response.PositionRecommendationResponse;
import com.aiinterview.platform.model.entity.PositionCatalog;
import com.aiinterview.platform.model.entity.UserCv;
import com.aiinterview.platform.model.entity.UserSkill;
import com.aiinterview.platform.model.repository.PositionCatalogRepository;
import com.aiinterview.platform.model.repository.UserCvRepository;
import com.aiinterview.platform.model.repository.UserSkillRepository;
import com.aiinterview.platform.service.job.impl.PositionRecommendationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Gợi ý vị trí dựa trên kỹ năng trong CV: so khớp qua {@link SkillMatcher} (không phân biệt hoa
 * thường, dấu phân cách, alias), xếp hạng theo số kỹ năng khớp rồi tới phần trăm khớp, và bỏ hẳn
 * vị trí không khớp kỹ năng nào để danh sách gợi ý không bị "rác".
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class PositionRecommendationServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long CV_ID = 11L;

    private PositionRecommendationServiceImpl service;
    private PositionCatalogRepository positionCatalogRepository;
    private UserCvRepository userCvRepository;
    private UserSkillRepository userSkillRepository;

    @BeforeEach
    void setUp() {
        positionCatalogRepository = mock(PositionCatalogRepository.class);
        userCvRepository = mock(UserCvRepository.class);
        userSkillRepository = mock(UserSkillRepository.class);
        service = new PositionRecommendationServiceImpl(
                positionCatalogRepository, userCvRepository, userSkillRepository);
    }

    @Test
    void matchesSkillsIgnoringCaseSeparatorAndAlias() {
        // Arrange: CV ghi "SpringBoot", "spring-boot", "RESTful API"; vị trí yêu cầu Java, Spring Boot, REST API
        givenCvSkills("SpringBoot", "spring-boot", "RESTful API");
        givenPositions(position(1L, "Backend Java Engineer", "Java", "Spring Boot", "REST API"));

        // Act
        List<PositionRecommendationResponse> result = service.recommend(USER_ID, 10);

        // Assert: chỉ 1 vị trí được trả về
        assertThat(result).hasSize(1);
        PositionRecommendationResponse match = result.get(0);

        // Assert: 2/3 kỹ năng khớp nhờ chuẩn hoá (khác hoa thường, khác dấu phân cách, alias)
        assertThat(match.getMatchedSkillCount()).isEqualTo(2);
        assertThat(match.getRequiredSkillCount()).isEqualTo(3);

        // Assert: 2/3 làm tròn thành 67%, và chỉ ra đúng kỹ năng còn thiếu để FE gợi ý học thêm
        assertThat(match.getMatchPercentage()).isEqualTo(67);
        assertThat(match.getMatchedSkills()).containsExactly("REST API", "Spring Boot");
        assertThat(match.getMissingSkills()).containsExactly("Java");
    }

    @Test
    void ranksByMatchedCountThenPercentageThenTitle() {
        // Arrange: 4 vị trí — A và C cùng khớp 4 kỹ năng nhưng C ít yêu cầu hơn (100% so với 67%),
        // B khớp 1, D không khớp gì
        givenCvSkills("Java", "Docker", "MySQL", "React");
        givenPositions(
                position(1L, "A Backend", "Java", "Docker", "MySQL", "React", "Kafka", "Redis"),
                position(2L, "B Frontend", "JavaScript", "React", "TypeScript", "CSS"),
                position(3L, "C Fullstack", "Java", "Docker", "MySQL", "React"),
                position(4L, "D Cobol", "Cobol", "JCL"));

        // Act
        List<PositionRecommendationResponse> result = service.recommend(USER_ID, 10);

        // Assert: C trước A (cùng 4 kỹ năng nhưng % cao hơn), rồi B; D bị loại vì không khớp
        assertThat(result).extracting(PositionRecommendationResponse::getTitle)
                .containsExactly("C Fullstack", "A Backend", "B Frontend");
        assertThat(result).extracting(PositionRecommendationResponse::getMatchedSkillCount)
                .containsExactly(4, 4, 1);
    }

    @Test
    void excludesPositionsWithoutAnyMatchedSkill() {
        // Arrange: CV chỉ có Java; 1 vị trí khớp, 1 vị trí chỉ yêu cầu Python/SQL
        givenCvSkills("Java");
        givenPositions(
                position(1L, "Backend Java Engineer", "Java", "Spring Boot"),
                position(2L, "Data Engineer", "Python", "SQL"));

        // Act
        List<PositionRecommendationResponse> result = service.recommend(USER_ID, 10);

        // Assert: vị trí 0 kỹ năng khớp không được trả về (không gợi ý "rác")
        assertThat(result).extracting(PositionRecommendationResponse::getTitle)
                .containsExactly("Backend Java Engineer");
    }

    @Test
    void appliesLimitToRankedResults() {
        // Arrange: 3 vị trí cùng khớp 2 kỹ năng, limit = 2
        givenCvSkills("Java", "Docker");
        givenPositions(
                position(1L, "Position A", "Java", "Docker"),
                position(2L, "Position B", "Java", "Docker"),
                position(3L, "Position C", "Java", "Docker"));

        // Act
        List<PositionRecommendationResponse> result = service.recommend(USER_ID, 2);

        // Assert: cắt sau khi xếp hạng (top N), không cắt trước khi tính điểm
        assertThat(result).hasSize(2);
    }

    @Test
    void returnsEmptyListWhenUserHasNoCv() {
        // Arrange: user chưa upload CV
        when(userCvRepository.findTopByUserIdOrderByUpdatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        // Act & Assert: trả danh sách rỗng thay vì lỗi (FE hiển thị "chưa có CV")
        assertThat(service.recommend(USER_ID, 10)).isEmpty();

        // Assert: không đọc catalog/kỹ năng khi không có CV — thoát sớm
        verifyNoInteractions(positionCatalogRepository, userSkillRepository);
    }

    @Test
    void matchPositionReturnsFullBreakdownWithoutCv() {
        // Arrange: xem chi tiết 1 vị trí nhưng user chưa có CV
        PositionCatalog position = position(5L, "DevOps Engineer", "Docker", "Kubernetes", "AWS");
        when(positionCatalogRepository.findActiveWithSkillsById(5L)).thenReturn(Optional.of(position));
        when(userCvRepository.findTopByUserIdOrderByUpdatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        // Act
        PositionRecommendationResponse match = service.matchPosition(USER_ID, 5L);

        // Assert: vẫn trả breakdown đầy đủ — 0 khớp, 0%, và liệt kê toàn bộ kỹ năng còn thiếu
        assertThat(match.getTitle()).isEqualTo("DevOps Engineer");
        assertThat(match.getMatchedSkillCount()).isZero();
        assertThat(match.getMatchPercentage()).isZero();
        assertThat(match.getMissingSkills()).containsExactly("AWS", "Docker", "Kubernetes");
    }

    @Test
    void matchPositionRejectsUnknownId() {
        // Arrange: id không tồn tại hoặc vị trí đã bị ẩn (query chỉ lấy active)
        when(positionCatalogRepository.findActiveWithSkillsById(99L)).thenReturn(Optional.empty());

        // Act & Assert: 404 để FE điều hướng về danh sách thay vì hiện trang trắng
        assertThatThrownBy(() -> service.matchPosition(USER_ID, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vị trí không tồn tại");
    }

    /** Stub CV hiện tại + danh sách kỹ năng của CV đó (service đọc kỹ năng qua userSkillRepository). */
    private void givenCvSkills(String... skillNames) {
        UserCv cv = UserCv.builder().id(CV_ID).build();
        when(userCvRepository.findTopByUserIdOrderByUpdatedAtDesc(USER_ID)).thenReturn(Optional.of(cv));
        when(userSkillRepository.findByCvId(CV_ID)).thenReturn(Arrays.stream(skillNames)
                .map(name -> UserSkill.builder().cv(cv).skillName(name).build())
                .toList());
    }

    /** Stub danh sách vị trí active mà service sẽ xếp hạng. */
    @SafeVarargs
    private void givenPositions(PositionCatalog... positions) {
        when(positionCatalogRepository.findActiveWithSkills()).thenReturn(List.of(positions));
    }

    /** Dựng PositionCatalog kèm kỹ năng đã chuẩn hoá skill_key (giống dữ liệu seed trong DB). */
    private PositionCatalog position(Long id, String title, String... requiredSkills) {
        PositionCatalog position = PositionCatalog.builder()
                .id(id)
                .title(title)
                .slug(title.toLowerCase().replace(' ', '-'))
                .level("MID")
                .description(title + " description")
                .active(true)
                .build();
        for (String skill : requiredSkills) {
            position.addRequiredSkill(skill, SkillMatcher.normalize(skill));
        }
        return position;
    }
}
