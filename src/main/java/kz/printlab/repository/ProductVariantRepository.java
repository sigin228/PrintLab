package kz.printlab.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import kz.printlab.entity.ProductVariant;
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> { List<ProductVariant> findByProductIdAndActiveTrueOrderById(Long productId); }
