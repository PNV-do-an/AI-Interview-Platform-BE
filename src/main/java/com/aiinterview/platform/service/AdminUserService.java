package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.request.AdminInviteUserRequest;
import com.aiinterview.platform.model.dto.request.AdminUpdateProfileRequest;
import com.aiinterview.platform.model.dto.request.AuditLogQuery;
import com.aiinterview.platform.model.dto.request.BulkRoleChangeRequest;
import com.aiinterview.platform.model.dto.request.BulkUserActionRequest;
import com.aiinterview.platform.model.dto.request.LockAccountRequest;
import com.aiinterview.platform.model.dto.response.AdminProfileUpdateResponse;
import com.aiinterview.platform.model.dto.response.AdminUserStatsResponse;
import com.aiinterview.platform.model.dto.response.AuditLogResponse;
import com.aiinterview.platform.model.dto.response.BulkUserActionResult;
import com.aiinterview.platform.model.dto.response.UserActivityResponse;
import com.aiinterview.platform.model.dto.response.UserDetailResponse;
import com.aiinterview.platform.model.dto.response.UserSummaryResponse;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.enums.UserStatusFilter;
import org.springframework.data.domain.Page;

public interface AdminUserService {

    /** Live accounts only; {@code status=DELETED} is rejected — see {@link #getDeletedUsers}. */
    Page<UserSummaryResponse> getUsers(int page, int size, String search, UserStatusFilter status, Role role);

    Page<UserSummaryResponse> getDeletedUsers(int page, int size, String search, Role role);

    /** Tab badges: every count uses the same filter as {@link #getUsers}. */
    AdminUserStatsResponse getStats(String search, Role role);

    /** Works for soft-deleted accounts too (read-only view before restoring). */
    UserDetailResponse getUserDetail(Long userId);

    /** Merged timeline of the account's applications, mock sessions and booked slots (009 FR-003). */
    Page<UserActivityResponse> getUserActivity(Long userId, int page, int size);

    /**
     * {@code request} is optional: null keeps the pre-008 behaviour (always notify).
     * Locking an ADMIN-role account is rejected (spec 008 FR-002).
     */
    UserDetailResponse lockUser(Long targetUserId, LockAccountRequest request);

    UserDetailResponse unlockUser(Long targetUserId);

    BulkUserActionResult bulkLock(BulkUserActionRequest request);

    BulkUserActionResult bulkUnlock(BulkUserActionRequest request);

    BulkUserActionResult bulkRole(BulkRoleChangeRequest request);

    UserDetailResponse setUserEnabled(Long targetUserId, boolean enabled);

    void deleteUser(Long targetUserId);

    UserDetailResponse restoreUser(Long targetUserId);

    void resetPassword(Long targetUserId);

    UserDetailResponse changeRole(Long targetUserId, Role newRole);

    /**
     * Create an account on the member's behalf (Clarify C-4: no password comes through the API).
     * The account is inactive until the invited person uses the link in the invitation email.
     */
    UserDetailResponse inviteUser(AdminInviteUserRequest request);

    UserDetailResponse resendInvitation(Long targetUserId);

    UserDetailResponse revokeInvitation(Long targetUserId);

    AdminProfileUpdateResponse updateProfile(Long targetUserId, AdminUpdateProfileRequest request);
    /**
     * CSV of the same rows {@link #getUsers} would list, capped by the export limit.
     * Rejected with 400 when the filter matches more rows than the cap.
     */
    String exportUsers(String search, UserStatusFilter status, Role role);

    Page<AuditLogResponse> getAuditLogs(AuditLogQuery query);
}
