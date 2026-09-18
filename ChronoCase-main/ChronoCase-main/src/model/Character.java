package model;

import java.util.Objects;

/**
 * A person involved in a case: suspect, witness, victim, investigator, etc.
 */
public class Character {

    private final int id;
    private String name;
    private String role;

    public Character(int id, String name, String role) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    @Override
    public String toString() {
        return "Character #" + id + ": " + name + " (" + role + ")";
    }
}
