package kz.printlab.service;
import java.time.Instant; import java.util.Locale; import org.springframework.dao.DataIntegrityViolationException; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import kz.printlab.dto.RegisterRequest; import kz.printlab.entity.*; import kz.printlab.enums.Role; import kz.printlab.repository.*;
@Service public class AuthService {
 private final UserRepository users; private final UserProfileRepository profiles; private final PasswordEncoder encoder;
 public AuthService(UserRepository u,UserProfileRepository p,PasswordEncoder e){users=u;profiles=p;encoder=e;}
 @Transactional public void registerUser(RegisterRequest r){
  String email=r.getEmail().trim().toLowerCase(Locale.ROOT), username=r.getUsername().toLowerCase(Locale.ROOT);
  if(users.existsByEmail(email)) throw new DuplicateEmailException(); if(users.existsByUsername(username)) throw new DuplicateUsernameException();
  User u=new User();u.setEmail(email);u.setUsername(username);u.setPasswordHash(encoder.encode(r.getPassword()));u.setRole(Role.CUSTOMER);u.setEnabled(true);u.setCreatedAt(Instant.now());
  try{users.saveAndFlush(u);} catch(DataIntegrityViolationException ex){String detail=ex.getMostSpecificCause().getMessage(); if(detail!=null && detail.contains("users_username_key")) throw new DuplicateUsernameException(); if(detail!=null && detail.contains("users_email_key")) throw new DuplicateEmailException(); throw ex;}
  UserProfile p=new UserProfile();p.setUser(u);p.setFullName(r.getFullName().trim());p.setCity(r.getCity().trim());profiles.save(p);
 }
 public static class DuplicateEmailException extends RuntimeException {} public static class DuplicateUsernameException extends RuntimeException {}
}
