package com.bank.bpo.processapi.adapter.web;

import com.bank.bpo.processapi.application.TaskInbox;
import com.bank.bpo.processapi.domain.Actor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** First-line compliance review: clear, reject or escalate a screening REVIEW (lib-screening-review analyst task). */
@RestController
@RequestMapping("/api/v1/actors/compliance-analyst")
public class ComplianceAnalystTaskController extends ActorTaskController {

    public ComplianceAnalystTaskController(TaskInbox inbox, StaffUsers users) {
        super(inbox, users);
    }

    @Override
    protected Actor actor() {
        return Actor.COMPLIANCE_ANALYST;
    }
}
