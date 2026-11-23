package kz.printlab.controller;

import jakarta.validation.Valid;
import kz.printlab.dto.ProfileUpdateRequest;
import kz.printlab.service.DeliveryAddressService;
import kz.printlab.service.KazakhstanPhone;
import kz.printlab.service.UserProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.util.Objects;

@Controller
public class ProfileController {
    private final UserProfileService profiles;
    private final DeliveryAddressService addresses;
    public ProfileController(UserProfileService profiles, DeliveryAddressService addresses) { this.profiles=profiles; this.addresses=addresses; }
    @GetMapping("/profile") public String profile(Model model) { model.addAttribute("profileUpdateRequest",profiles.getCurrentProfile()); return "profile"; }
    @PostMapping("/profile") public String update(@Valid @ModelAttribute ProfileUpdateRequest request, BindingResult errors,
            @RequestParam(defaultValue="") String deliveryLatitude, @RequestParam(defaultValue="") String deliveryLongitude,
            @RequestParam(defaultValue="") String deliveryApartment, Model model) {
        model.addAttribute("deliveryLatitude", deliveryLatitude); model.addAttribute("deliveryLongitude", deliveryLongitude); model.addAttribute("deliveryApartment", deliveryApartment);
        if (request.getPhone()!=null && !request.getPhone().isBlank()) try { request.setPhone(KazakhstanPhone.normalize(request.getPhone())); }
        catch (IllegalArgumentException ex) { errors.rejectValue("phone", "invalid", ex.getMessage()); }
        String previous=profiles.getCurrentProfile().getDeliveryAddress();
        if (!deliveryLatitude.isBlank() || !deliveryLongitude.isBlank()) {
            try { request.setDeliveryAddress(addresses.complete(deliveryLatitude,deliveryLongitude,deliveryApartment)); }
            catch (IllegalArgumentException ex) { errors.rejectValue("deliveryAddress", "invalid", ex.getMessage()); }
        } else if (request.getDeliveryAddress()!=null && !request.getDeliveryAddress().isBlank() && !Objects.equals(request.getDeliveryAddress(),previous)) {
            errors.rejectValue("deliveryAddress", "invalid", "Выберите адрес в Астане на карте.");
        }
        if (errors.hasErrors()) return "profile";
        profiles.updateCurrentProfile(request); return "redirect:/profile?saved";
    }
}
