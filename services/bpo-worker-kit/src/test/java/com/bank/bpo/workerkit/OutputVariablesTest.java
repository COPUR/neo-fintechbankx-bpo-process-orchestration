package com.bank.bpo.workerkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OutputVariablesTest {

    @Test
    void declaredNamesPass() {
        assertThat(OutputVariables.of(Set.of("kycStatus"), Map.of("kycStatus", "VERIFIED"))).containsEntry("kycStatus", "VERIFIED");
    }

    @Test
    void anUndeclaredNameIsRefused() {
        assertThatThrownBy(() -> OutputVariables.of(Set.of("kycStatus"), Map.of("extra", "x")))
            .hasMessageContaining("not declared");
    }

    @Test
    void aNameThatLooksLikeMoneyOrPersonalDataIsRefusedEvenWhenDeclared() {
        for (String name : new String[] {"paymentAmount", "customerEmail", "iban", "firstName", "phoneNumber", "tckn", "balance"}) {
            assertThatThrownBy(() -> OutputVariables.of(Set.of(name), Map.of(name, "x")))
                .as(name).hasMessageContaining("money or personal data");
        }
    }

    @Test
    void aResultOverEightKilobytesIsRefused() {
        java.util.Map<String, Object> many = new java.util.LinkedHashMap<>();
        for (int n = 0; n < 150; n++) {
            many.put("v" + n, "x".repeat(60));
        }
        assertThatThrownBy(() -> OutputVariables.of(many.keySet(), many))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("bytes");
    }

    @Test
    void theForbiddenPatternIsTheOneTheModelLintUses() throws Exception {
        String js = Files.readString(Path.of("..", "..", "lint", "plugin", "lib", "dictionary.js"));
        Matcher m = Pattern.compile("FORBIDDEN_NAME = /(.*)/i;").matcher(js);
        assertThat(m.find()).isTrue();
        assertThat(OutputVariables.FORBIDDEN.pattern()).isEqualTo(m.group(1));
    }

    @Test
    void aValueThatIsNotAPlainIdOrEnumIsRefused() {
        for (Object bad : new Object[] {"null value with spaces", "../x", "a.b@c", 12.5, java.util.List.of("x")}) {
            assertThatThrownBy(() -> OutputVariables.of(Set.of("accountId"), java.util.Collections.singletonMap("accountId", bad)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not an id");
        }
        assertThatThrownBy(() -> OutputVariables.of(Set.of("accountId"), java.util.Collections.singletonMap("accountId", null)))
            .isInstanceOf(IllegalStateException.class);
    }
}
