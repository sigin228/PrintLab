package kz.printlab.dto;
import jakarta.validation.constraints.*;
public class RegisterRequest {
 @NotBlank(message="Укажите логин") @Pattern(regexp="[A-Za-z0-9_]{3,40}", message="Логин: 3–40 латинских букв, цифр или знаков подчёркивания") private String username;
 @NotBlank(message="Укажите email") @Email(message="Некорректный email") @Size(max=255) private String email;
 @NotBlank(message="Укажите имя") @Size(max=120,message="Не более 120 символов") private String fullName;
 @NotBlank(message="Укажите город") @Size(max=100,message="Не более 100 символов") private String city;
 @NotBlank(message="Укажите пароль") @Size(min=8,max=72,message="Пароль должен содержать от 8 до 72 символов") @Pattern(regexp="^(?=.*[A-Za-zА-Яа-яЁё])(?=.*[0-9]).*$",message="Пароль должен содержать буквы и цифры") private String password;
 @NotBlank(message="Повторите пароль") private String confirmPassword;
 public String getUsername(){return username;} public void setUsername(String v){username=v;}
 public String getEmail(){return email;} public void setEmail(String v){email=v;}
 public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;}
 public String getCity(){return city;} public void setCity(String v){city=v;}
 public String getPassword(){return password;} public void setPassword(String v){password=v;}
 public String getConfirmPassword(){return confirmPassword;} public void setConfirmPassword(String v){confirmPassword=v;}
}
