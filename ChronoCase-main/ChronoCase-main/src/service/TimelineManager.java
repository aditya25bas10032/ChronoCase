package service;

import model.Event;
import model.Timeline;

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Business logic for managing a family of timelines: creation,
 * branching, lookup, switching and hierarchy traversal.
 *
 * Timelines are never deleted by this manager - switching or
 * branching only ever adds or re-points, never removes (core rule:
 * the old timeline always survives).
 */
public class TimelineManager {

    private final Map<Integer, Timeline> timelinesById = new LinkedHashMap<>();
    private Timeline root;
    private Timeline current;
    private TimelineNavigator navigator; // navigation/history logic (Step 5)

    // ----- creation -----

    /**
     * Creates the root timeline. Fails if a root already exists;
     * there is exactly one root per managed timeline family.
     */
    public Timeline createRoot(int id, String label) {
        if (root != null) {
            throw new IllegalStateException("Root timeline already exists (id "
                    + root.getId() + "); there can only be one root");
        }
        root = new Timeline(id, label);
        timelinesById.put(id, root);
        current = root;
        return root;
    }

    /**
     * Creates a branch of the given parent timeline, preserving the
     * parent-child relationship. The parent is not modified beyond
     * registering its new child.
     */
    public Timeline createBranch(int id, String label, Timeline parent) {
        Objects.requireNonNull(parent, "parent timeline must not be null");
        requireKnown(parent, "parent");
        requireFreeId(id);

        Timeline branch = parent.createBranch(id, label);
        timelinesById.put(id, branch);
        return branch;
    }

    /** Convenience overload: branch off the currently active timeline. */
    public Timeline createBranch(int id, String label) {
        requireCurrentExists();
        return createBranch(id, label, current);
    }

    // ----- lookup -----

    /** Returns the timeline with the given id, or empty if unknown. */
    public java.util.Optional<Timeline> findTimelineById(int id) {
        return java.util.Optional.ofNullable(timelinesById.get(id));
    }

    /** Returns the timeline with the given id, or throws a clear error. */
    public Timeline getTimelineById(int id) {
        Timeline t = timelinesById.get(id);
        if (t == null) {
            throw new NoSuchElementException("No timeline with id " + id
                    + " (known ids: " + timelinesById.keySet() + ")");
        }
        return t;
    }

    /** The root timeline; throws if createRoot was never called. */
    public Timeline getRoot() {
        if (root == null) {
            throw new IllegalStateException("No root timeline has been created yet");
        }
        return root;
    }

    /** The currently active timeline; throws if none exists yet. */
    public Timeline getCurrent() {
        requireCurrentExists();
        return current;
    }

    // ----- switching -----

    /**
     * Makes the timeline with the given id the current one. No
     * timelines are modified or deleted by switching.
     */
    public void switchTo(int id) {
        current = getTimelineById(id);
    }

    /** Switches to the given timeline instance if it is managed here. */
    public void switchTo(Timeline timeline) {
        Objects.requireNonNull(timeline, "timeline must not be null");
        requireKnown(timeline, "switch target");
        current = timeline;
    }

    /** Switches to the root timeline. */
    public void switchToRoot() {
        current = getRoot();
    }

    // ----- time travel (Step 5) -----

    /**
     * Travels back to the point of the given event in the current
     * timeline: makes the timeline that owns that event the current
     * one and returns the event.
     *
     * Navigation history (the Stack) lives in TimelineNavigator;
     * this method coordinates: navigator moves, manager stays the
     * source of truth for timelines.
     */
    public Event travelTo(int eventId) {
        requireCurrentExists();
        // Branches snapshot their parent's events, so the same event id
        // can exist on several timelines; prefer the current one.
        java.util.Optional<Event> onCurrent = current.getEvents().stream()
                .filter(e -> e.getId() == eventId)
                .findFirst();
        if (onCurrent.isPresent()) {
            return onCurrent.get();
        }
        for (Timeline t : timelinesById.values()) {
            if (t == current) {
                continue;
            }
            java.util.Optional<Event> hit = t.getEvents().stream()
                    .filter(e -> e.getId() == eventId)
                    .findFirst();
            if (hit.isPresent()) {
                if (navigator != null) {
                    navigator.travelTo(t, LocalDateTime.now());
                }
                current = t;
                return hit.get();
            }
        }
        throw new NoSuchElementException("No event with id " + eventId
                + " on any managed timeline");
    }

