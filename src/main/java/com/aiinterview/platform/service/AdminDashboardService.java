package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.response.DashboardResponse;

public interface AdminDashboardService {

    /** All numbers in one response; {@code rangeDays ∈ {7, 30, 90}} (validated in the controller). */
    DashboardResponse getDashboard(int rangeDays);
}
