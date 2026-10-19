package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByVerificationToken(String verificationToken);
    Optional<User> findByResetToken(String resetToken);
    User save(User user);
    Optional<User> findById(Long id);
    List<User> findAllById(Iterable<Long> ids);
    void deleteAll();

    /** Send pending INSERTs to the DB now, so a unique-constraint clash surfaces inside the caller. */
    void flush();

    /** Live accounts only ({@code deleted_at IS NULL}). {@code search} must already be LIKE-escaped. */
    Page<User> findAllWithFilters(String search, Role role, String status, Pageable pageable);

    /** Soft-deleted accounts only. {@code search} must already be LIKE-escaped. */
    Page<User> findDeletedWithFilters(String search, Role role, Pageable pageable);

    /**
     * Live accounts counted by status in one pass; the single row is
     * {@code [total, active, pendingVerification, locked, disabled]} — all Long.
     */
    List<Object[]> countLiveMetrics(String search, Role role);

    long countDeleted(String search, Role role);

    /** Live accounts with at least one successful {@code login_attempts} row since {@code since}. */
    long countWithRecentLogin(String search, Role role, LocalDateTime since);

    long countByRoleAndDeletedAtIsNull(Role role);
}
