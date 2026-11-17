package kz.printlab.entity; import jakarta.persistence.*;
@Entity @Table(name="user_profiles") public class UserProfile {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
@OneToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false,unique=true) private User user;
@Column(name="full_name",length=120) private String fullName; @Column(length=30) private String phone; @Column(name="delivery_address",columnDefinition="text") private String deliveryAddress;
public void setUser(User v){user=v;} public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;} public String getPhone(){return phone;} public void setPhone(String v){phone=v;} public String getDeliveryAddress(){return deliveryAddress;} public void setDeliveryAddress(String v){deliveryAddress=v;}}
