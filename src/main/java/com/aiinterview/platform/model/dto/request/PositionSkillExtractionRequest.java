package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Body cho buoc preview: admin dan JD, AI tra ve danh sach ky nang yeu cau. */
@Data
public class PositionSkillExtractionRequest {

    @NotBlank(message = "JD text không được để trống")
    @Size(max = 20000, message = "JD tối đa 20000 ký tự")
    private String jdText;
}
