package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TopCvImportRequest {

    @NotBlank(message = "URL không được để trống")
    @Size(max = 2000, message = "URL tối đa 2000 ký tự")
    private String url;
}
