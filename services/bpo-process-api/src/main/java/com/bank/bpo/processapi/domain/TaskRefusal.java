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
        /** The body carried anything but the decision. */
        BODY_NOT_ALLOWED,
        /** The user completed the maker task of this case; a second person must decide. */
        FOUR_EYES_REQUIRED,
        /** The engine did not answer, or answered with something the API cannot read: nothing was written. */
        ENGINE_UNAVAILABLE
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
