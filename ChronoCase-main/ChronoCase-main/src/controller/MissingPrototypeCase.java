package controller;

import model.Case;
import model.Character;
import model.Evidence;
import model.EvidencePriority;
import model.Event;
import model.Location;
import model.Timeline;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * CASE-001 - "The Missing Prototype": the seeded demo investigation
 * behind "New Investigation". It exercises the full ChronoCase
 * feature set with real, interlocking data:
 *
 * - 4 suspects, 5 locations, 13 chronological events (08:00-11:00),
 * - 8 evidence items, each linked to the character and location it
 *   belongs to (the ContradictionDetector judges evidence through
 *   its linked character, so those links are the case's backbone),
 * - the ROOT timeline holding the canonical event sequence,
 * - three demo branches: TIMELINE-A ("camera is repaired" at 09:40,
 *   parent ROOT), TIMELINE-B ("camera remains offline" at 09:40,
 *   parent ROOT) and TIMELINE-C ("Maya does NOT enter Prototype Lab"
 *   at 10:00, parent TIMELINE-B) - a branch of a branch, so the
 *   hierarchy has real depth. ROOT is never modified by any of this.
 *
 * Designed demonstrations (all produced by the real services):
 * - E001 (Maya's 10:00 access log) SUPPORTS ROOT and CONTRADICTS
 *   TIMELINE-C - the contradiction demo,
 * - E002 (camera offline at 09:40) CONTRADICTS TIMELINE-A,
 * - MysterySolver names Maya Kapoor as the strongest suspect on the
 *   evidence set (access, opportunity, security-system privilege).
 */
public final class MissingPrototypeCase {

    public static final int CASE_ID = 1;
    public static final String CASE_CODE = "CASE-001";
    public static final String TITLE = "The Missing Prototype";
    public static final String DESCRIPTION =
            "The Quantum-X prototype vanished from the Prototype Lab between 10:20 and 10:45. "
            + "The security camera went offline at 09:40 and no relocation order exists. "
            + "Detective: interview the suspects, survey the rooms, collect the evidence, "
            + "then travel back through the timeline to test what really happened.";

    private static final LocalDate DAY = LocalDate.of(2026, 9, 15);

    // ----- suspect ids -----
    private static final int ARJUN_ID = 1;
    private static final int MAYA_ID = 2;
    private static final int ROHAN_ID = 3;
    private static final int NEHA_ID = 4;

    // ----- location ids -----
    private static final int ENTRANCE_ID = 1;
    private static final int LAB_ID = 2;
    private static final int SECURITY_ID = 3;
    private static final int OFFICE_ID = 4;
    private static final int STORAGE_ID = 5;

    // ----- timeline ids -----
    private static final int ROOT_ID = 1;
    private static final int TIMELINE_A_ID = 2;
    private static final int TIMELINE_B_ID = 3;
    private static final int TIMELINE_C_ID = 4;

    private MissingPrototypeCase() {
    }

    /** Builds the complete seeded case: case file, ROOT and demo branches. */
    public static Case create() {
        Case kase = new Case(CASE_ID, CASE_CODE + " - " + TITLE, DESCRIPTION);

        addSuspects(kase);
        addLocations(kase);
        addEvents(kase);
        addEvidence(kase);
        addTimelines(kase);
        return kase;
    }

    private static void addSuspects(Case kase) {
        kase.addCharacter(new Character(ARJUN_ID, "Arjun Mehta", "Research Engineer"));
        kase.addCharacter(new Character(MAYA_ID, "Maya Kapoor", "Security Engineer"));
        kase.addCharacter(new Character(ROHAN_ID, "Rohan Shah", "Lab Assistant"));
        kase.addCharacter(new Character(NEHA_ID, "Neha Verma", "Project Manager"));
    }

    private static void addLocations(Case kase) {
        kase.addLocation(new Location(ENTRANCE_ID, "Main Entrance",
                "The only public door into the building - fully logged"));
        kase.addLocation(new Location(LAB_ID, "Prototype Lab",
                "Restricted room where the Quantum-X prototype was kept"));
        kase.addLocation(new Location(SECURITY_ID, "Security Room",
                "Camera monitors, the security console and the door-access log"));
        kase.addLocation(new Location(OFFICE_ID, "Research Office",
                "Arjun's office, one floor above the Prototype Lab"));
        kase.addLocation(new Location(STORAGE_ID, "Storage Room",
                "Equipment and spare parts storage in the basement"));
    }

    /** The 13 canonical events, 08:00 to 11:00. */
    private static void addEvents(Case kase) {
        Character arjun = kase.findCharacterById(ARJUN_ID).orElseThrow();
        Character maya = kase.findCharacterById(MAYA_ID).orElseThrow();
        Character rohan = kase.findCharacterById(ROHAN_ID).orElseThrow();
        Character neha = kase.findCharacterById(NEHA_ID).orElseThrow();

        Location entrance = kase.findLocationById(ENTRANCE_ID).orElseThrow();
        Location lab = kase.findLocationById(LAB_ID).orElseThrow();
        Location security = kase.findLocationById(SECURITY_ID).orElseThrow();
        Location office = kase.findLocationById(OFFICE_ID).orElseThrow();
        Location storage = kase.findLocationById(STORAGE_ID).orElseThrow();

        kase.addEvent(new Event(1, "Laboratory opens", at(8, 0), rohan, entrance));
        kase.addEvent(new Event(2, "Arjun enters laboratory", at(8, 30), arjun, entrance));
        kase.addEvent(new Event(3, "Rohan enters laboratory", at(9, 0), rohan, entrance));
        kase.addEvent(new Event(4, "Neha enters laboratory", at(9, 15), neha, entrance));
        kase.addEvent(new Event(5, "Maya enters laboratory", at(9, 30), maya, entrance));
        kase.addEvent(new Event(6, "Security camera goes offline", at(9, 40), maya, security));
        kase.addEvent(new Event(7, "Maya enters Prototype Lab", at(10, 0), maya, lab));
        kase.addEvent(new Event(8, "Arjun enters Research Office", at(10, 10), arjun, office));
        kase.addEvent(new Event(9, "Prototype disappears", at(10, 20), arjun, lab));
        kase.addEvent(new Event(10, "Rohan enters Storage Room", at(10, 15), rohan, storage));
        kase.addEvent(new Event(11, "Maya leaves Prototype Lab", at(10, 30), maya, lab));
        kase.addEvent(new Event(12, "Missing prototype discovered", at(10, 45), neha, lab));
        kase.addEvent(new Event(13, "Security investigation begins", at(11, 0), neha, security));
    }

    /**
     * The 8 evidence items. Every item is linked to its character and
     * its location; the detector needs the character link (it judges
     * evidence through its linked character) and the descriptions
     * carry the clock times the time rule compares.
     */
    private static void addEvidence(Case kase) {
        Character arjun = kase.findCharacterById(ARJUN_ID).orElseThrow();
        Character maya = kase.findCharacterById(MAYA_ID).orElseThrow();
        Character rohan = kase.findCharacterById(ROHAN_ID).orElseThrow();
        Character neha = kase.findCharacterById(NEHA_ID).orElseThrow();

        Location entrance = kase.findLocationById(ENTRANCE_ID).orElseThrow();
        Location lab = kase.findLocationById(LAB_ID).orElseThrow();
        Location security = kase.findLocationById(SECURITY_ID).orElseThrow();
        Location office = kase.findLocationById(OFFICE_ID).orElseThrow();
        Location storage = kase.findLocationById(STORAGE_ID).orElseThrow();

        kase.addEvidence(new Evidence(1, "Security Access Log: Maya's security card was used "
                + "to enter the Prototype Lab at 10:00", maya, lab, EvidencePriority.CRITICAL));
        kase.addEvidence(new Evidence(2, "Camera Failure Report: Prototype Lab camera stopped "
                + "recording at 09:40", maya, security, EvidencePriority.CRITICAL));
        kase.addEvidence(new Evidence(3, "Prototype Case Fingerprint: partial fingerprint "
                + "belonging to Arjun found on prototype case", arjun, lab, EvidencePriority.HIGH));
        kase.addEvidence(new Evidence(4, "Storage Room Record: Rohan accessed Storage Room "
                + "at 10:15", rohan, storage, EvidencePriority.MEDIUM));
        kase.addEvidence(new Evidence(5, "Project Authorization: Neha was authorized to relocate "
                + "the prototype, but no relocation order exists", neha, office,
                EvidencePriority.HIGH));
        kase.addEvidence(new Evidence(6, "Security Console Log: camera system was manually "
                + "disabled using a security administrator account", maya, security,
                EvidencePriority.CRITICAL));
        kase.addEvidence(new Evidence(7, "Office Entry Record: Arjun entered Research Office "
                + "at 10:10 and remained until 10:35", arjun, office, EvidencePriority.MEDIUM));
        kase.addEvidence(new Evidence(8, "Prototype Transport Record: prototype was not "
                + "recorded as transported through Main Entrance", rohan, entrance,
                EvidencePriority.MEDIUM));
    }

    /**
     * ROOT plus the three demo branches. ROOT is built by snapshotting
     * the case's master events exactly as the player's own timeline
     * would; the branches use the standard branching API, so the
     * original ROOT stays completely intact.
     */
    private static void addTimelines(Case kase) {
        Timeline root = new Timeline(ROOT_ID, "ROOT");
        for (Event e : kase.getEvents()) {
            root.addEvent(new Event(e)); // the timeline's own snapshot copies
        }
        kase.addTimeline(root);

        // TIMELINE-A (parent ROOT): at 09:40 the camera is repaired -
        // by the technician, so Maya (the security engineer) was never
        // at the console in this branch. That absence assertion is what
        // makes E002 ("camera stopped recording at 09:40", linked to
        // Maya) contradict this timeline in the detector's time rule.
        Timeline timelineA = root.createBranch(TIMELINE_A_ID, "TIMELINE-A: camera repaired");
        timelineA.replaceEvent(6, new Event(6,
                "Security camera is repaired - Maya never touches the camera",
                at(9, 40),
                kase.findCharacterById(MAYA_ID).orElseThrow(),
                kase.findLocationById(SECURITY_ID).orElseThrow()));

        // TIMELINE-B (parent ROOT): at 09:40 the camera remains offline.
        Timeline timelineB = root.createBranch(TIMELINE_B_ID, "TIMELINE-B: camera offline");
        timelineB.replaceEvent(6, new Event(6, "Security camera remains offline",
                at(9, 40),
                kase.findCharacterById(MAYA_ID).orElseThrow(),
                kase.findLocationById(SECURITY_ID).orElseThrow()));

        // TIMELINE-C (parent TIMELINE-B): at 10:00 Maya does NOT enter.
        Timeline timelineC = timelineB.createBranch(TIMELINE_C_ID, "TIMELINE-C: Maya absent");
        timelineC.replaceEvent(7, new Event(7, "Maya does NOT enter Prototype Lab",
                at(10, 0),
                kase.findCharacterById(MAYA_ID).orElseThrow(),
                kase.findLocationById(LAB_ID).orElseThrow()));
    }

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(DAY, LocalTime.of(hour, minute));
    }

    /**
     * Fills an already-built case with the scenario content (used when
     * the player keeps an edited title but wants the seeded demo
     * underneath): suspects, locations, events, evidence and the
     * ROOT + TIMELINE-A/B/C hierarchy.
     */
    public static void populate(Case kase) {
        Case template = create();
        for (Character c : template.getCharacters()) {
            kase.addCharacter(c);
        }
        for (Location l : template.getLocations()) {
            kase.addLocation(l);
        }
        for (Event e : template.getEvents()) {
            kase.addEvent(e);
        }
        for (Evidence item : template.getEvidence()) {
            kase.addEvidence(item);
        }
        for (Timeline root : template.getTimelines()) {
            kase.addTimeline(root);
        }
    }
}
