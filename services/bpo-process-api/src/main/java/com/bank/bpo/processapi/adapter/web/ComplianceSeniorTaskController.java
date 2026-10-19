package com.bank.bpo.processapi.adapter.web;

import com.bank.bpo.processapi.application.TaskInbox;
import com.bank.bpo.processapi.domain.Actor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Senior review (MLRO in AE, compliance officer in TR): clear or reject an escalated case, never one the user escalated. */
@RestController
@RequestMapping("/api/v1/actors/compliance-senior")
public class ComplianceSeniorTaskController extends ActorTaskController {

    public ComplianceSeniorTaskController(TaskInbox inbox, StaffUsers users) {
        super(inbox, users);
    }

    @Override
    protected Actor actor() {
        return Actor.COMPLIANCE_SENIOR;
    }
}
