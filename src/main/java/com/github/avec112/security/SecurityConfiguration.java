package com.github.avec112.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

/**
 * Demo security setup: two users, so the shared vocabulary can be seen
 * changing for one user while the other edits it. A real application uses
 * its own user store; the vocabulary only needs the authenticated user name.
 */
@EnableWebSecurity
@Configuration
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer.loginView(LoginView.class));
        return http.build();
    }

    @Bean
    UserDetailsService users() {
        // Demo only: plain-text passwords.
        return new InMemoryUserDetailsManager(
                User.withUsername("anna").password("{noop}demo").roles("USER").build(),
                User.withUsername("bjorn").password("{noop}demo").roles("USER").build());
    }
}
