package com.ayojon.repository;

import com.ayojon.entity.VoucherClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoucherClaimRepository extends JpaRepository<VoucherClaim, Long> {

    List<VoucherClaim> findByUserIdOrderByClaimedAtDesc(Long userId);

    boolean existsByUserIdAndVoucherId(Long userId, Long voucherId);
}
