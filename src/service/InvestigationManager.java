package service;

import model.Case;
import model.Character;
import model.Evidence;
import model.Event;
import model.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * One investigator's working session over a {@link Case}: inspecting
 * events, characters and locations, collecting and searching
 * evidence, and associating clues with suspects, locations and
 * events.
 *
 * The manager owns only session state and holds references to the
 * Case's entities - it never copies them. Evidence associations to
 * characters and locations are written straight onto the Case's
 * Evidence objects; the evidence-to-event relation, which the model
 * has no field for, is tracked here.
 *
 * Data structures, each with a real job:
 * - LinkedHashSet: collected evidence, in collection order, with
 *   O(1) "already collected?" checks and no way to collect twice.
 * - HashSet (x3): ids of already-inspected events, characters and
 *   locations - the session's progress, so visits stay unique.
 * - HashMap of HashSets: evidence id -> linked event ids. Sets make
 *   duplicate associations impossible and lookups constant-time.
 */
public class InvestigationManager implements io.TextReportable {

    private final Case kase;

    // Session state: which clues the investigator has bagged, in order.
    private final Set<Evidence> collected = new LinkedHashSet<>();

    // Session progress: what has already been looked at.
    private final Set<Integer> inspectedEventIds = new HashSet<>();
    private final Set<Integer> inspectedCharacterIds = new HashSet<>();
    private final Set<Integer> inspectedLocationIds = new HashSet<>();

    // Evidence -> events relation (model has no such field on Evidence).
    private final Map<Integer, Set<Integer>> eventIdsByEvidenceId = new HashMap<>();

    public InvestigationManager(Case kase) {
        this.kase = Objects.requireNonNull(kase, "case must not be null");
    }

    // ----- inspection -----

    /**
     * Studies a specific event. Returns the Case's own Event instance
     * (no copy) and records the visit in the session progress.
     */
    public Event inspectEvent(int eventId) {
        Event event = requireEvent(eventId);
        inspectedEventIds.add(eventId);
        return event;
    }

    /**
     * Interviews a character: gathers everything the case knows about
     * them - the events they took part in and the evidence linked to
     * them - without duplicating any of it.
     */
    public CharacterInvestigation investigateCharacter(int characterId) {
        Character subject = requireCharacter(characterId);
        inspectedCharacterIds.add(characterId);
        List<Event> events = new ArrayList<>();
        for (Event e : kase.getEvents()) {
            if (e.getCharacter().getId() == characterId) {
                events.add(e);
            }
        }
        List<Evidence> linked = new ArrayList<>();
        for (Evidence item : kase.getEvidence()) {
            if (item.getLinkedCharacter() != null
                    && item.getLinkedCharacter().getId() == characterId) {
                linked.add(item);
            }
        }
        return new CharacterInvestigation(subject, events, linked);
    }

    /**
     * Surveys a location: the events that happened there and the
     * evidence found there.
     */
    public LocationInvestigation investigateLocation(int locationId) {
        Location subject = requireLocation(locationId);
        inspectedLocationIds.add(locationId);
        List<Event> events = new ArrayList<>();
        for (Event e : kase.getEvents()) {
            if (e.getLocation().getId() == locationId) {
                events.add(e);
            }
        }
        List<Evidence> linked = new ArrayList<>();
        for (Evidence item : kase.getEvidence()) {
            if (item.getLinkedLocation() != null
                    && item.getLinkedLocation().getId() == locationId) {
                linked.add(item);
            }
        }
        return new LocationInvestigation(subject, events, linked);
    }

    // ----- session progress -----

    public boolean wasEventInspected(int eventId) {
        return inspectedEventIds.contains(eventId);
    }

    public boolean wasCharacterInspected(int characterId) {
        return inspectedCharacterIds.contains(characterId);
    }

    public boolean wasLocationInspected(int locationId) {
        return inspectedLocationIds.contains(locationId);
    }

    // ----- collecting evidence -----

    /**
     * Bags a piece of evidence for this session. Unknown ids throw;
     * collecting the same item twice returns false - a clue is either
     * collected or not, never twice.
     */
    public boolean collectEvidence(int evidenceId) {
        Evidence item = requireEvidence(evidenceId);
        return collected.add(item); // LinkedHashSet: false if already present
    }

    /** The clues collected in this session, in collection order. Read-only. */
    public List<Evidence> getCollectedEvidence() {
        return Collections.unmodifiableList(new ArrayList<>(collected));
    }

    public int getCollectedEvidenceCount() {
        return collected.size();
    }

    public boolean isCollected(int evidenceId) {
        // The set holds the Case's own instances; an id maps to at most one.
        return findCollectedById(evidenceId) != null;
    }

    // ----- searching evidence -----

    /**
     * Case-insensitive search over every piece of evidence in the
     * case (collected or not) by description. Null queries are
     * rejected; blank queries match nothing.
     */
    public List<Evidence> searchEvidence(String query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        String needle = query.trim().toLowerCase();
        if (needle.isEmpty()) {
            return Collections.emptyList();
        }
        List<Evidence> hits = new ArrayList<>();
        for (Evidence item : kase.getEvidence()) {
            if (item.getDescription().toLowerCase().contains(needle)) {
                hits.add(item);
            }
        }
        return Collections.unmodifiableList(hits);
    }

