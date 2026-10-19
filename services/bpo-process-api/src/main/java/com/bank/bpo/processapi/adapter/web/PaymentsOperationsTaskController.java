package com.bank.bpo.processapi.adapter.web;

import com.bank.bpo.processapi.application.TaskInbox;
import com.bank.bpo.processapi.domain.Actor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Payments operations: record what the scheme shows for an instant payment exception (settled, rejected or escalate). */
@RestController
@RequestMapping("/api/v1/actors/payments-operations")
public class PaymentsOperationsTaskController extends ActorTaskController {

    public PaymentsOperationsTaskController(TaskInbox inbox, StaffUsers users) {
        super(inbox, users);
    }

    @Override
    protected Actor actor() {
        return Actor.PAYMENTS_OPERATIONS;
    }
}
