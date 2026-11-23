package kz.printlab.controller;

import java.util.UUID;
import kz.printlab.service.CartService;
import kz.printlab.service.OrderService;
import kz.printlab.service.PaymentService;
import kz.printlab.service.UserProfileService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OrderController {
    private final OrderService orders; private final CartService carts; private final UserProfileService profiles; private final PaymentService payments;
    public OrderController(OrderService orders,CartService carts,UserProfileService profiles,PaymentService payments){this.orders=orders;this.carts=carts;this.profiles=profiles;this.payments=payments;}
    private String form(Model model,String key,String name,String phone,String address,String code,String error,boolean preview,String lat,String lon,String apartment) {
        model.addAttribute("cart",carts.getCart()); model.addAttribute("checkoutKey",key); model.addAttribute("deliveryName",name);
        model.addAttribute("deliveryPhone",phone); model.addAttribute("deliveryAddress",address); model.addAttribute("promoCode",code);
        model.addAttribute("deliveryLatitude",lat); model.addAttribute("deliveryLongitude",lon); model.addAttribute("deliveryApartment",apartment);
        if(error!=null) model.addAttribute("checkoutError",error);
        if(preview) try { model.addAttribute("pricing",orders.preview(code)); } catch(IllegalArgumentException|ArithmeticException ex) { model.addAttribute("checkoutError",ex instanceof ArithmeticException?"Сумма заказа слишком велика.":ex.getMessage()); }
        return "checkout";
    }
    @GetMapping("/checkout") public String checkout(Model model) {
        var profile=profiles.getCurrentProfile();
        return form(model,UUID.randomUUID().toString(),profile.getFullName(),profile.getPhone(),profile.getDeliveryAddress(),"",null,false,"","","");
    }
    @PostMapping("/checkout/preview") public String preview(@RequestParam String checkoutKey,@RequestParam(defaultValue="") String deliveryName,@RequestParam(defaultValue="") String deliveryPhone,@RequestParam(defaultValue="") String deliveryAddress,@RequestParam(defaultValue="") String promoCode,@RequestParam(defaultValue="") String deliveryLatitude,@RequestParam(defaultValue="") String deliveryLongitude,@RequestParam(defaultValue="") String deliveryApartment,Model model) {
        return form(model,checkoutKey,deliveryName,deliveryPhone,deliveryAddress,promoCode,null,true,deliveryLatitude,deliveryLongitude,deliveryApartment);
    }
    @PostMapping("/checkout") public String submit(@RequestParam String checkoutKey,@RequestParam(defaultValue="") String deliveryName,@RequestParam(defaultValue="") String deliveryPhone,@RequestParam(defaultValue="") String deliveryAddress,@RequestParam(defaultValue="") String promoCode,@RequestParam(defaultValue="") String deliveryLatitude,@RequestParam(defaultValue="") String deliveryLongitude,@RequestParam(defaultValue="") String deliveryApartment,Model model) {
        try { return "redirect:/orders/"+orders.checkout(UUID.fromString(checkoutKey),deliveryName,deliveryPhone,deliveryAddress,promoCode,deliveryLatitude,deliveryLongitude,deliveryApartment); }
        catch(IllegalArgumentException|ArithmeticException ex) { return form(model,checkoutKey,deliveryName,deliveryPhone,deliveryAddress,promoCode,ex instanceof ArithmeticException?"Сумма заказа слишком велика.":ex.getMessage(),false,deliveryLatitude,deliveryLongitude,deliveryApartment); }
    }
    @GetMapping("/orders") public String list(Model model) { model.addAttribute("orders",orders.list()); return "orders"; }
    @GetMapping("/orders/{id}") public String details(@PathVariable long id,Model model) { var order=orders.get(id);model.addAttribute("order",order);model.addAttribute("items",orders.items(id));if(order.status().equals("AWAITING_PAYMENT")) model.addAttribute("wallet",payments.wallet());return "order-details"; }
    @PostMapping("/orders/{id}/cancel") public String cancel(@PathVariable long id,RedirectAttributes flash) {
        try { orders.cancel(id);flash.addFlashAttribute("orderSuccess","Заказ отменён."); }
        catch(IllegalArgumentException ex) { flash.addFlashAttribute("orderError",ex.getMessage()); }
        return "redirect:/orders/"+id;
    }
    @GetMapping("/orders/{id}/images/{fileId}") @ResponseBody public ResponseEntity<byte[]> image(@PathVariable long id,@PathVariable UUID fileId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options","nosniff").body(orders.image(id,fileId));
    }
}
