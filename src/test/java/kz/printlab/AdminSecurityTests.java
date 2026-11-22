package kz.printlab;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityTests {
    @Autowired MockMvc mvc;
    @Test void customerCannotReadOrMutateAdmin() throws Exception {
        mvc.perform(get("/admin").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/orders/1/cancel").with(user("customer").roles("CUSTOMER")).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void adminNeedsCsrfButCanReachProtectedHandler() throws Exception {
        mvc.perform(post("/admin/orders/9223372036854775807/cancel").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/orders/9223372036854775807/cancel").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void productImagesArePublicButMissingImagesReturn404() throws Exception {
        mvc.perform(get("/product-images/00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
    }
}
