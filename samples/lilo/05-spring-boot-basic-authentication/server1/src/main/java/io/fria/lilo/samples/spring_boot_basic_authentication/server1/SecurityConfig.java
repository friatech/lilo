package io.fria.lilo.samples.spring_boot_basic_authentication.server1;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public @NotNull UserDetailsService userDetailsService(
      final @NotNull PasswordEncoder passwordEncoder) {

    final UserDetails user =
        User.withUsername("user")
            .password(passwordEncoder.encode("password"))
            .roles("USER")
            .build();

    return new InMemoryUserDetailsManager(user);
  }

  @Bean
  public @NotNull SecurityFilterChain filterChain(final @NotNull HttpSecurity http)
      throws Exception {

    http.authorizeHttpRequests()
        .anyRequest()
        .authenticated()
        .and()
        .httpBasic()
        .and()
        .csrf()
        .disable();

    return http.build();
  }

  @Bean
  public @NotNull PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(8);
  }
}
