package com.bank.bpo.processapi.config;

import com.bank.bpo.processapi.adapter.camunda.CamundaUserTaskAdapter;
import com.bank.bpo.processapi.adapter.metrics.MicrometerTaskEvents;
import com.bank.bpo.processapi.adapter.web.StaffUsers;
import com.bank.bpo.processapi.application.QueueGroups;
import com.bank.bpo.processapi.application.TaskInbox;
import com.bank.bpo.processapi.application.port.TaskEvents;
import com.bank.bpo.processapi.application.port.UserTaskPort;
import com.bank.bpo.processapi.domain.Actor;
import com.bank.bpo.workerkit.ClientCredentialsToken;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the use cases to the engine adapter and the token reader. */
@Configuration(proxyBeanMethods = false)
public class ApplicationConfiguration {

    @Bean
    UserTaskPort userTaskPort(ProcessApiProperties properties) {
        ProcessApiProperties.Engine engine = properties.engine();
        ClientCredentialsToken token = new ClientCredentialsToken(engine.tokenUri(), engine.clientId(),
            engine.clientCredential(), engine.scope(), Clock.systemUTC());
        return new CamundaUserTaskAdapter(engine.baseUrl(), token);
    }

    @Bean
    QueueGroups queueGroups(ProcessApiProperties properties) {
        return new QueueGroups(queues(properties.queues()));
    }

    @Bean
    TaskEvents taskEvents(ObjectProvider<MeterRegistry> registry) {
        // The platform's registry when one is configured (actuator arrives with the chart); else the global one.
        return new MicrometerTaskEvents(registry.getIfAvailable(() -> Metrics.globalRegistry));
    }

    @Bean
    TaskInbox taskInbox(UserTaskPort tasks, QueueGroups queues, TaskEvents events) {
        return new TaskInbox(tasks, queues, events, Clock.systemUTC());
    }

    @Bean
    StaffUsers staffUsers(ProcessApiProperties properties) {
        ProcessApiProperties.Token token = properties.token();
        return new StaffUsers(token.userClaim(), token.displayClaim(), token.groupsClaim(), token.groupPrefix());
    }

    /** Every actor needs at least one queue group, and no group may serve two actors. */
    static Map<Actor, Set<String>> queues(Map<String, List<String>> configured) {
        if (configured == null) {
            throw new IllegalStateException("bpo.process-api.queues is required");
        }
        Map<Actor, Set<String>> byActor = new EnumMap<>(Actor.class);
        Set<String> seen = new LinkedHashSet<>();
        configured.forEach((id, groups) -> {
            Actor actor = Actor.fromId(id)
                .orElseThrow(() -> new IllegalStateException("bpo.process-api.queues names an unknown actor"));
            for (String g : groups) {
                if (g == null || g.isBlank() || !seen.add(g)) {
                    throw new IllegalStateException("a queue group is blank or serves two actors");
                }
            }
            byActor.put(actor, new LinkedHashSet<>(groups));
        });
        for (Actor actor : Actor.values()) {
            if (byActor.getOrDefault(actor, Set.of()).isEmpty()) {
                throw new IllegalStateException("bpo.process-api.queues has no group for " + actor.id());
            }
        }
        return byActor;
    }
}
