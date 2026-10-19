package com.bank.bpo.processapi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.bpo.processapi.domain.StaffUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Which token groups name a queue, and which names the engine may know the user by. */
class StaffUsersTest {

    private static final String SUB = "5d3f9a52-8f0c-4a51-9b1e-2f1a7c3e6d40";
    private final StaffUsers users = new StaffUsers("sub", "preferred_username", "groups", "/staff/fbx-");

    @Test
    void onlyAPlainLowerCaseQueueNameUnderTheExactPrefixNamesAQueue() {
        StaffUser user = users.from(token("ayse.analyst", List.of(
            "/staff/fbx-cmp-tr-onboarding-l1",
            "/Staff/FBX-cmp-tr-mlro", "/staff/fbx-CMP-TR-MLRO", "/staff/FBX-cmp-review-l2",
            "/staff/fbx-", "/staff/fbx-pay-ops-l1/", "/staff/fbx-pay-ops-l2/nested", "/staff/fbx-/pay-ops-l2",
            "/staff/fbx-pay--ops", "/staff/fbx-pay-ops-l1-", "/staff/fbx-../admin",
            "/staff/banker", "/other/staff/fbx-pay-tr-fast-ops", "cmp-review-l1")));

        assertThat(user.groups()).containsExactly("cmp-tr-onboarding-l1");
    }

    @Test
    void aPrefixThatWouldAdmitTopLevelGroupsIsRefusedAtStartUp() {
        for (String prefix : new String[] {"/", "/staff/", "", " ", "/staff", "fbx-", "/Staff/fbx-"}) {
            assertThatThrownBy(() -> new StaffUsers("sub", "preferred_username", "groups", prefix))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void theEngineSeesTheSubjectAndThePlainDisplayNameOnlyAsAFormerId() {
        StaffUser user = users.from(token("ayse.analyst", List.of()));

        assertThat(user.id()).isEqualTo(SUB);
        assertThat(user.formerIds()).containsExactly("ayse.analyst");
        assertThat(user.mayBe("ayse.analyst")).isTrue();
        assertThat(users.from(token("Ayşe Analist", List.of())).formerIds()).isEmpty();
    }

    private static JwtAuthenticationToken token(String displayName, List<String> groups) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(SUB)
            .claim("preferred_username", displayName).claim("groups", groups).build();
        return new JwtAuthenticationToken(jwt);
    }
}
