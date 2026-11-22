package kz.printlab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Sprint3SecurityTests {
    @Autowired MockMvc mvc;

    @Test
    void mutatingDesignEndpointsRejectMissingOrInvalidCsrf() throws Exception {
        mvc.perform(post("/api/designs").with(user("test@example.com"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/designs/1").with(user("test@example.com")).with(csrf().useInvalidToken())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/designs/1").with(user("test@example.com")))
            .andExpect(status().isForbidden());
        MockMultipartFile image = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1});
        mvc.perform(multipart("/api/designs/uploads").file(image).with(user("test@example.com")))
            .andExpect(status().isForbidden());
    }

    @Test
    void guestCannotMutateDesignOrAccessCartEvenWithCsrf() throws Exception {
        mvc.perform(post("/api/designs").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/designs/1").with(csrf()))
            .andExpect(status().isUnauthorized());
        MockMultipartFile image = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1});
        mvc.perform(multipart("/api/designs/uploads").file(image).with(csrf()))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/cart")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/cart/items").with(csrf()).param("variantId", "1"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void cartMutationRequiresCsrf() throws Exception {
        mvc.perform(post("/cart/items").with(user("test@example.com")).param("variantId", "1"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/cart/items/1/quantity").with(user("test@example.com"))
                .with(csrf().useInvalidToken()).param("quantity", "1"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/cart/items/1/remove").with(user("test@example.com")))
            .andExpect(status().isForbidden());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void guestCanOpenDesignerForActiveProduct() throws Exception {
        Long productId = jdbc.queryForObject("insert into products(name, category, base_price_kzt, active) values ('Security test', 'TSHIRT', 5000, true) returning id", Long.class);
        mvc.perform(get("/designer/{id}", productId)).andExpect(status().isOk());
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
}
