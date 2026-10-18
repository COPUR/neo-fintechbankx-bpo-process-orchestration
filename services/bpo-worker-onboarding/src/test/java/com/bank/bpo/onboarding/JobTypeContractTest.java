package com.bank.bpo.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.annotation.JobWorker;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The job types the workers serve are the ones models/dictionary/job-types.yaml lists for this family, one class
 * per type (07 O8), and every variable a worker fetches is in the variable allow-list.
 */
class JobTypeContractTest {

    private static final Path DICTIONARY = Path.of("..", "..", "models", "dictionary");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(String file, String key) throws Exception {
        Map<String, Object> doc = new Yaml().load(Files.readString(DICTIONARY.resolve(file)));
        return (Map<String, Object>) doc.get(key);
    }

    private static List<Method> jobWorkerMethods() throws Exception {
        List<Method> methods = new ArrayList<>();
        for (String name : new String[] {"VerifyKycWorker", "ScreenWorker", "OpenReviewCaseWorker", "RecordReviewDecisionWorker",
            "OpenAccountWorker", "RejectApplicationWorker", "AliasOfferWorker", "NotifyWorker"}) {
            Class<?> type = Class.forName("com.bank.bpo.onboarding.handler." + name);
            List<Method> annotated = Arrays.stream(type.getDeclaredMethods()).filter((m) -> m.isAnnotationPresent(JobWorker.class)).toList();
            assertThat(annotated).as("one job type per class: " + name).hasSize(1);
            methods.addAll(annotated);
        }
        return methods;
    }

    @Test
    @SuppressWarnings("unchecked")
    void theServedJobTypesAreExactlyTheOnesTheDictionaryAssignsToThisFamily() throws Exception {
        Set<String> served = new TreeSet<>();
        jobWorkerMethods().forEach((m) -> served.add(m.getAnnotation(JobWorker.class).type()));
        Set<String> declared = new TreeSet<>();
        read("job-types.yaml", "job-types").forEach((type, spec) -> {
            if ("onboarding".equals(((Map<String, Object>) spec).get("family"))) {
                declared.add(type);
            }
        });
        assertThat(served).isEqualTo(declared);
    }

    @Test
    void everyFetchedVariableIsInTheAllowList() throws Exception {
        Set<String> allowed = read("variables.yaml", "variables").keySet();
        for (Method m : jobWorkerMethods()) {
            assertThat(allowed).containsAll(Arrays.asList(m.getAnnotation(JobWorker.class).fetchVariables()));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyOutputTheDictionaryDeclaresIsInTheAllowListAndInTheWorkersOutputSet() throws Exception {
        Set<String> allowed = read("variables.yaml", "variables").keySet();
        for (Map.Entry<String, Object> e : read("job-types.yaml", "job-types").entrySet()) {
            List<String> outputs = (List<String>) ((Map<String, Object>) e.getValue()).get("outputs");
            assertThat(allowed).as(e.getKey()).containsAll(outputs);
        }
    }
}
