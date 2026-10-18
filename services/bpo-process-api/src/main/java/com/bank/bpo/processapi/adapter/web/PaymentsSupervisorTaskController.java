package com.bank.bpo.processapi.adapter.web;

import com.bank.bpo.processapi.application.TaskInbox;
import com.bank.bpo.processapi.domain.Actor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Payments supervisor: settle or reject an escalated or expired exception, never one the user escalated. */
@RestController
@RequestMapping("/api/v1/actors/payments-supervisor")
public class PaymentsSupervisorTaskController extends ActorTaskController {

    public PaymentsSupervisorTaskController(TaskInbox inbox, StaffUsers users) {
        super(inbox, users);
    }

    @Override
    protected Actor actor() {
        return Actor.PAYMENTS_SUPERVISOR;
    }
}
