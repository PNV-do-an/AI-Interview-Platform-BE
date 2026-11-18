package com.aiinterview.platform.model.dto.request;

import lombok.Data;

@Data
public class TopupWebhookRequest {
    private Long userId;
    private long amount;
    private String providerTxId;
}
