package com.ayojon.repository;

import com.ayojon.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByTagNameIn(Collection<String> tagNames);
}
