package com.ayojon.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

/** F4: a voucher with live stock and claimability. */
public interface VoucherOffer {
    Long getVoucherId();
    String getPromoCode();
    String getDescription();
    BigDecimal getDiscountPercent();
    BigDecimal getMaxDiscountAmount();
    Integer getMinPointsRequired();
    Instant getValidUntil();
    String getBookingUrl();
    String getCampaignName();
    String getSponsorName();
    String getLogoUrl();
    /** null when stock is unlimited. */
    Long getRemaining();
    Boolean getClaimable();
}
