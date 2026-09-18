package model;

/**
 * Triage priority for a piece of evidence, ordered from most urgent
 * to least urgent. Used by the EvidenceBoard's PriorityQueue so the
 * lab always examines the most promising clue first.
 */
public enum EvidencePriority {

    CRITICAL(0),
    HIGH(1),
    MEDIUM(2),
    LOW(3);

    /** Lower number = examined earlier; used as the queue tiebreaker. */
    private final int rank;

    EvidencePriority(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    /** Parses a stored priority string; unknown or null values fall back to MEDIUM. */
    public static EvidencePriority fromString(String value) {
        if (value != null) {
            for (EvidencePriority p : values()) {
                if (p.name().equalsIgnoreCase(value)) {
                    return p;
                }
            }
        }
        return MEDIUM;
    }
}
