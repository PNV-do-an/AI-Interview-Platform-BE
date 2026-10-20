package com.aiinterview.platform.model.dto.response;

import lombok.Builder;

import java.util.List;

/**
 * Result of a bulk admin action: each id is handled on its own, so a rejected id never
 * blocks the others (spec 008 US-03).
 */
@Builder
public record BulkUserActionResult(int requested, int succeeded, List<FailedItem> failed) {

    public record FailedItem(Long id, String message) {
    }
}
