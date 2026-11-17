package kz.printlab.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "products")
public class Product {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @Column(nullable = false, length = 150) private String name;
 @Column(nullable = false, length = 20) private String category;
 @Column(columnDefinition = "text") private String description;
 @Column(name = "base_price_kzt", nullable = false) private long basePriceKzt;
 @Column(name = "image_url", columnDefinition = "text") private String imageUrl;
 @Column(nullable = false) private boolean active;
 @Column(name = "created_at", nullable = false) private Instant createdAt;
 @OneToMany(mappedBy = "product", fetch = FetchType.LAZY) private List<ProductVariant> variants = new ArrayList<>();
 public Long getId(){return id;} public String getName(){return name;} public String getCategory(){return category;} public String getDescription(){return description;} public long getBasePriceKzt(){return basePriceKzt;} public String getImageUrl(){return imageUrl;} public boolean isActive(){return active;} public List<ProductVariant> getVariants(){return variants;}
}
