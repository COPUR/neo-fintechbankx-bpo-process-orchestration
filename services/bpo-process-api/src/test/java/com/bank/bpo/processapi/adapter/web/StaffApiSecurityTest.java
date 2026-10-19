package com.bank.bpo.processapi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bank.bpo.processapi.FakeUserTasks;
import com.bank.bpo.processapi.application.port.UserTaskPort;
import com.bank.bpo.processapi.config.SecurityConfiguration;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The whole filter chain with real tokens and real DPoP proofs: only a DPoP-bound staff token with a matching
 * proof reaches a controller, each actor's routes need that actor's queue group, and the decision body is the
 * decision and its coded reason alone.
 */
@SpringBootTest(properties = {
    "bpo.process-api.token.issuer-uri=https://keycloak.test/realms/staff",
    "bpo.process-api.token.jwk-set-uri=https://keycloak.test/realms/staff/protocol/openid-connect/certs",
    "bpo.process-api.engine.base-url=https://camunda.test",
    "bpo.process-api.engine.token-uri=https://keycloak.test/realms/services/protocol/openid-connect/token",
    "bpo.process-api.engine.client-credential=test-only-not-a-credential",
    "bpo.process-api.engine.scope=aud-camunda-orchestration"})
@AutoConfigureMockMvc
class StaffApiSecurityTest {

