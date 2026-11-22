package kz.printlab.repository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import kz.printlab.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface UserRepository extends JpaRepository<User,Long> {
 Optional<User> findByEmail(String email); boolean existsByEmail(String email);
 Optional<User> findByUsername(String username); boolean existsByUsername(String username);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select u from User u where u.email = :email")
 Optional<User> lockByEmail(@Param("email") String email);
}
