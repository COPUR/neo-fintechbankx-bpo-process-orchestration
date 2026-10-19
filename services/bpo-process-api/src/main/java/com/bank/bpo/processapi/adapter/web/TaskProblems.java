package com.bank.bpo.processapi.adapter.web;

import com.bank.bpo.processapi.domain.TaskRefusal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Refusals as RFC 9457 problems with a stable {@code code} the staff web translates. No ids in the text. */
@RestControllerAdvice
public class TaskProblems {

    @ExceptionHandler(TaskRefusal.class)
    public ResponseEntity<ProblemDetail> refusal(TaskRefusal refusal) {
        return problem(status(refusal.reason()), refusal.reason().name());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, TaskRefusal.Reason.BODY_NOT_ALLOWED.name());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> badUser(IllegalArgumentException e) {
        // The token's subject (or a former user name) is not a plain id: the token is not one this API serves.
        return problem(HttpStatus.FORBIDDEN, "USER_NOT_ALLOWED");
    }

    static HttpStatus status(TaskRefusal.Reason reason) {
        return switch (reason) {
            case NOT_YOUR_ACTOR -> HttpStatus.FORBIDDEN;
            case TASK_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TASK_CLAIMED_BY_OTHER, TASK_NOT_ASSIGNED_TO_YOU, FOUR_EYES_REQUIRED -> HttpStatus.CONFLICT;
            case DECISION_NOT_ALLOWED, REASON_NOT_ALLOWED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case BODY_NOT_ALLOWED, QUERY_NOT_ALLOWED -> HttpStatus.BAD_REQUEST;
            case ENGINE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case OUTCOME_UNKNOWN -> HttpStatus.GATEWAY_TIMEOUT;
        };
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(code);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}