    private static final String ISSUER = "https://keycloak.test/realms/staff";
    /** The token's sub: the engine records it as assignee, so a rename in the directory changes nothing. */
    private static final String USER_ID = "5d3f9a52-8f0c-4a51-9b1e-2f1a7c3e6d40";
    private static final RSAKey ISSUER_KEY = rsa();

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        JwtDecoder testDecoder() throws Exception {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(ISSUER_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(SecurityConfiguration.tokenValidator(ISSUER, "svc-bpo-process"));
            return decoder;
        }

        @Bean
        @Primary
        UserTaskPort fakeEngine() {
            return new FakeUserTasks();
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserTaskPort engine;

    private FakeUserTasks fake;
    private ECKey userKey;

    @BeforeEach
    void setUp() throws Exception {
        fake = (FakeUserTasks) engine;
        fake.tasks.clear();
        fake.completions.clear();
        fake.writes.clear();
        fake.add(1, "lib-screening-review", "Task_AnalystReview", USER_ID, 100, "cmp-tr-onboarding-l1");
        userKey = new ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate();
    }

    @Test
    void aDpopBoundTokenWithAMatchingProofIsServed() throws Exception {
        String token = token(userKey, "svc-bpo-process", List.of("/staff/fbx-cmp-tr-onboarding-l1", "/staff/banker"));

        mvc.perform(dpop(get("/api/v1/me"), "GET", "http://localhost/api/v1/me", token, userKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user").value("ayse.analyst"))
            .andExpect(jsonPath("$.actors[0]").value("compliance-analyst"));
    }

    @Test
    void onlyGroupsUnderTheQueuePrefixNameQueuesAndTheEngineSeesTheSubject() throws Exception {
        // A group without the prefix, even one spelled like a queue, opens no queue; role groups are ignored.
        String unprefixed = token(userKey, "svc-bpo-process", List.of("/cmp-tr-onboarding-l1", "cmp-tr-onboarding-l1", "/staff/banker"));
        mvc.perform(dpop(get("/api/v1/me"), "GET", "http://localhost/api/v1/me", unprefixed, userKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actors").isEmpty());

        fake.add(2, "lib-screening-review", "Task_AnalystReview", null, 200, "cmp-tr-onboarding-l1");
        String analyst = token(userKey, "svc-bpo-process", List.of("/staff/fbx-cmp-tr-onboarding-l1"));
        String url = "http://localhost/api/v1/actors/compliance-analyst/tasks/2/claim";
        mvc.perform(dpop(post("/api/v1/actors/compliance-analyst/tasks/2/claim"), "POST", url, analyst, userKey))
            .andExpect(status().isOk());
        assertThat(fake.writes).containsExactly("assign 2 " + USER_ID);
    }

    @Test
    void theSameTokenAsAPlainBearerIsRefused() throws Exception {
        String token = token(userKey, "svc-bpo-process", List.of("/staff/fbx-cmp-tr-onboarding-l1", "/staff/banker"));

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        // Two guards refuse an unbound bearer: the API reads no bearer token, and the token validator wants cnf.jkt.
        String unbound = token(null, "svc-bpo-process", List.of("/staff/fbx-cmp-tr-onboarding-l1", "/staff/banker"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + unbound)).andExpect(status().isUnauthorized());
    }

    @Test
    void aProofFromAnotherKeyOrForAnotherUrlIsRefused() throws Exception {
        String token = token(userKey, "svc-bpo-process", List.of("/staff/fbx-cmp-tr-onboarding-l1", "/staff/banker"));
        ECKey thief = new ECKeyGenerator(Curve.P_256).generate();

        mvc.perform(dpop(get("/api/v1/me"), "GET", "http://localhost/api/v1/me", token, thief))
            .andExpect(status().isUnauthorized());
        mvc.perform(dpop(get("/api/v1/me"), "GET", "http://localhost/api/v1/other", token, userKey))
            .andExpect(status().isUnauthorized());
        mvc.perform(dpop(get("/api/v1/me"), "POST", "http://localhost/api/v1/me", token, userKey))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWithoutKeyBindingOrForAnotherAudienceIsRefused() throws Exception {
        String unbound = token(null, "svc-bpo-process", List.of("/staff/fbx-cmp-tr-onboarding-l1", "/staff/banker"));
        String otherAudience = token(userKey, "svc-acc-accounts-ledger", List.of("/staff/fbx-cmp-tr-onboarding-l1", "/staff/banker"));

        mvc.perform(dpop(get("/api/v1/me"), "GET", "http://localhost/api/v1/me", unbound, userKey))
            .andExpect(status().isUnauthorized());
        mvc.perform(dpop(get("/api/v1/me"), "GET", "http://localhost/api/v1/me", otherAudience, userKey))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void eachActorsRoutesNeedThatActorsQueueGroup() throws Exception {
        String analyst = token(userKey, "svc-bpo-process", List.of("/staff/fbx-cmp-tr-onboarding-l1", "/staff/banker"));
        String url = "http://localhost/api/v1/actors/compliance-senior/tasks";

        mvc.perform(dpop(get("/api/v1/actors/compliance-senior/tasks"), "GET", url, analyst, userKey))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_YOUR_ACTOR"));
        mvc.perform(dpop(get("/api/v1/actors/compliance-analyst/tasks"), "GET",
                "http://localhost/api/v1/actors/compliance-analyst/tasks", analyst, userKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].taskKey").value(1))
            .andExpect(jsonPath("$.items[0].assignment").value("MINE"))
            .andExpect(jsonPath("$.counts.mine").value(1))
            .andExpect(jsonPath("$.next").doesNotExist());
    }

    @Test
    void aListQueryIsCoveredByTheProofWithoutItsQueryStringAndAMalformedOneIsRefused() throws Exception {
        String analyst = token(userKey, "svc-bpo-process", List.of("/staff/fbx-cmp-tr-onboarding-l1"));
        String htu = "http://localhost/api/v1/actors/compliance-analyst/tasks";

        mvc.perform(dpop(get("/api/v1/actors/compliance-analyst/tasks").queryParam("filter", "mine").queryParam("limit", "1"),
                "GET", htu, analyst, userKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(dpop(get("/api/v1/actors/compliance-analyst/tasks").queryParam("limit", "1000"), "GET", htu, analyst, userKey))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("QUERY_NOT_ALLOWED"));
    }

    @Test
    void aDecisionCarryingAnIdOrNoCodedReasonIsRefusedAndOnlyDecisionAndReasonAreWritten() throws Exception {
        String analyst = token(userKey, "svc-bpo-process", List.of("/staff/fbx-cmp-tr-onboarding-l1", "/staff/banker"));
        String url = "http://localhost/api/v1/actors/compliance-analyst/tasks/1/decision";

        mvc.perform(dpop(post("/api/v1/actors/compliance-analyst/tasks/1/decision"), "POST", url, analyst, userKey)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CLEAR\",\"caseId\":\"CASE-OTHER\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BODY_NOT_ALLOWED"));
        mvc.perform(dpop(post("/api/v1/actors/compliance-analyst/tasks/1/decision"), "POST", url, analyst, userKey)
                .contentType(MediaType.APPLICATION_JSON).content("[\"CLEAR\"]"))
            .andExpect(status().isBadRequest());
        assertThat(fake.completions).isEmpty();

        mvc.perform(dpop(post("/api/v1/actors/compliance-analyst/tasks/1/decision"), "POST", url, analyst, userKey)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CLEAR\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BODY_NOT_ALLOWED"));
        mvc.perform(dpop(post("/api/v1/actors/compliance-analyst/tasks/1/decision"), "POST", url, analyst, userKey)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CLEAR\",\"reason\":\"SLA_EXPIRED\"}"))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("REASON_NOT_ALLOWED"));
        assertThat(fake.completions).isEmpty();

        mvc.perform(dpop(post("/api/v1/actors/compliance-analyst/tasks/1/decision"), "POST", url, analyst, userKey)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CLEAR\",\"reason\":\"OTHER\"}"))
            .andExpect(status().isNoContent());
        assertThat(fake.completions.get(1L))
            .containsOnly(Map.entry("reviewDecision", "CLEAR"), Map.entry("decisionReason", "OTHER"));
    }

    @Test
    void aMalformedTaskKeyLooksLikeNoTask() throws Exception {
        String analyst = token(userKey, "svc-bpo-process", List.of("/staff/fbx-cmp-tr-onboarding-l1", "/staff/banker"));

        mvc.perform(dpop(get("/api/v1/actors/compliance-analyst/tasks/1e3"), "GET",
                "http://localhost/api/v1/actors/compliance-analyst/tasks/1e3", analyst, userKey))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void nothingOutsideTheApiIsServed() throws Exception {
        String token = token(userKey, "svc-bpo-process", List.of("/staff/fbx-cmp-tr-onboarding-l1", "/staff/banker"));

        mvc.perform(dpop(get("/actuator/env"), "GET", "http://localhost/actuator/env", token, userKey))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }

    private static MockHttpServletRequestBuilder dpop(MockHttpServletRequestBuilder request, String htm, String htu,
                                                      String token, ECKey key) throws Exception {
        String ath = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        SignedJWT proof = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType("dpop+jwt")).jwk(key.toPublicJWK()).build(),
            new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).claim("htm", htm).claim("htu", htu)
                .issueTime(new Date()).claim("ath", ath).build());
        proof.sign(new ECDSASigner(key));
        return request.header("Authorization", "DPoP " + token).header("DPoP", proof.serialize());
    }

    private static String token(ECKey boundTo, String audience, List<String> groups) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience(audience)
            .subject(USER_ID).claim("preferred_username", "ayse.analyst").claim("groups", groups)
            .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 300_000));
        if (boundTo != null) {
            claims.claim("cnf", Map.of("jkt", boundTo.computeThumbprint().toString()));
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(ISSUER_KEY.getKeyID()).build(),
            claims.build());
        jwt.sign(new RSASSASigner(ISSUER_KEY));
        return jwt.serialize();
    }

    private static RSAKey rsa() {
        try {
            return new RSAKeyGenerator(2048).keyID("staff-test").generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
