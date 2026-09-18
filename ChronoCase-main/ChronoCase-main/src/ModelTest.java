import model.Case;
import model.Character;
import model.Evidence;
import model.Event;
import model.Location;
import model.EvidencePriority;
import model.Timeline;
import service.ContradictionDetector;
import service.EvidenceBoard;
import io.FileManager;
import service.InvestigationManager;
import service.MysterySolver;
import service.InvestigationService;
import service.RelationshipGraph;
import service.TimelineManager;
import service.TimelineNavigator;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Plain-Java test harness for the model and service behavior. No
 * JUnit yet (the project has no build tool to fetch dependencies), so
 * this is a simple main that throws AssertionError on the first
 * failure and prints a pass count when everything holds.
 */
public class ModelTest {

    private static int passed = 0;

    public static void main(String[] args) {
        testAddMethodsPopulateCase();
        testByIdLookups();
        testByIdLookupMissReturnsEmpty();
        testCaseRejectsNullEntries();
        testCaseRejectsNullTitleAndDescription();
        testEventRequiresAssociations();
        testEvidenceMayBeUnlinked();
        testCharacterAndLocationRejectNullFields();
        testCollectionsAreReadOnly();

        testRootTimelineHasNoParent();
        testBranchHasParentAndSibling();
        testBranchingDoesNotModifyParent();
        testBranchSnapshotsEvents();
        testDivergedBranchesAreIndependent();
        testTimelineRejectsNullEventAndLabel();
        testTimelineCollectionsAreReadOnly();

        testManagerCreatesRootAndBranches();
        testManagerRejectsSecondRoot();
        testManagerFindsById();
        testManagerClearErrorsForInvalidIds();
        testManagerSwitching();
        testManagerSwitchNeverDeletes();
        testManagerPreservesParentChildRelations();
        testManagerHierarchyAndAncestry();
        testManagerRejectsForeignAndDuplicateIds();

        testReplaceEventFindsAndReturnsOriginal();
        testReplaceEventRejectsUnknownId();
        testNavigatorStackBasics();
        testNavigatorReturnEmptyWithoutHistory();
        testNavigatorRejectsTravelToCurrent();
        testNavigatorTrimsStaleHistory();
        testTravelToPrefersCurrentTimeline();
        testTravelToJumpsAcrossTimelines();
        testCreateAlternateTimelineForksAndKeepsOriginal();
        testAlternateTimelineBecomesCurrentWithHistoryPushed();
        testCreateAlternateRejectsUnknownEvent();
        testReturnToPreviousTimelinePopsStack();
        testOriginalNeverModifiedByAlternateCreation();

        testCaseHashMapIndexLookups();
        testInvestigationQueueProcessesInOrder();
        testInvestigationQueueRejectsDuplicates();
        testEvidenceBoardExaminesHighestPriorityFirst();
        testEvidenceBoardSkipsDuplicateAndExaminedItems();
        testEvidenceBoardLinksAreUniqueAndSymmetric();

        testInspectEventRecordsVisitAndReturnsCaseInstance();
        testInvestigateCharacterFindsEventsAndEvidence();
        testInvestigateLocationFindsEventsAndEvidence();
        testCollectEvidenceIsOrderedAndUnique();
        testSearchEvidenceFindsCaseInsensitiveAndRejectsBadQueries();
        testAssociationsRequireCollectedEvidenceAndValidIds();
        testAssociationsWriteThroughToTheCaseAndDedupEvents();
        testClueBoardRendersCollectedClues();

        testGraphStoresNodesAndTypedEdgesWithoutDuplicates();
        testGraphFindsPathAndConnectivity();
        testDetectorTimeMatchSupportsAndClashContradicts();
        testDetectorNegationContradictsPresence();
        testDetectorUnlinkedEvidenceStaysNeutral();
        testDetectorReportSplitsVerdictsAndRenders();
        testDetectorRegistersFindingsInGraph();

        testSolverRanksEvidenceLinkedSuspectHighest();
        testSolverJudgesAgainstCredibleTimelineAndReportsIt();
        testSolverResultChangesWithEvidence();
        testSolverExplainsScoresAndSplitsEvidence();
        testSolverRejectsEmptyCases();

        // Step 10 helpers use temp files, so they may throw IOException;
        // convert to AssertionError to keep main's signature simple.
        try {
            testSaveLoadRoundTripPreservesEverything();
            testSaveLoadRoundTripWithBranchedTimelines();
            testFileManagerEscapesSpecialCharacters();
            testFileManagerReportsMissingFileAndBadHeader();
            testFileManagerRejectsUnknownReferencesAndRecords();
        } catch (AssertionError e) {
            throw e;
        } catch (IOException e) {            throw new AssertionError("Step 10 file I/O test failed: " + e, e);
        }

        System.out.println("All " + passed + " tests passed.");
    }

    // ----- helpers -----

    private static Character character(int id) {
        return new Character(id, "Person " + id, "Witness");
    }

    private static Location location(int id) {
        return new Location(id, "Place " + id, "Somewhere");
    }

    private static Event event(int id, Character c, Location l) {
        return new Event(id, "Event " + id, LocalDateTime.of(2026, 9, 14, 12, 0), c, l);
    }

    private static Evidence evidence(int id, Character c, Location l) {
        return new Evidence(id, "Item " + id, c, l);
    }

