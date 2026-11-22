package kz.printlab.controller;

import kz.printlab.service.AdminPromoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/promos")
public class AdminPromoController {
    private final AdminPromoService promos;
    public AdminPromoController(AdminPromoService promos) { this.promos=promos; }
    @GetMapping public String list(Model model) { model.addAttribute("promos",promos.list()); return "admin/promos"; }
    @PostMapping("/create") public String create(@RequestParam String code,@RequestParam String type,@RequestParam long value,@RequestParam long minimum,@RequestParam(required=false) Integer limit,RedirectAttributes flash) {
        try { promos.create(code,type,value,minimum,limit); flash.addFlashAttribute("adminSuccess","Промокод создан."); }
        catch(IllegalArgumentException e) { flash.addFlashAttribute("adminError",e.getMessage()); }
        return "redirect:/admin/promos";
    }
    @PostMapping("/{id}/active") public String active(@PathVariable long id,@RequestParam boolean active,RedirectAttributes flash) {
        try { promos.setActive(id,active); flash.addFlashAttribute("adminSuccess","Промокод обновлён."); }
        catch(IllegalArgumentException e) { flash.addFlashAttribute("adminError",e.getMessage()); }
        return "redirect:/admin/promos";
    }
}
