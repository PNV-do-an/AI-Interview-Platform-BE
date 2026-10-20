package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of UserRepository backed by MySQL.
 * Marked @Primary so Spring prefers this over FakeUserRepository when both are on the classpath.
 */
@Repository
@Primary
public interface JpaUserRepository extends JpaRepository<User, Long>, UserRepository {

    /**
     * Plan 002 D1: the list, the tab badges and the CSV export must answer "which rows" from one
     * predicate, otherwise a badge count drifts away from the table below it as soon as a search
     * term is typed. These fragments are the only place that answers it.
     */
    String SEARCH_PREDICATE = """
        (:search IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '\\'
                               OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '\\')
        """;

    String ROLE_PREDICATE = "(:role IS NULL OR u.role = :role)";

    /** {@code PENDING} = account exists but the verification link has never been used (Clarify C-2). */
    String STATUS_PREDICATE = """
        (:status IS NULL
          OR (:status = 'ACTIVE'   AND u.enabled = true  AND u.locked = false)
          OR (:status = 'INACTIVE' AND u.enabled = false)
          OR (:status = 'LOCKED'   AND u.locked = true)
          OR (:status = 'PENDING'  AND u.verificationToken IS NOT NULL))
        """;

    String LIVE_FILTERED = "u.deletedAt IS NULL AND " + SEARCH_PREDICATE
            + " AND " + ROLE_PREDICATE + " AND " + STATUS_PREDICATE;

    /** Live accounts without the status dimension — used when every status is counted at once. */
    String LIVE_FILTER = "u.deletedAt IS NULL AND " + SEARCH_PREDICATE + " AND " + ROLE_PREDICATE;

    String DELETED_FILTERED = "u.deletedAt IS NOT NULL AND " + SEARCH_PREDICATE + " AND " + ROLE_PREDICATE;

    @Override
    Optional<User> findByEmail(String email);

    @Override
    boolean existsByEmail(String email);

    @Override
    Optional<User> findByVerificationToken(String verificationToken);

    @Override
    Optional<User> findByResetToken(String resetToken);

    /**
     * Admin list query for live accounts: search by email/fullName, filter by status and role.
     * statusFilter values: "ACTIVE", "INACTIVE", "LOCKED", "PENDING" — handled via JPQL CASE logic.
     * {@code search} is escaped by the caller and matched with ESCAPE '\'.
     */
    @Override
    @Query("SELECT u FROM User u WHERE " + LIVE_FILTERED)
    Page<User> findAllWithFilters(
            @Param("search") String search,
            @Param("role") Role role,
            @Param("status") String status,
            Pageable pageable
    );

    @Override
    @Query("SELECT u FROM User u WHERE " + DELETED_FILTERED)
    Page<User> findDeletedWithFilters(
            @Param("search") String search,
            @Param("role") Role role,
            Pageable pageable
    );

    /**
     * One row of tab badges: total, active, pendingVerification, locked, disabled — every number
     * counted by the same {@link #LIVE_FILTER} predicate the list uses (plan 002 D1 / AC-1).
     */
    @Override
    @Query("""
        SELECT COUNT(u.id),
               COALESCE(SUM(CASE WHEN u.enabled = true AND u.locked = false THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN u.verificationToken IS NOT NULL THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN u.locked = true THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN u.enabled = false THEN 1 ELSE 0 END), 0)
        FROM User u
        WHERE
        """ + LIVE_FILTER)
    List<Object[]> countLiveMetrics(
            @Param("search") String search,
            @Param("role") Role role
    );

    @Override
    @Query("SELECT COUNT(u.id) FROM User u WHERE " + DELETED_FILTERED)
    long countDeleted(
            @Param("search") String search,
            @Param("role") Role role
    );

    /**
     * KPI "active in the last 30 days". Clarify C-1 derives it from {@code login_attempts}, so the
     * question is "has a successful login since :since", not "is online".
     */
    @Override
    @Query("""
        SELECT COUNT(u.id) FROM User u
        WHERE
        """ + LIVE_FILTER + """
          AND EXISTS (SELECT 1 FROM LoginAttempt la
                      WHERE la.email = u.email AND la.success = true AND la.timestamp >= :since)
        """)
    long countWithRecentLogin(
            @Param("search") String search,
            @Param("role") Role role,
            @Param("since") LocalDateTime since
    );

    @Override
    long countByRoleAndDeletedAtIsNull(Role role);

    @Override
    @Query("SELECT u.role, COUNT(u.id) FROM User u WHERE u.deletedAt IS NULL GROUP BY u.role")
    List<Object[]> countLiveByRole();

    @Override
    long countByCreatedAtAfterAndDeletedAtIsNull(LocalDateTime since);
}
