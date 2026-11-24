package com.aiinterview.platform.model.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/** Payload webhook payOS: {code, desc, success, data:{...}, signature}. */
@Data
public class PayosWebhookRequest {
    private String code;
    private String desc;
    private Boolean success;
    private JsonNode data;
    private String signature;
}
