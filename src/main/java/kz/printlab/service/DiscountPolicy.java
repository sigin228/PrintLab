package kz.printlab.service;

public interface DiscountPolicy {
    long discount(long subtotal, long value);
}
