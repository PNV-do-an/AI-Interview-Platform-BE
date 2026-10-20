package com.aiinterview.platform.model.enums;

/**
 * Closed set of AI autonomous screening formats. Free-form track names would make the
 * pass-score threshold meaningless, so the wizard maps its UI options onto these values.
 */
public enum AiScreeningTrack {
    LIVE_CODING,
    SYSTEM_DESIGN,
    ASYNC_TECHNICAL,
    CULTURAL_FIT
}