    /**
     * Changes the past: creates a child of the current timeline with
     * the given event replaced by a revised version, then makes the
     * new branch current. The original timeline is NOT modified - it
     * keeps its original event (core rule).
     *
     * @return the newly created alternate timeline (now current)
     */
    public Timeline createAlternateTimeline(int revisedEventId, Event revisedEvent) {
        requireCurrentExists();
        Objects.requireNonNull(revisedEvent, "revised event must not be null");

        // Validate against the current timeline before touching anything.
        current.getEvents().stream()
                .filter(e -> e.getId() == revisedEventId)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Current timeline '"
                        + current.getLabel() + "' has no event with id " + revisedEventId));

        int newId = nextTimelineId();
        String label = "Alternate " + newId + " (revised event " + revisedEventId + ")";
        Timeline alternate = createBranch(newId, label, current);
        alternate.replaceEvent(revisedEventId, revisedEvent);
        navigator.travelTo(alternate, LocalDateTime.now());
        current = alternate;
        return alternate;
    }

    /**
     * Steps back through navigation history (Stack pop). Returns the
     * timeline returned to, or empty if there is no history.
     */
    public java.util.Optional<Timeline> returnToPreviousTimeline() {
        requireCurrentExists();
        java.util.Optional<Timeline> previous = navigator.returnToPreviousTimeline();
        previous.ifPresent(t -> current = t);
        return previous;
    }

    /** Attach a navigator (navigation/history logic) to this manager. */
    public void setNavigator(TimelineNavigator navigator) {
        this.navigator = Objects.requireNonNull(navigator, "navigator must not be null");
    }

    /** The navigation history tracker, if one is attached. */
    public TimelineNavigator getNavigator() { return navigator; }

    // ----- hierarchy -----

    /** All managed timelines in creation order, as a read-only list. */
    public List<Timeline> getTimelines() {
        return Collections.unmodifiableList(new ArrayList<>(timelinesById.values()));
    }

    /**
     * The chain from the root down to (and including) the given
     * timeline, root first. Read-only.
     */
    public List<Timeline> getAncestryOf(Timeline timeline) {
        Objects.requireNonNull(timeline, "timeline must not be null");
        requireKnown(timeline, "timeline");

        List<Timeline> chain = new ArrayList<>();
        for (Timeline t = timeline; t != null; t = t.getParent()) {
            chain.add(t);
        }
        Collections.reverse(chain);
        return Collections.unmodifiableList(chain);
    }

    /**
     * Depth-first pre-order rendering of the whole tree, one timeline
     * per line, indented to show parent-child nesting. Read-only.
     */
    public List<String> renderHierarchy() {
        requireRootExists();
        List<String> lines = new ArrayList<>();
        appendSubtree(getRoot(), 0, lines);
        return Collections.unmodifiableList(lines);
    }

    private void appendSubtree(Timeline t, int depth, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        sb.append(t.isRoot() ? "" : "+--- ")
          .append("Timeline #").append(t.getId())
          .append(" '").append(t.getLabel()).append("'")
          .append(t == current ? "  [current]" : "");
        lines.add(sb.toString());
        for (Timeline child : t.getBranches()) {
            appendSubtree(child, depth + 1, lines);
        }
    }

    // ----- error helpers -----

    private void requireKnown(Timeline t, String role) {
        Timeline registered = timelinesById.get(t.getId());
        if (registered != t) {
            throw new IllegalArgumentException(role + " timeline #" + t.getId()
                    + " is not managed by this TimelineManager");
        }
    }

    private void requireFreeId(int id) {
        if (timelinesById.containsKey(id)) {
            throw new IllegalArgumentException("Timeline id " + id
                    + " is already in use by '" + timelinesById.get(id).getLabel() + "'");
        }
    }

    private void requireCurrentExists() {
        if (current == null) {
            throw new IllegalStateException("No timeline exists yet; create the root timeline first");
        }
    }

    /** Smallest unused positive timeline id. */
    private int nextTimelineId() {
        int candidate = 1;
        while (timelinesById.containsKey(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private void requireRootExists() {
        if (root == null) {
            throw new IllegalStateException("No root timeline has been created yet");
        }
    }
}
