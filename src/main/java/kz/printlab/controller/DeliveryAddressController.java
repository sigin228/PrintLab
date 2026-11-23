package kz.printlab.controller;

import java.util.Map;
import kz.printlab.service.DeliveryAddressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliveryAddressController {
    private final DeliveryAddressService addresses;
    public DeliveryAddressController(DeliveryAddressService addresses) { this.addresses = addresses; }
    @GetMapping("/api/delivery/address")
    public ResponseEntity<?> resolve(@RequestParam String lat, @RequestParam String lon) {
        try { return ResponseEntity.ok().body(addresses.resolve(lat, lon)); }
        catch (IllegalArgumentException ex) { return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage())); }
    }
}
