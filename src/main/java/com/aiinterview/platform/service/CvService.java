package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.response.CvParseResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.UserSkill;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CvService {

    CvParseResponse uploadCv(MultipartFile file, User user);

    CvParseResponse parseCvText(String cvText, User user);

    CvParseResponse getCv(Long userId);

    CvParseResponse setPosition(Long userId, String position, String level);

    List<UserSkill> getCvSkills(Long userId);
}