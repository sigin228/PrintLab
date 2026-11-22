package kz.printlab.dto;

public record PricingDto(long subtotalKzt, long discountKzt, long totalKzt, String promoCode) { }
