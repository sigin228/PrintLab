package kz.printlab.service;

public final class FixedDiscountPolicy implements DiscountPolicy {
    @Override public long discount(long subtotal, long value) {
        if (subtotal < 0 || value < 1) throw new IllegalArgumentException("Некорректная скидка.");
        return Math.min(subtotal, value);
    }
}
