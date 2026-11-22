package kz.printlab.config;

import java.util.LinkedHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> routes = new LinkedHashMap<>();
        routes.put(request -> request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/"), new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(routes);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        return http.authorizeHttpRequests(a -> a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
            .requestMatchers(HttpMethod.GET, "/designer/**", "/product-images/**", "/admin-prints/*/image").permitAll()
            .requestMatchers("/", "/catalog", "/catalog/**", "/register", "/login", "/css/**", "/js/**", "/images/**", "/vendor/**").permitAll()
            .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
            .formLogin(f -> f.loginPage("/login").successHandler((request, response, authentication) -> {
                String productId = null;
                if (request.getCookies() != null) {
                    for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                        if ("printlab_designer_return".equals(cookie.getName())
                                && cookie.getValue().matches("[1-9][0-9]{0,17}")) {
                            productId = cookie.getValue();
                            break;
                        }
                    }
                }
                jakarta.servlet.http.Cookie cleared = new jakarta.servlet.http.Cookie("printlab_designer_return", "");
                cleared.setPath("/");
                cleared.setMaxAge(0);
                cleared.setHttpOnly(true);
                cleared.setSecure(request.isSecure());
                response.addCookie(cleared);
                response.sendRedirect(request.getContextPath() + (productId == null
                        ? "/profile" : "/designer/" + productId + "?resume=1"));
            }).permitAll())
            .logout(l -> l.logoutSuccessUrl("/?logout").invalidateHttpSession(true).deleteCookies("JSESSIONID"))
            .build();
    }
}
