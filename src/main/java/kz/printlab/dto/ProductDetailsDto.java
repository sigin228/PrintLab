package kz.printlab.dto;
import java.util.List;
public record ProductDetailsDto(Long id, String name, String description, String category, long basePriceKzt, String imageUrl, List<ProductVariantDto> variants) {}
