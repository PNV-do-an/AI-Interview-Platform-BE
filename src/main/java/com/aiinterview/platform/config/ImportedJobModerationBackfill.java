package com.aiinterview.platform.config;

import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.JobModerationRecord;
import com.aiinterview.platform.model.enums.ModerationAction;
import com.aiinterview.platform.model.enums.ModerationActorRole;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.JobModerationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bổ sung bản ghi lịch sử kiểm duyệt cho các JD IT đã được đồng bộ tự động trước khi
 * backend biết ghi lịch sử. Trước đây các JD này được đăng thẳng (OPEN) nên không có
 * quyết định APPROVED nào, vì vậy chúng không xuất hiện ở màn "Lịch sử kiểm duyệt".
 * Chỉ chạy lúc khởi động và bỏ qua JD đã có bản ghi APPROVED.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImportedJobModerationBackfill implements ApplicationRunner {

    private static final String NOTE = "Đồng bộ JD IT tự động";

    private final JobDescriptionRepository jobRepository;
    private final JobModerationRecordRepository moderationRecordRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<JobDescription> jobs = jobRepository.findAutoPublishedAdminJobs();
        if (jobs.isEmpty()) {
            return;
        }

        int created = 0;
        for (JobDescription job : jobs) {
            if (moderationRecordRepository.existsByJobIdAndAction(job.getId(), ModerationAction.APPROVED)) {
                continue;
            }
            moderationRecordRepository.save(JobModerationRecord.builder()
                    .job(job)
                    .actor(job.getEmployer())
                    .actorRole(ModerationActorRole.ADMIN)
                    .action(ModerationAction.APPROVED)
                    .note(NOTE)
                    .processingSeconds(null)
                    .build());
            created++;
        }

        if (created > 0) {
            log.info("Backfilled {} moderation history records for auto-imported jobs", created);
        }
    }
}
