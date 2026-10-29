package com.ayojon.repository;

import com.ayojon.entity.Voucher;
import com.ayojon.repository.projection.VoucherOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    Optional<Voucher> findByPromoCode(String promoCode);

    /** F4 vouchers that can be claimed right now, soonest expiry first. */
    @Query(value = """
            SELECT voucher_id AS voucherId, promo_code AS promoCode, description,
                   discount_percent AS discountPercent, max_discount_amount AS maxDiscountAmount,
                   min_points_required AS minPointsRequired, valid_until AS validUntil, booking_url AS bookingUrl,
                   campaign_name AS campaignName, sponsor_name AS sponsorName, logo_url AS logoUrl,
                   remaining, is_claimable AS claimable
              FROM v_voucher_availability WHERE is_claimable ORDER BY valid_until
            """, nativeQuery = true)
    List<VoucherOffer> findClaimable();
}
