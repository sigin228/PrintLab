package kz.printlab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import kz.printlab.dto.CartItemRequest;
import kz.printlab.service.CartService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class CartIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired CartService cart;
    String email, otherEmail;
    long userId, otherId, productId, variantId, otherVariantId, designId, foreignDesignId;
    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString();
        email = suffix + "@example.com"; otherEmail = "other-" + email;
        userId = user(email); otherId = user(otherEmail);
        productId = jdbc.queryForObject("insert into products(name,category,base_price_kzt,active) values (?, 'TSHIRT',5000,true) returning id", Long.class, suffix);
        variantId = variant(suffix + "-1", 4); otherVariantId = variant(suffix + "-2", 4);
        designId = design(userId, productId, variantId); foreignDesignId = design(otherId, productId, variantId);
        auth(email);
    }
    private long user(String address) { return jdbc.queryForObject("insert into users(email,password_hash,role,enabled,created_at) values (?, 'hash', 'CUSTOMER', true, current_timestamp) returning id", Long.class, address); }
    private long variant(String sku, int stock) { return jdbc.queryForObject("insert into product_variants(product_id,sku,size,color,extra_price_kzt,stock_quantity,active) values (?,?,'M','Black',250,?,true) returning id", Long.class, productId, sku, stock); }
    private long design(long user, long product, long variant) {
        String json = "{\"version\":1,\"productId\":" + product + ",\"variantId\":" + variant + ",\"front\":[],\"back\":[]}";
        return jdbc.queryForObject("insert into designs(user_id,title,editor_state,print_price_kzt) values (?,'Test',cast(? as jsonb),1000) returning id", Long.class, user, json);
    }
    private void auth(String address) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(address,"unused")); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void addUpdateRemoveAndClearCalculateServerPrices() {
        cart.add(new CartItemRequest(variantId,designId,2));
        var summary = cart.getCart(); assertEquals(12500, summary.subtotal());
        var item = summary.items().get(0); assertEquals(6250, item.unitPrice()); assertEquals(4,item.stock());
        cart.updateQuantity(item.id(),3); assertEquals(18750, cart.getCart().subtotal());
        cart.remove(item.id()); assertTrue(cart.getCart().items().isEmpty());
        cart.add(new CartItemRequest(variantId,null,1)); assertEquals(5250,cart.getCart().subtotal());
        cart.clear(); assertTrue(cart.getCart().items().isEmpty());
    }
    @Test void stockIsCheckedAcrossLines() {
        cart.add(new CartItemRequest(variantId,designId,3));
        assertThrows(IllegalArgumentException.class, () -> cart.add(new CartItemRequest(variantId,null,2)));
    }
    @Test void foreignDesignIsNotAccessible() {
        assertEquals(404,assertThrows(ResponseStatusException.class, () -> cart.add(new CartItemRequest(variantId,foreignDesignId,1))).getStatusCode().value());
    }
    @Test void foreignCartItemIsNotAccessible() {
        cart.add(new CartItemRequest(variantId,designId,1)); long itemId = cart.getCart().items().get(0).id();
        auth(otherEmail);
        assertEquals(404,assertThrows(ResponseStatusException.class, () -> cart.remove(itemId)).getStatusCode().value());
    }
    @Test void cannotAttachDesignToAnotherVariant() {
        assertThrows(IllegalArgumentException.class, () -> cart.add(new CartItemRequest(otherVariantId,designId,1)));
    }
    @Test void quantitiesMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> cart.add(new CartItemRequest(variantId,null,0)));
    }
}
