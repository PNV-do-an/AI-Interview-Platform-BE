package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.InterviewReport;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InterviewReportResponse {

    private Long id;
    private Long sessionId;
    private Integer chuyenMon;
    private Integer duAn;
    private Integer tuDuy;
    private Integer truyenDat;
    private Integer mucKhop;
    /** Trung bình cộng 5 chiều, BE làm tròn — không phải value client gửi lên. */
    private Integer overallScore;
    private String strengths;
    private String improvements;
    private String rubricVersion;
    private LocalDateTime createdAt;

    public static InterviewReportResponse from(InterviewReport r) {
        return InterviewReportResponse.builder()
                .id(r.getId())
                .sessionId(r.getSession().getId())
                .chuyenMon(r.getChuyenMon())
                .duAn(r.getDuAn())
                .tuDuy(r.getTuDuy())
                .truyenDat(r.getTruyenDat())
                .mucKhop(r.getMucKhop())
                .overallScore(r.getOverallScore())
                .strengths(r.getStrengths())
                .improvements(r.getImprovements())
                .rubricVersion(r.getRubricVersion())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
