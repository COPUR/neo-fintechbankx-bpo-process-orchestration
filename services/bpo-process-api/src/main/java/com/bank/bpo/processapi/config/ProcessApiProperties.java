package com.bank.bpo.processapi.config;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the API. There are no working defaults: without an issuer, a JWK set, an audience, the queue
 * groups and the engine client the application does not start.
 *
 * @param queues actor id (for example {@code compliance-analyst}) to the candidate groups the routing DMNs write
 */
@ConfigurationProperties("bpo.process-api")
public record ProcessApiProperties(Token token, Map<String, List<String>> queues, Engine engine) {

    /** The staff access token: who issues it, where its keys are, its audience and which claims name the user. */
    public record Token(String issuerUri, URI jwkSetUri, String audience, String userClaim, String groupsClaim) {
    }

    /** The Orchestration Cluster REST API and the API's own client-credentials client for it. */
    public record Engine(URI baseUrl, URI tokenUri, String clientId, String clientCredential, String scope) {
    }
}
