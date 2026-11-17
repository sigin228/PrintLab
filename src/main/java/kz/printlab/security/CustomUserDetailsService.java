package kz.printlab.security;
import java.util.Locale; import org.springframework.security.core.userdetails.*; import org.springframework.stereotype.Service; import kz.printlab.repository.UserRepository;
@Service public class CustomUserDetailsService implements UserDetailsService {
 private final UserRepository users; public CustomUserDetailsService(UserRepository u){users=u;}
 public UserDetails loadUserByUsername(String identifier){String value=identifier.trim().toLowerCase(Locale.ROOT); var u=(value.contains("@") ? users.findByEmail(value) : users.findByUsername(value)).orElseThrow(()->new UsernameNotFoundException("User not found")); return org.springframework.security.core.userdetails.User.withUsername(u.getEmail()).password(u.getPasswordHash()).roles(u.getRole().name()).disabled(!u.isEnabled()).build();}
}
