package kz.printlab.dto;

public record CartItemResponse(Long id, Long variantId, String productName, String imageUrl,
        String size, String color, long unitPrice, int quantity, int stock,
        Long designId, String designTitle, long lineTotal, boolean available, String productCategory) {}
