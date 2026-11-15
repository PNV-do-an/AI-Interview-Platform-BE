package com.aiinterview.platform.service.interview;

import com.aiinterview.platform.model.dto.request.SlotRequest;
import com.aiinterview.platform.model.dto.response.SlotResponse;

import java.util.List;

public interface SlotService {

    List<SlotResponse> getMySlots();

    List<SlotResponse> getAvailableSlots(Long employerId);

    SlotResponse createSlot(SlotRequest request);

    SlotResponse updateSlot(Long slotId, SlotRequest request);

    void cancelSlot(Long slotId);
}