    // ----- associations -----

    /**
     * Links collected evidence to a suspect by writing the
     * association onto the Case's own Evidence object.
     */
    public void associateWithSuspect(int evidenceId, int suspectId) {
        Evidence item = requireCollectedEvidence(evidenceId);
        Character suspect = requireCharacter(suspectId);
        item.setLinkedCharacter(suspect);
    }

    /**
     * Links collected evidence to a location on the Case's own
     * Evidence object.
     */
    public void associateWithLocation(int evidenceId, int locationId) {
        Evidence item = requireCollectedEvidence(evidenceId);
        Location location = requireLocation(locationId);
        item.setLinkedLocation(location);
    }

    /**
     * Links collected evidence to an event. The model has no
     * evidence-event field, so this relation is tracked by the
     * manager; duplicate links are ignored.
     */
    public void associateWithEvent(int evidenceId, int eventId) {
        requireCollectedEvidence(evidenceId);
        requireEvent(eventId);
        eventIdsByEvidenceId
                .computeIfAbsent(evidenceId, id -> new HashSet<>())
                .add(eventId);
    }

    /** Read-only view of the events linked to a piece of evidence. */
    public Set<Integer> getLinkedEventIds(int evidenceId) {
        Set<Integer> ids = eventIdsByEvidenceId.get(evidenceId);
        return ids == null ? Collections.emptySet() : Collections.unmodifiableSet(ids);
    }

    public boolean isLinkedToEvent(int evidenceId, int eventId) {
        Set<Integer> ids = eventIdsByEvidenceId.get(evidenceId);
        return ids != null && ids.contains(eventId);
    }

    // ----- clue board -----

    /**
     * Renders the current clue board: one line per collected clue,
     * with its suspect, location and linked events.
     */
    public List<String> renderClueBoard() {
        List<String> lines = new ArrayList<>();
        lines.add("Clue board - case #" + kase.getId() + " '"
                + kase.getTitle() + "' (" + collected.size() + " clues)");
        for (Evidence item : collected) {
            StringBuilder sb = new StringBuilder("  Evidence #").append(item.getId())
                    .append(" [").append(item.getPriority()).append("] ")
                    .append(item.getDescription());
            sb.append(" | suspect: ")
                    .append(item.getLinkedCharacter() == null
                            ? "none" : item.getLinkedCharacter().getName());
            sb.append(" | location: ")
                    .append(item.getLinkedLocation() == null
                            ? "none" : item.getLinkedLocation().getName());
            sb.append(" | events: ").append(getLinkedEventIds(item.getId()));
            lines.add(sb.toString());
        }
        return Collections.unmodifiableList(lines);
    }

    /** The clue board doubles as the session's text report. */
    @Override
    public List<String> renderReport() {
        return renderClueBoard();
    }

    // ----- validation helpers -----

    private Character requireCharacter(int id) {
        return kase.findCharacterById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "No character with id " + id + " in case '" + kase.getTitle() + "'"));
    }

    private Location requireLocation(int id) {
        return kase.findLocationById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "No location with id " + id + " in case '" + kase.getTitle() + "'"));
    }

    private Event requireEvent(int id) {
        return kase.findEventById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "No event with id " + id + " in case '" + kase.getTitle() + "'"));
    }

    private Evidence requireEvidence(int id) {
        return kase.findEvidenceById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "No evidence with id " + id + " in case '" + kase.getTitle() + "'"));
    }

    private Evidence requireCollectedEvidence(int id) {
        Evidence item = findCollectedById(id);
        if (item == null) {
            throw new IllegalStateException("Evidence #" + id
                    + " has not been collected yet; collect it before associating it");
        }
        return item;
    }

    private Evidence findCollectedById(int id) {
        for (Evidence item : collected) {
            if (item.getId() == id) {
                return item;
            }
        }
        return null;
    }

    // ----- inspection results -----

    /** What interviewing a character turned up. References, not copies. */
    public static final class CharacterInvestigation {
        private final Character subject;
        private final List<Event> events;
        private final List<Evidence> linkedEvidence;

        CharacterInvestigation(Character subject, List<Event> events, List<Evidence> linkedEvidence) {
            this.subject = subject;
            this.events = Collections.unmodifiableList(events);
            this.linkedEvidence = Collections.unmodifiableList(linkedEvidence);
        }

        public Character getSubject() {
            return subject;
        }

        /** Events in which the character took part. */
        public List<Event> getEvents() {
            return events;
        }

        /** Evidence linked to the character. */
        public List<Evidence> getLinkedEvidence() {
            return linkedEvidence;
        }
    }

    /** What surveying a location turned up. References, not copies. */
    public static final class LocationInvestigation {
        private final Location subject;
        private final List<Event> events;
        private final List<Evidence> linkedEvidence;

        LocationInvestigation(Location subject, List<Event> events, List<Evidence> linkedEvidence) {
            this.subject = subject;
            this.events = Collections.unmodifiableList(events);
            this.linkedEvidence = Collections.unmodifiableList(linkedEvidence);
        }

        public Location getSubject() {
            return subject;
        }

        /** Events that happened at the location. */
        public List<Event> getEvents() {
            return events;
        }

        /** Evidence found at the location. */
        public List<Evidence> getLinkedEvidence() {
            return linkedEvidence;
        }
    }
}
