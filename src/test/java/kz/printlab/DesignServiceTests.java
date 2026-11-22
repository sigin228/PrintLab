package kz.printlab;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import kz.printlab.dto.DesignRequest;
import kz.printlab.service.DesignService;
import kz.printlab.service.DesignStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DesignServiceTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired DesignService designs;
    @Autowired ObjectMapper mapper;
    @Autowired DesignStorageService configuredStorage;
    @Autowired MockMvc mvc;
    @TempDir static Path uploadRoot;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("printlab.upload-dir", () -> uploadRoot.toString()); }
    @Test void stateRoundTripsAndIsPrivate() {
        String email="design-test-"+java.util.UUID.randomUUID()+"@example.org";
        jdbc.update("insert into users(email,password_hash,role,enabled,created_at) values (?, 'test','CUSTOMER',true,now())",email);
        Long product=jdbc.queryForObject("insert into products(name,category,base_price_kzt,active) values ('Design test','TSHIRT',5000,true) returning id",Long.class);
        Long variant=jdbc.queryForObject("insert into product_variants(product_id,sku,size,color,extra_price_kzt,stock_quantity,active) values (?,?,'M','black',0,3,true) returning id",Long.class,product,"test-"+java.util.UUID.randomUUID());
        String json="{\"version\":1,\"productId\":"+product+",\"variantId\":"+variant+",\"front\":[{\"type\":\"text\",\"x\":1,\"y\":2,\"scaleX\":1,\"scaleY\":1,\"rotation\":0,\"text\":\"Привет\",\"fontSize\":30,\"color\":\"#171717\"}],\"back\":[]}";
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email,"",java.util.List.of()));
        try {
            var saved=designs.createDesign(new DesignRequest("Тест",mapper.readTree(json)));
            assertEquals(1000,saved.printPriceKzt());
            assertEquals(mapper.readTree(json),designs.getDesignById(saved.id()).editorState());
            String persisted=jdbc.queryForObject("select editor_state::text from designs where id=?",String.class,saved.id());
            assertEquals(mapper.readTree(json),mapper.readTree(persisted));
            String other="other-"+java.util.UUID.randomUUID()+"@example.org";
            jdbc.update("insert into users(email,password_hash,role,enabled,created_at) values (?, 'test','CUSTOMER',true,now())",other);
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(other,"",java.util.List.of()));
            assertEquals(404,assertThrows(ResponseStatusException.class,()->designs.getDesignById(saved.id())).getStatusCode().value());
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void imageHttpRouteRequiresOwnershipAndAuthentication() throws Exception {
        String owner="owner-"+java.util.UUID.randomUUID()+"@example.org";
        String other="other-"+java.util.UUID.randomUUID()+"@example.org";
        Long ownerId=jdbc.queryForObject("insert into users(email,password_hash,role,enabled,created_at) values (?, 'test','CUSTOMER',true,now()) returning id",Long.class,owner);
        jdbc.update("insert into users(email,password_hash,role,enabled,created_at) values (?, 'test','CUSTOMER',true,now())",other);
        var bytes=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB),"png",bytes);
        var id=configuredStorage.storeUploadedImage(ownerId,new MockMultipartFile("file","test.png","image/png",bytes.toByteArray()));
        mvc.perform(get("/api/designs/uploads/{id}",id).with(user(owner))).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/designs/uploads/{id}",id).with(user(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/designs/uploads/{id}",id)).andExpect(status().isUnauthorized());
    }
    @Test void imagesAreValidatedAndOwnerScoped(@TempDir Path root) throws Exception {
        var storage=new DesignStorageService(root.toString());
        var bytes=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_ARGB),"png",bytes);
        var id=storage.storeUploadedImage(1,new MockMultipartFile("file","art.png","image/png",bytes.toByteArray()));
        assertTrue(storage.belongsTo(1,id)); assertArrayEquals(bytes.toByteArray(),storage.loadAuthorizedImage(1,id));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->storage.loadAuthorizedImage(2,id)).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->storage.storeUploadedImage(1,new MockMultipartFile("file","art.png","image/png",new byte[]{1,2,3})));
        assertThrows(ResponseStatusException.class,()->storage.storeUploadedImage(1,new MockMultipartFile("file","art.jpg","image/jpeg",bytes.toByteArray())));
    }
}


