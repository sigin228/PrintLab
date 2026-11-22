package kz.printlab.repository;

import java.util.List;
import java.util.Optional;
import kz.printlab.entity.Design;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignRepository extends JpaRepository<Design, Long> {
    Optional<Design> findByIdAndUserId(Long id, Long userId);
    List<Design> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
