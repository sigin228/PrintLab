package kz.printlab.service;

import java.time.Instant;
import java.util.Locale;
import kz.printlab.dto.PricingDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PricingService {
    private final JdbcTemplate jdbc;
    public PricingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PricingDto calculate(long subtotal, String rawCode, boolean lock) {
        if (subtotal < 0) throw new IllegalArgumentException("Некорректная сумма корзины.");
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) return new PricingDto(subtotal, 0, subtotal, "");
        if (code.length() > 60) throw new IllegalArgumentException("Недействительный промокод.");
        var found = jdbc.query("select id, discount_type, discount_value, min_order_kzt, usage_limit, starts_at, expires_at, active from promo_codes where code = ?" + (lock ? " for update" : ""),
            (rs, row) -> new Promo(rs.getLong("id"), rs.getString("discount_type"), rs.getLong("discount_value"), rs.getLong("min_order_kzt"), (Integer) rs.getObject("usage_limit"), rs.getTimestamp("starts_at") == null ? null : rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(), rs.getBoolean("active")), code);
        if (found.isEmpty()) throw new IllegalArgumentException("Недействительный промокод.");
        Promo promo = found.getFirst(); Instant now = Instant.now();
        if (!promo.active || promo.starts != null && now.isBefore(promo.starts) || promo.expires != null && !now.isBefore(promo.expires)) throw new IllegalArgumentException("Промокод недействителен или его срок истёк.");
        if (subtotal < promo.minimum) throw new IllegalArgumentException("Сумма заказа меньше минимума для промокода.");
        if (promo.limit != null) {
            Long used = jdbc.queryForObject("select count(*) from orders where promo_code_id = ? and status <> 'CANCELLED'", Long.class, promo.id);
            if (used != null && used >= promo.limit) throw new IllegalArgumentException("Лимит использования промокода исчерпан.");
        }
        DiscountPolicy policy = switch (promo.type) { case "PERCENT" -> new PercentageDiscountPolicy(); case "FIXED" -> new FixedDiscountPolicy(); default -> throw new IllegalArgumentException("Некорректный промокод."); };
        long discount = policy.discount(subtotal, promo.value);
        return new PricingDto(subtotal, discount, Math.subtractExact(subtotal, discount), code);
    }
    private record Promo(long id, String type, long value, long minimum, Integer limit, Instant starts, Instant expires, boolean active) { }
}
