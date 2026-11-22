package com.aiinterview.platform.model.dto.request;

import lombok.Data;

/** Payload ZaloPay gửi callback: {"data":"...json...","mac":"...","type":1}. */
@Data
public class ZalopayCallbackRequest {
    private String data;
    private String mac;
    private Integer type;
}
