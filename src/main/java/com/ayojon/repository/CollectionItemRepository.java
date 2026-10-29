package com.ayojon.repository;

import com.ayojon.entity.CollectionItem;
import com.ayojon.entity.CollectionItemId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, CollectionItemId> {

    List<CollectionItem> findByCollectionIdOrderByAddedAtDesc(Long collectionId);
}
