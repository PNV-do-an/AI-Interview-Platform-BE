package com.aiinterview.platform.service.admin.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.common.util.SkillMatcher;
import com.aiinterview.platform.model.dto.request.PositionRequest;
import com.aiinterview.platform.model.dto.response.AdminPositionResponse;
import com.aiinterview.platform.model.entity.PositionCatalog;
import com.aiinterview.platform.model.repository.PositionCatalogRepository;
import com.aiinterview.platform.service.admin.impl.AdminPositionServiceImpl;
import com.aiinterview.platform.service.infra.OpenAiClient;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Quản trị danh mục vị trí (position catalog) — nguồn dữ liệu cho gợi ý việc làm và bộ lọc.
 * Hai điểm dễ sai: kỹ năng do admin nhập phải được chuẩn hoá/khử trùng trước khi lưu (nếu không
 * gợi ý sẽ đếm trùng), và chỉ gọi AI khi admin không nhập kỹ năng nào (tránh đốt token vô ích).
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class AdminPositionServiceImplTest {

    private AdminPositionServiceImpl service;
    private PositionCatalogRepository positionCatalogRepository;
    private OpenAiClient openAiClient;

    @BeforeEach
    void setUp() {
        positionCatalogRepository = mock(PositionCatalogRepository.class);
        openAiClient = mock(OpenAiClient.class);
        service = new AdminPositionServiceImpl(positionCatalogRepository, openAiClient);
        // save trả lại chính entity để response đọc được id/field sau khi lưu
        when(positionCatalogRepository.save(any(PositionCatalog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createUsesExplicitSkillsAndDeduplicatesThem() {
        // Arrange: admin nhập tay "Java", " java " (trùng sau khi trim/không phân biệt hoa thường)
        PositionRequest request = request("Backend Java Engineer");
        request.setSkills(List.of("Java", " java ", "Spring Boot"));

        // Act
        AdminPositionResponse response = service.createPosition(request);

        // Assert: chỉ còn 2 kỹ năng duy nhất, slug sinh từ title
        assertThat(response.getRequiredSkills()).containsExactly("Java", "Spring Boot");
        assertThat(response.getSlug()).isEqualTo("backend-java-engineer");
        assertThat(response.getActive()).isTrue();

        // Assert: đã có kỹ năng nhập tay thì không được gọi AI (tiết kiệm token)
        verifyNoInteractions(openAiClient);
    }

    @Test
    void createExtractsSkillsFromJdWhenSkillsAreMissing() {
        // Arrange: chỉ có JD, chưa có kỹ năng; AI trả 3 mục nhưng "kotlin" trùng "Kotlin"
        PositionRequest request = request("Android Engineer");
        request.setJdText("We need Kotlin and Jetpack Compose experience");
        when(openAiClient.extractRequiredSkills(request.getJdText()))
                .thenReturn(List.of("Kotlin", "Jetpack Compose", "kotlin"));

        // Act
        AdminPositionResponse response = service.createPosition(request);

        // Assert: kết quả AI cũng phải được khử trùng, giữ thứ tự xuất hiện
        assertThat(response.getRequiredSkills()).containsExactly("Kotlin", "Jetpack Compose");
        verify(openAiClient).extractRequiredSkills(request.getJdText());
    }

    @Test
    void createRejectsRequestWithoutSkillsAndJd() {
        // Arrange: request không có skills và cũng không có jdText

        // Act & Assert: từ chối ngay, không tạo vị trí rỗng trong catalog
        assertThatThrownBy(() -> service.createPosition(request("Data Engineer")))
                .isInstanceOf(BadRequestException.class);

        // Assert: không ghi DB
        verify(positionCatalogRepository, never()).save(any(PositionCatalog.class));
    }

    @Test
    void createStoresJdTextForLaterEditing() {
        // Arrange: jdText gửi lên có khoảng trắng thừa hai đầu
        PositionRequest request = request("Backend Java Engineer");
        request.setJdText("  3+ years Java, Spring Boot, MySQL  ");
        request.setSkills(List.of("Java"));

        // Act
        AdminPositionResponse response = service.createPosition(request);

        // Assert: lưu bản đã trim để admin mở lại form thấy dữ liệu sạch
        assertThat(response.getJdText()).isEqualTo("3+ years Java, Spring Boot, MySQL");
    }

    @Test
    void extractSkillsCleansAiOutput() {
        // Arrange: AI trả bẩn — có khoảng trắng, trùng lặp và phần tử rỗng
        when(openAiClient.extractRequiredSkills("Kotlin, Compose"))
                .thenReturn(List.of(" Kotlin ", "kotlin", "", "Jetpack Compose"));

        // Act: JD gửi vào có khoảng trắng hai đầu, phải được trim trước khi gọi AI
        List<String> skills = service.extractSkills("  Kotlin, Compose  ");

        // Assert: bỏ phần tử rỗng, trim, khử trùng
        assertThat(skills).containsExactly("Kotlin", "Jetpack Compose");
        verify(openAiClient).extractRequiredSkills("Kotlin, Compose");
    }

    @Test
    void extractSkillsRejectsBlankJd() {
        // Arrange: jdText chỉ có khoảng trắng

        // Act & Assert: báo lỗi 400 thay vì gọi AI bằng chuỗi rỗng
        assertThatThrownBy(() -> service.extractSkills("   "))
                .isInstanceOf(BadRequestException.class);

        // Assert: không tốn lời gọi AI nào
        verifyNoInteractions(openAiClient);
    }

    @Test
    void createAddsSuffixWhenSlugAlreadyExists() {
        // Arrange: slug gốc đã tồn tại, bản "-2" còn trống
        PositionRequest request = request("Backend Java Engineer");
        request.setSkills(List.of("Java"));
        when(positionCatalogRepository.existsBySlug("backend-java-engineer")).thenReturn(true);
        when(positionCatalogRepository.existsBySlug("backend-java-engineer-2")).thenReturn(false);

        // Act
        AdminPositionResponse response = service.createPosition(request);

        // Assert: slug duy nhất (URL công khai không được trùng) nhưng title giữ nguyên
        assertThat(response.getSlug()).isEqualTo("backend-java-engineer-2");
    }

    @Test
    void updateKeepsExistingSkillsWhenNeitherSkillsNorJdProvided() {
        // Arrange: update chỉ đổi thông tin mô tả, không gửi skills lẫn jdText
        PositionCatalog position = position(3L, "DevOps Engineer", "Docker", "Kubernetes");
        when(positionCatalogRepository.findWithSkillsById(3L)).thenReturn(Optional.of(position));
        PositionRequest request = request("DevOps Engineer");

        // Act
        AdminPositionResponse response = service.updatePosition(3L, request);

        // Assert: kỹ năng cũ được giữ nguyên, không bị xoá trắng
        assertThat(response.getRequiredSkills()).containsExactly("Docker", "Kubernetes");

        // Assert: không gọi AI khi admin không yêu cầu trích xuất lại
        verifyNoInteractions(openAiClient);
    }

    @Test
    void updateReplacesSkillsAndRemovesTheMissingOnes() {
        // Arrange: vị trí cũ có Python + SQL, admin đổi sang Python + Apache Spark
        PositionCatalog position = position(4L, "Data Engineer", "Python", "SQL");
        when(positionCatalogRepository.findWithSkillsById(4L)).thenReturn(Optional.of(position));
        PositionRequest request = request("Data Engineer");
        request.setSkills(List.of("Python", "Apache Spark"));

        // Act
        AdminPositionResponse response = service.updatePosition(4L, request);

        // Assert: kỹ năng mới thay thế hoàn toàn, không còn SQL sót lại
        assertThat(response.getRequiredSkills()).containsExactly("Python", "Apache Spark");

        // Assert: skill_key cũng được chuẩn hoá lại (gợi ý so khớp bằng key, không bằng tên hiển thị)
        assertThat(position.getRequiredSkills()).extracting(skill -> skill.getSkillKey())
                .containsExactly("python", SkillMatcher.normalize("Apache Spark"));
    }

    @Test
    void changeStatusTogglesActiveFlag() {
        // Arrange: vị trí đang active
        PositionCatalog position = position(5L, "QA Automation Engineer", "Java");
        when(positionCatalogRepository.findWithSkillsById(5L)).thenReturn(Optional.of(position));

        // Act: admin đặt active = false (ẩn vị trí khỏi gợi ý / bộ lọc)
        AdminPositionResponse response = service.changeStatus(5L, false);

        // Assert: cả response lẫn entity đều đổi trạng thái
        assertThat(response.getActive()).isFalse();
        assertThat(position.getActive()).isFalse();
    }

    @Test
    void getPositionRejectsUnknownId() {
        // Arrange: id không có trong catalog
        when(positionCatalogRepository.findWithSkillsById(99L)).thenReturn(Optional.empty());

        // Act & Assert: 404 thay vì trả null/response rỗng
        assertThatThrownBy(() -> service.getPosition(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Payload tối thiểu hợp lệ: title + level + description; test tự thêm skills/jdText khi cần. */
    private PositionRequest request(String title) {
        PositionRequest request = new PositionRequest();
        request.setTitle(title);
        request.setLevel("MID");
        request.setDescription(title + " description");
        return request;
    }

    /** Dựng PositionCatalog kèm kỹ năng và skill_key đã chuẩn hoá (giống dữ liệu thật trong DB). */
    private PositionCatalog position(Long id, String title, String... skills) {
        PositionCatalog position = PositionCatalog.builder()
                .id(id)
                .title(title)
                .slug(title.toLowerCase().replace(' ', '-'))
                .level("MID")
                .active(true)
                .build();
        for (String skill : skills) {
            position.addRequiredSkill(skill, SkillMatcher.normalize(skill));
        }
        return position;
    }
}
