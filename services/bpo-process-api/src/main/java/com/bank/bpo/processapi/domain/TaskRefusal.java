package com.bank.bpo.processapi.domain;

/** Why the API refused a task operation. Each reason maps to one HTTP status and one code the UI translates. */
public final class TaskRefusal extends RuntimeException {

    public enum Reason {
        /** The user holds none of this actor's queue groups. */
        NOT_YOUR_ACTOR,
        /** Unknown, closed, of another actor or outside the user's queues: one answer for all, so nothing leaks. */
        TASK_NOT_FOUND,
        /** Someone else holds the task. */
        TASK_CLAIMED_BY_OTHER,
        /** Decide or release on a task the user has not claimed. */
        TASK_NOT_ASSIGNED_TO_YOU,
        /** The decision is not one the task's form offers. */
        DECISION_NOT_ALLOWED,
        /** The body carried anything but the decision and its reason code. */
        BODY_NOT_ALLOWED,
        /** The user is the last assignee of this case's maker task; a second person must claim and decide. */
        FOUR_EYES_REQUIRED,
        /** The engine could not be reached or read; no write was sent. */
        ENGINE_UNAVAILABLE,
        /** A write was sent but its answer was lost or was a server error: it may have been applied. */
        OUTCOME_UNKNOWN,
        /** The reason is not one of the task type's human codes. */
        REASON_NOT_ALLOWED
    }

    private final Reason reason;

    public TaskRefusal(Reason reason) {
        super(reason.name(), null, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
