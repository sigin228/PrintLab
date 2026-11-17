package kz.printlab.entity;
import jakarta.persistence.*;
@Entity @Table(name = "product_variants")
public class ProductVariant {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false) private Product product;
 @Column(nullable = false, unique = true, length = 60) private String sku;
 @Column(nullable = false, length = 10) private String size;
 @Column(nullable = false, length = 40) private String color;
 @Column(name = "extra_price_kzt", nullable = false) private long extraPriceKzt;
 @Column(name = "stock_quantity", nullable = false) private int stockQuantity;
 @Column(nullable = false) private boolean active;
 public Long getId(){return id;} public Product getProduct(){return product;} public String getSku(){return sku;} public String getSize(){return size;} public String getColor(){return color;} public long getExtraPriceKzt(){return extraPriceKzt;} public int getStockQuantity(){return stockQuantity;} public boolean isActive(){return active;}
}
