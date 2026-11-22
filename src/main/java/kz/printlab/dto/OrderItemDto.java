package kz.printlab.dto;

public record OrderItemDto(long id, long variantId, String productName, String size, String color,
                           String imageUrl, String designSnapshot, int quantity, long unitPriceKzt,
                           long lineTotalKzt, java.util.List<String> imageIds) { }
