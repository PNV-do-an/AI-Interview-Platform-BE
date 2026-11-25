package com.aiinterview.platform.service.document;

import com.aiinterview.platform.model.dto.response.CvParseResponse;
import com.aiinterview.platform.model.dto.response.SkillResponse;
import com.aiinterview.platform.model.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CvService {

    /**
     * @param targetPositionId vị trí đích user chọn trước khi upload; null = không chọn (client cũ)
     */
    CvParseResponse uploadCv(MultipartFile file, User user, Long targetPositionId);

    CvParseResponse parseCvText(String cvText, User user, Long targetPositionId);

    CvParseResponse getCv(Long userId);

    CvParseResponse setPosition(Long userId, Long positionId, String position, String level);

    List<SkillResponse> getCvSkills(Long userId);
}
