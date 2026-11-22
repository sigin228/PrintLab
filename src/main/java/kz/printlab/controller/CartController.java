package kz.printlab.controller;

import kz.printlab.dto.CartItemRequest;
import kz.printlab.service.CartService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cart")
public class CartController {
    private final CartService cart;
    public CartController(CartService cart) { this.cart = cart; }
    @GetMapping public String index(Model model) { model.addAttribute("cart", cart.getCart()); return "cart"; }
    @PostMapping("/items") public String add(@RequestParam Long variantId, @RequestParam(required = false) Long designId,
            @RequestParam(defaultValue = "1") Integer quantity, RedirectAttributes flash) {
        try { cart.add(new CartItemRequest(variantId, designId, quantity)); flash.addFlashAttribute("cartSuccess", "Товар добавлен в корзину."); }
        catch (IllegalArgumentException | ArithmeticException e) { flash.addFlashAttribute("cartError", e instanceof ArithmeticException ? "Сумма слишком велика." : e.getMessage()); }
        return "redirect:/cart";
    }
    @PostMapping("/items/{id}/quantity") public String quantity(@PathVariable Long id, @RequestParam Integer quantity, RedirectAttributes flash) {
        try { cart.updateQuantity(id, quantity); flash.addFlashAttribute("cartSuccess", "Количество обновлено."); }
        catch (IllegalArgumentException | ArithmeticException e) { flash.addFlashAttribute("cartError", e instanceof ArithmeticException ? "Сумма слишком велика." : e.getMessage()); }
        return "redirect:/cart";
    }
    @PostMapping("/items/{id}/remove") public String remove(@PathVariable Long id) { cart.remove(id); return "redirect:/cart"; }
    @PostMapping("/clear") public String clear() { cart.clear(); return "redirect:/cart"; }
}