    /** Anything that can be run and may fail with any exception. */
    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private static void expectThrows(ThrowingAction action, String expectedMessagePart, String label) {
        try {
            action.run();
            throw new AssertionError(label + ": expected an exception but none was thrown");
        } catch (Exception e) {
            if (expectedMessagePart != null && !e.getMessage().contains(expectedMessagePart)) {
                throw new AssertionError(label + ": message '" + e.getMessage()
                        + "' does not contain '" + expectedMessagePart + "'", e);
            }
            passed++;
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
        passed++;
    }

    // ----- tests -----

    private static void testAddMethodsPopulateCase() {
        Case c = new Case(1, "T", "D");
        c.addCharacter(character(1));
        c.addLocation(location(1));
        c.addEvent(event(1, character(2), location(1)));
        c.addEvidence(evidence(1, character(1), null));

        check(c.getCharacters().size() == 1, "addCharacter should add one");
        check(c.getLocations().size() == 1, "addLocation should add one");
        check(c.getEvents().size() == 1, "addEvent should add one");
        check(c.getEvidence().size() == 1, "addEvidence should add one");
    }

    private static void testByIdLookups() {
        Case c = new Case(1, "T", "D");
        Character mira = character(2);
        Location depot = location(2);
        c.addCharacter(mira);
        c.addLocation(depot);

        check(c.findCharacterById(2).orElse(null) == mira, "findCharacterById returns added instance");
        check(c.findLocationById(2).orElse(null) == depot, "findLocationById returns added instance");
    }

    private static void testByIdLookupMissReturnsEmpty() {
        Case c = new Case(1, "T", "D");
        check(c.findCharacterById(9).isEmpty(), "missing character id -> empty Optional");
        check(c.findLocationById(9).isEmpty(), "missing location id -> empty Optional");
        check(c.findEventById(9).isEmpty(), "missing event id -> empty Optional");
        check(c.findEvidenceById(9).isEmpty(), "missing evidence id -> empty Optional");
    }

    private static void testCaseRejectsNullEntries() {
        Case c = new Case(1, "T", "D");
        expectThrows(() -> c.addCharacter(null), "character must not be null", "addCharacter(null)");
        expectThrows(() -> c.addLocation(null), "location must not be null", "addLocation(null)");
        expectThrows(() -> c.addEvent(null), "event must not be null", "addEvent(null)");
        expectThrows(() -> c.addEvidence(null), "evidence must not be null", "addEvidence(null)");
    }

    private static void testCaseRejectsNullTitleAndDescription() {
        expectThrows(() -> new Case(1, null, "D"), "title must not be null", "Case(null title)");
        expectThrows(() -> new Case(1, "T", null), "description must not be null", "Case(null description)");
        Case c = new Case(1, "T", "D");
        expectThrows(() -> c.setTitle(null), "title must not be null", "setTitle(null)");
        expectThrows(() -> c.setDescription(null), "description must not be null", "setDescription(null)");
    }

    private static void testEventRequiresAssociations() {
        Character c = character(1);
        Location l = location(1);
        expectThrows(() -> new Event(1, "E", LocalDateTime.now(), null, l),
                "event character must not be null", "Event(null character)");
        expectThrows(() -> new Event(1, "E", LocalDateTime.now(), c, null),
                "event location must not be null", "Event(null location)");
        expectThrows(() -> new Event(1, "E", null, c, l),
                "timestamp must not be null", "Event(null timestamp)");
        expectThrows(() -> new Event(1, null, LocalDateTime.now(), c, l),
                "description must not be null", "Event(null description)");
    }

    private static void testEvidenceMayBeUnlinked() {
        // Unlinked evidence is valid by design; only the description is required.
        Evidence unlinked = new Evidence(1, "Unlinked item", null, null);
        check(unlinked.getLinkedCharacter() == null && unlinked.getLinkedLocation() == null,
                "evidence may be fully unlinked");
        expectThrows(() -> new Evidence(2, null, null, null),
                "description must not be null", "Evidence(null description)");
    }

    private static void testCharacterAndLocationRejectNullFields() {
        expectThrows(() -> new Character(1, null, "Role"), "name must not be null", "Character(null name)");
        expectThrows(() -> new Character(1, "N", null), "role must not be null", "Character(null role)");
        expectThrows(() -> new Location(1, null, "D"), "name must not be null", "Location(null name)");
        expectThrows(() -> new Location(1, "L", null), "description must not be null", "Location(null description)");
    }

    private static void testCollectionsAreReadOnly() {
        Case c = new Case(1, "T", "D");
        c.addCharacter(character(1));
        c.addLocation(location(1));
        c.addEvent(event(1, character(2), location(1)));
        c.addEvidence(evidence(1, character(1), null));

        List<Character> characters = c.getCharacters();
        expectThrows(() -> characters.add(character(9)), null, "getCharacters().add");
        expectThrows(() -> characters.remove(0), null, "getCharacters().remove");
        check(characters.size() == 1, "external mutation must not leak into the case");
    }

    // ----- Step 3: timeline tests -----

    private static void testRootTimelineHasNoParent() {
        Timeline root = new Timeline(1, "Root");
        check(root.isRoot(), "root.isRoot()");
        check(root.getParent() == null, "root has no parent");
        check(root.getCreatedAtBranch() == 0, "root has no branch point");
        check(root.getDepth() == 1, "root depth is 1");
        check(root.isLeaf(), "fresh root is a leaf");
    }

    private static void testBranchHasParentAndSibling() {
        Timeline root = new Timeline(1, "Root");
        Timeline a = root.createBranch(2, "A");
        Timeline b = root.createBranch(3, "B");
        check(!a.isRoot() && !b.isRoot(), "branches are not roots");
        check(a.getParent() == root && b.getParent() == root, "branches point at their parent");
        check(root.getBranches().size() == 2, "root has two branches");
        check(root.getBranches().contains(a) && root.getBranches().contains(b), "branches are listed on parent");
        check(a.getDepth() == 2 && b.getDepth() == 2, "branch depth is 2");
    }

    private static void testBranchingDoesNotModifyParent() {
        Timeline root = new Timeline(1, "Root");
        root.addEvent(event(1, character(1), location(1)));
        int eventsBefore = root.getEventCount();
        int branchesBefore = root.getBranches().size();

        Timeline branch = root.createBranch(2, "B");

        check(root.getEventCount() == eventsBefore, "parent events unchanged by branching");
        check(root.getBranches().size() == branchesBefore + 1, "parent gains exactly one branch entry");
        check(branch.getEventCount() == eventsBefore, "branch starts with parent's event count");
    }

    private static void testBranchSnapshotsEvents() {
        Timeline root = new Timeline(1, "Root");
        Event original = event(1, character(1), location(1));
        root.addEvent(original);

        Timeline branch = root.createBranch(2, "B");
        Event copy = branch.getEvents().get(0);

        check(copy != original, "branch holds a copy, not the same Event instance");
        check(copy.getId() == original.getId(), "copy keeps the event id");
        check(copy.getDescription().equals(original.getDescription()), "copy keeps the description");

        // Post-branch edits to either side must not leak across.
        branch.addEvent(event(2, character(2), location(2)));
        check(root.getEventCount() == 1, "adding to branch leaves parent untouched");
        check(branch.getEventCount() == 2, "branch event count grew");
    }

    private static void testDivergedBranchesAreIndependent() {
        Timeline root = new Timeline(1, "Root");
        Timeline a = root.createBranch(2, "A");
        Timeline b = root.createBranch(3, "B");
        a.addEvent(event(1, character(1), location(1)));

        check(b.getEventCount() == 0, "sibling branch unaffected by A's events");
        check(root.getEventCount() == 0, "root unaffected by A's events");
        check(a.getEventCount() == 1, "A kept its own event");
    }

    private static void testTimelineRejectsNullEventAndLabel() {
        Timeline t = new Timeline(1, "T");
        expectThrows(() -> t.addEvent(null), "event must not be null", "addEvent(null)");
        expectThrows(() -> new Timeline(2, null), "label must not be null", "Timeline(null label)");
    }

    private static void testTimelineCollectionsAreReadOnly() {
        Timeline t = new Timeline(1, "T");
        t.addEvent(event(1, character(1), location(1)));
        expectThrows(() -> t.getEvents().add(event(9, character(9), location(9))),
                null, "getEvents().add");
        expectThrows(() -> t.getBranches().add(t), null, "getBranches().add");
    }

    // ----- Step 4: TimelineManager tests -----

    private static void testManagerCreatesRootAndBranches() {
        TimelineManager m = new TimelineManager();
        Timeline root = m.createRoot(1, "Root");
        check(m.getCurrent() == root, "current starts at root");

        Timeline a = m.createBranch(2, "A");
        check(a.getParent() == root, "branch off current parents to root");
        check(m.getTimelines().size() == 2, "manager tracks root + branch");
    }

    private static void testManagerRejectsSecondRoot() {
        TimelineManager m = new TimelineManager();
        m.createRoot(1, "Root");
        expectThrows(() -> m.createRoot(2, "Again"),
                "already exists", "second createRoot");
    }

    private static void testManagerFindsById() {
        TimelineManager m = new TimelineManager();
        Timeline root = m.createRoot(1, "Root");
        m.createBranch(2, "A", root);

        check(m.findTimelineById(2).isPresent(), "findTimelineById hit");
        check(m.findTimelineById(99).isEmpty(), "findTimelineById miss -> empty");
        check(m.getTimelineById(1) == root, "getTimelineById returns instance");
    }

    private static void testManagerClearErrorsForInvalidIds() {
        TimelineManager m = new TimelineManager();
        expectThrows(m::getCurrent, "No timeline exists yet", "getCurrent before root");
        expectThrows(() -> m.getTimelineById(1),
                "No timeline with id 1", "getTimelineById before root");
        m.createRoot(1, "Root");
        expectThrows(() -> m.getTimelineById(7),
                "No timeline with id 7", "getTimelineById unknown id");
    }

    private static void testManagerSwitching() {
        TimelineManager m = new TimelineManager();
        m.createRoot(1, "Root");
        Timeline a = m.createBranch(2, "A");
        Timeline b = m.createBranch(3, "B");

        m.switchTo(3);
        check(m.getCurrent() == b, "switchTo(id) works");
        m.switchTo(a);
        check(m.getCurrent() == a, "switchTo(instance) works");
        m.switchToRoot();
        check(m.getCurrent().isRoot(), "switchToRoot works");
    }

    private static void testManagerSwitchNeverDeletes() {
        TimelineManager m = new TimelineManager();
        m.createRoot(1, "Root");
        m.createBranch(2, "A");
        m.createBranch(3, "B");

        m.switchTo(2);
        m.switchTo(3);
        m.switchTo(1);
        check(m.getTimelines().size() == 3, "switching deletes nothing");
        check(m.findTimelineById(2).isPresent() && m.findTimelineById(3).isPresent(),
                "both branches still findable after switching");
    }

    private static void testManagerPreservesParentChildRelations() {
        TimelineManager m = new TimelineManager();
        Timeline root = m.createRoot(1, "Root");
        Timeline a = m.createBranch(2, "A", root);
        Timeline b = m.createBranch(3, "B", root);
        Timeline c = m.createBranch(4, "C", b);

        check(a.getParent() == root && b.getParent() == root && c.getParent() == b,
                "parent-child relations preserved");
        check(root.getBranches().size() == 2 && b.getBranches().size() == 1,
                "children registered on the right parents");
        check(c.getDepth() == 3, "C is two levels below root");
    }

    private static void testManagerHierarchyAndAncestry() {
        TimelineManager m = new TimelineManager();
        Timeline root = m.createRoot(1, "Root");
        Timeline a = m.createBranch(2, "A", root);
        Timeline b = m.createBranch(3, "B", root);
        m.createBranch(4, "C", b);

        List<String> lines = m.renderHierarchy();
        check(lines.size() == 4, "hierarchy renders every timeline once");
        check(lines.get(0).contains("Root") && lines.get(0).contains("#1"),
                "hierarchy starts at root");
        check(lines.get(3).contains("C") && lines.get(3).startsWith("    "),
                "C is indented under B");

        m.switchTo(4);
        List<String> afterSwitch = m.renderHierarchy();
        check(afterSwitch.get(3).contains("[current]"),
                "current timeline is marked in the hierarchy");
        check(!afterSwitch.get(0).contains("[current]"),
                "root is not marked current after switching to C");
    }

    private static void testManagerRejectsForeignAndDuplicateIds() {
        TimelineManager m = new TimelineManager();
        Timeline root = m.createRoot(1, "Root");
        m.createBranch(2, "A", root);

        expectThrows(() -> m.createBranch(2, "Dup", root),
                "already in use", "duplicate branch id");
        expectThrows(() -> m.createBranch(5, "X", null),
                "must not be null", "null parent");

        Timeline foreign = new Timeline(9, "Foreign");
        expectThrows(() -> m.createBranch(10, "X", foreign),
                "not managed by this TimelineManager", "foreign parent");
        expectThrows(() -> m.switchTo(foreign),
                "not managed by this TimelineManager", "switch to foreign");
        expectThrows(() -> m.getAncestryOf(foreign),
                "not managed by this TimelineManager", "ancestry of foreign");
    }

    // ----- Step 5: time travel tests -----

    private static Event timedEvent(int id, String desc, int hour) {
        return new Event(id, desc, LocalDateTime.of(2026, 9, 14, hour, 0),
                character(1), location(1));
    }

    private static void testReplaceEventFindsAndReturnsOriginal() {
        Timeline t = new Timeline(1, "T");
        Event original = timedEvent(1, "Original", 9);
        t.addEvent(original);
        Event replacement = timedEvent(1, "Revised", 10);

        Event displaced = t.replaceEvent(1, replacement);
        check(displaced == original, "replaceEvent returns the displaced original");
        check(t.getEvents().get(0) == replacement, "replacement is now on the timeline");
        check(t.getEventCount() == 1, "replace does not change the event count");
    }

    private static void testReplaceEventRejectsUnknownId() {
        Timeline t = new Timeline(1, "T");
        t.addEvent(timedEvent(1, "A", 9));
        expectThrows(() -> t.replaceEvent(99, timedEvent(99, "X", 10)),
                "No event with id 99", "replaceEvent unknown id");
        expectThrows(() -> t.replaceEvent(1, null),
                "must not be null", "replaceEvent null");
    }

    private static void testNavigatorStackBasics() {
        Timeline root = new Timeline(1, "Root");
        Timeline alt = root.createBranch(2, "Alt");
        TimelineNavigator nav = new TimelineNavigator(root);

        check(nav.getCurrent() == root, "navigator starts on root");
        check(nav.getHistoryDepth() == 0, "history empty at start");
        nav.travelTo(alt, LocalDateTime.now());
        check(nav.getCurrent() == alt, "travelTo moves current");
        check(nav.getHistoryDepth() == 1, "travel pushes history");
        check(nav.getHistorySnapshot().get(0) == root, "history snapshot records the root");
        check(nav.canReturn(), "canReturn true after one travel");
    }

    private static void testNavigatorReturnEmptyWithoutHistory() {
        TimelineNavigator nav = new TimelineNavigator(new Timeline(1, "Root"));
        check(nav.returnToPreviousTimeline().isEmpty(), "no history -> empty return");
        check(!nav.canReturn(), "canReturn false with no history");
    }

    private static void testNavigatorRejectsTravelToCurrent() {
        Timeline root = new Timeline(1, "Root");
        TimelineNavigator nav = new TimelineNavigator(root);
        expectThrows(() -> nav.travelTo(root, LocalDateTime.now()),
                "Already on timeline", "travelTo current");
    }

    private static void testNavigatorTrimsStaleHistory() {
        Timeline root = new Timeline(1, "Root");
        Timeline a = root.createBranch(2, "A");
        Timeline b = root.createBranch(3, "B");
        TimelineNavigator nav = new TimelineNavigator(root);

        nav.travelTo(a, LocalDateTime.now()); // history: root
        nav.travelTo(b, LocalDateTime.now()); // history: a, root
        nav.travelTo(a, LocalDateTime.now()); // stale A visit trimmed; history becomes [b, root]
        check(nav.getHistoryDepth() == 2, "stale visit to A was trimmed");
        check(nav.getHistorySnapshot().get(0) == b, "most recent visit first");
        nav.returnToPreviousTimeline();
        check(nav.getCurrent() == b, "first return goes to the timeline just left (B)");
        nav.returnToPreviousTimeline();
        check(nav.getCurrent() == root, "second return reaches root");
        check(!nav.canReturn(), "history exhausted after two returns");
    }

    private static void testTravelToPrefersCurrentTimeline() {
        TimelineManager m = new TimelineManager();
        Timeline root = m.createRoot(1, "Root");
        m.setNavigator(new TimelineNavigator(root));
        root.addEvent(timedEvent(1, "Root version", 9));

        Timeline branch = m.createBranch(2, "Branch", root);
        branch.replaceEvent(1, timedEvent(1, "Branch version", 9));

        Event found = m.travelTo(1);
        check(found.getDescription().equals("Root version"),
                "travelTo prefers the current timeline's copy of a duplicated event id");
        check(m.getCurrent() == root, "no jump when event is on current");
    }

    private static void testTravelToJumpsAcrossTimelines() {
        TimelineManager m = new TimelineManager();
        Timeline root = m.createRoot(1, "Root");
        m.setNavigator(new TimelineNavigator(root));
        Timeline branch = m.createBranch(2, "Branch", root);
        branch.addEvent(timedEvent(50, "Only on branch", 8));

        Event found = m.travelTo(50);
        check(found.getDescription().equals("Only on branch"), "event on other timeline found");
        check(m.getCurrent() == branch, "travelTo jumps to the owning timeline");
        check(m.getNavigator().getHistoryDepth() == 1, "jump recorded in history");
    }

    private static void testCreateAlternateTimelineForksAndKeepsOriginal() {
        TimelineManager m = new TimelineManager();
        Timeline root = m.createRoot(1, "Root");
        m.setNavigator(new TimelineNavigator(root));
        root.addEvent(timedEvent(1, "A", 9));
        root.addEvent(timedEvent(2, "B", 10));
        root.addEvent(timedEvent(3, "C", 11));

        Timeline alt = m.createAlternateTimeline(2, timedEvent(2, "B revised", 10));
        check(alt.getParent() == root, "alternate is a child of the current timeline");
        check(alt.getEventCount() == 3, "alternate holds the full snapshot");
        check(root.getEvents().stream().filter(e -> e.getId() == 2).findFirst().orElseThrow()
                .getDescription().equals("B"),
                "original root keeps the original event B");
        check(alt.getEvents().stream().filter(e -> e.getId() == 2).findFirst().orElseThrow()
                .getDescription().equals("B revised"),
                "alternate carries the revised event");
        check(m.getTimelines().size() == 2, "no timeline was deleted");
    }

    private static void testAlternateTimelineBecomesCurrentWithHistoryPushed() {
        TimelineManager m = new TimelineManager();
        Timeline root = m.createRoot(1, "Root");
        m.setNavigator(new TimelineNavigator(root));
        root.addEvent(timedEvent(1, "A", 9));

        Timeline alt = m.createAlternateTimeline(1, timedEvent(1, "A revised", 9));
        check(m.getCurrent() == alt, "alternate becomes current");
        check(m.getNavigator().getHistoryDepth() == 1, "departure pushed onto the stack");
    }

    private static void testCreateAlternateRejectsUnknownEvent() {
        TimelineManager m = new TimelineManager();
        Timeline root = m.createRoot(1, "Root");
        m.setNavigator(new TimelineNavigator(root));
        expectThrows(() -> m.createAlternateTimeline(42, timedEvent(42, "X", 9)),
                "has no event with id 42", "alternate for unknown event");
    }

    private static void testReturnToPreviousTimelinePopsStack() {
        TimelineManager m = new TimelineManager();
        Timeline root = m.createRoot(1, "Root");
        m.setNavigator(new TimelineNavigator(root));
        root.addEvent(timedEvent(1, "A", 9));

        m.createAlternateTimeline(1, timedEvent(1, "A revised", 9));
        check(m.getCurrent().getLabel().startsWith("Alternate"), "on the alternate");

        Timeline returned = m.returnToPreviousTimeline().orElseThrow();
        check(returned == root, "return pops back to root");
        check(m.getCurrent() == root, "current follows the return");
        check(!m.getNavigator().canReturn(), "history exhausted after pop");
        check(m.returnToPreviousTimeline().isEmpty(), "second return is empty");
        check(m.getTimelines().size() == 2, "returning deletes nothing");
    }

    private static void testOriginalNeverModifiedByAlternateCreation() {
        TimelineManager m = new TimelineManager();
        Timeline root = m.createRoot(1, "Root");
        m.setNavigator(new TimelineNavigator(root));
        Event a = timedEvent(1, "A", 9);
        Event b = timedEvent(2, "B", 10);
        Event c = timedEvent(3, "C", 11);
        root.addEvent(a);
        root.addEvent(b);
        root.addEvent(c);

        Timeline alt = m.createAlternateTimeline(2, timedEvent(2, "B revised", 10));

        // Root: same instances, same order, same content.
        check(root.getEvents().get(0) == a && root.getEvents().get(1) == b
                && root.getEvents().get(2) == c, "root event instances untouched");
        check(alt.getEvents().get(0) != a && alt.getEvents().get(1) != b
                && alt.getEvents().get(2) != c, "alternate holds copies, not shared instances");
        check(m.getNavigator().getRoot() == root, "navigator root unchanged");
    }

    // ----- Step 6: collections & data structures -----

    private static void testCaseHashMapIndexLookups() {
        Case c = new Case(1, "T", "D");
        Character mira = character(2);
        Location depot = location(2);
        Event e1 = event(3, mira, depot);
        Evidence item = evidence(4, mira, null);
        c.addCharacter(mira);
        c.addLocation(depot);
        c.addEvent(e1);
        c.addEvidence(item);

        // HashMap indexes: same instances come back, misses are empty.
        check(c.findCharacterById(2).orElse(null) == mira, "character index returns the instance");
        check(c.findLocationById(2).orElse(null) == depot, "location index returns the instance");
        check(c.findEventById(3).orElse(null) == e1, "event index returns the instance");
        check(c.findEvidenceById(4).orElse(null) == item, "evidence index returns the instance");
        check(c.findCharacterById(99).isEmpty(), "character index miss -> empty");
        check(c.findEventById(99).isEmpty(), "event index miss -> empty");
    }

    private static void testInvestigationQueueProcessesInOrder() {
        InvestigationService inv = new InvestigationService();
        Event first = event(1, character(1), location(1));
        Event second = event(2, character(2), location(2));
        Event third = event(3, character(3), location(3));
        inv.submitLead(second);
        inv.submitLead(first);
        inv.submitLead(third);

        check(inv.getOpenLeadCount() == 3, "three leads queued");
        check(inv.workNextLead().getLead() == second, "FIFO: earliest submission worked first");
        check(inv.workNextLead().getLead() == first, "FIFO: second submission next");
        check(inv.workNextLead().getLead() == third, "FIFO: last submission last");
        check(inv.getOpenLeadCount() == 0, "queue drained");
        check(inv.getWorkedLeadCount() == 3, "three distinct leads worked");
        check(inv.getResults().size() == 3, "one result per worked lead");
        check(inv.workNextLead() == null, "working an empty queue returns null");
    }

    private static void testInvestigationQueueRejectsDuplicates() {
        InvestigationService inv = new InvestigationService();
        Event lead = event(1, character(1), location(1));
        check(inv.submitLead(lead), "fresh lead accepted");
        check(!inv.submitLead(event(1, character(1), location(1))), "queued duplicate rejected");
        inv.workNextLead();
        check(!inv.submitLead(event(1, character(1), location(1))), "worked lead cannot be re-queued");
        check(inv.getOpenLeadCount() == 0 && inv.getWorkedLeadCount() == 1,
                "duplicate handling left exactly one worked lead");
        check(inv.isWorked(1), "isWorked reports the lead");
        expectThrows(() -> inv.submitLead(null), "must not be null", "submitLead(null)");
    }

    private static void testEvidenceBoardExaminesHighestPriorityFirst() {
        EvidenceBoard board = new EvidenceBoard();
        board.submit(new Evidence(5, "Low", null, null)); // MEDIUM by default
        board.submit(new Evidence(3, "Critical", null, null, EvidencePriority.CRITICAL));
        board.submit(new Evidence(7, "High", null, null, EvidencePriority.HIGH));
        board.submit(new Evidence(1, "Lowest", null, null, EvidencePriority.LOW));
        board.submit(new Evidence(2, "Critical tie", null, null, EvidencePriority.CRITICAL));

        check(board.getPendingCount() == 5, "five items pending");
        check(board.examineNext().getId() == 2, "CRITICAL tie broken by lower id first");
        check(board.examineNext().getId() == 3, "second CRITICAL next");
        check(board.examineNext().getId() == 7, "HIGH next");
        check(board.examineNext().getId() == 5, "MEDIUM before LOW");
        check(board.examineNext().getId() == 1, "LOW last");
        check(board.examineNext() == null, "empty board yields nothing");
        check(board.getExaminedCount() == 5, "all examined");
        check(board.getExaminationOrder().get(0).getId() == 2, "examination order recorded");
    }

    private static void testEvidenceBoardSkipsDuplicateAndExaminedItems() {
        EvidenceBoard board = new EvidenceBoard();
        Evidence item = new Evidence(1, "Default priority", null, null); // old 4-arg ctor
        check(item.getPriority() == EvidencePriority.MEDIUM, "old constructor defaults to MEDIUM");

        board.submit(new Evidence(2, "Pending", null, null));
        check(!board.submit(new Evidence(2, "Pending dup", null, null)), "pending duplicate rejected");
        board.examineNext();
        check(board.isExamined(2), "item marked examined");
        check(!board.submit(new Evidence(2, "Re-examined", null, null)), "examined item cannot be re-queued");
        check(board.examineNext() == null, "nothing left to examine");
        check(board.getPendingCount() == 0 && board.getExaminedCount() == 1,
                "exactly one pass through the lab");
    }

    private static void testEvidenceBoardLinksAreUniqueAndSymmetric() {
        EvidenceBoard board = new EvidenceBoard();
        check(board.link(1, 2), "first link added");
        check(!board.link(2, 1), "same link from the other side is a no-op");
        check(!board.link(1, 2), "exact duplicate link is a no-op");
        check(!board.link(3, 3), "no self-links");
        check(board.areLinked(1, 2) && board.areLinked(2, 1), "links are symmetric");
        check(!board.areLinked(1, 3), "unlinked pair reports false");
        check(board.getLinkedIds(1).size() == 1 && board.getLinkedIds(1).contains(2), "linked ids listed");
        check(board.getLinkedIds(9).isEmpty(), "unknown item has no links");
    }

    // ----- Step 7: investigation manager -----

    /** Builds the shared fixture: one case with two characters, two locations, three events, three evidence items. */
    private static Case investigationCase() {
        Case c = new Case(1, "The Vanishing", "A physicist disappears.");
        Character aris = new Character(1, "Aris", "Missing physicist");
        Character mira = new Character(2, "Mira", "Lab assistant");
        Location lab = new Location(1, "Lab", "Basement lab");
        Location depot = new Location(2, "Depot", "Old rail depot");
        c.addCharacter(aris);
        c.addCharacter(mira);
        c.addLocation(lab);
        c.addLocation(depot);
        c.addEvent(new Event(1, "Last sighting", LocalDateTime.of(2026, 9, 10, 21, 30), aris, lab));
        c.addEvent(new Event(2, "Van leaves", LocalDateTime.of(2026, 9, 10, 23, 45), mira, depot));
        c.addEvent(new Event(3, "Case opened", LocalDateTime.of(2026, 9, 11, 8, 0), mira, lab));
        c.addEvidence(new Evidence(1, "Broken pocket watch", aris, lab));
        c.addEvidence(new Evidence(2, "Tyre tread cast", null, depot));
        c.addEvidence(new Evidence(3, "Handwritten note", mira, null));
        return c;
    }

    private static void testInspectEventRecordsVisitAndReturnsCaseInstance() {
        Case c = investigationCase();
        InvestigationManager inv = new InvestigationManager(c);
        Event inspected = inv.inspectEvent(2);

        check(inspected == c.findEventById(2).orElseThrow(),
                "inspectEvent returns the Case's own instance, not a copy");
        check(inv.wasEventInspected(2), "visit recorded");
        check(!inv.wasEventInspected(1), "other events not marked");
        expectThrows(() -> inv.inspectEvent(99), "No event with id 99", "inspectEvent unknown id");
    }

    private static void testInvestigateCharacterFindsEventsAndEvidence() {
        Case c = investigationCase();
        InvestigationManager inv = new InvestigationManager(c);
        InvestigationManager.CharacterInvestigation result = inv.investigateCharacter(2);

        check(result.getSubject().getName().equals("Mira"), "right subject returned");
        check(result.getEvents().size() == 2, "Mira took part in two events");
        check(result.getLinkedEvidence().size() == 1
                && result.getLinkedEvidence().get(0).getId() == 3, "note linked to Mira");
        check(inv.wasCharacterInspected(2), "character visit recorded");
        expectThrows(() -> inv.investigateCharacter(42), "No character with id 42",
                "investigateCharacter unknown id");
    }

    private static void testInvestigateLocationFindsEventsAndEvidence() {
        Case c = investigationCase();
        InvestigationManager inv = new InvestigationManager(c);
        InvestigationManager.LocationInvestigation result = inv.investigateLocation(2);

        check(result.getSubject().getName().equals("Depot"), "right subject returned");
        check(result.getEvents().size() == 1
                && result.getEvents().get(0).getId() == 2, "one event at the depot");
        check(result.getLinkedEvidence().size() == 1
                && result.getLinkedEvidence().get(0).getId() == 2, "tread cast found at the depot");
        check(inv.wasLocationInspected(2), "location visit recorded");
        expectThrows(() -> inv.investigateLocation(42), "No location with id 42",
                "investigateLocation unknown id");
    }

    private static void testCollectEvidenceIsOrderedAndUnique() {
        Case c = investigationCase();
        InvestigationManager inv = new InvestigationManager(c);

        check(inv.collectEvidence(2), "first collect accepted");
        check(!inv.collectEvidence(2), "same item cannot be collected twice");
        check(inv.collectEvidence(1), "second distinct collect accepted");

        List<Evidence> bag = inv.getCollectedEvidence();
        check(bag.size() == 2, "bag holds two clues");
        check(bag.get(0).getId() == 2 && bag.get(1).getId() == 1,
                "collection order preserved (LinkedHashSet)");
        check(inv.isCollected(2) && !inv.isCollected(3), "isCollected reflects the bag");
        check(inv.getCollectedEvidenceCount() == 2, "count matches");
        expectThrows(() -> inv.collectEvidence(99), "No evidence with id 99",
                "collectEvidence unknown id");
    }

    private static void testSearchEvidenceFindsCaseInsensitiveAndRejectsBadQueries() {
        Case c = investigationCase();
        InvestigationManager inv = new InvestigationManager(c);

        List<Evidence> hits = inv.searchEvidence("POCKET");
        check(hits.size() == 1 && hits.get(0).getId() == 1, "case-insensitive hit");
        check(inv.searchEvidence("cast").get(0).getId() == 2, "substring match works");
        check(inv.searchEvidence("   ").isEmpty(), "blank query matches nothing");
        check(inv.searchEvidence("xylophone").isEmpty(), "no match -> empty list");
        expectThrows(() -> inv.searchEvidence(null), "must not be null", "searchEvidence(null)");
    }

    private static void testAssociationsRequireCollectedEvidenceAndValidIds() {
        Case c = investigationCase();
        InvestigationManager inv = new InvestigationManager(c);

        // Associations are only allowed for collected evidence.
        expectThrows(() -> inv.associateWithSuspect(1, 2),
                "has not been collected yet", "suspect link before collecting");
        expectThrows(() -> inv.associateWithLocation(1, 1),
                "has not been collected yet", "location link before collecting");
        expectThrows(() -> inv.associateWithEvent(1, 2),
                "has not been collected yet", "event link before collecting");

        inv.collectEvidence(1);
        expectThrows(() -> inv.associateWithSuspect(1, 99), "No character with id 99",
                "suspect link to unknown character");
        expectThrows(() -> inv.associateWithLocation(1, 99), "No location with id 99",
                "location link to unknown location");
        expectThrows(() -> inv.associateWithEvent(1, 99), "No event with id 99",
                "event link to unknown event");
    }

    private static void testAssociationsWriteThroughToTheCaseAndDedupEvents() {
        Case c = investigationCase();
        InvestigationManager inv = new InvestigationManager(c);
        inv.collectEvidence(2); // tread cast: initially unlinked to any character

        inv.associateWithSuspect(2, 2); // Mira
        inv.associateWithLocation(2, 2); // Depot (already set; overwrite is fine)
        inv.associateWithEvent(2, 3);
        inv.associateWithEvent(2, 3); // duplicate must not add anything

        Evidence inCase = c.findEvidenceById(2).orElseThrow();
        check(inCase.getLinkedCharacter() != null
                && inCase.getLinkedCharacter().getId() == 2,
                "suspect association written through to the Case's own evidence");
        check(inCase.getLinkedLocation() != null
                && inCase.getLinkedLocation().getId() == 2,
                "location association written through to the Case's own evidence");
        check(inv.getLinkedEventIds(2).size() == 1 && inv.isLinkedToEvent(2, 3),
                "duplicate event links are deduplicated (HashSet)");
        check(!inv.isLinkedToEvent(2, 1), "unlinked event reports false");
        check(inv.getLinkedEventIds(1).isEmpty(), "evidence without links -> empty set");
    }

    private static void testClueBoardRendersCollectedClues() {
        Case c = investigationCase();
        InvestigationManager inv = new InvestigationManager(c);

        check(inv.renderClueBoard().size() == 1, "empty board renders just the header");

        inv.collectEvidence(1);
        inv.associateWithEvent(1, 1);
        List<String> lines = inv.renderClueBoard();
        check(lines.size() == 2, "one line per collected clue plus header");
        check(lines.get(1).contains("Broken pocket watch"), "clue description on the board");
        check(lines.get(1).contains("Aris"), "suspect name on the board");
        check(lines.get(1).contains("Lab"), "location name on the board");
        check(lines.get(1).contains("[1]"), "linked event ids on the board");
    }

    // ----- Step 8: relationship graph & contradiction detection -----

    private static Evidence linkedEvidence(int id, String description, Character actor) {
        return new Evidence(id, description, actor, null);
    }

    private static Timeline singleTimeline(int id, String label, Event... events) {
        Timeline t = new Timeline(id, label);
        for (Event e : events) {
            t.addEvent(e);
        }
        return t;
    }

    private static void testGraphStoresNodesAndTypedEdgesWithoutDuplicates() {
        RelationshipGraph g = new RelationshipGraph();
        g.addNode(RelationshipGraph.NodeType.EVIDENCE, 1);
        g.addNode(RelationshipGraph.NodeType.EVIDENCE, 1); // no-op
        g.addNode(RelationshipGraph.NodeType.CHARACTER, 7);
        g.addEdge(RelationshipGraph.NodeType.EVIDENCE, 1,
                RelationshipGraph.Relation.SUPPORTS,
                RelationshipGraph.NodeType.CHARACTER, 7);
        g.addEdge(RelationshipGraph.NodeType.EVIDENCE, 1,
                RelationshipGraph.Relation.SUPPORTS,
                RelationshipGraph.NodeType.CHARACTER, 7); // duplicate edge

        check(g.getNodeCount() == 2, "two distinct nodes registered");
        check(g.getEdgeCount() == 1, "duplicate edge stored once");
        check(g.hasNode(RelationshipGraph.NodeType.EVIDENCE, 1), "hasNode true");
        check(!g.hasNode(RelationshipGraph.NodeType.LOCATION, 1), "unknown node false");
        check(g.getEdgesTouching(RelationshipGraph.NodeType.EVIDENCE, 1).size() == 1,
                "edge visible from the evidence side");
        check(g.getEdgesTouching(RelationshipGraph.NodeType.CHARACTER, 7).size() == 1,
                "edge visible from the character side");
        check(g.getEdgesTouching(RelationshipGraph.NodeType.LOCATION, 1).isEmpty(),
                "unknown node has no edges");
    }

    private static void testGraphFindsPathAndConnectivity() {
        RelationshipGraph g = new RelationshipGraph();
        // evidence#1 -support-> event#5 -involves-> character#3 ; location#9 isolated
        g.addEdge(RelationshipGraph.NodeType.EVIDENCE, 1,
                RelationshipGraph.Relation.SUPPORTS, RelationshipGraph.NodeType.EVENT, 5);
        g.addEdge(RelationshipGraph.NodeType.EVENT, 5,
                RelationshipGraph.Relation.INVOLVES, RelationshipGraph.NodeType.CHARACTER, 3);
        g.addNode(RelationshipGraph.NodeType.LOCATION, 9);

        List<RelationshipGraph.NodeKey> path = g.findPath(
                RelationshipGraph.NodeType.EVIDENCE, 1,
                RelationshipGraph.NodeType.CHARACTER, 3);
        check(path.size() == 3, "BFS finds the three-node path");
        check(path.get(0).getType() == RelationshipGraph.NodeType.EVIDENCE
                && path.get(2).getType() == RelationshipGraph.NodeType.CHARACTER,
                "path runs evidence -> event -> character");
        check(g.areConnected(RelationshipGraph.NodeType.EVIDENCE, 1,
                RelationshipGraph.NodeType.CHARACTER, 3), "connected pair");
        check(g.findPath(RelationshipGraph.NodeType.EVIDENCE, 1,
                RelationshipGraph.NodeType.LOCATION, 9).isEmpty(),
                "isolated node unreachable");
        check(!g.areConnected(RelationshipGraph.NodeType.EVIDENCE, 1,
                RelationshipGraph.NodeType.LOCATION, 9), "not connected");
        check(g.findPath(RelationshipGraph.NodeType.EVIDENCE, 1,
                RelationshipGraph.NodeType.EVIDENCE, 1).size() == 1,
                "trivial path to self");
    }

    private static void testDetectorTimeMatchSupportsAndClashContradicts() {
        Character maya = new Character(1, "Maya", "Technician");
        Location lab = new Location(1, "Laboratory", "Basement lab");
        Case c = new Case(1, "T", "D");
        c.addCharacter(maya);
        c.addLocation(lab);

        // Timeline says: Maya entered at 10:00 (structured timestamp).
        Timeline consistent = singleTimeline(10, "Timeline A",
                new Event(1, "Maya entered the laboratory",
                        LocalDateTime.of(2026, 9, 14, 10, 0), maya, lab));
        // Timeline says: Maya left long before.
        Timeline conflicting = singleTimeline(11, "Timeline B",
                new Event(2, "Maya left the building",
                        LocalDateTime.of(2026, 9, 14, 8, 0), maya, lab));

        Evidence watch = linkedEvidence(100, "Maya entered the laboratory at 10:00", maya);
        RelationshipGraph graph = new RelationshipGraph();
        ContradictionDetector detector = new ContradictionDetector(graph);

        check(detector.classify(watch, consistent).getVerdict() == ContradictionDetector.Verdict.SUPPORTS,
                "matching time supports the timeline");
        check(detector.classify(watch, conflicting).getVerdict() == ContradictionDetector.Verdict.CONTRADICTS,
                "clashing time contradicts the timeline");
        check(detector.classify(watch, consistent).getReason().contains("10:00"),
                "reason quotes the stated time");
    }

    private static void testDetectorNegationContradictsPresence() {
        Character maya = new Character(1, "Maya", "Technician");
        Location lab = new Location(1, "Laboratory", "Basement lab");
        Case c = new Case(1, "T", "D");
        c.addCharacter(maya);
        c.addLocation(lab);

        // Timeline B asserts Maya never entered.
        Timeline deniesEntry = singleTimeline(20, "Timeline B",
                new Event(1, "Maya never entered the laboratory",
                        LocalDateTime.of(2026, 9, 14, 10, 0), maya, lab));

        RelationshipGraph graph = new RelationshipGraph();
        ContradictionDetector detector = new ContradictionDetector(graph);

        // Evidence asserts presence -> contradiction.
        ContradictionDetector.Finding f = detector.classify(
                linkedEvidence(200, "Maya entered the laboratory at 10:00", maya), deniesEntry);
        check(f.getVerdict() == ContradictionDetector.Verdict.CONTRADICTS,
                "presence evidence vs absence timeline contradicts");
        check(f.getReason().contains("absence"), "reason mentions the asserted absence");

        // Evidence asserting absence too -> supports.
        ContradictionDetector.Finding agrees = detector.classify(
                linkedEvidence(201, "Maya never entered the laboratory", maya), deniesEntry);
        check(agrees.getVerdict() == ContradictionDetector.Verdict.SUPPORTS,
                "absence evidence agrees with absence timeline");
    }

    private static void testDetectorUnlinkedEvidenceStaysNeutral() {
        Case c = new Case(1, "T", "D");
        Timeline t = singleTimeline(1, "Root");
        RelationshipGraph graph = new RelationshipGraph();
        ContradictionDetector detector = new ContradictionDetector(graph);

        ContradictionDetector.Finding unlinked = detector.classify(
                linkedEvidence(1, "Unlinked item at 10:00", null), t);
        check(unlinked.getVerdict() == ContradictionDetector.Verdict.NEUTRAL,
                "evidence without a character is neutral");

        Character lone = new Character(1, "Lone", "Stranger");
        c.addCharacter(lone);
        Timeline other = singleTimeline(2, "Other",
                new Event(5, "Lone sang a song", LocalDateTime.of(2026, 9, 14, 12, 0),
                        lone, new Location(1, "L", "D")));

        // Evidence with no stated time and no negation can never
        // produce a contradiction.
        ContradictionDetector.Finding unrelated = detector.classify(
                linkedEvidence(2, "Lone owns a red umbrella", lone), other);
        check(unrelated.getVerdict() != ContradictionDetector.Verdict.CONTRADICTS,
                "uncheckable evidence is never a contradiction");

        // But a stated time clashing with the actor's timeline event
        // is an alibi-style contradiction (documented rule 1).
        ContradictionDetector.Finding alibi = detector.classify(
                linkedEvidence(3, "Something happened at 10:00", lone), other);
        check(alibi.getVerdict() == ContradictionDetector.Verdict.CONTRADICTS,
                "stated time clashing with the actor's event contradicts");
    }

    private static void testDetectorReportSplitsVerdictsAndRenders() {
        Character maya = new Character(1, "Maya", "Technician");
        Location lab = new Location(1, "Laboratory", "Basement");
        Case c = new Case(1, "T", "D");
        c.addCharacter(maya);
        c.addLocation(lab);
        c.addEvidence(linkedEvidence(1, "Maya entered the laboratory at 10:00", maya));
        c.addEvidence(linkedEvidence(2, "Unrelated note", maya));

        Timeline consistent = singleTimeline(10, "Timeline A",
                new Event(1, "Maya entered the laboratory",
                        LocalDateTime.of(2026, 9, 14, 10, 0), maya, lab));
        Timeline denies = singleTimeline(20, "Timeline B",
                new Event(2, "Maya never entered the laboratory",
                        LocalDateTime.of(2026, 9, 14, 10, 0), maya, lab));

        RelationshipGraph graph = new RelationshipGraph();
        ContradictionDetector detector = new ContradictionDetector(graph);
        ContradictionDetector.Report report = detector.analyze(c,
                java.util.Arrays.asList(consistent, denies));

        check(report.getFindings().size() == 4, "2 evidence x 2 timelines = 4 findings");
        check(report.getContradictions().size() == 1, "exactly one contradiction (B vs entry evidence)");
        check(report.getContradictions().get(0).getTimeline().getId() == 20,
                "the contradiction targets Timeline B");
        // Entry evidence supports A; "Unrelated note" names nobody and
        // states no time, so it is neutral towards both timelines.
        check(report.getSupporting().size() == 1, "one support finding");
        check(report.render().get(0).contains("Contradictions found: 1"),
                "report leads with the contradiction count");
        check(report.render().get(report.render().size() - 1).contains("Neutral: 2"),
                "neutral items reported");
    }

    private static void testDetectorRegistersFindingsInGraph() {
        Character maya = new Character(1, "Maya", "Technician");
        Location lab = new Location(1, "Laboratory", "Basement");
        Case c = new Case(1, "T", "D");
        c.addCharacter(maya);
        c.addLocation(lab);
        c.addEvidence(linkedEvidence(1, "Maya entered the laboratory at 10:00", maya));

        Timeline consistent = singleTimeline(10, "Timeline A",
                new Event(1, "Maya entered the laboratory",
                        LocalDateTime.of(2026, 9, 14, 10, 0), maya, lab));
        Timeline denies = singleTimeline(20, "Timeline B",
                new Event(2, "Maya never entered the laboratory",
                        LocalDateTime.of(2026, 9, 14, 10, 0), maya, lab));

        RelationshipGraph graph = new RelationshipGraph();
        ContradictionDetector detector = new ContradictionDetector(graph);
        detector.analyze(c, java.util.Arrays.asList(consistent, denies));

        // All five entity kinds must appear as nodes.
        check(graph.hasNode(RelationshipGraph.NodeType.EVIDENCE, 1), "evidence node registered");
        check(graph.hasNode(RelationshipGraph.NodeType.CHARACTER, 1), "character node registered");
        check(graph.hasNode(RelationshipGraph.NodeType.LOCATION, 1), "location node registered");
        check(graph.hasNode(RelationshipGraph.NodeType.EVENT, 1), "event node registered");
        check(graph.hasNode(RelationshipGraph.NodeType.TIMELINE, 20), "timeline node registered");

        // Evidence must carry one verdict edge per analyzed timeline.
        int verdictEdges = 0;
        boolean hasContradicts = false;
        boolean hasSupports = false;
        for (RelationshipGraph.Edge e : graph.getEdgesTouching(RelationshipGraph.NodeType.EVIDENCE, 1)) {
            if (e.getRelation() == RelationshipGraph.Relation.CONTRADICTS) {
                hasContradicts = true;
                verdictEdges++;
            } else if (e.getRelation() == RelationshipGraph.Relation.SUPPORTS) {
                hasSupports = true;
                verdictEdges++;
            }
        }
        check(verdictEdges == 2, "one verdict edge per analyzed timeline");
        check(hasSupports && hasContradicts, "support and contradict edges both present");
        check(graph.areConnected(RelationshipGraph.NodeType.EVIDENCE, 1,
                RelationshipGraph.NodeType.TIMELINE, 20),
                "evidence connected to the contradictory timeline in the graph");
    }

    // ----- Step 9: mystery solver -----

    /** Fixture: Maya (present at 10:00), Reeve (never there), one lab. */
    private static Case solverCase() {
        Case c = new Case(1, "The Laboratory Entry", "Who was really there?");
        Character maya = new Character(1, "Maya", "Technician");
        Character reeve = new Character(2, "Reeve", "Director");
        Location lab = new Location(1, "Laboratory", "Restricted");
        c.addCharacter(maya);
        c.addCharacter(reeve);
        c.addLocation(lab);
        return c;
    }

    private static Timeline mayaEnteredTimeline() {
        Character maya = new Character(1, "Maya", "Technician");
        Character reeve = new Character(2, "Reeve", "Director");
        Location lab = new Location(1, "Laboratory", "Restricted");
        Timeline t = new Timeline(10, "Timeline A: Maya entered");
        t.addEvent(new Event(1, "Maya entered the laboratory",
                LocalDateTime.of(2026, 9, 14, 10, 0), maya, lab));
        t.addEvent(new Event(2, "Reeve started the experiment",
                LocalDateTime.of(2026, 9, 14, 10, 15), reeve, lab));
        return t;
    }

    private static Timeline mayaNeverEnteredTimeline() {
        Character maya = new Character(1, "Maya", "Technician");
        Character reeve = new Character(2, "Reeve", "Director");
        Location lab = new Location(1, "Laboratory", "Restricted");
        Timeline t = new Timeline(20, "Timeline B: Maya never entered");
        t.addEvent(new Event(3, "Maya never entered the laboratory",
                LocalDateTime.of(2026, 9, 14, 10, 0), maya, lab));
        t.addEvent(new Event(4, "Reeve started the experiment",
                LocalDateTime.of(2026, 9, 14, 10, 15), reeve, lab));
        return t;
    }

    private static void testSolverRanksEvidenceLinkedSuspectHighest() {
        Case c = solverCase();
        Character maya = c.findCharacterById(1).orElseThrow();
        c.addEvidence(new Evidence(1, "Keycard log: Maya entered the laboratory at 10:00", maya, null));

        MysterySolver solver = new MysterySolver();
        MysterySolver.MysteryResult result = solver.solve(c,
                java.util.Arrays.asList(mayaEnteredTimeline(), mayaNeverEnteredTimeline()));

        check(result.getCulprit().getSuspect().getId() == 1,
                "Maya, whose evidence places her there, ranks first");
        check(result.getRankings().size() == 2, "both suspects ranked");
        check(result.getRankings().get(1).getSuspect().getId() == 2, "Reeve ranks second");
        check(result.getConfidence() >= 0 && result.getConfidence() <= 100,
                "confidence within 0-100");
    }

    private static void testSolverJudgesAgainstCredibleTimelineAndReportsIt() {
        Case c = solverCase();
        Character maya = c.findCharacterById(1).orElseThrow();
        c.addEvidence(new Evidence(1, "Keycard log: Maya entered the laboratory at 10:00", maya, null));

        MysterySolver solver = new MysterySolver();
        MysterySolver.MysteryResult result = solver.solve(c,
                java.util.Arrays.asList(mayaEnteredTimeline(), mayaNeverEnteredTimeline()));

        check(result.getTimelineUsed().getId() == 10,
                "credible timeline (evidence-supported A) is chosen");
        boolean found = false;
        for (String line : result.render()) {
            if (line.contains("Timeline A")) {
                found = true;
            }
        }
        check(found, "rendered report names the timeline used");
    }

    private static void testSolverResultChangesWithEvidence() {
        MysterySolver solver = new MysterySolver();
        java.util.List<Timeline> timelines =
                java.util.Arrays.asList(mayaEnteredTimeline(), mayaNeverEnteredTimeline());

        // Run 1: evidence places Maya at the lab -> Maya is the culprit.
        Case run1 = solverCase();
        Character maya1 = run1.findCharacterById(1).orElseThrow();
        run1.addEvidence(new Evidence(1, "Keycard log: Maya entered the laboratory at 10:00", maya1, null));
        MysterySolver.MysteryResult r1 = solver.solve(run1, timelines);
        check(r1.getCulprit().getSuspect().getId() == 1, "with entry evidence, Maya is implicated");

        // Run 2: only Reeve's false testimony exists -> Reeve implicated.
        Case run2 = solverCase();
        Character reeve2 = run2.findCharacterById(2).orElseThrow();
        run2.addEvidence(new Evidence(2, "Reeve forged the entry log at 10:00", reeve2, null));
        MysterySolver.MysteryResult r2 = solver.solve(run2, timelines);
        check(r2.getCulprit().getSuspect().getId() == 2,
                "with forged-log evidence, Reeve is implicated instead");

        // The verdict must differ between the two runs.
        check(r1.getCulprit().getSuspect().getId() != r2.getCulprit().getSuspect().getId(),
                "the result depends on the evidence, nothing hard-coded");
    }

    private static void testSolverExplainsScoresAndSplitsEvidence() {
        Case c = solverCase();
        Character maya = c.findCharacterById(1).orElseThrow();
        Character reeve = c.findCharacterById(2).orElseThrow();
        c.addEvidence(new Evidence(1, "Keycard log: Maya entered the laboratory at 10:00", maya, null));
        c.addEvidence(new Evidence(2, "Maya never entered the laboratory", reeve, null));
        // A stated time that clashes with Maya's 10:00 event on timeline A.
        c.addEvidence(new Evidence(3, "Maya's watch stopped at 09:00", maya, null));

        MysterySolver solver = new MysterySolver();
        MysterySolver.MysteryResult result = solver.solve(c,
                java.util.Arrays.asList(mayaEnteredTimeline(), mayaNeverEnteredTimeline()));

        // Explanations: every reason must carry its signed points.
        for (MysterySolver.SuspectScore s : result.getRankings()) {
            int sum = 0;
            for (MysterySolver.ScoreReason r : s.getReasons()) {
                sum += r.getPoints();
            }
            check(sum == s.getTotal(), "reasons add up to the total for " + s.getSuspect().getName());
        }

        // Evidence split: keycard supports the credible timeline, the
        // watch's 09:00 clashes with Maya's 10:00 event on it.
        check(!result.getSupportingEvidence().isEmpty(), "supporting evidence reported");
        check(!result.getContradictions().isEmpty(), "contradictions reported");
        check(result.getContradictions().get(0).getEvidence().getId() == 3,
                "the watch is the contradicting item");

        // Reeve's denial (linked to him, asserts Maya's absence) must not
        // give Reeve an alibi - the alibi rule checks the suspect's name.
        for (MysterySolver.ScoreReason r : result.getRankings().get(1).getReasons()) {
            check(!r.getExplanation().contains("alibi"),
                    "Reeve gains no alibi from a denial about Maya");
        }
    }

    private static void testSolverRejectsEmptyCases() {
        MysterySolver solver = new MysterySolver();
        expectThrows(() -> solver.solve(new Case(1, "T", "D"),
                java.util.Arrays.asList(mayaEnteredTimeline())),
                "without characters", "solve without characters");
        Case c = solverCase();
        expectThrows(() -> solver.solve(c, java.util.Collections.emptyList()),
                "without timelines", "solve without timelines");
        expectThrows(() -> solver.solve(null, java.util.Arrays.asList(mayaEnteredTimeline())),
                "must not be null", "solve(null case)");
    }

    // ----- Step 10: file I/O -----

    private static void testSaveLoadRoundTripPreservesEverything() throws IOException {
        Case original = investigationCase();
        Evidence item1 = original.findEvidenceById(1).orElseThrow();
        Evidence item3 = original.findEvidenceById(3).orElseThrow();
        item1.setPriority(model.EvidencePriority.CRITICAL);

        InvestigationManager session = new InvestigationManager(original);
        session.collectEvidence(1);
        session.collectEvidence(3);

        FileManager fm = new FileManager();
        java.nio.file.Path file = java.nio.file.Files.createTempFile("chronocase", ".txt");
        try {
            fm.saveCase(original, file, new java.util.LinkedHashSet<>(session.getCollectedEvidence()));

            FileManager.LoadResult loaded = fm.loadCase(file);
            Case restored = loaded.getCase();

            check(restored.getId() == 1, "case id survived");
            check(restored.getTitle().equals("The Vanishing"), "title survived");
            check(restored.getCharacters().size() == 2, "characters survived");
            check(restored.getLocations().size() == 2, "locations survived");
            check(restored.getEvents().size() == 3, "events survived");
            check(restored.getEvidence().size() == 3, "evidence survived");
            check(restored.findEvidenceById(1).orElseThrow().getPriority()
                    == model.EvidencePriority.CRITICAL, "priority survived");
            check(restored.findEvidenceById(2).orElseThrow().getLinkedCharacter() == null
                    && restored.findEvidenceById(2).orElseThrow().getLinkedLocation() != null,
                    "partial evidence links survived");
            check(restored.findEventById(2).orElseThrow().getCharacter().getId() == 2,
                    "event associations survived");
            check(loaded.getCollectedEvidence().size() == 2, "collected evidence survived");
            check(loaded.getCollectedEvidence().stream().mapToInt(Evidence::getId)
                    .summaryStatistics().getMin() == 1, "collected evidence ids correct");
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    private static void testSaveLoadRoundTripWithBranchedTimelines() throws IOException {
        Case original = investigationCase();
        Character mira = original.findCharacterById(2).orElseThrow();
        Location depot = original.findLocationById(2).orElseThrow();

        Timeline root = new Timeline(1, "Root");
        root.addEvent(new Event(1, "Last sighting", LocalDateTime.of(2026, 9, 10, 21, 30),
                original.findCharacterById(1).orElseThrow(),
                original.findLocationById(1).orElseThrow()));
        Timeline branch = root.createBranch(2, "Alternate");
        branch.addEvent(new Event(1, "Revised sighting",
                LocalDateTime.of(2026, 9, 10, 22, 0), mira, depot));
        root.createBranch(3, "Empty branch");
        original.addTimeline(root);

        FileManager fm = new FileManager();
        java.nio.file.Path file = java.nio.file.Files.createTempFile("chronocase-tl", ".txt");
        try {
            fm.saveCase(original, file);
            Case restored = fm.loadCase(file).getCase();

            check(restored.getTimelines().size() == 1, "one root timeline restored");
            Timeline restoredRoot = restored.getTimelines().get(0);
            check(restoredRoot.getId() == 1 && restoredRoot.getLabel().equals("Root"),
                    "root identity survived");
            check(restoredRoot.getBranches().size() == 2, "both branches restored");
            check(restoredRoot.getEvents().size() == 1
                    && restoredRoot.getEvents().get(0).getDescription().equals("Last sighting"),
                    "root event survived");
            Timeline restoredBranch = restoredRoot.getBranches().get(0);
            check(restoredBranch.getLabel().equals("Alternate"), "branch label survived");
            check(restoredBranch.getEvents().get(0).getDescription().equals("Revised sighting"),
                    "divergent branch event survived");
            check(restoredRoot.getEvents().get(0)
                    != restoredBranch.getEvents().get(0),
                    "branch event is an independent copy, not a shared instance");
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    private static void testFileManagerEscapesSpecialCharacters() throws IOException {
        Case original = new Case(1, "Case | with pipes \\ and \\ backslashes",
                "Description with | pipes | everywhere");
        Character c = new Character(1, "Name | with pipe", "Role \\ escaped");
        Location l = new Location(1, "Place | piped", "Desc");
        original.addCharacter(c);
        original.addLocation(l);
        original.addEvent(new Event(1, "Event | desc \\ here",
                LocalDateTime.of(2026, 9, 14, 10, 0), c, l));

        FileManager fm = new FileManager();
        java.nio.file.Path file = java.nio.file.Files.createTempFile("chronocase-esc", ".txt");
        try {
            fm.saveCase(original, file);
            Case restored = fm.loadCase(file).getCase();
            check(restored.getTitle().equals(original.getTitle()), "pipes and backslashes in title survive");
            check(restored.getDescription().equals(original.getDescription()), "escaped description survives");
            check(restored.findCharacterById(1).orElseThrow().getName().equals("Name | with pipe"),
                    "escaped name survives");
            check(restored.findEventById(1).orElseThrow().getDescription().equals("Event | desc \\ here"),
                    "escaped event description survives");
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    private static void testFileManagerReportsMissingFileAndBadHeader() {
        FileManager fm = new FileManager();
        expectThrows(() -> fm.loadCase(java.nio.file.Path.of("no-such-file-"
                + java.util.UUID.randomUUID() + ".txt")),
                "File not found", "missing file");

        try {
            java.nio.file.Path bad = java.nio.file.Files.createTempFile("bad-header", ".txt");
            java.nio.file.Files.writeString(bad, "NOT-CHRONOCASE\n");
            try {
                expectThrows(() -> fm.loadCase(bad), "Not a ChronoCase data file", "bad header");
            } finally {
                java.nio.file.Files.deleteIfExists(bad);
            }
        } catch (java.io.IOException e) {
            throw new AssertionError("temp file setup failed", e);
        }
    }

    private static void testFileManagerRejectsUnknownReferencesAndRecords() throws IOException {
        FileManager fm = new FileManager();
        try {
            java.nio.file.Path bad = java.nio.file.Files.createTempFile("bad-ref", ".txt");
            java.nio.file.Files.writeString(bad, String.join("\n",
                    "CHRONOCASE-V1",
                    "CASE|1|T|D",
                    "EVENT|1|2026-09-14T10:00|desc|99|1",
                    ""));
            try {
                expectThrows(() -> fm.loadCase(bad), "unknown character id 99", "unknown character ref");
            } finally {
                java.nio.file.Files.deleteIfExists(bad);
            }

            java.nio.file.Path unknownTag = java.nio.file.Files.createTempFile("bad-tag", ".txt");
            java.nio.file.Files.writeString(unknownTag, String.join("\n",
                    "CHRONOCASE-V1",
                    "CASE|1|T|D",
                    "MYSTERY|1|X",
                    ""));
            try {
                expectThrows(() -> fm.loadCase(unknownTag), "unknown record type", "unknown record");
            } finally {
                java.nio.file.Files.deleteIfExists(unknownTag);
            }
        } catch (java.io.IOException e) {
            throw new AssertionError("temp file setup failed", e);
        }
    }
}
