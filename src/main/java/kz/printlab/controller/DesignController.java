package kz.printlab.controller;

import java.util.Map;
import java.util.UUID;
import kz.printlab.dto.*;
import kz.printlab.service.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class DesignController {
    private final DesignService designs; private final DesignStorageService storage; private final ProductService products; private final DesignUploadRegistry uploads; private final AdminPrintPresetService presets;
    public DesignController(DesignService designs, DesignStorageService storage, ProductService products, DesignUploadRegistry uploads, AdminPrintPresetService presets) { this.designs=designs; this.storage=storage; this.products=products; this.uploads=uploads; this.presets=presets; }
    @GetMapping("/designer/{productId}") public String designer(@PathVariable Long productId, @RequestParam(required=false) Long variantId, @RequestParam(required=false) Long designId, Model model) {
        model.addAttribute("adminPresets",presets.active()); model.addAttribute("product",products.getProductById(productId)); model.addAttribute("selectedVariantId",variantId); model.addAttribute("authenticated",org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null && !(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)); model.addAttribute("designId",designId); return "designer";
    }
    @GetMapping("/my-designs") public String myDesigns(Model model) { model.addAttribute("designs",designs.getCurrentUserDesigns()); return "my-designs"; }
    @PostMapping("/api/designs/uploads") @ResponseBody public Map<String,String> upload(@RequestParam("file") MultipartFile file) {
        UUID id=uploads.store(designs.currentUser().getId(),file); return Map.of("fileId",id.toString(),"url","/api/designs/uploads/"+id);
    }
    @PostMapping("/api/designs/uploads/{fileId}/lease") @ResponseBody public ResponseEntity<Void> lease(@PathVariable UUID fileId) {
        uploads.renew(designs.currentUser().getId(),fileId); return ResponseEntity.noContent().build();
    }
    @GetMapping("/api/designs/uploads/{fileId}") @ResponseBody public ResponseEntity<byte[]> image(@PathVariable UUID fileId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options","nosniff").body(storage.loadAuthorizedImage(designs.currentUser().getId(),fileId));
    }
    @PostMapping("/api/designs") @ResponseBody public DesignResponse create(@RequestBody DesignRequest request) { return designs.createDesign(request); }
    @PutMapping("/api/designs/{id}") @ResponseBody public DesignResponse update(@PathVariable long id, @RequestBody DesignRequest request) { return designs.updateDesign(id,request); }
    @GetMapping("/api/designs/{id}") @ResponseBody public DesignResponse get(@PathVariable long id) { return designs.getDesignById(id); }
    @DeleteMapping("/api/designs/{id}") @ResponseBody public ResponseEntity<Void> delete(@PathVariable long id) { designs.deleteDesign(id); return ResponseEntity.noContent().build(); }
}

