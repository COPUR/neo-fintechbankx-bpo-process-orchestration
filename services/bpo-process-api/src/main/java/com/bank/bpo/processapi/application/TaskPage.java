package com.bank.bpo.processapi.application;

import java.time.Instant;
import java.util.List;

/**
 * One page of a task list. {@code next} is the cursor for the following page, or null on the last one.
 * {@code counts} covers every task the query's {@code createdAfter} lets through, whatever the filter, so the
 * filter buttons show their totals. {@code asOf} is the server time the list was read at; the screen sends it
 * back as {@code createdAfter} to count tasks that arrived since.
 */
public record TaskPage(List<TaskView> items, String next, Counts counts, Instant asOf) {

    public record Counts(int mine, int open, int all) {
    }
}
