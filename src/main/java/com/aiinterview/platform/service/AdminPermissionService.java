package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.response.PermissionMatrixResponse;

public interface AdminPermissionService {

    /** Static matrix describing the hard-coded permissions; userCount is real (one GROUP BY). */
    PermissionMatrixResponse getMatrix();
}
