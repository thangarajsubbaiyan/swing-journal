package com.swingjournal.domain;

/**
 * A chart setup from the user's playbook (e.g. "Channel up") with a reference card and a checklist.
 * {@code checklist} holds one checklist item per line.
 */
public record Setup(
        Long id,
        String name,
        String description,
        String tradingNotes,
        String checklist,
        String warnings) {
}
