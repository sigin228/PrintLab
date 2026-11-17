package kz.printlab;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import kz.printlab.repository.ProductRepository;
import kz.printlab.service.ProductService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductCatalogTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductService service;
    @Autowired ProductRepository repository;
    @Autowired MockMvc mvc;
    private Long shirtId;
    private Long inactiveId;
    private String prefix;

    @BeforeEach
    void fixtures() {
        prefix = "Test" + UUID.randomUUID().toString().replace("-", "");
        shirtId = insertProduct(prefix + " Cotton", "TSHIRT", true);
        insertProduct(prefix + " Hoodie", "HOODIE", true);
        inactiveId = insertProduct(prefix + " Hidden", "HOODIE", false);
        insertVariant(shirtId, prefix + "-M", "M", "Black", 250, 4, true);
        insertVariant(shirtId, prefix + "-L", "L", "White", 0, 0, true);
        insertVariant(shirtId, prefix + "-OFF", "XL", "Grey", 0, 2, false);
    }

    private Long insertProduct(String name, String category, boolean active) {
        return jdbc.queryForObject("insert into products(name, category, base_price_kzt, active) values (?, ?, 5000, ?) returning id", Long.class, name, category, active);
    }

    private void insertVariant(Long productId, String sku, String size, String color, long extra, int stock, boolean active) {
        jdbc.update("insert into product_variants(product_id, sku, size, color, extra_price_kzt, stock_quantity, active) values (?, ?, ?, ?, ?, ?, ?)", productId, sku, size, color, extra, stock, active);
    }

    @Test
    void searchAndFilterReturnOnlyActiveMatchingProducts() {
        assertEquals(2, service.searchProducts(prefix.toLowerCase(), null).size());
        assertEquals(1, service.searchProducts(prefix.toLowerCase(), "tshirt").size());
        assertEquals(1, service.searchProducts("hoodie", "HOODIE").stream().filter(p -> p.name().startsWith(prefix)).count());
        assertTrue(service.searchProducts(prefix, "UNKNOWN").isEmpty());
        assertTrue(service.getAllProducts().stream().noneMatch(p -> p.id().equals(inactiveId)));
        assertTrue(service.searchProducts(prefix + "%", null).isEmpty());
    }

    @Test
    void variantsExposeSizesColorsPricesAndJpaRelationship() {
        var product = service.getProductById(shirtId);
        assertEquals(2, product.variants().size());
        var variant = product.variants().stream().filter(v -> v.size().equals("M")).findFirst().orElseThrow();
        assertEquals("Black", variant.color());
        assertEquals(5250, variant.price());
        assertEquals(4, variant.stockQuantity());
        assertTrue(product.variants().stream().anyMatch(v -> v.size().equals("L") && v.color().equals("White") && v.price() == 5000));
        var entity = repository.findById(shirtId).orElseThrow();
        assertEquals(3, entity.getVariants().size());
        assertTrue(entity.getVariants().stream().allMatch(v -> v.getProduct().getId().equals(shirtId)));
    }

    @Test
    void absentAndInactiveProductsReturn404() throws Exception {
        mvc.perform(get("/catalog/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(get("/catalog/{id}", inactiveId)).andExpect(status().isNotFound());
    }

    @Test
    void databaseRejectsNegativeBasePrice() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into products(name, category, base_price_kzt) values (?, 'TSHIRT', -1)", prefix));
    }

    @Test
    void databaseRejectsNegativeExtraPrice() {
        assertThrows(DataIntegrityViolationException.class, () -> insertVariant(shirtId, prefix + "-NEG", "S", "Black", -1, 1, true));
    }

    @Test
    void databaseRejectsNegativeStock() {
        assertThrows(DataIntegrityViolationException.class, () -> insertVariant(shirtId, prefix + "-NEG", "S", "Black", 0, -1, true));
    }

    @Test
    void databaseRejectsDuplicateSku() {
        assertThrows(DataIntegrityViolationException.class, () -> insertVariant(shirtId, prefix + "-M", "S", "Black", 0, 1, true));
    }

    @Test
    void databaseRejectsMissingProduct() {
        assertThrows(DataIntegrityViolationException.class, () -> insertVariant(Long.MAX_VALUE, prefix + "-BAD", "S", "Black", 0, 1, true));
    }
}
