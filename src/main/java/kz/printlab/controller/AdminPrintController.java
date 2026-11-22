package kz.printlab.controller;

import java.util.UUID;
import kz.printlab.service.AdminPrintPresetService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminPrintController {
    private final AdminPrintPresetService presets;
    public AdminPrintController(AdminPrintPresetService presets) { this.presets=presets; }
    @GetMapping("/admin-prints/{id}/image") @ResponseBody public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options","nosniff").body(presets.image(id));
    }
    @PreAuthorize("hasRole('ADMIN')") @GetMapping("/admin/prints") public String list(Model model) {
        model.addAttribute("prints",presets.all()); return "admin/prints";
    }
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/admin/prints") public String create(@RequestParam String name,@RequestParam MultipartFile file, RedirectAttributes flash) {
        try { presets.create(name,file); flash.addFlashAttribute("adminSuccess","Принт добавлен."); }
        catch(ResponseStatusException e) { flash.addFlashAttribute("adminError",e.getReason()!=null?e.getReason():"Не удалось добавить принт."); }
        return "redirect:/admin/prints";
    }
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/admin/prints/{id}/disable") public String disable(@PathVariable UUID id, RedirectAttributes flash) {
        presets.deactivate(id); flash.addFlashAttribute("adminSuccess","Принт скрыт из конструктора."); return "redirect:/admin/prints";
    }
}
