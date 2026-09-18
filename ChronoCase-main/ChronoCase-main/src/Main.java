import io.FileManager;
import model.Case;
import model.Character;
import model.Evidence;
import model.EvidencePriority;
import model.Event;
import model.Location;
import model.Timeline;
import service.ContradictionDetector;
import service.EvidenceBoard;
import service.InvestigationManager;
import service.InvestigationService;
import service.MysterySolver;
import service.RelationshipGraph;
import service.TimelineManager;
import service.TimelineNavigator;

import database.DatabaseManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Step 2 smoke test: builds a small case, then demonstrates the
 * hardened Case API - by-id lookups, null rejection and encapsulated
 * collections - before printing everything to the console.
 */
public class Main {

    public static void main(String[] args) {
        Case coldCase = new Case(1, "The Vanishing of Dr. Aris Thorne",
                "A physicist disappears and the timeline does not add up.");

        // Characters
        Character aris = new Character(1, "Dr. Aris Thorne", "Missing physicist");
        Character mira = new Character(2, "Mira Kade", "Lab assistant and key witness");
        Character voss = new Character(3, "Insp. Dana Voss", "Lead investigator");

        // Locations
        Location lab = new Location(1, "Thorne Laboratory", "Basement lab at the university");
        Location depot = new Location(2, "Old Rail Depot", "Abandoned freight depot on the edge of town");

        // Events (character and location are required, enforced by the model)
        coldCase.addEvent(new Event(1, "Last confirmed sighting of Thorne",
                LocalDateTime.of(2026, 9, 10, 21, 30), aris, lab));
        coldCase.addEvent(new Event(2, "Camera records a van leaving the depot",
                LocalDateTime.of(2026, 9, 10, 23, 45), mira, depot));
        coldCase.addEvent(new Event(3, "Voss opens the investigation",
                LocalDateTime.of(2026, 9, 11, 8, 0), voss, lab));

        // Evidence: linked to character + location, location only, and character only
        coldCase.addEvidence(new Evidence(1, "Broken pocket watch stopped at 21:47", aris, lab));
        coldCase.addEvidence(new Evidence(2, "Tyre tread cast from the depot yard", null, depot));
        coldCase.addEvidence(new Evidence(3, "Handwritten note signed 'M.K.'", mira, null));

        // Wire characters and locations into the case
        coldCase.addCharacter(aris);
        coldCase.addCharacter(mira);
        coldCase.addCharacter(voss);
        coldCase.addLocation(lab);
        coldCase.addLocation(depot);

        // --- New in Step 2: by-id lookups ---
        System.out.println("Lookup by id:");
        System.out.println("  Character 2 -> " + coldCase.findCharacterById(2)
                .map(Character::getName).orElse("not found"));
        System.out.println("  Location 2  -> " + coldCase.findLocationById(2)
                .map(Location::getName).orElse("not found"));
        System.out.println("  Event 1     -> " + coldCase.findEventById(1)
                .map(Event::getDescription).orElse("not found"));
        System.out.println("  Evidence 3  -> " + coldCase.findEvidenceById(3)
                .map(Evidence::getDescription).orElse("not found"));
        System.out.println("  Character 9 -> " + coldCase.findCharacterById(9)
                .map(Character::getName).orElse("not found"));
        System.out.println();

        // --- New in Step 2: null rejection ---
        try {
            coldCase.addCharacter(null);
            System.out.println("ERROR: null character was accepted");
        } catch (NullPointerException e) {
            System.out.println("Rejected null character as expected: " + e.getMessage());
        }
        try {
            new Event(99, "Impossible event", LocalDateTime.now(), null, lab);
            System.out.println("ERROR: event with null character was accepted");
        } catch (NullPointerException e) {
            System.out.println("Rejected event without character as expected: " + e.getMessage());
        }
        System.out.println();

        // --- New in Step 2: collections stay encapsulated ---
        try {
            coldCase.getCharacters().add(aris);
            System.out.println("ERROR: internal character list was mutated from outside");
        } catch (UnsupportedOperationException e) {
            System.out.println("Character list is read-only from outside, as expected");
        }
        System.out.println();

        // Print the whole case
        System.out.println(coldCase);
        System.out.println();
        System.out.println("Characters:");
        for (Character c : coldCase.getCharacters()) {
            System.out.println("  " + c);
        }
        System.out.println("Locations:");
        for (Location l : coldCase.getLocations()) {
            System.out.println("  " + l);
        }
        System.out.println("Events:");
        for (Event e : coldCase.getEvents()) {
            System.out.println("  " + e);
        }
        System.out.println("Evidence:");
        for (Evidence item : coldCase.getEvidence()) {
            System.out.println("  " + item);
        }

        // ==============================================
        // Step 5: time-travel navigation
        //
        // Root Timeline
        //   09:00 Event A
        //   10:00 Event B
        //   11:00 Event C
        //
        // Travel back to Event B, change what happened there:
        // Root
        //  \--- Alternate Timeline
        // ==============================================
        System.out.println();
        System.out.println("--- Time travel demo ---");

        TimelineManager manager = new TimelineManager();

        // Root timeline: 09:00 A, 10:00 B, 11:00 C
        Timeline root2 = manager.createRoot(1, "Root");
        manager.setNavigator(new TimelineNavigator(root2));
        Character witness = mira;
        root2.addEvent(new Event(100, "Event A", LocalDateTime.of(2026, 9, 14, 9, 0), witness, lab));
        Event eventB = new Event(101, "Event B", LocalDateTime.of(2026, 9, 14, 10, 0), witness, lab);
        root2.addEvent(eventB);
        root2.addEvent(new Event(102, "Event C", LocalDateTime.of(2026, 9, 14, 11, 0), witness, lab));

        System.out.println("Before travel:");
        for (Event e : root2.getEvents()) {
            System.out.println("  " + e);
        }

        // Travel back to Event B.
        Event traveledTo = manager.travelTo(101);
        System.out.println();
        System.out.println("Traveled back to: " + traveledTo);

        // Change what happened at Event B - this must fork a new branch.
        Timeline alternate = manager.createAlternateTimeline(101,
                new Event(101, "Event B (REVISED: witness came forward earlier)",
                        LocalDateTime.of(2026, 9, 14, 9, 30), witness, depot));
        System.out.println("Changed the past -> created alternate timeline: "
                + alternate.getLabel());

        // Show both versions of Event B, and that Root is unchanged.
        System.out.println();
        System.out.println("Event B on the alternate timeline:");
        System.out.println("  " + alternate.getEvents().stream()
                .filter(e -> e.getId() == 101).findFirst().orElseThrow());
        System.out.println("Event B on the original Root timeline (must be unchanged):");
        System.out.println("  " + root2.getEvents().stream()
                .filter(e -> e.getId() == 101).findFirst().orElseThrow());

        // Navigate back through history (Stack pop).
        System.out.println();
        System.out.println("Navigation history depth: " + manager.getNavigator().getHistoryDepth());
        manager.returnToPreviousTimeline()
                .ifPresentOrElse(
                        t -> System.out.println("Returned to: " + t.getLabel()),
                        () -> System.out.println("No history to return through"));
        System.out.println("Current timeline now: " + manager.getCurrent().getLabel());

        // Final structure + proof that the root survived untouched.
        System.out.println();
        System.out.println("Hierarchy:");
        for (String line : manager.renderHierarchy()) {
            System.out.println("  " + line);
        }
        System.out.println();
        System.out.println("Root still holds all original events:");
        for (Event e : root2.getEvents()) {
            System.out.println("  " + e);
        }

        // ==============================================
        // Step 6: Collections Framework in action
        //
        // HashMap       -> Case indexes: O(1) by-id lookups
        // Queue         -> InvestigationService: FIFO lead processing
        // HashSet       -> worked-lead dedup, unique evidence links
        // PriorityQueue -> EvidenceBoard: lab examines urgent clues first
        // ==============================================
        System.out.println();
        System.out.println("--- Step 6: collections demo ---");

        // HashMap index: by-id lookups are now O(1) map hits.
        System.out.println("HashMap index on Case:");
        System.out.println("  findEventById(2)   -> " + coldCase.findEventById(2)
                .map(Event::getDescription).orElse("not found"));
        System.out.println("  findEvidenceById(1)-> " + coldCase.findEvidenceById(1)
                .map(Evidence::getDescription).orElse("not found"));

        // Queue + HashSet: leads processed strictly FIFO, duplicates dropped.
        System.out.println();
        System.out.println("Queue + HashSet - investigation work queue (FIFO):");
        InvestigationService investigation = new InvestigationService();
        investigation.submitLead(new Event(201, "Lab assistant found a burned note",
                LocalDateTime.of(2026, 9, 10, 22, 15), mira, lab));
        investigation.submitLead(new Event(202, "Neighbor heard a van at midnight",
                LocalDateTime.of(2026, 9, 10, 23, 50), voss, depot));
        investigation.submitLead(new Event(201, "Duplicate of the burned-note lead",
                LocalDateTime.of(2026, 9, 10, 22, 15), mira, lab)); // HashSet dedup
        investigation.submitLead(new Event(203, "Security camera recorded nothing useful",
                LocalDateTime.of(2026, 9, 11, 0, 30), voss, depot));
        System.out.println("  submitted 4 leads, queued: " + investigation.getOpenLeadCount()
                + " (duplicate dropped by the HashSet)");
        for (InvestigationService.LeadResult r : investigation.workAllLeads()) {
            System.out.println("  worked " + r);
        }
        System.out.println("  worked leads: " + investigation.getWorkedLeadCount());

        // PriorityQueue + HashSet: the lab triages by priority.
        System.out.println();
        System.out.println("PriorityQueue - evidence triage (most urgent first): ");
        EvidenceBoard board = new EvidenceBoard();
        board.submit(new Evidence(11, "Tyre tread cast", null, depot, EvidencePriority.MEDIUM));
        board.submit(new Evidence(12, "Broken pocket watch", aris, lab, EvidencePriority.CRITICAL));
        board.submit(new Evidence(13, "Handwritten note 'M.K.'", mira, null, EvidencePriority.HIGH));
        board.submit(new Evidence(14, "Dust sample from the depot", null, depot, EvidencePriority.LOW));
        while (board.getPendingCount() > 0) {
            Evidence next = board.examineNext();
            System.out.println("  lab examined " + next);
        }

        // HashSet board links: unique, symmetric red-string connections.
        System.out.println();
        System.out.println("HashSet - unique evidence links on the board:");
        board.link(12, 13);
        board.link(13, 12); // same link, ignored
        board.link(12, 14);
        System.out.println("  watch<->note linked: " + board.areLinked(12, 13)
                + ", added twice but stored once");
        System.out.println("  watch linked to: " + board.getLinkedIds(12));
        System.out.println("  note linked to:  " + board.getLinkedIds(13));

        // ==============================================
        // Step 7: investigation scenario
        //
        // InvestigationManager inspects the case, collects clues,
        // searches evidence and associates clues with suspects,
        // locations and events - all session state, no data copies.
        // ==============================================
        System.out.println();
        System.out.println("--- Step 7: investigation demo ---");
        InvestigationManager detective = new InvestigationManager(coldCase);

        // Inspect an event and interview the key witness.
        System.out.println("Inspected event: " + detective.inspectEvent(2));
        InvestigationManager.CharacterInvestigation interview = detective.investigateCharacter(2);
        System.out.println("Interviewing " + interview.getSubject().getName() + ":");
        System.out.println("  events she took part in: " + interview.getEvents().size());
        System.out.println("  evidence linked to her:  " + interview.getLinkedEvidence().get(0));

        // Survey the depot.
        InvestigationManager.LocationInvestigation survey = detective.investigateLocation(2);
        System.out.println("Surveying " + survey.getSubject().getName() + ":");
        System.out.println("  events there:   " + survey.getEvents().size());
        System.out.println("  evidence there: " + survey.getLinkedEvidence().get(0));

        // Search the case file, then collect what matters.
        System.out.println("Search 'note' -> " + detective.searchEvidence("note").size() + " hit(s)");
        detective.collectEvidence(1); // pocket watch
        detective.collectEvidence(2); // tyre tread
        detective.collectEvidence(3); // handwritten note
        detective.collectEvidence(3); // ignored: already in the bag
        System.out.println("Collected clues: " + detective.getCollectedEvidenceCount());

        // Associate clues: the note implicates Mira and ties to the van event.
        detective.associateWithSuspect(3, 2);   // note 'M.K.' -> Mira
        detective.associateWithLocation(2, 2);  // tread cast -> depot (already linked)
        detective.associateWithEvent(3, 2);     // note -> van event
        detective.associateWithEvent(3, 2);     // duplicate link, stored once
        System.out.println();
        for (String line : detective.renderClueBoard()) {
            System.out.println(line);
        }

        // ==============================================
        // Step 8: relationships & contradiction detection
        //
        // Two timelines tell conflicting stories about Maya's entry.
        // The ContradictionDetector classifies every piece of
        // evidence against every timeline and registers the verdicts
        // as typed edges in a RelationshipGraph.
        // ==============================================
        System.out.println();
        System.out.println("--- Step 8: contradiction demo ---");

        Case entryCase = new Case(2, "The Laboratory Entry", "Did Maya enter at 10:00?");
        Character maya = new Character(10, "Maya", "Lab technician");
        Character reeve = new Character(11, "Dr. Reeve", "Lab director");
        Location lab2 = new Location(10, "Laboratory", "Restricted basement lab");
        entryCase.addCharacter(maya);
        entryCase.addCharacter(reeve);
        entryCase.addLocation(lab2);

        // Timeline A: Maya entered at 10:00.
        Timeline timelineA = new Timeline(10, "Timeline A: Maya entered");
        timelineA.addEvent(new Event(10, "Maya entered the laboratory",
                LocalDateTime.of(2026, 9, 14, 10, 0), maya, lab2));
        timelineA.addEvent(new Event(11, "Reeve started the experiment",
                LocalDateTime.of(2026, 9, 14, 10, 15), reeve, lab2));

        // Timeline B: Maya never entered (the conflicting story).
        Timeline timelineB = new Timeline(20, "Timeline B: Maya never entered");
        timelineB.addEvent(new Event(20, "Maya never entered the laboratory",
                LocalDateTime.of(2026, 9, 14, 10, 0), maya, lab2));
        timelineB.addEvent(new Event(21, "Reeve started the experiment alone",
                LocalDateTime.of(2026, 9, 14, 10, 15), reeve, lab2));

        // Evidence: corroborates A, clashes with B, or is neutral.
        entryCase.addEvidence(new Evidence(100,
                "Keycard log: Maya entered the laboratory at 10:00", maya, lab2));
        entryCase.addEvidence(new Evidence(101,
                "CCTV still shows Maya at the laboratory door at 10:00", maya, lab2));
        entryCase.addEvidence(new Evidence(102,
                "Reeve's testimony: Maya never entered the laboratory", reeve, lab2));
        entryCase.addEvidence(new Evidence(103,
                "Coffee cup found in the break room", maya, null));

        RelationshipGraph graph = new RelationshipGraph();
        ContradictionDetector detector = new ContradictionDetector(graph);
        ContradictionDetector.Report report =
                detector.analyze(entryCase, java.util.Arrays.asList(timelineA, timelineB));

        System.out.println("Graph: " + graph.getNodeCount() + " nodes, "
                + graph.getEdgeCount() + " relationships");
        System.out.println();
        for (String line : report.render()) {
            System.out.println(line);
        }

        // The graph also answers reachability questions, e.g. can the
        // contradicting evidence be tied back to the lab through events?
        System.out.println();
        System.out.println("Path evidence#102 -> laboratory in the relationship graph:");
        for (RelationshipGraph.NodeKey node : graph.findPath(
                RelationshipGraph.NodeType.EVIDENCE, 102,
                RelationshipGraph.NodeType.LOCATION, 10)) {
            System.out.println("  " + node);
        }

        // ==============================================
        // Step 9: mystery solver
        //
        // Three runs over the same people and timelines, different
        // evidence each time: the verdict must follow the facts -
        // nothing about the culprit is hard-coded.
        // ==============================================
        System.out.println();
        System.out.println("--- Step 9: mystery solver demo ---");
        MysterySolver solver = new MysterySolver();

        // Run 1: Maya's keycard places her at the lab at 10:00.
        Case run1 = new Case(3, "The Laboratory Entry", "Whose story holds?");
        Character mayaS9 = new Character(1, "Maya", "Lab technician");
        Character reeveS9 = new Character(2, "Dr. Reeve", "Lab director");
        Location lab3 = new Location(1, "Laboratory", "Restricted basement lab");
        run1.addCharacter(mayaS9);
        run1.addCharacter(reeveS9);
        run1.addLocation(lab3);
        run1.addEvidence(new Evidence(1,
                "Keycard log: Maya entered the laboratory at 10:00", mayaS9, lab3));

        Timeline storyA = new Timeline(10, "Timeline A: Maya entered");
        storyA.addEvent(new Event(1, "Maya entered the laboratory",
                LocalDateTime.of(2026, 9, 14, 10, 0), mayaS9, lab3));
        storyA.addEvent(new Event(2, "Reeve started the experiment",
                LocalDateTime.of(2026, 9, 14, 10, 15), reeveS9, lab3));
        Timeline storyB = new Timeline(20, "Timeline B: Maya never entered");
        storyB.addEvent(new Event(3, "Maya never entered the laboratory",
                LocalDateTime.of(2026, 9, 14, 10, 0), mayaS9, lab3));
        storyB.addEvent(new Event(4, "Reeve started the experiment",
                LocalDateTime.of(2026, 9, 14, 10, 15), reeveS9, lab3));

        System.out.println("RUN 1 - evidence: Maya's keycard entry");
        MysterySolver.MysteryResult verdict1 = solver.solve(run1,
                java.util.Arrays.asList(storyA, storyB));
        for (String line : verdict1.render()) {
            System.out.println(line);
        }

        // Run 2: instead, evidence shows Reeve forged the log.
        System.out.println();
        System.out.println("RUN 2 - evidence: Reeve forged the entry log");
        Case run2 = new Case(3, "The Laboratory Entry", "Whose story holds?");
        Character maya2 = new Character(1, "Maya", "Lab technician");
        Character reeve2 = new Character(2, "Dr. Reeve", "Lab director");
        run2.addCharacter(maya2);
        run2.addCharacter(reeve2);
        run2.addLocation(lab3);
        run2.addEvidence(new Evidence(2,
                "Ink analysis: Reeve forged the entry log at 10:00", reeve2, lab3));
        MysterySolver.MysteryResult verdict2 = solver.solve(run2,
                java.util.Arrays.asList(storyA, storyB));
        System.out.println("  culprit: " + verdict2.getCulprit().getSuspect().getName()
                + " (confidence " + verdict2.getConfidence() + "%"
                + ", timeline '" + verdict2.getTimelineUsed().getLabel() + "')");
        for (MysterySolver.ScoreReason r : verdict2.getCulprit().getReasons()) {
            System.out.println("    " + r);
        }

        // Run 3: Maya has an alibi; the forgery evidence still points at Reeve.
        System.out.println();
        System.out.println("RUN 3 - evidence: forgery PLUS Maya's alibi");
        Case run3 = new Case(3, "The Laboratory Entry", "Whose story holds?");
        Character maya3 = new Character(1, "Maya", "Lab technician");
        Character reeve3 = new Character(2, "Dr. Reeve", "Lab director");
        run3.addCharacter(maya3);
        run3.addCharacter(reeve3);
        run3.addLocation(lab3);
        run3.addEvidence(new Evidence(2,
                "Ink analysis: Reeve forged the entry log at 10:00", reeve3, lab3));
        run3.addEvidence(new Evidence(3,
                "Badge record: Maya never entered the laboratory that day", null, lab3));
        MysterySolver.MysteryResult verdict3 = solver.solve(run3,
                java.util.Arrays.asList(storyA, storyB));
        System.out.println("  culprit: " + verdict3.getCulprit().getSuspect().getName()
                + " (confidence " + verdict3.getConfidence() + "%"
                + ", timeline '" + verdict3.getTimelineUsed().getLabel() + "')");
        for (MysterySolver.SuspectScore s : verdict3.getRankings()) {
            System.out.println("    " + s.getSuspect().getName() + ": " + s.getTotal() + " points");
            for (MysterySolver.ScoreReason r : s.getReasons()) {
                System.out.println("      " + r);
            }
        }

        // ==============================================
        // Step 10: file I/O - save, load and compare
        //
        // The whole case (characters, locations, events,
        // evidence) plus the session's collected clues is
        // written to a temp file in the CHRONOCASE-V1 pipe
        // format, loaded back, and compared with the
        // original.
        // ==============================================
        System.out.println();
        System.out.println("--- Step 10: save/load demo ---");
        FileManager fileManager = new FileManager();
        Path saveFile = null;
        try {
            saveFile = Files.createTempFile("chronocase-demo", ".txt");
            fileManager.saveCase(coldCase, saveFile,
                    new LinkedHashSet<>(detective.getCollectedEvidence()));
            System.out.println("Saved to " + saveFile + " (" + Files.size(saveFile) + " bytes)");

            FileManager.LoadResult reloaded = fileManager.loadCase(saveFile);
            Case restored = reloaded.getCase();

            System.out.println("Original: " + coldCase);
            System.out.println("Loaded:   " + restored);
            System.out.println("Collected clues restored: " + reloaded.getCollectedEvidence().size());

            boolean identical = restored.getTitle().equals(coldCase.getTitle())
                    && restored.getDescription().equals(coldCase.getDescription())
                    && restored.getCharacters().size() == coldCase.getCharacters().size()
                    && restored.getLocations().size() == coldCase.getLocations().size()
                    && restored.getEvents().size() == coldCase.getEvents().size()
                    && restored.getEvidence().size() == coldCase.getEvidence().size()
                    && reloaded.getCollectedEvidence().size() == detective.getCollectedEvidenceCount();
            System.out.println("Round-trip check (title, counts, collected clues match): "
                    + (identical ? "OK" : "MISMATCH"));
        } catch (IOException e) {
            System.out.println("File I/O demo failed: " + e.getMessage());
        } finally {
            if (saveFile != null) {
                try {
                    Files.deleteIfExists(saveFile);
                } catch (IOException cleanupFailure) {
                    System.out.println("Could not delete temp file: " + cleanupFailure.getMessage());
                }
            }
        }
        // ==============================================
        // Step 11: database persistence (JDBC + MySQL)
        //
        // Saves the cold case to MySQL, loads it back and
        // compares. Settings come from the environment:
        //   CHRONOCASE_DB_URL      (default jdbc:mysql://localhost:3306/chronocase)
        //   CHRONOCASE_DB_USER     (default root)
        //   CHRONOCASE_DB_PASSWORD (no default - never hard-coded)
        // The MySQL Connector/J jar must be on the classpath, e.g.:
        //   java -cp out;<path-to>/mysql-connector-j-9.x.jar Main
        // If no server is reachable the demo reports that and moves on
        // instead of crashing.
        // ==============================================
        System.out.println();
        System.out.println("--- Step 11: database demo ---");
        runDatabaseDemo(coldCase, detective);
    }

