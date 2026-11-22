package com.aiinterview.platform.model.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract of the wizard payload (Postman folder 04). Structural bounds live here, not in
 * JobServiceImpl, so a wrong request is rejected before any repository is touched. The rules that
 * need cross-field context (salaryMin &gt; salaryMax, AI screening without track) stay in the
 * service test.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class JobRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        // Tao validator that (Hibernate Validator qua jakarta.validation) mot lan cho ca class
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        // Dong factory de giai phong tai nguyen sau khi het class
        factory.close();
    }

    @Test
    @DisplayName("Tieu de va ten cong ty bat buoc")
    void titleAndCompanyName_areRequired() {
        // Arrange: title chi co khoang trang (@NotBlank), companyName = null (@NotNull/@NotBlank)
        JobRequest request = request();
        request.setTitle("   ");
        request.setCompanyName(null);

        // Act & Assert: ca 2 thong bao deu phai co (khong dung o loi dau tien)
        assertThat(messages(request))
                .contains("Tiêu đề không được để trống", "Tên công ty không được để trống");
    }

    @Test
    @DisplayName("Nguong diem dau AI nam trong 50..95")
    void aiScreeningPassScore_mustStayWithin50And95() {
        // Arrange: 1 request vuot tran (96), 1 request duoi san (49)
        JobRequest tooHigh = request();
        tooHigh.setAiScreeningPassScore(96);
        JobRequest tooLow = request();
        tooLow.setAiScreeningPassScore(49);

        // Act & Assert: moi gia tri vi pham tra dung message cua no
        assertThat(messages(tooHigh)).contains("Ngưỡng điểm đậu tối đa là 95");
        assertThat(messages(tooLow)).contains("Ngưỡng điểm đậu tối thiểu là 50");
    }

    @Test
    @DisplayName("Nguong diem dau AI bang dung bien van hop le")
    void aiScreeningPassScore_acceptsBoundaryValues() {
        // Arrange: 2 gia tri bien 50 va 95
        JobRequest lower = request();
        lower.setAiScreeningPassScore(50);
        JobRequest upper = request();
        upper.setAiScreeningPassScore(95);

        // Act & Assert: bien phai duoc chap nhan (min/max la bao gom), khong co violation nao
        assertThat(messages(lower)).isEmpty();
        assertThat(messages(upper)).isEmpty();
    }

    @Test
    @DisplayName("Luong phai la so duong")
    void salary_mustBePositive() {
        // Arrange: salaryMin = 0 va salaryMax = -1
        JobRequest request = request();
        request.setSalaryMin(0L);
        request.setSalaryMax(-1L);

        // Act & Assert: @Positive chan ca 0 lan so am, moi truong 1 message
        assertThat(messages(request))
                .contains("Lương tối thiểu phải là số dương", "Lương tối đa phải là số dương");
    }

    @Test
    @DisplayName("Cac truong mo ta ngan bi gioi han do dai")
    void descriptiveFields_areLengthLimited() {
        // Arrange: moi truong dai hon gioi han cua no dung 1 ky tu
        JobRequest request = request();
        request.setDepartment("x".repeat(101));
        request.setExperienceLevel("x".repeat(101));
        request.setEmploymentType("x".repeat(101));
        request.setTimezoneRequirement("x".repeat(101));
        request.setSalaryCurrency("VNDONGNAM");
        request.setEquityBand("x".repeat(201));
        request.setAnnualBonus("x".repeat(201));
        request.setAiQuestionFocusAreas("x".repeat(2001));

        // Act & Assert: 8 message tuong ung 8 truong (100/100/100/100/8/200/200/2000)
        assertThat(messages(request)).contains(
                "Phòng ban tối đa 100 ký tự",
                "Cấp bậc kinh nghiệm tối đa 100 ký tự",
                "Loại hình lao động tối đa 100 ký tự",
                "Yêu cầu múi giờ tối đa 100 ký tự",
                "Mã tiền tệ tối đa 8 ký tự",
                "Dải cổ phần tối đa 200 ký tự",
                "Thưởng thường niên tối đa 200 ký tự",
                "Danh sách trọng tâm câu hỏi tối đa 2000 ký tự");
    }

    /** Payload hop le toi thieu; tung test chi lam sai dung truong no quan tam. */
    private JobRequest request() {
        JobRequest request = new JobRequest();
        request.setTitle("Senior Backend Engineer");
        request.setCompanyName("Acme");
        request.setSalaryMin(200_000L);
        request.setSalaryMax(300_000L);
        return request;
    }

    /** Chay Bean Validation va tra ve tap message loi (Set de khu trung lap). */
    private Set<String> messages(JobRequest request) {
        Set<ConstraintViolation<JobRequest>> violations = validator.validate(request);
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
