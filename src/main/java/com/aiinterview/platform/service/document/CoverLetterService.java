package com.aiinterview.platform.service.document;

import com.aiinterview.platform.model.dto.request.CoverLetterGenerateRequest;
import com.aiinterview.platform.model.dto.response.CoverLetterResponse;
import com.aiinterview.platform.model.entity.User;

import java.util.List;

public interface CoverLetterService {

    CoverLetterResponse generate(User candidate, CoverLetterGenerateRequest request);

    List<CoverLetterResponse> getMyLetters(User candidate);

    CoverLetterResponse getLetter(User candidate, Long id);

    CoverLetterResponse updateContent(User candidate, Long id, String content);

    void delete(User candidate, Long id);
}
