package com.citacloud.springboot.contacloud.app.config;

import com.citacloud.springboot.contacloud.app.security.TenantAuthenticationProvider;
import com.citacloud.springboot.contacloud.app.security.PrintAgentAuthenticationFilter;
import com.citacloud.springboot.contacloud.app.views.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, TenantAuthenticationProvider provider,
                                                   PrintAgentAuthenticationFilter printAgents) throws Exception {
        http.authenticationProvider(provider);
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/print-agents/**"));
        http.addFilterBefore(printAgents, AnonymousAuthenticationFilter.class);
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/favicon.svg").permitAll()
            .requestMatchers("/api/print-agents/pair", "/api/print-agents/refresh").permitAll()
            .requestMatchers("/api/print-agents/me", "/api/print-agents/heartbeat").hasAuthority("PRINT_AGENT")
            .requestMatchers("/api/company/logo").authenticated());
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer.loginView(LoginView.class));
        return http.build();
    }
}
