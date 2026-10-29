package com.ayojon.repository;

import com.ayojon.entity.AdCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdCampaignRepository extends JpaRepository<AdCampaign, Long> {

    List<AdCampaign> findBySponsorId(Long sponsorId);
}
