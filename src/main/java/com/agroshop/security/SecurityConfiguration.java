package com.agroshop.security;

import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            Environment environment,
            PasswordEncoder passwordEncoder) {
        String username = environment.getRequiredProperty("spring.security.user.name");
        String password = environment.getRequiredProperty("spring.security.user.password");
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "Configure SPRING_SECURITY_USER_NAME and SPRING_SECURITY_USER_PASSWORD");
        }

        return new InMemoryUserDetailsManager(User.withUsername(username.trim())
                .password(passwordEncoder.encode(password))
                .roles("SHOP_OWNER")
                .build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
