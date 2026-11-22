package kz.printlab.dto;

import java.time.Instant;

public record OrderDto(long id, long userId, String status, long subtotalKzt, long discountKzt,
                       long totalKzt, String deliveryName, String deliveryPhone, String deliveryAddress,
                       Instant createdAt, boolean stockReserved) { }
