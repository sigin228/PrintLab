package kz.printlab.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import kz.printlab.dto.*;
import kz.printlab.entity.*;
import kz.printlab.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CartService {
    private final UserRepository users;
    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductVariantRepository variants;
    private final DesignRepository designs;
    private final ObjectMapper mapper;
    public CartService(UserRepository users, CartRepository carts, CartItemRepository items,
                       ProductVariantRepository variants, DesignRepository designs, ObjectMapper mapper) {
        this.users = users; this.carts = carts; this.items = items; this.variants = variants; this.designs = designs;
        this.mapper = mapper;
    }
    private String email() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private User current() { return users.findByEmail(email()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private User locked() { return users.lockByEmail(email()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private Cart getOrCreate(User user) {
        return carts.findByUserId(user.getId()).orElseGet(() -> { Cart cart = new Cart(); cart.setUser(user); return carts.save(cart); });
    }
    private CartItem owned(Long id, User user) {
        return items.findByIdAndCartUserId(id, user.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private static void validQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) throw new IllegalArgumentException("Укажите количество больше нуля.");
    }
    private static void validateStock(ProductVariant variant, long quantity) {
        if (!variant.isActive() || !variant.getProduct().isActive()) throw new IllegalArgumentException("Товар больше недоступен.");
        if (quantity > variant.getStockQuantity()) throw new IllegalArgumentException("Недостаточно товара в наличии.");
    }
    private long quantityForVariant(Long cartId, Long variantId, Long excludeId) {
        return items.findByCartIdOrderById(cartId).stream()
            .filter(i -> i.getVariant().getId().equals(variantId) && !Objects.equals(i.getId(), excludeId))
            .mapToLong(CartItem::getQuantity).sum();
    }
    private void touch(Cart cart) { cart.setUpdatedAt(java.time.Instant.now()); }
    @Transactional(readOnly = true)
    public CartSummaryDto getCart() {
        User user = current();
        List<CartItem> cartItems = carts.findByUserId(user.getId()).map(c -> items.findByCartIdOrderById(c.getId())).orElseGet(List::of);
        Map<Long,Long> quantities = cartItems.stream().collect(Collectors.groupingBy(i -> i.getVariant().getId(), Collectors.summingLong(CartItem::getQuantity)));
        List<CartItemResponse> lines = cartItems.stream().map(i -> toResponse(i, quantities.get(i.getVariant().getId()))).toList();
        long total = 0;
        for (CartItemResponse line : lines) total = Math.addExact(total, line.lineTotal());
        return new CartSummaryDto(lines, total);
    }
    private CartItemResponse toResponse(CartItem item, long totalQuantity) {
        ProductVariant v = item.getVariant(); Product p = v.getProduct(); Design d = item.getDesign();
        long price = Math.addExact(Math.addExact(p.getBasePriceKzt(), v.getExtraPriceKzt()), d == null ? 0 : d.getPrintPriceKzt());
        return new CartItemResponse(item.getId(), v.getId(), p.getName(), p.getImageUrl(), v.getSize(), v.getColor(), price,
            item.getQuantity(), v.getStockQuantity(), d == null ? null : d.getId(), d == null ? null : d.getTitle(),
            Math.multiplyExact(price, item.getQuantity()), v.isActive() && p.isActive() && totalQuantity <= v.getStockQuantity());
    }
    @Transactional
    public void add(CartItemRequest request) {
        validQuantity(request.quantity());
        User user = locked();
        ProductVariant variant = variants.findById(request.variantId() == null ? -1L : request.variantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Design design = request.designId() == null ? null : designs.findByIdAndUserId(request.designId(), user.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (design != null) {
            var state = mapper.readTree(design.getEditorState());
            var productId = state.path("productId"); var variantId = state.path("variantId");
            if (!productId.isIntegralNumber() || !productId.canConvertToLong() || !variantId.isIntegralNumber() || !variantId.canConvertToLong()
                || productId.longValue() != variant.getProduct().getId() || variantId.longValue() != variant.getId())
                throw new IllegalArgumentException("Дизайн создан для другого товара или варианта.");
        }
        Cart cart = getOrCreate(user);
        validateStock(variant, Math.addExact(quantityForVariant(cart.getId(), variant.getId(), null), request.quantity().longValue()));
        CartItem item = new CartItem(); item.setCart(cart); item.setVariant(variant); item.setDesign(design); item.setQuantity(request.quantity()); items.save(item); touch(cart);
    }
    @Transactional
    public void updateQuantity(Long id, Integer quantity) {
        validQuantity(quantity); User user = locked(); CartItem item = owned(id, user);
        validateStock(item.getVariant(), Math.addExact(quantityForVariant(item.getCart().getId(), item.getVariant().getId(), id), quantity.longValue()));
        item.setQuantity(quantity); touch(item.getCart());
    }
    @Transactional
    public void remove(Long id) { User user = locked(); CartItem item = owned(id, user); touch(item.getCart()); items.delete(item); }
    @Transactional
    public void clear() { User user = locked(); carts.findByUserId(user.getId()).ifPresent(c -> { items.deleteAll(items.findByCartIdOrderById(c.getId())); touch(c); }); }
}
