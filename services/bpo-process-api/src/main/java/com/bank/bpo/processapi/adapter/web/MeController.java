package com.bank.bpo.processapi.adapter.web;

import com.bank.bpo.processapi.application.TaskInbox;
import com.bank.bpo.processapi.domain.Actor;
import com.bank.bpo.processapi.domain.StaffUser;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Who the staff web is talking to: a display name to greet and the actors whose screens it should offer. */
@RestController
public class MeController {

    private final TaskInbox inbox;
    private final StaffUsers users;

    public MeController(TaskInbox inbox, StaffUsers users) {
        this.inbox = inbox;
        this.users = users;
    }

    @GetMapping("/api/v1/me")
    public Me me(Authentication authentication) {
        StaffUser user = users.from(authentication);
        return new Me(users.displayName(authentication).orElse(null), inbox.actorsOf(user).stream().map(Actor::id).toList());
    }

    public record Me(String user, List<String> actors) {
    }
}
