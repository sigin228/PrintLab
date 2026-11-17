package kz.printlab.dto;
public record ProductVariantDto(Long id, String sku, String size, String color, long extraPriceKzt, int stockQuantity, long price) {}
