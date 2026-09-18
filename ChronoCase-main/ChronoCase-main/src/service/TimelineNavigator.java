package service;

import model.Event;
import model.Timeline;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Time-travel navigation state for one investigator session.
 *
 * Holds a Stack of the timelines the investigator has visited, so
 * returnToPreviousTimeline can walk back through navigation history.
 * This class is pure navigation/history logic: it never modifies
 * timelines or decides what a "change to the past" means - that is
 * TimelineManager's job. It only tracks where the investigator is
 * and where they have been.
 */
public class TimelineNavigator {

    /** One step of navigation history: where we were, and when we left. */
    private static final class Visit {
        final Timeline timeline;
        final LocalDateTime departedAt;

        Visit(Timeline timeline, LocalDateTime departedAt) {
            this.timeline = timeline;
            this.departedAt = departedAt;
        }
    }

    private final Timeline root;
    private Timeline current;

    /**
     * Navigation history as a LIFO stack of past visits. When the
     * investigator travels to a timeline already on the stack, the
     * entries above it are discarded (like browser back-history).
     */
    private final Deque<Visit> history = new ArrayDeque<>();

    public TimelineNavigator(Timeline root) {
        this.root = Objects.requireNonNull(root, "root timeline must not be null");
        this.current = root;
    }

    // ----- navigation -----

    /**
     * Travels to the given timeline, pushing the departure onto the
     * history stack. Returns the time of departure from the previous
     * timeline (useful for narration).
     */
    public LocalDateTime travelTo(Timeline target, LocalDateTime now) {
        Objects.requireNonNull(target, "target timeline must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (target == current) {
            throw new IllegalStateException("Already on timeline '" + current.getLabel() + "'");
        }
        trimHistoryTo(target);
        history.push(new Visit(current, now));
        current = target;
        return now;
    }

    /**
     * Pops the most recent visit off the stack and returns to it.
     * Returns the timeline returned to, or empty if there is no
     * navigation history (already at the start).
     */
    public java.util.Optional<Timeline> returnToPreviousTimeline() {
        if (history.isEmpty()) {
            return java.util.Optional.empty();
        }
        Visit visit = history.pop();
        current = visit.timeline;
        return java.util.Optional.of(visit.timeline);
    }

    /** True if there is navigation history to return through. */
    public boolean canReturn() {
        return !history.isEmpty();
    }

    /** The timeline the investigator is currently on. */
    public Timeline getCurrent() {
        return current;
    }

    /** The root timeline this navigation session started from. */
    public Timeline getRoot() {
        return root;
    }

    /** Number of steps of navigation history currently recorded. */
    public int getHistoryDepth() {
        return history.size();
    }

    /**
     * Read-only view of the history stack, most recent first. Does
     * not modify the stack.
     */
    public List<Timeline> getHistorySnapshot() {
        List<Timeline> out = new ArrayList<>();
        for (Visit v : history) {
            out.add(v.timeline);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * If the target is already somewhere in the history stack, drop
     * everything above it so returning never loops through stale
     * visits (same rule a browser back-button follows).
     */
    private void trimHistoryTo(Timeline target) {
        // ArrayDeque has no mid-stack removal; rebuild the stack.
        List<Visit> kept = new ArrayList<>();
        boolean found = false;
        for (Visit v : history) {
            if (!found && v.timeline == target) {
                found = true; // keep this entry, drop everything after (newer)
                continue;
            }
            kept.add(v);
        }
        if (found) {
            history.clear();
            for (int i = kept.size() - 1; i >= 0; i--) {
                history.push(kept.get(i));
            }
        }
    }
}
