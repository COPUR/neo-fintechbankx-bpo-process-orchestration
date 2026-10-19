package com.bank.bpo.processapi.adapter.web;

import com.bank.bpo.processapi.application.TaskInbox;
import com.bank.bpo.processapi.application.TaskView;
import com.bank.bpo.processapi.domain.Actor;
import com.bank.bpo.processapi.domain.TaskRefusal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * The task endpoints every actor has, under {@code /api/v1/actors/<actor>/tasks}. Each actor gets its own
 * controller so its routes, its screens and its authorization stay separate; the rules live in
 * {@link TaskInbox}. The decision body is {@code {"decision": "..."}} and nothing else.
 */
abstract class ActorTaskController {

    private static final Pattern KEY = Pattern.compile("[0-9]{1,19}");

    private final TaskInbox inbox;
    private final StaffUsers users;

    ActorTaskController(TaskInbox inbox, StaffUsers users) {
        this.inbox = inbox;
        this.users = users;
    }

    protected abstract Actor actor();

    @GetMapping("/tasks")
    public List<TaskView> list(Authentication authentication) {
        return inbox.list(actor(), users.from(authentication));
    }

    @GetMapping("/tasks/{taskKey}")
    public TaskView read(Authentication authentication, @PathVariable String taskKey) {
        return inbox.read(actor(), users.from(authentication), key(taskKey));
    }

    @PostMapping("/tasks/{taskKey}/claim")
    public TaskView claim(Authentication authentication, @PathVariable String taskKey) {
        return inbox.claim(actor(), users.from(authentication), key(taskKey));
    }

    @PostMapping("/tasks/{taskKey}/release")
    public ResponseEntity<Void> release(Authentication authentication, @PathVariable String taskKey) {
        inbox.release(actor(), users.from(authentication), key(taskKey));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks/{taskKey}/decision")
    public ResponseEntity<Void> decide(Authentication authentication, @PathVariable String taskKey,
                                       @RequestBody Map<String, Object> body) {
        inbox.decide(actor(), users.from(authentication), key(taskKey), body);
        return ResponseEntity.noContent().build();
    }

    private static long key(String taskKey) {
        if (!KEY.matcher(taskKey).matches()) {
            throw new TaskRefusal(TaskRefusal.Reason.TASK_NOT_FOUND);
        }
        try {
            return Long.parseLong(taskKey);
        } catch (NumberFormatException e) {
            throw new TaskRefusal(TaskRefusal.Reason.TASK_NOT_FOUND);
        }
    }
}
