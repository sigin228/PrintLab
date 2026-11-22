package kz.printlab.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "cart_items")
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cart_id", nullable = false) private Cart cart;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "variant_id", nullable = false) private ProductVariant variant;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "design_id") private Design design;
    @Column(nullable = false) private int quantity;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @PrePersist void createTimestamp() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; }
    public Cart getCart() { return cart; } public void setCart(Cart cart) { this.cart = cart; }
    public ProductVariant getVariant() { return variant; } public void setVariant(ProductVariant variant) { this.variant = variant; }
    public Design getDesign() { return design; } public void setDesign(Design design) { this.design = design; }
    public int getQuantity() { return quantity; } public void setQuantity(int quantity) { this.quantity = quantity; }
    public Instant getCreatedAt() { return createdAt; }
}
