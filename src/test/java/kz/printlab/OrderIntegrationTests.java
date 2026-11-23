package kz.printlab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import kz.printlab.service.OrderService;
import kz.printlab.service.PercentageDiscountPolicy;
import kz.printlab.service.FixedDiscountPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class OrderIntegrationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean kz.printlab.service.DeliveryAddressService addresses;
    @org.junit.jupiter.api.BeforeEach void mockAddress() { org.mockito.Mockito.when(addresses.complete(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn("Астана, Тестовая, 1"); }
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orders;
    long user, variant, cart;
    String email;
    @BeforeEach void fixtures() {
        String suffix=UUID.randomUUID().toString().replace("-",""); email="o"+suffix+"@example.com";
        user=jdbc.queryForObject("insert into users(email,username,password_hash,role,enabled,created_at) values (?,?, 'hash','CUSTOMER',true,now()) returning id",Long.class,email,"u"+suffix);
        long product=jdbc.queryForObject("insert into products(name,category,base_price_kzt) values ('Test','TSHIRT',5000) returning id",Long.class);
        variant=jdbc.queryForObject("insert into product_variants(product_id,sku,size,color,stock_quantity) values (?,?, 'M','Black',5) returning id",Long.class,product,"sku-"+suffix);
        cart=jdbc.queryForObject("insert into carts(user_id) values (?) returning id",Long.class,user);
        jdbc.update("insert into cart_items(cart_id,variant_id,quantity) values (?,?,2)",cart,variant);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email,"",java.util.List.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void checkoutIsIdempotentAndCancellationRestoresStockAndPromoUse() {
        String code="T"+UUID.randomUUID().toString().replace("-","").toUpperCase();
        jdbc.update("insert into promo_codes(code,discount_type,discount_value,usage_limit) values (?,'PERCENT',10,1)",code);
        UUID key=UUID.randomUUID(); long id=orders.checkout(key,"Иван","+77011234567","Адрес",code,"51.1","71.4","");
        assertEquals(id,orders.checkout(key,"Иван","+77011234567","Адрес",code,"51.1","71.4",""));
        assertEquals(9000,orders.get(id).totalKzt());
        assertEquals(1,orders.items(id).size());
        assertEquals(3,jdbc.queryForObject("select stock_quantity from product_variants where id=?",Integer.class,variant));
        assertEquals(0,jdbc.queryForObject("select count(*) from cart_items where cart_id=?",Integer.class,cart));
        orders.cancel(id); orders.cancel(id);
        assertEquals(5,jdbc.queryForObject("select stock_quantity from product_variants where id=?",Integer.class,variant));
        assertEquals("CANCELLED",orders.get(id).status());
        assertEquals(0,jdbc.queryForObject("select count(*) from orders where promo_code_id=(select id from promo_codes where code=?) and status<>'CANCELLED'",Integer.class,code));
    }
    @Test void wrongPromoPreviewDoesNotDiscardCart() {
        assertThrows(IllegalArgumentException.class,()->orders.preview("MISSING"));
        assertEquals(5,jdbc.queryForObject("select stock_quantity from product_variants where id=?",Integer.class,variant));
        assertEquals(1,jdbc.queryForObject("select count(*) from cart_items where cart_id=?",Integer.class,cart));
    }
    @Test void promoMinimumErrorReportsConfiguredThreshold() {
        String code="M"+UUID.randomUUID().toString().replace("-", "").toUpperCase();
        jdbc.update("insert into promo_codes(code,discount_type,discount_value,min_order_kzt) values (?,'FIXED',500,15000)",code);
        IllegalArgumentException error=assertThrows(IllegalArgumentException.class,()->orders.preview(code));
        String message=error.getMessage().replace("\u00a0", " ").replace("\u202f", " ");
        assertTrue(message.contains("15 000 ₸"));
        assertEquals(1,jdbc.queryForObject("select count(*) from cart_items where cart_id=?",Integer.class,cart));
    }
    @Test void anotherUserCannotReadOrder() {
        long id=orders.checkout(UUID.randomUUID(),"Name","+77011234567","Address","","51.1","71.4","");
        String second="x"+UUID.randomUUID()+"@example.com";
        jdbc.update("insert into users(email,password_hash,role,enabled,created_at) values (?,'hash','CUSTOMER',true,now())",second);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(second,"",java.util.List.of()));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->orders.get(id)).getStatusCode().value());
    }
    @Test void policiesHandleLargeAmountsAndCapFixedDiscount() {
        assertEquals(Long.MAX_VALUE,new PercentageDiscountPolicy().discount(Long.MAX_VALUE,100));
        assertEquals(100,new FixedDiscountPolicy().discount(100,200));
    }
}
