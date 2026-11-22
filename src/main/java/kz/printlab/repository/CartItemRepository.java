package kz.printlab.repository;

import java.util.List;
import java.util.Optional;
import kz.printlab.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartIdOrderById(Long cartId);
    Optional<CartItem> findByIdAndCartUserId(Long id, Long userId);
    boolean existsByDesignId(Long designId);
}
