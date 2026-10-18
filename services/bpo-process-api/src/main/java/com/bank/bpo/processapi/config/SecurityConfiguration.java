package com.bank.bpo.processapi.config;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Staff calls are DPoP only (local ADR 0002, user calls through a UI): every request carries
 * {@code Authorization: DPoP <token>} and a {@code DPoP} proof for this method and URL, the token is bound to
 * the proof key ({@code cnf.jkt}), its issuer is the staff realm and its audience is this API. A plain bearer
 * token is not read at all, so a stolen token without its key gets 401. No cookies, no session, so no CSRF.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain api(HttpSecurity http, JwtDecoder decoder) throws Exception {
        http.securityMatcher("/api/**")
            .authorizeHttpRequests(a -> a.anyRequest().authenticated())
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(o -> o
                .bearerTokenResolver(request -> null)
                .jwt(j -> j.decoder(decoder))
                .dPoP(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    SecurityFilterChain everythingElse(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a.anyRequest().denyAll())
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(ProcessApiProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.token().jwkSetUri().toString()).build();
        decoder.setJwtValidator(tokenValidator(properties.token().issuerUri(), properties.token().audience()));
        return decoder;
    }

    /** Issuer and time, this API in {@code aud}, and a DPoP key binding in {@code cnf.jkt}. */
    public static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, String audience) {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalStateException("bpo.process-api.token issuer-uri and audience are required");
        }
        OAuth2TokenValidator<Jwt> ours = jwt -> {
            List<String> aud = jwt.getAudience();
            if (aud == null || !aud.contains(audience)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience", null));
            }
            if (!(jwt.getClaim("cnf") instanceof Map<?, ?> cnf) || !(cnf.get("jkt") instanceof String jkt) || jkt.isBlank()) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "not DPoP-bound", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), ours);
    }
}
