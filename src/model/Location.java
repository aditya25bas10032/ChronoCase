package model;

import java.util.Objects;

/**
 * A place where events can happen or evidence can be found.
 */
public class Location {

    private final int id;
    private String name;
    private String description;

    public Location(int id, String name, String description) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    @Override
    public String toString() {
        return "Location #" + id + ": " + name + " - " + description;
    }
}
