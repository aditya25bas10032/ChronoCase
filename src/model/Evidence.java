package model;

import java.util.Objects;

/**
 * A piece of evidence. It may be linked to a character, a location,
 * both, or neither - whichever fits how the item was found.
 */
public class Evidence {

    private final int id;
    private String description;
    private Character linkedCharacter; // may be null
    private Location linkedLocation;   // may be null
    private EvidencePriority priority;

    public Evidence(int id, String description, Character linkedCharacter, Location linkedLocation) {
        this(id, description, linkedCharacter, linkedLocation, EvidencePriority.MEDIUM);
    }

    public Evidence(int id, String description, Character linkedCharacter, Location linkedLocation,
                    EvidencePriority priority) {
        this.id = id;
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.linkedCharacter = linkedCharacter;
        this.linkedLocation = linkedLocation;
        this.priority = priority == null ? EvidencePriority.MEDIUM : priority;
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

    public Character getLinkedCharacter() {
        return linkedCharacter;
    }

    public void setLinkedCharacter(Character linkedCharacter) {
        this.linkedCharacter = linkedCharacter;
    }

    public Location getLinkedLocation() {
        return linkedLocation;
    }

    public void setLinkedLocation(Location linkedLocation) {
        this.linkedLocation = linkedLocation;
    }

    public EvidencePriority getPriority() {
        return priority;
    }

    public void setPriority(EvidencePriority priority) {
        this.priority = priority == null ? EvidencePriority.MEDIUM : priority;
    }

    @Override
    public String toString() {
        String linkedTo;
        if (linkedCharacter != null && linkedLocation != null) {
            linkedTo = linkedCharacter.getName() + " @ " + linkedLocation.getName();
        } else if (linkedCharacter != null) {
            linkedTo = linkedCharacter.getName();
        } else if (linkedLocation != null) {
            linkedTo = linkedLocation.getName();
        } else {
            linkedTo = "unlinked";
        }
        return "Evidence #" + id + " [" + priority + "]: " + description + " (linked to: " + linkedTo + ")";
    }
}
