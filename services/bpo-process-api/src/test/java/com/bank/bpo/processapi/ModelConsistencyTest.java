package com.bank.bpo.processapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.bpo.processapi.domain.Actor;
import com.bank.bpo.processapi.domain.TaskType;
import com.bank.bpo.processapi.domain.TaskTypes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The API's task catalogue against models/: every Camunda user task in a model is served by exactly one task
 * type, each type's decisions equal its form's options, and each actor's configured queues equal the groups
 * the routing DMN can write. A new user task in a model fails here until an actor and a screen exist for it.
 */
class ModelConsistencyTest {

    private static final Path MODELS = Path.of("../../models");
    private static final Pattern USER_TASK = Pattern.compile(
        "<bpmn:userTask id=\"([^\"]+)\".*?formId=\"([^\"]+)\".*?</bpmn:userTask>", Pattern.DOTALL);
    private static final Pattern PROCESS = Pattern.compile("<bpmn:process id=\"([^\"]+)\"");

    @Test
    void everyUserTaskInTheModelsIsServedWithItsFormsDecisions() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MODELS.resolve("bpmn"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".bpmn")).toList()) {
                String xml = Files.readString(file);
                Matcher process = PROCESS.matcher(xml);
                assertThat(process.find()).as(file.toString()).isTrue();
                Matcher task = USER_TASK.matcher(xml);
                while (task.find()) {
                    TaskType type = TaskTypes.find(process.group(1), task.group(1)).orElse(null);
                    assertThat(type).as("user task %s/%s has no task type", process.group(1), task.group(1)).isNotNull();
                    assertThat(type.decisions()).as("decisions of %s", task.group(1))
                        .containsExactlyElementsOf(formValues(task.group(2), type.decisionVariable()));
                    found.add(process.group(1) + "/" + task.group(1));
                }
            }
        }
        assertThat(found).containsExactlyInAnyOrderElementsOf(
            TaskTypes.all().stream().map(t -> t.processDefinitionId() + "/" + t.elementId()).toList());
    }

    @Test
    void theConfiguredQueuesAreTheGroupsTheRoutingDecisionWrites() throws IOException {
        String cmp = Files.readString(MODELS.resolve("dmn/cmp/cmp-review-routing.dmn"));
        String pay = Files.readString(MODELS.resolve("dmn/pay/pay-exception-routing.dmn"));
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(configured(yml, Actor.COMPLIANCE_ANALYST)).isEqualTo(dmnOutputs(cmp, "Q"));
        assertThat(configured(yml, Actor.COMPLIANCE_SENIOR)).isEqualTo(dmnOutputs(cmp, "EQ"));
        assertThat(configured(yml, Actor.PAYMENTS_OPERATIONS)).isEqualTo(dmnOutputs(pay, "Q"));
        assertThat(configured(yml, Actor.PAYMENTS_SUPERVISOR)).isEqualTo(dmnOutputs(pay, "SQ"));
    }

    private static List<String> formValues(String formId, String key) throws IOException {
        JsonNode form = JsonMapper.builder().build().readTree(Files.readString(MODELS.resolve("forms/" + formId + ".form")));
        List<String> values = new ArrayList<>();
        for (JsonNode component : form.path("components")) {
            if (key.equals(component.path("key").asString(null))) {
                component.path("values").forEach(v -> values.add(v.path("value").asString()));
            }
        }
        return values;
    }

    /** Output entries named OutputEntry_<rule>_<suffix>: Q is the first queue, EQ or SQ the escalation queue. */
    private static Set<String> dmnOutputs(String dmn, String suffix) {
        Matcher m = Pattern.compile("<outputEntry id=\"OutputEntry_[A-Za-z]+_" + suffix + "\"><text>\"([^\"]+)\"</text>").matcher(dmn);
        Set<String> groups = new LinkedHashSet<>();
        while (m.find()) {
            groups.add(m.group(1));
        }
        assertThat(groups).as("routing outputs %s", suffix).isNotEmpty();
        return groups;
    }

    private static Set<String> configured(String yml, Actor actor) {
        Matcher m = Pattern.compile("\\n\\s+" + actor.id() + ": \\[([^\\]]+)\\]").matcher(yml);
        assertThat(m.find()).as("queues of %s", actor.id()).isTrue();
        Set<String> groups = new LinkedHashSet<>();
        for (String g : m.group(1).split(",")) {
            groups.add(g.trim());
        }
        return groups;
    }

}
