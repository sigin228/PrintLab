package kz.printlab.controller;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import kz.printlab.service.AdminService;
import kz.printlab.service.OrderService;
import kz.printlab.service.AdminMessagingService;
import kz.printlab.service.ProductImageStorageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminController {
    private final AdminService admin;
    private final ProductImageStorageService images;
    private final OrderService orders;
    private final AdminMessagingService messages;
    public AdminController(AdminService admin, ProductImageStorageService images, OrderService orders, AdminMessagingService messages) { this.admin=admin; this.images=images; this.orders=orders; this.messages=messages; }
    @GetMapping("/product-images/{id}") @ResponseBody
    public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options","nosniff").body(images.load(id));
    }
    @GetMapping("/admin") public String dashboard(Model model) {
        model.addAttribute("stats",admin.dashboard()); model.addAttribute("recentOrders",admin.orders(null,null).stream().limit(8).toList()); return "admin/dashboard";
    }
    @GetMapping("/admin/products") public String products(Model model) { model.addAttribute("products",admin.products()); return "admin/products"; }
    @GetMapping("/admin/products/new") public String newProduct(Model model) {
        Map<String,Object> empty = new LinkedHashMap<>();
        for (String key : List.of("id", "name", "category", "description", "base_price_kzt", "image_url", "active")) empty.put(key, null);
        model.addAttribute("product",empty); model.addAttribute("variants",List.of()); return "admin/product-form";
    }
    @GetMapping("/admin/products/{id}") public String product(@PathVariable long id,Model model) { model.addAttribute("product",admin.product(id)); model.addAttribute("variants",admin.variants(id)); return "admin/product-form"; }
    @PostMapping("/admin/products/save") public String saveProduct(@RequestParam(required=false) Long id, @RequestParam String name,
            @RequestParam String category, @RequestParam(required=false) String description, @RequestParam long price,
            @RequestParam(required=false) String existingImageUrl, @RequestParam(defaultValue="false") boolean active,
            @RequestParam(required=false) MultipartFile file, RedirectAttributes flash) {
        try {
            // Only retain an existing image from the actual product, never from an untrusted form value.
            String imageUrl = id == null ? null : (String) admin.product(id).get("image_url");
            if (file != null && !file.isEmpty()) imageUrl="/product-images/"+images.store(file);
            long saved = admin.saveProduct(id,name,description,category,price,imageUrl,active);
            flash.addFlashAttribute("adminSuccess","Товар сохранён."); return "redirect:/admin/products/"+saved;
        } catch (ResponseStatusException | DataIntegrityViolationException e) {
            flash.addFlashAttribute("adminError", e instanceof ResponseStatusException x && x.getReason()!=null ? x.getReason() : "Не удалось сохранить товар.");
            return id == null ? "redirect:/admin/products/new" : "redirect:/admin/products/"+id;
        }
    }
    @PostMapping("/admin/products/{id}/variants/save") public String saveVariant(@PathVariable long id,
            @RequestParam(required=false) Long variantId, @RequestParam String sku, @RequestParam String size,
            @RequestParam String color, @RequestParam long extraPrice, @RequestParam int stock, @RequestParam(required=false) Integer expectedStock,
            @RequestParam(defaultValue="false") boolean active, RedirectAttributes flash) {
        try { admin.saveVariant(id,variantId,sku,size,color,extraPrice,stock,expectedStock,active); flash.addFlashAttribute("adminSuccess","Вариант сохранён."); }
        catch (ResponseStatusException | DataIntegrityViolationException e) { flash.addFlashAttribute("adminError",e instanceof ResponseStatusException x && x.getReason()!=null ? x.getReason() : "Не удалось сохранить вариант. Проверьте уникальность SKU."); }
        return "redirect:/admin/products/"+id;
    }
    @GetMapping("/admin/orders") public String orders(@RequestParam(required=false) String status, @RequestParam(required=false) String search, Model model) {
        model.addAttribute("orders",admin.orders(status,search)); model.addAttribute("status",status); model.addAttribute("search",search); return "admin/orders";
    }
    @GetMapping("/admin/orders/{id}") public String order(@PathVariable long id,Model model) { model.addAttribute("order",admin.order(id)); model.addAttribute("items",admin.orderItems(id)); return "admin/order-detail"; }
    @PostMapping("/admin/orders/{id}/status") public String status(@PathVariable long id,@RequestParam String next,RedirectAttributes flash) {
        try { admin.advanceOrder(id,next); flash.addFlashAttribute("adminSuccess","Статус обновлён."); }
        catch (ResponseStatusException e) { if(e.getStatusCode().value()==404) throw e; flash.addFlashAttribute("adminError",e.getReason()); }
        return "redirect:/admin/orders/"+id;
    }
    @PostMapping("/admin/orders/{id}/cancel") public String cancel(@PathVariable long id,RedirectAttributes flash) {
        try { admin.cancelOrder(id); flash.addFlashAttribute("adminSuccess","Заказ отменён."); }
        catch (ResponseStatusException e) { if(e.getStatusCode().value()==404) throw e; flash.addFlashAttribute("adminError",e.getReason()); }
        return "redirect:/admin/orders/"+id;
    }
    @GetMapping("/admin/orders/{id}/images/{fileId}") @ResponseBody
    public ResponseEntity<byte[]> orderImage(@PathVariable long id,@PathVariable UUID fileId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(orders.adminImage(id,fileId));
    }
    @PostMapping("/admin/orders/{id}/message") public String message(@PathVariable long id,@RequestParam String subject,@RequestParam String body,RedirectAttributes flash) {
        try { messages.sendOrderMessage(id,subject,body); flash.addFlashAttribute("adminSuccess","Сообщение отправлено."); }
        catch (IllegalArgumentException | IllegalStateException e) { flash.addFlashAttribute("adminError",e.getMessage()); }
        return "redirect:/admin/orders/"+id;
    }}
