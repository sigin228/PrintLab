package kz.printlab.controller;

import kz.printlab.service.PaymentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WalletController {
    private final PaymentService payments;
    public WalletController(PaymentService payments) { this.payments = payments; }
    @GetMapping("/wallet") public String wallet(Model model) { model.addAttribute("wallet",payments.wallet()); return "wallet"; }
    @PostMapping("/wallet/top-up") public String topUp(@RequestParam Long amount, RedirectAttributes flash) {
        try { payments.topUp(amount); flash.addFlashAttribute("walletSuccess","Демонстрационный баланс пополнен."); }
        catch (IllegalArgumentException | ArithmeticException e) { flash.addFlashAttribute("walletError",e instanceof ArithmeticException ? "Превышен допустимый баланс." : e.getMessage()); }
        return "redirect:/wallet";
    }
    @PostMapping("/orders/{id}/pay") public String pay(@PathVariable long id, RedirectAttributes flash) {
        try {
            if (payments.pay(id)) flash.addFlashAttribute("paymentSuccess","Заказ оплачен демонстрационными средствами.");
            else flash.addFlashAttribute("paymentError","Недостаточно средств. Списания не было.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("paymentError",e.getMessage()); }
        return "redirect:/orders/"+id;
    }
}
