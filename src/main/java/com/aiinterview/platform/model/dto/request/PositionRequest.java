package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PositionRequest {

    @NotBlank(message = "Tên vị trí không được để trống")
    @Size(max = 100, message = "Tên vị trí tối đa 100 ký tự")
    private String title;

    @Size(max = 50, message = "Level tối đa 50 ký tự")
    private String level;

    @Size(max = 4000, message = "Mô tả tối đa 4000 ký tự")
    private String description;

    /** JD tho do admin nhap; AI se trich xuat ky nang yeu cau khi khong truyen skills. */
    @Size(max = 20000, message = "JD tối đa 20000 ký tự")
    private String jdText;

    /** Neu co, dung truc tiep (bo qua AI); neu trong, AI trich xuat tu jdText. */
    private List<@NotBlank(message = "Kỹ năng không được để trống")
            @Size(max = 100, message = "Mỗi kỹ năng tối đa 100 ký tự") String> skills;
}
