package kz.printlab.repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import kz.printlab.entity.Product;
public interface ProductRepository extends JpaRepository<Product, Long> {
 Optional<Product> findByIdAndActiveTrue(Long id);
 @Query("select p from Product p where p.active = true and (:category = '' or p.category = :category) and lower(p.name) like concat('%', :search, '%') escape '\\' order by p.id")
 List<Product> searchActive(@Param("search") String search, @Param("category") String category);
}
