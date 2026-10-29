package com.ayojon.repository;

import com.ayojon.entity.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionRepository extends JpaRepository<Collection, Long> {

    List<Collection> findByUserIdOrderByCreatedAtAsc(Long userId);

    /** Public collections only — use when showing someone else's profile. */
    List<Collection> findByUserIdAndPrivateCollectionFalse(Long userId);
}
