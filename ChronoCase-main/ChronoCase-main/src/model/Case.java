package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import io.TextReportable;

/**
 * A mystery case: the container for every character, location, event
 * and piece of evidence that belongs to one investigation.
 *
 * The internal lists are never exposed; callers mutate the case only
 * through the add methods and read through unmodifiable views or the
 * by-id lookups below.
 */
public class Case implements io.TextReportable {

    private final int id;
    private String title;
    private String description;

    private final List<Character> characters = new ArrayList<>();
    private final List<Location> locations = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Evidence> evidence = new ArrayList<>();
    private final List<Timeline> timelines = new ArrayList<>(); // root timelines only

    // HashMap indexes: O(1) by-id lookup instead of a linear scan over
    // the lists. The lists stay the ordered source of truth; these are
    // just a fast view on the same objects.
    private final Map<Integer, Character> charactersById = new HashMap<>();
    private final Map<Integer, Location> locationsById = new HashMap<>();
    private final Map<Integer, Event> eventsById = new HashMap<>();
    private final Map<Integer, Evidence> evidenceById = new HashMap<>();

    public Case(int id, String title, String description) {
        this.id = id;
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    // ----- getters / setters -----

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title must not be null");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    // ----- collection management -----
    // Null entries are rejected: a case cannot contain an unnamed or
    // dangling entity, and callers get a clear failure at the boundary.

    public void addCharacter(Character character) {
        characters.add(Objects.requireNonNull(character, "character must not be null"));
        charactersById.put(character.getId(), character);
    }

    public void addLocation(Location location) {
        locations.add(Objects.requireNonNull(location, "location must not be null"));
        locationsById.put(location.getId(), location);
    }

    public void addEvent(Event event) {
        events.add(Objects.requireNonNull(event, "event must not be null"));
        eventsById.put(event.getId(), event);
    }

    public void addEvidence(Evidence evidenceItem) {
        evidence.add(Objects.requireNonNull(evidenceItem, "evidence must not be null"));
        evidenceById.put(evidenceItem.getId(), evidenceItem);
    }

    /**
     * Registers a root timeline (with all of its branches) with this
     * case. The timeline's events are not copied here - timelines
     * keep their own snapshots; the case only remembers its roots.
     */
    public void addTimeline(Timeline timeline) {
        timelines.add(Objects.requireNonNull(timeline, "timeline must not be null"));
    }

    // ----- by-id lookups -----

    /** Returns the character with the given id, or empty if absent. */
    public Optional<Character> findCharacterById(int id) {
        return Optional.ofNullable(charactersById.get(id));
    }

    /** Returns the location with the given id, or empty if absent. */
    public Optional<Location> findLocationById(int id) {
        return Optional.ofNullable(locationsById.get(id));
    }

    /** Returns the event with the given id, or empty if absent. */
    public Optional<Event> findEventById(int id) {
        return Optional.ofNullable(eventsById.get(id));
    }

    /** Returns the evidence item with the given id, or empty if absent. */
    public Optional<Evidence> findEvidenceById(int id) {
        return Optional.ofNullable(evidenceById.get(id));
    }

    // ----- read-only access -----

    public List<Character> getCharacters() {
        return Collections.unmodifiableList(characters);
    }

    public List<Location> getLocations() {
        return Collections.unmodifiableList(locations);
    }

    public List<Event> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public List<Evidence> getEvidence() {
        return Collections.unmodifiableList(evidence);
    }

    /** The root timelines registered with this case. Read-only. */
    public List<Timeline> getTimelines() {
        return Collections.unmodifiableList(timelines);
    }

    @Override
    public String toString() {
        return "Case #" + id + ": " + title + " - " + description
                + " [" + characters.size() + " characters, "
                + locations.size() + " locations, "
                + events.size() + " events, "
                + evidence.size() + " evidence items]";
    }

    /** Plain-text case report, one entity per line. */
    @Override
    public java.util.List<String> renderReport() {
        java.util.List<String> lines = new ArrayList<>();
        lines.add(toString());
        lines.add("Characters:");
        for (Character c : characters) {
            lines.add("  " + c);
        }
        lines.add("Locations:");
        for (Location l : locations) {
            lines.add("  " + l);
        }
        lines.add("Events:");
        for (Event e : events) {
            lines.add("  " + e);
        }
        lines.add("Evidence:");
        for (Evidence item : evidence) {
            lines.add("  " + item);
        }
        return lines;
    }
}
