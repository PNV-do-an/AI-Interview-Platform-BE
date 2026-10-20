package com.aiinterview.platform.model.dto.request;

import com.aiinterview.platform.model.enums.AiScreeningTrack;
import com.aiinterview.platform.model.enums.WorkplaceModel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotBlank(message = "Tên công ty không được để trống")
    private String companyName;

    private String position;

    private String location;

    private String salaryRange;

    private String description;

    private String requirements;

    /** Comma-separated skills */
    private String requiredSkills;

    @Size(max = 100, message = "Phòng ban tối đa 100 ký tự")
    private String department;

    @Size(max = 100, message = "Cấp bậc kinh nghiệm tối đa 100 ký tự")
    private String experienceLevel;

    @Size(max = 100, message = "Loại hình lao động tối đa 100 ký tự")
    private String employmentType;

    private WorkplaceModel workplaceModel;

    @Size(max = 100, message = "Yêu cầu múi giờ tối đa 100 ký tự")
    private String timezoneRequirement;

    @Size(max = 8, message = "Mã tiền tệ tối đa 8 ký tự")
    private String salaryCurrency;

    @Positive(message = "Lương tối thiểu phải là số dương")
    private Long salaryMin;

    @Positive(message = "Lương tối đa phải là số dương")
    private Long salaryMax;

    @Size(max = 200, message = "Dải cổ phần tối đa 200 ký tự")
    private String equityBand;

    @Size(max = 200, message = "Thưởng thường niên tối đa 200 ký tự")
    private String annualBonus;

    private Boolean aiScreeningEnabled;

    private AiScreeningTrack aiScreeningTrack;

    @Min(value = 50, message = "Ngưỡng điểm đậu tối thiểu là 50")
    @Max(value = 95, message = "Ngưỡng điểm đậu tối đa là 95")
    private Integer aiScreeningPassScore;

    /** Comma-separated focus areas */
    @Size(max = 2000, message = "Danh sách trọng tâm câu hỏi tối đa 2000 ký tự")
    private String aiQuestionFocusAreas;
}
