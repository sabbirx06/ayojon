package com.ayojon.model;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class VoucherRepository {
    private static final RowMapper<Voucher> VOUCHER_ROW = (row, index) -> new Voucher(
            row.getString("sponsor_name"), row.getString("promo_code"), row.getBigDecimal("discount_percent"));

    private final JdbcTemplate jdbc;

    public VoucherRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Offer(String sponsorName, java.math.BigDecimal discountPercent, boolean claimed) {}

    public Optional<Offer> findOffer(String promoCode, int userId) {
        return jdbc.query("""
                SELECT s.sponsor_name, v.discount_percent, c.user_id IS NOT NULL AS claimed
                FROM voucher v
                JOIN sponsor s ON s.sponsor_id = v.sponsor_id
                LEFT JOIN voucher_claim c ON c.voucher_id = v.voucher_id AND c.user_id = ?
                WHERE v.promo_code = ? AND (v.valid_until > LOCALTIMESTAMP OR c.user_id IS NOT NULL)
                """, (row, index) -> new Offer(row.getString("sponsor_name"),
                        row.getBigDecimal("discount_percent"), row.getBoolean("claimed")), userId, promoCode)
                .stream().findFirst();
    }

    public boolean claim(String promoCode, int userId) {
        int inserted = jdbc.update("""
                INSERT INTO voucher_claim (user_id, voucher_id)
                SELECT ?, voucher_id FROM voucher
                WHERE promo_code = ? AND valid_until > LOCALTIMESTAMP
                ON CONFLICT (user_id, voucher_id) DO NOTHING
                """, userId, promoCode);
        return inserted == 1 || findOffer(promoCode, userId).map(Offer::claimed).orElse(false);
    }

    public List<Voucher> findClaimed(int userId) {
        return jdbc.query("""
                SELECT s.sponsor_name, v.promo_code, v.discount_percent
                FROM voucher_claim c
                JOIN voucher v ON v.voucher_id = c.voucher_id
                JOIN sponsor s ON s.sponsor_id = v.sponsor_id
                WHERE c.user_id = ?
                ORDER BY c.claimed_at DESC, c.voucher_id DESC
                """, VOUCHER_ROW, userId);
    }
}
