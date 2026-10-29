package com.ayojon.repository;

import com.ayojon.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AreaRepository extends JpaRepository<Area, Long> {

    List<Area> findAllByOrderByAreaNameAsc();
}
