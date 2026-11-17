package kz.printlab.controller;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import kz.printlab.service.ProductService;
@Controller public class ProductController {
 private final ProductService products; public ProductController(ProductService p){products=p;}
 @GetMapping("/catalog") public String catalog(@RequestParam(defaultValue="") String search, @RequestParam(defaultValue="") String category, Model model){model.addAttribute("products",products.searchProducts(search,category));model.addAttribute("search",search);model.addAttribute("category",category);return "catalog";}
 @GetMapping("/catalog/{id}") public String details(@PathVariable Long id, Model model){model.addAttribute("product",products.getProductById(id));return "product-details";}
}
