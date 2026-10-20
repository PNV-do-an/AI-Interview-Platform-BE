package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    List<LoginAttempt> findByEmailAndSuccessFalseAndTimestampAfter(String email, LocalDateTime since);
    long countByIpAddressAndSuccessFalseAndTimestampAfter(String ip, LocalDateTime since);

    /**
     * Last successful login for every email of the current page in one read
     * (plan 002 R-3: a query per row would be 100 reads per page).
     * Rows are {@code [email, MAX(created_at)]}.
     */
    @Query("""
        SELECT la.email, MAX(la.timestamp) FROM LoginAttempt la
        WHERE la.success = true AND la.email IN :emails
        GROUP BY la.email
        """)
    List<Object[]> findLastSuccessByEmailIn(@Param("emails") Collection<String> emails);

    long countBySuccessTrueAndTimestampAfter(LocalDateTime since);

    long countBySuccessFalseAndTimestampAfter(LocalDateTime since);

    /** Distinct emails with a successful login in the window — the "loginsInPeriod" KPI (002 C-1). */
    @Query("SELECT COUNT(DISTINCT la.email) FROM LoginAttempt la "
            + "WHERE la.success = true AND la.timestamp >= :since")
    long countDistinctSuccessEmailsSince(@Param("since") LocalDateTime since);
}
