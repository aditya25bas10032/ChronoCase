package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Something that happened at a specific point in time, involving one
 * character at one location. Both associations are required.
 */
public class Event {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final int id;
    private String description;
    private LocalDateTime timestamp;
    private final Character character;
    private final Location location;

    public Event(int id, String description, LocalDateTime timestamp, Character character, Location location) {
        this.id = id;
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.character = Objects.requireNonNull(character, "event character must not be null");
        this.location = Objects.requireNonNull(location, "event location must not be null");
    }

    /** Copy constructor used when a branch snapshots its parent's events. */
    public Event(Event other) {
        this(other.id, other.description, other.timestamp, other.character, other.location);
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public Character getCharacter() {
        return character;
    }

    public Location getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return "Event #" + id + " [" + timestamp.format(FORMATTER) + "] "
                + description + " - " + character.getName()
                + " @ " + location.getName();
    }
}
