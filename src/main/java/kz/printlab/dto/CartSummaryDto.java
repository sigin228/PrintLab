package kz.printlab.dto;
import java.util.List;
public record CartSummaryDto(List<CartItemResponse> items, long subtotal) {}
