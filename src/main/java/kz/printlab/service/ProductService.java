package kz.printlab.service;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import kz.printlab.dto.*;
import kz.printlab.repository.*;
@Service public class ProductService {
 private final ProductRepository products; private final ProductVariantRepository variants;
 public ProductService(ProductRepository p, ProductVariantRepository v){products=p;variants=v;}
 @Transactional(readOnly=true) public List<ProductListItemDto> getAllProducts(){return searchProducts(null,null);}
 @Transactional(readOnly=true) public List<ProductListItemDto> searchProducts(String search,String category){
  String q=search==null?"":search.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
  String c=category==null?"":category.trim().toUpperCase(Locale.ROOT);
  return products.searchActive(q,c).stream().map(p->new ProductListItemDto(p.getId(),p.getName(),p.getCategory(),p.getBasePriceKzt(),p.getImageUrl())).toList();
 }
 @Transactional(readOnly=true) public ProductDetailsDto getProductById(Long id){
  var p=products.findByIdAndActiveTrue(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  var items=variants.findByProductIdAndActiveTrueOrderById(id).stream().map(v->new ProductVariantDto(v.getId(),v.getSku(),v.getSize(),v.getColor(),v.getExtraPriceKzt(),v.getStockQuantity(),Math.addExact(p.getBasePriceKzt(),v.getExtraPriceKzt()))).toList();
  return new ProductDetailsDto(p.getId(),p.getName(),p.getDescription(),p.getCategory(),p.getBasePriceKzt(),p.getImageUrl(),items);
 }
}
