package com.aiinterview.platform.model.repository;

import com.aiinterview.platform.model.entity.PositionCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionCatalogRepository extends JpaRepository<PositionCatalog, Long> {

    /** Fetch join de tranh N+1 khi tinh diem recommendation (open-in-view = false). */
    @Query("select distinct p from PositionCatalog p left join fetch p.requiredSkills where p.active = true")
    List<PositionCatalog> findActiveWithSkills();

    @Query("select distinct p from PositionCatalog p left join fetch p.requiredSkills "
            + "where p.id = :id and p.active = true")
    Optional<PositionCatalog> findActiveWithSkillsById(@Param("id") Long id);

    /** Validate vị trí đích lúc user chọn (không kéo theo requiredSkills). */
    Optional<PositionCatalog> findByIdAndActiveTrue(Long id);

    /** Dung cho admin: lay ca vi tri da ngung hoat dong. */
    @Query("select distinct p from PositionCatalog p left join fetch p.requiredSkills where p.id = :id")
    Optional<PositionCatalog> findWithSkillsById(@Param("id") Long id);

    @Query("select p from PositionCatalog p "
            + "where (:search is null or lower(p.title) like lower(concat('%', :search, '%'))) "
            + "and (:active is null or p.active = :active)")
    Page<PositionCatalog> search(@Param("search") String search,
                                 @Param("active") Boolean active,
                                 Pageable pageable);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /** Bo sung JD cho vi tri seed san co ma chua co JD (chi cap nhat khi dang trong). */
    @Modifying
    @Transactional
    @Query("update PositionCatalog p set p.jdText = :jdText "
            + "where p.slug = :slug and (p.jdText is null or p.jdText = '')")
    int backfillJdText(@Param("slug") String slug, @Param("jdText") String jdText);
}
