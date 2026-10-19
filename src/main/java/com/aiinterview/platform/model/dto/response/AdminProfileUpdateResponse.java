package com.aiinterview.platform.model.dto.response;

/**
 * Result of {@code PATCH /api/v1/admin/users/{id}}.
 *
 * <p>{@code emailChanged} tells the frontend that the account went back to "pending invite":
 * a new address has never been proven, so the BE disabled it, issued a fresh invitation link and
 * invalidated the member's existing tokens. The message the admin sees has to say that, which is
 * why the flag travels with the row instead of being guessed from the status.
 */
public record AdminProfileUpdateResponse(
        UserDetailResponse user,
        boolean emailChanged
) {
}
