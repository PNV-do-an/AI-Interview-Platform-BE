package com.aiinterview.platform.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPositionResponse {

    private Long id;
    private String title;
    private String slug;
    private String level;
    private String description;
    /** JD goc admin da nhap (co the null neu vi tri chi co danh sach ky nang). */
    private String jdText;
    private Boolean active;

    @Builder.Default
    private List<String> requiredSkills = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