    /**
     * Step 11 database round trip: save -> load -> compare ->
     * update -> delete. Reports a skipped demo instead of crashing
     * when no MySQL server is reachable.
     */
    private static void runDatabaseDemo(Case coldCase, InvestigationManager detective) {
        DatabaseManager db = new DatabaseManager();
        try {
            db.createSchema();
        } catch (DatabaseManager.DataAccessException e) {
            System.out.println("Database not reachable - demo skipped.");
            System.out.println("  (" + rootMessage(e) + ")");
            System.out.println("  Start MySQL and set CHRONOCASE_DB_URL / _USER / _PASSWORD");
            System.out.println("  (and put mysql-connector-j on the classpath) to run it.");
            return;
        }

        try {
            // A fresh standalone case built like the file-format demo.
            Case toSave = new Case(77, "The Depot Burglary",
                    "Saved to MySQL and read back by the Step 11 demo.");
            Character nick = new Character(1, "Nick Cagey", "Night watchman");
            Character rita = new Character(2, "Rita Blaine", "Fence");
            Location yard = new Location(1, "Freight Yard", "Dark, fenced, muddy");
            Location safehouse = new Location(2, "Safehouse", "Rented garage");
            toSave.addCharacter(nick);
            toSave.addCharacter(rita);
            toSave.addLocation(yard);
            toSave.addLocation(safehouse);
            toSave.addEvent(new Event(1, "Lock on the freight yard gate cut",
                    LocalDateTime.of(2026, 9, 12, 2, 10), nick, yard));
            toSave.addEvent(new Event(2, "Van seen leaving the yard",
                    LocalDateTime.of(2026, 9, 12, 2, 40), rita, yard));
            toSave.addEvidence(new Evidence(1, "Bolt cutter left at the gate", nick, yard,
                    EvidencePriority.HIGH));
            toSave.addEvidence(new Evidence(2, "Muddy boot print", null, yard));
            toSave.addEvidence(new Evidence(3, "Anonymous tip letter", rita, null));

            Timeline root = new Timeline(1, "Root");
            root.addEvent(new Event(1, "Lock on the freight yard gate cut",
                    LocalDateTime.of(2026, 9, 12, 2, 10), nick, yard));
            Timeline branch = root.createBranch(2, "Alternate: Rita was the lookout");
            branch.addEvent(new Event(2, "Van seen leaving the yard - driven by Rita",
                    LocalDateTime.of(2026, 9, 12, 2, 40), rita, yard));
            toSave.addTimeline(root);

            boolean updated = db.saveCase(toSave, List.of(1, 3));
            System.out.println("saveCase -> " + (updated ? "updated existing" : "inserted new"));

            DatabaseManager.LoadResult back = db.loadCaseWithCollected(77);
            Case restored = back.getCase();
            System.out.println("Loaded:   " + restored);
            System.out.println("Collected evidence ids restored: " + back.getCollectedEvidenceIds());

            boolean ok = restored.getTitle().equals(toSave.getTitle())
                    && restored.getCharacters().size() == toSave.getCharacters().size()
                    && restored.getLocations().size() == toSave.getLocations().size()
                    && restored.getEvents().size() == toSave.getEvents().size()
                    && restored.getEvidence().size() == toSave.getEvidence().size()
                    && restored.getTimelines().size() == toSave.getTimelines().size()
                    && restored.getTimelines().get(0).getBranches().size() == 1
                    && restored.getTimelines().get(0).getBranches().get(0).getEvents().size() == 2
                    && restored.getTimelines().get(0).getBranches().get(0).getEvents().stream()
                            .anyMatch(e -> e.getDescription().contains("driven by Rita"))
                    && back.getCollectedEvidenceIds().equals(List.of(1, 3));
            System.out.println("Round-trip check (counts, branch, collected ids match): "
                    + (ok ? "OK" : "MISMATCH"));

            // Update: change the title, save again -> must report "updated".
            toSave.setTitle("The Depot Burglary (revised)");
            boolean updatedAgain = db.saveCase(toSave);
            Case reloaded = db.loadCase(77);
            System.out.println("update -> " + (updatedAgain ? "updated existing" : "inserted new")
                    + ", title now: " + reloaded.getTitle());

            // Clean up the demo row.
            db.deleteCase(77);
            System.out.println("delete -> case 77 removed (exists: " + db.caseExists(77) + ")");
        } catch (DatabaseManager.DataAccessException e) {
            System.out.println("Database error during demo: " + rootMessage(e));
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
