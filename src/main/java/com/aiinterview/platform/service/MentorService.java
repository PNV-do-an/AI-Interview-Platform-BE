package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.response.MentorResponse;

import java.util.List;

public interface MentorService {
    /** Danh sách mentor (nha tuyen dung / chuyen gia) dang hoat dong, kem diem danh gia. */
    List<MentorResponse> listMentors();
}
