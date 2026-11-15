package com.aiinterview.platform.service.job;

import com.aiinterview.platform.model.dto.response.MatchResponse;
import com.aiinterview.platform.model.entity.JobDescription;

import java.util.List;

public interface MatchingService {

    /** Suggests candidates whose skills overlap with the job's required skills, sorted by score. */
    List<MatchResponse> matchCandidates(JobDescription job);
}
