package kz.printlab;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import kz.printlab.service.DeliveryAddressService;
import kz.printlab.service.KazakhstanPhone;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DeliveryValidationTests {
    @Test void validatesKazakhstanNumbers() {
        assertEquals("+77011234567", KazakhstanPhone.normalize("8 (701) 123-45-67"));
        assertEquals("+77471234567", KazakhstanPhone.normalize("+7 747 123 45 67"));
        assertThrows(IllegalArgumentException.class, () -> KazakhstanPhone.normalize("+79161234567"));
        assertThrows(IllegalArgumentException.class, () -> KazakhstanPhone.normalize("123"));
    }
    @Test void validatesAddressAgainstGeocoderAndRejectsOutsideCity() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/reverse", exchange -> {
            String json="{\"features\":[{\"geometry\":{\"coordinates\":[71.4304,51.1283]},\"properties\":{\"countrycode\":\"KZ\",\"city\":\"Астана\",\"street\":\"Тестовая\",\"housenumber\":\"1\"}}]}";
            byte[] body=json.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var service=new DeliveryAddressService("http://127.0.0.1:"+server.getAddress().getPort()+"/reverse",new ObjectMapper());
            assertEquals("Астана, Тестовая, 1, кв./офис 12",service.complete("51.1283","71.4304","12"));
            assertThrows(IllegalArgumentException.class,()->service.complete("43.2","76.9", ""));
            assertThrows(IllegalArgumentException.class,()->service.complete("nan","71.4", ""));
        } finally { server.stop(0); }
    }
}
