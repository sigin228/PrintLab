package kz.printlab;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

@SpringBootTest
@AutoConfigureMockMvc
class PrintLabApplicationTests {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired PasswordEncoder encoder;
 @Test void registrationLoginProfileAndLogout() throws Exception {
  String email="test-"+UUID.randomUUID()+"@example.com";
  try {
   mvc.perform(get("/")).andExpect(status().isOk());
   mvc.perform(get("/profile")).andExpect(status().is3xxRedirection());
   mvc.perform(post("/register").param("email",email).param("password","Password1").param("confirmPassword","Password1")).andExpect(status().isForbidden());
   mvc.perform(post("/register").with(csrf()).param("email",email).param("password","short").param("confirmPassword","short")).andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("registerRequest","password"));
   mvc.perform(post("/register").with(csrf()).param("email",email).param("password","Password1").param("confirmPassword","Different1")).andExpect(model().attributeHasFieldErrors("registerRequest","confirmPassword"));
   mvc.perform(post("/register").with(csrf()).param("email",email.toUpperCase()).param("password","Password1").param("confirmPassword","Password1")).andExpect(redirectedUrl("/login?registered"));
   String hash=jdbc.queryForObject("select password_hash from users where email=?",String.class,email);
   assertTrue(encoder.matches("Password1",hash)); assertNotEquals("Password1",hash);
   assertEquals(1,jdbc.queryForObject("select count(*) from user_profiles p join users u on p.user_id=u.id where u.email=?",Integer.class,email));
   mvc.perform(post("/register").with(csrf()).param("email",email).param("password","Password1").param("confirmPassword","Password1")).andExpect(model().attributeHasFieldErrors("registerRequest","email"));
   var login=mvc.perform(post("/login").with(csrf()).param("username",email).param("password","Password1")).andExpect(status().is3xxRedirection()).andReturn();
   MockHttpSession session=(MockHttpSession)login.getRequest().getSession(false); assertNotNull(session);
   mvc.perform(post("/profile").session(session).with(csrf()).param("fullName","Test User").param("phone","123").param("deliveryAddress","Test address")).andExpect(redirectedUrl("/profile?saved"));
   assertEquals("Test User",jdbc.queryForObject("select p.full_name from user_profiles p join users u on p.user_id=u.id where u.email=?",String.class,email));
   mvc.perform(post("/logout").session(session).with(csrf())).andExpect(status().is3xxRedirection());
   assertTrue(session.isInvalid());
  } finally {jdbc.update("delete from user_profiles where user_id in (select id from users where email=?)",email);jdbc.update("delete from users where email=?",email);}
 }
}
