package kz.printlab.service;

import java.math.BigInteger;

public final class PercentageDiscountPolicy implements DiscountPolicy {
    @Override public long discount(long subtotal, long value) {
        if (subtotal < 0 || value < 1 || value > 100) throw new IllegalArgumentException("Некорректная скидка.");
        return BigInteger.valueOf(subtotal).multiply(BigInteger.valueOf(value)).divide(BigInteger.valueOf(100)).longValueExact();
    }
}
