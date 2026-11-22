package kz.printlab;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommercePageIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void customerAndAdminPagesRenderAcrossCheckoutAndPayment() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-", "");
        String email="page-"+suffix+"@example.com", code="P"+suffix.toUpperCase();
        long userId=jdbc.queryForObject("insert into users(email,username,password_hash,role,enabled,created_at) values (?,?,'hash','CUSTOMER',true,now()) returning id",Long.class,email,"u"+suffix);
        jdbc.update("insert into user_profiles(user_id,full_name,city,phone,delivery_address) values (?,'Test User','Almaty','123','Test address')",userId);
        long product=jdbc.queryForObject("insert into products(name,category,base_price_kzt) values ('Page test product','TSHIRT',5000) returning id",Long.class);
        long variant=jdbc.queryForObject("insert into product_variants(product_id,sku,size,color,stock_quantity) values (?,?,'M','Black',5) returning id",Long.class,product,"PAGE-"+suffix);
        long cart=jdbc.queryForObject("insert into carts(user_id) values (?) returning id",Long.class,userId);
        jdbc.update("insert into cart_items(cart_id,variant_id,quantity) values (?,?,2)",cart,variant);
        jdbc.update("insert into promo_codes(code,discount_type,discount_value,usage_limit) values (?,'PERCENT',10,1)",code);
        var customer=user(email).roles("CUSTOMER");
        var admin=user("admin-test@example.com").roles("ADMIN");

        mvc.perform(get("/checkout").with(customer)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Page test product")));
        mvc.perform(get("/orders").with(customer)).andExpect(status().isOk());
        mvc.perform(get("/wallet").with(customer)).andExpect(status().isOk());
        mvc.perform(get("/admin").with(customer)).andExpect(status().isForbidden());
        mvc.perform(post("/checkout").with(customer).param("checkoutKey",UUID.randomUUID().toString())).andExpect(status().isForbidden());

        String key=UUID.randomUUID().toString();
        mvc.perform(post("/checkout/preview").with(customer).with(csrf()).param("checkoutKey",key).param("promoCode",code))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("9000")));
        mvc.perform(post("/checkout").with(customer).with(csrf()).param("checkoutKey",key)
                .param("deliveryName","Test User").param("deliveryPhone","123").param("deliveryAddress","Test address").param("promoCode",code))
            .andExpect(status().is3xxRedirection());
        long order=jdbc.queryForObject("select id from orders where checkout_key=?::uuid",Long.class,key);
        assertEquals(9000L,jdbc.queryForObject("select total_kzt from orders where id=?",Long.class,order));
        assertEquals(3,jdbc.queryForObject("select stock_quantity from product_variants where id=?",Integer.class,variant));
        jdbc.update("update order_items set design_snapshot=cast(? as jsonb) where order_id=?", "{\"version\":2,\"productId\":"+product+",\"variantId\":"+variant+",\"front\":[{\"type\":\"preset\",\"presetId\":\"cosmic-orbit\",\"visible\":true}],\"back\":[]}",order);
        mvc.perform(get("/orders").with(customer)).andExpect(status().isOk());
        mvc.perform(get("/orders/{id}",order).with(customer)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Page test product")));
        mvc.perform(get("/orders/{id}",order).with(customer)).andExpect(content().string(org.hamcrest.Matchers.containsString("order-snapshot")));
        mvc.perform(post("/orders/{id}/pay",order).with(customer)).andExpect(status().isForbidden());
        mvc.perform(post("/orders/{id}/pay",order).with(customer).with(csrf())).andExpect(status().is3xxRedirection());
        assertEquals("PAID_SIMULATED",jdbc.queryForObject("select status from orders where id=?",String.class,order));

        mvc.perform(get("/admin").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/products").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/products/new").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/products/{id}",product).with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/promos").with(admin)).andExpect(status().isOk());
        mvc.perform(post("/admin/promos/create").with(admin).with(csrf()).param("code","NEW"+suffix).param("type","FIXED").param("value","250").param("minimum","0"))
            .andExpect(status().is3xxRedirection());
        assertEquals(1,jdbc.queryForObject("select count(*) from promo_codes where code=?",Integer.class,("NEW"+suffix).toUpperCase()));
        // An obsolete inventory form must not overwrite units already reserved by checkout.
        mvc.perform(post("/admin/products/{id}/variants/save",product).with(admin).with(csrf()).param("variantId",Long.toString(variant)).param("sku","PAGE-"+suffix).param("size","M").param("color","Black").param("extraPrice","0").param("stock","5").param("expectedStock","5").param("active","true"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("adminError"));
        assertEquals(3,jdbc.queryForObject("select stock_quantity from product_variants where id=?",Integer.class,variant));
        mvc.perform(get("/admin/orders").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/orders/{id}",order).with(admin)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("order-snapshot")));
        mvc.perform(post("/admin/orders/{id}/status",order).with(admin).param("next","IN_PRODUCTION")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/orders/{id}/status",order).with(admin).with(csrf()).param("next","IN_PRODUCTION")).andExpect(status().is3xxRedirection());
        assertEquals("IN_PRODUCTION",jdbc.queryForObject("select status from orders where id=?",String.class,order));
    }
}
