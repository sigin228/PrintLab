package kz.printlab.dto;
import java.time.LocalDateTime;
public record DesignListItem(Long id, String title, LocalDateTime updatedAt, long productId, long variantId) {}

