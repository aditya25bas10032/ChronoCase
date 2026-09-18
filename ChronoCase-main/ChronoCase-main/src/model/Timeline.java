package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single branch of the case timeline.
 *
 * The core rule of ChronoCase: changing the past never destroys the
 * old timeline - it creates a new branch. A branch therefore holds a
 * snapshot of its parent's events at the moment of creation and is
 * free to diverge afterwards, while the parent timeline is never
 * modified by branching.
 */
public class Timeline {

    private final int id;
    private final Timeline parent; // null only for the root timeline
    private final String label;
    private final long createdAtBranch; // zero for the root, otherwise the branch point id

    private final List<Timeline> branches = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();

    /** Creates the root timeline: no parent, no branch point. */
    public Timeline(int id, String label) {
        this(id, label, null);
    }

    private Timeline(int id, String label, Timeline parent) {
        this.id = id;
        this.label = Objects.requireNonNull(label, "label must not be null");
        this.parent = parent;
        this.createdAtBranch = parent == null ? 0 : parent.id;
        if (parent != null) {
            // Snapshot: copy, don't reference, so future edits on either
            // side stay independent (rule: branching never touches the parent).
            for (Event e : parent.events) {
                this.events.add(new Event(e));
            }
        }
    }

    // ----- branching -----

    /**
     * Creates a child timeline that starts from this timeline's current
     * events. This timeline is left completely unchanged.
     */
    public Timeline createBranch(int id, String label) {
        Timeline branch = new Timeline(id, label, this);
        branches.add(branch);
        return branch;
    }

    public Timeline getParent() {
        return parent;
    }

    public boolean isRoot() {
        return parent == null;
    }

    /** True if this timeline has no branches of its own. */
    public boolean isLeaf() {
        return branches.isEmpty();
    }

    /** Number of steps from the root down to this timeline. */
    public int getDepth() {
        int depth = 1;
        for (Timeline t = parent; t != null; t = t.parent) {
            depth++;
        }
        return depth;
    }

    // ----- events -----

    public void addEvent(Event event) {
        events.add(Objects.requireNonNull(event, "event must not be null"));
    }

    /**
     * Replaces the event with the given id by another instance, in place.
     * Returns the displaced original so callers can keep it (e.g. for
     * comparison or history). Throws if no such event exists.
     */
    public Event replaceEvent(int eventId, Event replacement) {
        Objects.requireNonNull(replacement, "replacement event must not be null");
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getId() == eventId) {
                return events.set(i, replacement);
            }
        }
        throw new IllegalArgumentException("No event with id " + eventId + " on timeline '"
                + label + "' (has " + events.size() + " events)");
    }

    public List<Event> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public int getEventCount() {
        return events.size();
    }

    // ----- branches -----

    /** Returns a read-only view of the direct child timelines. */
    public List<Timeline> getBranches() {
        return Collections.unmodifiableList(branches);
    }

    // ----- metadata -----

    public int getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    /** Id of the timeline this one branched from; 0 for the root. */
    public long getCreatedAtBranch() {
        return createdAtBranch;
    }

    @Override
    public String toString() {
        return "Timeline #" + id + " '" + label + "' [depth " + getDepth()
                + ", " + events.size() + " events, "
                + branches.size() + " branches]";
    }
}
