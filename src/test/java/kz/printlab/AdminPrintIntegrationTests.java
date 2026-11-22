package kz.printlab;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminPrintIntegrationTests {
    @TempDir static Path productImageRoot;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("printlab.product-upload-dir", () -> productImageRoot.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test void permissionsValidationAndSavedDesignSurvivesDeactivation() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","");
        String email="print-"+suffix+"@example.com", name="Test print "+suffix;
        long userId=jdbc.queryForObject("insert into users(email,username,password_hash,role,enabled,created_at) values (?,?,'hash','CUSTOMER',true,now()) returning id",Long.class,email,"u"+suffix);
        long product=jdbc.queryForObject("insert into products(name,category,base_price_kzt) values ('Print test','TSHIRT',5000) returning id",Long.class);
        long variant=jdbc.queryForObject("insert into product_variants(product_id,sku,size,color,stock_quantity) values (?,?,'M','Black',3) returning id",Long.class,product,"PRINT-"+suffix);
        var customer=user(email).roles("CUSTOMER");
        var admin=user("admin-test@example.com").roles("ADMIN");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB),"png",bytes);
        MockMultipartFile valid=new MockMultipartFile("file","print.png","image/png",bytes.toByteArray());
        MockMultipartFile invalid=new MockMultipartFile("file","bad.png","image/png",new byte[]{1,2,3});
        UUID absent=UUID.randomUUID();

        mvc.perform(multipart("/admin/prints").file(valid).param("name",name).with(customer).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/prints/{id}/disable",absent).with(customer).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart("/admin/prints").file(valid).param("name",name).with(admin)).andExpect(status().isForbidden());
        mvc.perform(multipart("/admin/prints").file(invalid).param("name",name).with(admin).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("adminError"));
        assertEquals(0,jdbc.queryForObject("select count(*) from admin_print_presets where name=?",Integer.class,name));
        mvc.perform(multipart("/admin/prints").file(valid).param("name",name).with(admin).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("adminSuccess"));
        UUID id=jdbc.queryForObject("select id from admin_print_presets where name=?",UUID.class,name);
        mvc.perform(get("/admin-prints/{id}/image",id)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/admin-prints/{id}/image",absent)).andExpect(status().isNotFound());

        String layerId=UUID.randomUUID().toString();
        String state=state(product,variant,layerId,"admin:"+id);
        String response=mvc.perform(post("/api/designs").with(customer).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Saved print\",\"editorState\":"+state+"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long designId=mapper.readTree(response).path("id").longValue();
        assertTrue(designId>0);
        mvc.perform(post("/admin/prints/{id}/disable",id).with(admin).with(csrf())).andExpect(status().is3xxRedirection());
        assertFalse(jdbc.queryForObject("select active from admin_print_presets where id=?",Boolean.class,id));
        mvc.perform(get("/admin-prints/{id}/image",id)).andExpect(status().isOk());
        mvc.perform(get("/api/designs/{id}",designId).with(customer)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("admin:"+id)));
        mvc.perform(put("/api/designs/{id}",designId).with(customer).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Saved print\",\"editorState\":"+state+"}")).andExpect(status().isOk());
        for(String invalidPreset : new String[]{"admin:"+absent,"https://example.com/untrusted.svg"}) {
            mvc.perform(post("/api/designs").with(customer).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Bad print\",\"editorState\":"+state(product,variant,UUID.randomUUID().toString(),invalidPreset)+"}"))
                .andExpect(status().isBadRequest());
        }
    }

    private static String state(long product,long variant,String layerId,String presetId) {
        return "{\"version\":2,\"productId\":"+product+",\"variantId\":"+variant+",\"front\":[{"+
            "\"type\":\"preset\",\"id\":\""+layerId+"\",\"name\":\"Print\",\"visible\":true,"+
            "\"x\":165,\"y\":230,\"scaleX\":1,\"scaleY\":1,\"rotation\":0,\"presetId\":\""+presetId+"\"}],\"back\":[]}";
    }
}
