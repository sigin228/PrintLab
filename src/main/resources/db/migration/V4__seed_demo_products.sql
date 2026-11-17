-- Demonstration data is inserted once by Flyway, never on an HTTP request.
WITH demo_products AS (
    INSERT INTO products (name, category, description, base_price_kzt, image_url)
    VALUES
        ('Oversize T-Shirt', 'TSHIRT', 'Свободная футболка для повседневных образов.', 5000, '/images/catalog/oversize-tshirt.png'),
        ('Classic T-Shirt', 'TSHIRT', 'Классическая футболка прямого кроя.', 3500, '/images/catalog/classic-tshirt.png'),
        ('Premium Hoodie', 'HOODIE', 'Худи с капюшоном и комфортной посадкой.', 12000, '/images/catalog/premium-hoodie.png'),
        ('Basic Sweatshirt', 'SWEATSHIRT', 'Лаконичный свитшот на каждый день.', 9000, '/images/catalog/basic-sweatshirt.png'),
        ('Oversize Hoodie', 'HOODIE', 'Объёмное худи в стиле streetwear.', 14000, '/images/catalog/oversize-hoodie.png')
    RETURNING id, name
)
INSERT INTO product_variants (product_id, sku, size, color, extra_price_kzt, stock_quantity)
SELECT p.id, v.sku, v.size, v.color, v.extra_price_kzt, v.stock_quantity
FROM demo_products p
JOIN (VALUES
    ('Oversize T-Shirt', 'OST-BLK-M', 'M', 'Black', 0, 8),
    ('Oversize T-Shirt', 'OST-WHT-L', 'L', 'White', 0, 6),
    ('Classic T-Shirt', 'CT-WHT-S', 'S', 'White', 0, 10),
    ('Classic T-Shirt', 'CT-BLK-M', 'M', 'Black', 0, 12),
    ('Premium Hoodie', 'PH-BLK-L', 'L', 'Black', 0, 5),
    ('Premium Hoodie', 'PH-BEI-XL', 'XL', 'Beige', 500, 4),
    ('Basic Sweatshirt', 'BS-GRY-M', 'M', 'Grey', 0, 7),
    ('Basic Sweatshirt', 'BS-BEI-L', 'L', 'Beige', 0, 6),
    ('Oversize Hoodie', 'OH-BLK-L', 'L', 'Black', 0, 5),
    ('Oversize Hoodie', 'OH-GRY-XL', 'XL', 'Grey', 0, 3)
) AS v(product_name, sku, size, color, extra_price_kzt, stock_quantity)
    ON v.product_name = p.name;
