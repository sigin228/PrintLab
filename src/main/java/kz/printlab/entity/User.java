package kz.printlab.entity;
import jakarta.persistence.*; import java.time.Instant; import kz.printlab.enums.Role;
@Entity @Table(name="users") public class User {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
@Column(nullable=false,unique=true) private String email;
@Column(unique=true,length=40) private String username; public String getUsername(){return username;} public void setUsername(String v){username=v;}
@Column(name="password_hash",nullable=false) private String passwordHash;
@Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Role role;
@Column(nullable=false) private boolean enabled; @Column(name="created_at",nullable=false) private Instant createdAt;
public Long getId(){return id;} public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;} public Role getRole(){return role;} public void setRole(Role v){role=v;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;} public void setCreatedAt(Instant v){createdAt=v;}}
