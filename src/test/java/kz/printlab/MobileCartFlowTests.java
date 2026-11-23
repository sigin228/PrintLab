package kz.printlab;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobileCartFlowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test void sessionDesignAndCartLifecycleAndCsrf() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","");
        String email="mobile-"+suffix+"@example.com", username="u"+suffix;
        long owner=jdbc.queryForObject("insert into users(email,username,password_hash,role,enabled,created_at) values (?,?,?,'CUSTOMER',true,now()) returning id",Long.class,email,username,encoder.encode("Password1"));
        String otherEmail="other-"+suffix+"@example.com";
        jdbc.update("insert into users(email,username,password_hash,role,enabled,created_at) values (?,?,?,'CUSTOMER',true,now())",otherEmail,"v"+suffix,encoder.encode("Password1"));
        long product=jdbc.queryForObject("insert into products(name,category,base_price_kzt,active) values ('Mobile test','TSHIRT',5000,true) returning id",Long.class);
        long variant=jdbc.queryForObject("insert into product_variants(product_id,sku,size,color,extra_price_kzt,stock_quantity,active) values (?,?,'M','Black',250,5,true) returning id",Long.class,product,"MOB-"+suffix);

        mvc.perform(get("/designer/{id}",product)).andExpect(status().isOk());
        mvc.perform(get("/cart")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/designs").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        var login=mvc.perform(post("/login").with(csrf()).cookie(new jakarta.servlet.http.Cookie("printlab_designer_return",Long.toString(product))).param("username",username).param("password","Password1"))
            .andExpect(redirectedUrl("/designer/"+product+"?resume=1"))
            .andExpect(cookie().httpOnly("printlab_designer_return",true))
            .andExpect(cookie().maxAge("printlab_designer_return",0)).andReturn();
        MockHttpSession session=(MockHttpSession)login.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/designer/{id}",product).session(session).param("variantId",Long.toString(variant))).andExpect(status().isOk());
        String state="{\"version\":1,\"productId\":"+product+",\"variantId\":"+variant+",\"front\":[{\"type\":\"text\",\"x\":165,\"y\":230,\"scaleX\":1,\"scaleY\":1,\"rotation\":0,\"text\":\"Hello\",\"fontSize\":30,\"color\":\"#171717\"}],\"back\":[]}";
        String body="{\"title\":\"Mobile design\",\"editorState\":"+state+"}";
        mvc.perform(post("/api/designs").session(session).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        String json=mvc.perform(post("/api/designs").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long design=mapper.readTree(json).path("id").longValue();
        assertTrue(design>0);
        mvc.perform(post("/cart/items").session(session).param("variantId",Long.toString(variant)).param("designId",Long.toString(design))).andExpect(status().isForbidden());
        mvc.perform(post("/cart/items").session(session).with(csrf()).param("variantId",Long.toString(variant)).param("designId",Long.toString(design)).param("quantity","1")).andExpect(redirectedUrl("/cart"));
        long item=jdbc.queryForObject("select ci.id from cart_items ci join carts c on c.id=ci.cart_id where c.user_id=?",Long.class,owner);
        mvc.perform(get("/cart").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("6250")));
        mvc.perform(post("/cart/items/{id}/quantity",item).session(session).with(csrf()).param("quantity","2")).andExpect(redirectedUrl("/cart"));
        mvc.perform(get("/cart").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("12500")));
        mvc.perform(post("/cart/items/{id}/quantity",item).session(session).with(csrf()).param("quantity","6")).andExpect(redirectedUrl("/cart"));
        assertEquals(2,jdbc.queryForObject("select quantity from cart_items where id=?",Integer.class,item));
        var otherLogin=mvc.perform(post("/login").with(csrf()).param("username",otherEmail).param("password","Password1")).andReturn();
        MockHttpSession other=(MockHttpSession)otherLogin.getRequest().getSession(false);
        assertNotNull(other);
        mvc.perform(get("/api/designs/{id}",design).session(other)).andExpect(status().isNotFound());
        mvc.perform(post("/cart/items/{id}/remove",item).session(other).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/cart/items/{id}/remove",item).session(session).with(csrf())).andExpect(redirectedUrl("/cart"));
        entityManager.flush();
        assertEquals(0,jdbc.queryForObject("select count(*) from cart_items where id=?",Integer.class,item));
        mvc.perform(post("/cart/items").session(session).with(csrf()).param("variantId",Long.toString(variant)).param("quantity","1")).andExpect(redirectedUrl("/cart"));
        mvc.perform(post("/cart/clear").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/cart/clear").session(session).with(csrf())).andExpect(redirectedUrl("/cart"));
        entityManager.flush();
        assertEquals(0,jdbc.queryForObject("select count(*) from cart_items ci join carts c on c.id=ci.cart_id where c.user_id=?",Integer.class,owner));
        mvc.perform(get("/cart").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("cart-empty")));
    }
}
