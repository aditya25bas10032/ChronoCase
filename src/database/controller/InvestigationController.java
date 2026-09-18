package controller;

import io.FileManager;
import model.Case;
import model.Character;
import model.Evidence;
import model.Event;
import model.Location;
import model.Timeline;
import service.ContradictionDetector;
import service.InvestigationManager;
import service.MysterySolver;
import service.RelationshipGraph;
import service.TimelineManager;
import service.TimelineNavigator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * One GUI session's controller: the single seam between the JavaFX
 * view and the business logic. Every screen action goes through this
 * class, so button handlers stay free of model and service calls.
 *
 * Owns and coordinates the services per opened case:
 * - {@link InvestigationManager}: inspect/collect/associate session
 *   (also the single source of truth for collected evidence)
 * - {@link TimelineNavigator}: time-travel history over the case's
 *   own timeline tree - the controller deliberately keeps no second
 *   tree, so every branch created here is part of the Case and
 *   therefore persisted by save/load
 * - {@link ContradictionDetector}/{@link MysterySolver} on demand:
 *   comparison and verdict results
 *
 * Failures surface as {@link ControllerException}; the view never
 * sees IOException, model construction or service plumbing.
 */
public class InvestigationController {

    /** The error the view knows about; message is user-presentable. */
    public static final class ControllerException extends RuntimeException {
        public ControllerException(String message) {
            super(message);
        }

        ControllerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** What one timeline comparison produced. */
    public static final class ComparisonResult {
        private final Timeline first;
        private final Timeline second;
        private final List<String> changedEvents;
        private final List<String> contradictions;

        ComparisonResult(Timeline first, Timeline second,
                         List<String> changedEvents, List<String> contradictions) {
            this.first = first;
            this.second = second;
            this.changedEvents = Collections.unmodifiableList(changedEvents);
            this.contradictions = Collections.unmodifiableList(contradictions);
        }

        public Timeline getFirst() {
            return first;
        }

        public Timeline getSecond() {
            return second;
        }

        /** Events that differ between the two timelines, one line each. */
        public List<String> getChangedEvents() {
            return changedEvents;
        }

        /** Evidence-vs-timeline contradictions on either side. */
        public List<String> getContradictions() {
            return contradictions;
        }
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Builds the "Missing Prototype" scenario into this case:
     * suspects, locations, the 08:00-10:45 event sequence, evidence
     * and the root timeline. Called right after
     * {@code newCase(...) + addRootTimeline(1, "Root")} by the New
     * Investigation flow, keeping all scenario data in the controller
     * layer (never hard-coded in the view).
     */
    public void buildMissingPrototypeContent() {
        controller.MissingPrototypeCase.populate(kase);
    }

    private final Case kase;
    private final InvestigationManager investigation;
    private final MysterySolver solver = new MysterySolver();
    private final ContradictionDetector detector = new ContradictionDetector(new RelationshipGraph());

    /** Where the investigator currently is on the case's timeline tree. */
    private Timeline current;
    private TimelineNavigator navigator;

    /** The event the investigator last travelled to on the current timeline. */
    private Integer currentEventId;

    /**
     * Starts a session over the given case. If the case carries
     * timelines (e.g. loaded from a file or the database), the first
     * root becomes the starting point of the investigation.
     */
    public InvestigationController(Case kase) {
        this.kase = kase;
        this.investigation = new InvestigationManager(kase);
        if (!kase.getTimelines().isEmpty()) {
            current = kase.getTimelines().get(0);
            navigator = new TimelineNavigator(current);
        }
    }

    /**
     * Starts a session over the given case with a given starting
     * timeline (used when a saved file names the branch the
     * investigation was on). Falls back to the first root when the
     * id is unknown to this case.
     */
    public InvestigationController(Case kase, int startTimelineId) {
        this(kase);
        Timeline start = findTimelineById(startTimelineId);
        if (start != null) {
            current = start;
            navigator = new TimelineNavigator(start);
        }
    }

    // ----- investigation time (current event) -----

    /**
     * The event marking the investigator's position in time on the
     * current timeline; empty when no position has been chosen yet.
     */
    public Optional<Event> getCurrentEvent() {
        if (currentEventId == null || current == null) {
            return Optional.empty();
        }
        return current.getEvents().stream()
                .filter(e -> e.getId() == currentEventId)
                .findFirst();
    }

    /** Records the investigator's position in time on the current timeline. */
    public void setCurrentEvent(int eventId) {
        this.currentEventId = eventId;
    }

    /** Forgets the investigator's position in time (e.g. after switching timelines). */
    public void clearCurrentEvent() {
        this.currentEventId = null;
    }

    /** The id of the current event, or -1 when no position is chosen. */
    public int getCurrentEventId() {
        return currentEventId == null ? -1 : currentEventId;
    }

    /** The case under investigation. */
    public Case getCase() {
        return kase;
    }

    // ----- dashboard data -----

    public List<Character> getSuspects() {
        return kase.getCharacters();
    }

    public List<Location> getLocations() {
        return kase.getLocations();
    }

    public List<Evidence> getEvidence() {
        return kase.getEvidence();
    }

    /** The case's master events (independent of any timeline), chronological. */
    public List<Event> getCaseEvents() {
        List<Event> events = new ArrayList<>(kase.getEvents());
        events.sort(Comparator.comparing(Event::getTimestamp)
                .thenComparing(Event::getId));
        return events;
    }

    public int countEvidenceForCharacter(int characterId) {
        int count = 0;
        for (Evidence item : kase.getEvidence()) {
            if (item.getLinkedCharacter() != null
                    && item.getLinkedCharacter().getId() == characterId) {
                count++;
            }
        }
        return count;
    }

    public int countEventsAtLocation(int locationId) {
        int count = 0;
        for (Event e : kase.getEvents()) {
            if (e.getLocation().getId() == locationId) {
                count++;
            }
        }
        return count;
    }

    public int countEvidenceAtLocation(int locationId) {
        int count = 0;
        for (Evidence item : kase.getEvidence()) {
            if (item.getLinkedLocation() != null
                    && item.getLinkedLocation().getId() == locationId) {
                count++;
            }
        }
        return count;
    }

    // ----- evidence session -----

    /** Collects a clue; returns false when it was already in the bag. */
    public boolean collectEvidence(int evidenceId) {
        return investigation.collectEvidence(evidenceId);
    }

    public boolean isCollected(int evidenceId) {
        return investigation.isCollected(evidenceId);
    }

    public int getCollectedCount() {
        return investigation.getCollectedEvidenceCount();
    }

    /** The collected clues, in collection order, as a read-only set. */
    public Set<Evidence> getCollectedEvidence() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(investigation.getCollectedEvidence()));
    }

    public List<Evidence> searchEvidence(String query) {
        return investigation.searchEvidence(query);
    }

    public void associateWithSuspect(int evidenceId, int suspectId) {
        try {
            investigation.associateWithSuspect(evidenceId, suspectId);
        } catch (IllegalStateException | NoSuchElementException e) {
            throw new ControllerException(e.getMessage());
        }
    }

    public void associateWithLocation(int evidenceId, int locationId) {
        try {
            investigation.associateWithLocation(evidenceId, locationId);
        } catch (IllegalStateException | NoSuchElementException e) {
            throw new ControllerException(e.getMessage());
        }
    }

    public void associateWithEvent(int evidenceId, int eventId) {
        try {
            investigation.associateWithEvent(evidenceId, eventId);
        } catch (IllegalStateException | NoSuchElementException e) {
            throw new ControllerException(e.getMessage());
        }
    }

    public Set<Integer> getLinkedEventIds(int evidenceId) {
        return investigation.getLinkedEventIds(evidenceId);
    }

    // ----- timeline views -----

    /** All timelines of the case, depth-first (root before branches). */
    public List<Timeline> getTimelines() {
        List<Timeline> all = new ArrayList<>();
        for (Timeline root : kase.getTimelines()) {
            collectSubtree(root, all);
        }
        return Collections.unmodifiableList(all);
    }

    private static void collectSubtree(Timeline timeline, List<Timeline> out) {
        out.add(timeline);
        for (Timeline branch : timeline.getBranches()) {
            collectSubtree(branch, out);
        }
    }

    /** The timeline the investigator is on; null before the first root. */
    public Timeline getCurrentTimeline() {
        return current;
    }

    /** Makes another timeline of this case the current one (history kept). */
    public void switchToTimeline(int timelineId) {
        Timeline target = findTimelineById(timelineId);
        if (target == null) {
            throw new ControllerException("No timeline with id " + timelineId);
        }
        if (target == current) {
            return;
        }
        travelThroughNavigator(target);
        clearCurrentEvent(); // the new timeline owns its own events
    }

    /** One line per timeline, indented to show the branch tree. */
    public List<String> renderHierarchy() {
        return describeBranches();
    }

    /**
     * The current timeline's events, chronological, one line each.
     * Branch events that revise their parent's version are marked.
     */
    public List<String> renderCurrentTimeline() {
        if (current == null) {
            return List.of("No timeline yet.");
        }
        List<Event> events = getTimelineEvents(current);
        List<String> lines = new ArrayList<>();
        lines.add("Timeline #" + current.getId() + " '" + current.getLabel()
                + "' (depth " + current.getDepth() + ", " + events.size() + " events)");
        for (Event e : events) {
            lines.add(timestamp(e) + "  #" + e.getId() + "  " + e.getDescription()
                    + "  [" + e.getCharacter().getName() + " @ " + e.getLocation().getName() + "]"
                    + (revisesParentEvent(current, e) ? "   (revises the parent's event)" : ""));
        }
        return lines;
    }

    private boolean revisesParentEvent(Timeline timeline, Event event) {
        Timeline parent = timeline.getParent();
        if (parent == null) {
            return false;
        }
        for (Event parentEvent : parent.getEvents()) {
            if (parentEvent.getId() == event.getId()) {
                return !parentEvent.getDescription().equals(event.getDescription())
                        || !parentEvent.getTimestamp().equals(event.getTimestamp());
            }
        }
        return false;
    }

    /**
     * Compact lines for the branch map: root first, one line per
     * timeline, indented by depth, with the current one marked.
     */
    public List<String> describeBranches() {
        List<String> lines = new ArrayList<>();
        for (Timeline t : getTimelines()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < t.getDepth(); i++) {
                sb.append("   ");
            }
            sb.append(t.isRoot() ? "[root] " : "+--- ");
            sb.append("#").append(t.getId()).append(" '").append(t.getLabel()).append("'");
            sb.append(" - ").append(t.getEventCount()).append(" events");
            if (t == current) {
                sb.append("  (current)");
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    // ----- time travel -----

    /** The event to revise, looked up in the current timeline. */
    public Event findEvent(int eventId) {
        if (current == null) {
            throw new ControllerException("No timeline exists yet");
        }
        return current.getEvents().stream()
                .filter(e -> e.getId() == eventId)
                .findFirst()
                .orElseThrow(() -> new ControllerException(
                        "The current timeline has no event with id " + eventId));
    }

    /**
     * Travels back to the point of the given event: prefers the
     * current timeline, otherwise jumps to whichever timeline owns
     * it (navigation history is kept). Returns the event travelled to.
     */
    public Event travelTo(int eventId) {
        if (current == null) {
            throw new ControllerException("No timeline exists yet");
        }
        Event onCurrent = findIn(current, eventId);
        if (onCurrent != null) {
            setCurrentEvent(eventId);
            return onCurrent;
        }
        for (Timeline t : getTimelines()) {
            if (t == current) {
                continue;
            }
            Event hit = findIn(t, eventId);
            if (hit != null) {
                travelThroughNavigator(t);
                clearCurrentEvent();
                setCurrentEvent(eventId);
                return hit;
            }
        }
        throw new ControllerException("No event with id " + eventId + " on any timeline");
    }

    /**
     * Makes a choice in the past: branches the current timeline and
     * replaces the event on the fresh branch, which becomes current.
     * The original timeline is NOT modified (core rule) - and because
     * the branch belongs to the case's own tree, it is persisted.
     */
    public Timeline reviseEvent(int eventId, String newDescription,
                                LocalDateTime newTimestamp) {
        if (newDescription == null || newDescription.isBlank()) {
            throw new ControllerException("The revised event needs a description");
        }
        if (newTimestamp == null) {
            throw new ControllerException("The revised event needs a time");
        }
        Event original = findEvent(eventId);
        int branchId = nextTimelineId();
        try {
            Timeline branch = current.createBranch(branchId,
                    "Alternate " + branchId + " (revised event " + eventId + ")");
            branch.replaceEvent(eventId, new Event(original.getId(), newDescription.trim(),
                    newTimestamp, original.getCharacter(), original.getLocation()));
            travelThroughNavigator(branch);
            setCurrentEvent(eventId);
            return branch;
        } catch (IllegalArgumentException e) {
            throw new ControllerException(e.getMessage());
        }
    }

    /**
     * The TimelineManager that mirrors this case's timeline tree,
     * built fresh on demand. The manager is the Step 5 service API
     * (time travel, alternate timelines, hierarchy); the case's own
     * tree stays the single source of truth, so the mirror is rebuilt
     * whenever the caller needs service-side operations.
     */
    public TimelineManager buildTimelineManager() {
        TimelineManager manager = new TimelineManager();
        Timeline serviceRoot = null;
        for (Timeline root : kase.getTimelines()) {
            serviceRoot = manager.createRoot(root.getId(), root.getLabel());
            copyTimelineEvents(root, serviceRoot);
            copyBranches(root, serviceRoot, manager);
        }
        if (current != null) {
            Timeline target = manager.getTimelineById(current.getId());
            // Rebuild the navigation path: the ancestry from the root
            // down to the investigation's current timeline becomes the
            // navigator's history, so returnToPreviousTimeline walks
            // back the same way the real session would.
            List<Timeline> ancestry = new ArrayList<>();
            for (Timeline t = current; t != null; t = t.getParent()) {
                ancestry.add(t);
            }
            java.util.Collections.reverse(ancestry);
            TimelineNavigator nav = new TimelineNavigator(
                    manager.getTimelineById(ancestry.get(0).getId()));
            for (int i = 1; i < ancestry.size(); i++) {
                nav.travelTo(manager.getTimelineById(ancestry.get(i).getId()), LocalDateTime.now());
            }
            manager.setNavigator(nav);
            manager.switchTo(target);
        }
        return manager;
    }

    private static void copyBranches(Timeline modelParent, Timeline serviceParent,
                                     TimelineManager manager) {
        for (Timeline child : modelParent.getBranches()) {
            Timeline serviceChild = manager.createBranch(child.getId(), child.getLabel(), serviceParent);
            copyTimelineEvents(child, serviceChild);
            copyBranches(child, serviceChild, manager);
        }
    }

    private static void copyTimelineEvents(Timeline from, Timeline to) {
        for (Event e : from.getEvents()) {
            to.addEvent(new Event(e));
        }
    }

    /** Steps back through the navigation history; false at the start. */
    public boolean returnToPreviousTimeline() {
        if (navigator == null) {
            return false;
        }
        return navigator.returnToPreviousTimeline().isPresent();
    }

    public int getHistoryDepth() {
        return navigator == null ? 0 : navigator.getHistoryDepth();
    }

    /** Describes an event as shown in the travel picker. */
    public String describeEventChoice(Event e) {
        return "#" + e.getId() + "  " + timestamp(e) + "  " + e.getDescription();
    }

    /** The given timeline's events in picker order (chronological). */
    public List<Event> getTimelineEvents(Timeline timeline) {
        List<Event> events = new ArrayList<>(timeline.getEvents());
        events.sort(Comparator.comparing(Event::getTimestamp).thenComparing(Event::getId));
        return events;
    }

    // ----- timeline comparison -----

    /**
     * Compares two timelines: which events changed and which evidence
     * contradicts which of the two. Pure service calls, no state.
     */
    public ComparisonResult compareTimelines(Timeline first, Timeline second) {
        List<String> changed = new ArrayList<>();
        TreeSet<Integer> allIds = new TreeSet<>();
        for (Event e : first.getEvents()) {
            allIds.add(e.getId());
        }
        for (Event e : second.getEvents()) {
            allIds.add(e.getId());
        }
        for (int id : allIds) {
            Event a = findIn(first, id);
            Event b = findIn(second, id);
            if (a == null && b != null) {
                changed.add("only on '" + second.getLabel() + "': #" + id
                        + " " + timestamp(b) + " " + b.getDescription());
            } else if (a != null && b == null) {
                changed.add("only on '" + first.getLabel() + "': #" + id
                        + " " + timestamp(a) + " " + a.getDescription());
            } else if (a != null && !a.getDescription().equals(b.getDescription())) {
                changed.add("#" + id + " on '" + first.getLabel() + "': " + a.getDescription()
                        + "   ->   on '" + second.getLabel() + "': " + b.getDescription());
            }
        }

        List<String> contradictions = new ArrayList<>();
        for (Evidence item : kase.getEvidence()) {
            ContradictionDetector.Finding f1 = detector.classify(item, first, kase.getCharacters());
            ContradictionDetector.Finding f2 = detector.classify(item, second, kase.getCharacters());
            if (f1.getVerdict() == ContradictionDetector.Verdict.CONTRADICTS) {
                contradictions.add("against '" + first.getLabel() + "': " + f1.getReason()
                        + " (evidence #" + item.getId() + ")");
            }
            if (f2.getVerdict() == ContradictionDetector.Verdict.CONTRADICTS) {
                contradictions.add("against '" + second.getLabel() + "': " + f2.getReason()
                        + " (evidence #" + item.getId() + ")");
            }
        }
        return new ComparisonResult(first, second, changed, contradictions);
    }

    private static Event findIn(Timeline timeline, int eventId) {
        for (Event e : timeline.getEvents()) {
            if (e.getId() == eventId) {
                return e;
            }
        }
        return null;
    }

    // ----- inspection session (delegates to InvestigationManager) -----

    /**
     * Interviews a suspect: their events and linked evidence. The
     * visit is recorded in the session's progress.
     */
    public InvestigationManager.CharacterInvestigation investigateCharacter(int characterId) {
        try {
            return investigation.investigateCharacter(characterId);
        } catch (NoSuchElementException e) {
            throw new ControllerException(e.getMessage());
        }
    }

    /** Surveys a location: its events and its evidence. */
    public InvestigationManager.LocationInvestigation investigateLocation(int locationId) {
        try {
            return investigation.investigateLocation(locationId);
        } catch (NoSuchElementException e) {
            throw new ControllerException(e.getMessage());
        }
    }

    /** Studies one event; the visit is recorded in the session progress. */
    public Event inspectEvent(int eventId) {
        try {
            return investigation.inspectEvent(eventId);
        } catch (NoSuchElementException e) {
            throw new ControllerException(e.getMessage());
        }
    }

    /** True if the suspect has been interviewed in this session. */
    public boolean wasCharacterInspected(int characterId) {
        return investigation.wasCharacterInspected(characterId);
    }

    /** True if the location has been surveyed in this session. */
    public boolean wasLocationInspected(int locationId) {
        return investigation.wasLocationInspected(locationId);
    }

    /** True if the event has been inspected in this session. */
    public boolean wasEventInspected(int eventId) {
        return investigation.wasEventInspected(eventId);
    }

    /** The collected clues in collection order (delegates to the session). */
    public List<Evidence> getCollectedEvidenceList() {
        return investigation.getCollectedEvidence();
    }

    // ----- contradiction report (ContradictionDetector on demand) -----

    /** One evidence-vs-timeline verdict, ready to display. */
    public static final class FindingRow {
        private final Evidence evidence;
        private final Timeline timeline;
        private final ContradictionDetector.Verdict verdict;
        private final String reason;

        FindingRow(Evidence evidence, Timeline timeline,
                   ContradictionDetector.Verdict verdict, String reason) {
            this.evidence = evidence;
            this.timeline = timeline;
            this.verdict = verdict;
            this.reason = reason;
        }

        public Evidence getEvidence() {
            return evidence;
        }

        public Timeline getTimeline() {
            return timeline;
        }

        public ContradictionDetector.Verdict getVerdict() {
            return verdict;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Runs the ContradictionDetector over the given timelines and
     * returns every finding as displayable rows.
     */
    public List<FindingRow> runContradictionCheck(List<Timeline> timelines) {
        List<FindingRow> rows = new ArrayList<>();
        for (Timeline timeline : timelines) {
            for (Evidence item : kase.getEvidence()) {
                ContradictionDetector.Finding finding =
                        detector.classify(item, timeline, kase.getCharacters());
                rows.add(new FindingRow(item, timeline,
                        finding.getVerdict(), finding.getReason()));
            }
        }
        return rows;
    }

    /** Convenience: check only the current timeline. */
    public List<FindingRow> runContradictionCheckOnCurrent() {
        requireCurrent("run a contradiction check on");
        return runContradictionCheck(List.of(current));
    }

    // ----- comparison (per-event diff) -----

    /** One event position compared across two timelines. */
    public static final class EventDiff {
        private final int eventId;
        private final Event first;
        private final Event second;

        EventDiff(int eventId, Event first, Event second) {
            this.eventId = eventId;
            this.first = first;
            this.second = second;
        }

        public int getEventId() {
            return eventId;
        }

        public Event getFirst() {
            return first;
        }

        public Event getSecond() {
            return second;
        }

        /** True when both timelines carry the event and the text matches. */
        public boolean isIdentical() {
            return first != null && second != null
                    && first.getDescription().equals(second.getDescription());
        }

        /** True when only one side has the event. */
        public boolean isMissingOnOneSide() {
            return first == null || second == null;
        }
    }

    /**
     * Per-event diff of two timelines, ordered by event id - the
     * structured counterpart to {@link #compareTimelines}.
     */
    public List<EventDiff> diffTimelines(Timeline first, Timeline second) {
        TreeSet<Integer> allIds = new TreeSet<>();
        for (Event e : first.getEvents()) {
            allIds.add(e.getId());
        }
        for (Event e : second.getEvents()) {
            allIds.add(e.getId());
        }
        List<EventDiff> diffs = new ArrayList<>();
        for (int id : allIds) {
            diffs.add(new EventDiff(id, findIn(first, id), findIn(second, id)));
        }
        return diffs;
    }

    // ----- investigation time -----

    /**
     * Runs the solver over the session's collected clues (or the
     * whole case file when nothing has been collected yet).
     */
    public MysterySolver.MysteryResult solveCase() {
        try {
            return solver.solve(kase, getTimelines(), getCollectedEvidence());
        } catch (IllegalStateException e) {
            throw new ControllerException(e.getMessage());
        }
    }

    /** The full verdict as text lines (also what gets exported). */
    public List<String> renderVerdict(MysterySolver.MysteryResult result) {
        return result.renderReport();
    }

    // ----- persistence -----

    /** Saves the case (and collected clues) to a CHRONOCASE-V1 file. */
    public void saveCase(Path file) {
        try {
            new FileManager().saveCase(kase, file, getCollectedEvidence(),
                    current == null ? 0 : current.getId());
        } catch (IOException e) {
            throw new ControllerException("Could not save the case: " + e.getMessage(), e);
        }
    }

    /**
     * Saves the case to MySQL via DatabaseManager (schema is created
     * if missing). Returns true when an existing case was updated.
     */
    public boolean saveCaseToDatabase() {
        database.DatabaseManager db = new database.DatabaseManager();
        db.createSchema();
        List<Integer> collectedIds = getCollectedEvidenceList().stream()
                .map(Evidence::getId)
                .collect(Collectors.toList());
        return db.saveCase(kase, collectedIds);
    }

    /**
     * Loads a case from MySQL. Returns null when no saved case with
     * the given id exists (the view decides how to react).
     */
    public static InvestigationController loadCaseFromDatabase(int caseId) {
        database.DatabaseManager db = new database.DatabaseManager();
        if (!db.caseExists(caseId)) {
            return null;
        }
        database.DatabaseManager.LoadResult loaded = db.loadCaseWithCollected(caseId);
        InvestigationController session = new InvestigationController(loaded.getCase());
        for (int evidenceId : loaded.getCollectedEvidenceIds()) {
            session.collectEvidence(evidenceId);
        }
        return session;
    }

    /** Lists every case id saved in MySQL (empty when the server is unreachable). */
    public static List<Integer> listDatabaseCaseIds() {
        try {
            return new database.DatabaseManager().listCaseIds();
        } catch (database.DatabaseManager.DataAccessException e) {
            return List.of();
        }
    }

    /**
     * Loads a case from a CHRONOCASE-V1 file. BUGFIX: the collected
     * clues from the file are now restored into the new session - the
     * old version returned only the Case and silently dropped the bag.
     */
    public static InvestigationController loadCase(Path file) {
        try {
            FileManager.LoadResult loaded = new FileManager().loadCase(file);
            InvestigationController session = new InvestigationController(loaded.getCase(),
                    loaded.getCurrentTimelineId());
            for (Evidence item : loaded.getCollectedEvidence()) {
                session.collectEvidence(item.getId());
            }
            return session;
        } catch (IOException e) {
            throw new ControllerException("Could not load the case: " + e.getMessage(), e);
        }
    }

    // ----- creating case content -----

    /** Builds an empty case for a new investigation. */
    public static Case newCase(int id, String title, String description) {
        if (title == null || title.isBlank()) {
            throw new ControllerException("The case needs a title");
        }
        return new Case(id, title.trim(), description == null || description.isBlank()
                ? "No description." : description.trim());
    }

    public void addCharacter(Character c) {
        kase.addCharacter(c);
    }

    public void addLocation(Location l) {
        kase.addLocation(l);
    }

    public void addEvent(Event e) {
        kase.addEvent(e);
    }

    public void addEvidence(Evidence item) {
        kase.addEvidence(item);
    }

    /**
     * Creates the case's root timeline and starts the navigator on
     * it. Fails politely when a root already exists.
     */
    public void addRootTimeline(int id, String label) {
        if (current != null) {
            throw new ControllerException("A root timeline already exists ("
                    + current.getLabel() + "); branch instead of adding another root");
        }
        Timeline root = new Timeline(id, label);
        kase.addTimeline(root);
        current = root;
        navigator = new TimelineNavigator(root);
    }

    /** Branches the current timeline; the branch becomes current. */
    public Timeline addBranchOfCurrent(String label) {
        requireCurrent("branch");
        if (label == null || label.isBlank()) {
            throw new ControllerException("The branch needs a label");
        }
        Timeline branch = current.createBranch(nextTimelineId(), label.trim());
        travelThroughNavigator(branch);
        return branch;
    }

    /** Adds an event to the current timeline. */
    public void addEventToTimeline(Event event) {
        requireCurrent("add an event to");
        current.addEvent(event);
    }

    /** True when a timeline exists and events can be added to it. */
    public boolean hasCurrentTimeline() {
        return current != null;
    }

    /** The total number of timelines in the case (roots plus branches). */
    public int getTimelineCount() {
        return getTimelines().size();
    }

    // ----- id helpers -----

    /** Smallest unused positive character id, for "add suspect". */
    public int nextCharacterId() {
        return nextId(kase.getCharacters().stream().mapToInt(Character::getId));
    }

    /** Smallest unused positive location id, for "add location". */
    public int nextLocationId() {
        return nextId(kase.getLocations().stream().mapToInt(Location::getId));
    }

    /** Smallest unused positive event id, for "add event". */
    public int nextEventId() {
        return nextId(kase.getEvents().stream().mapToInt(Event::getId));
    }

    /** Smallest unused positive evidence id, for "add evidence". */
    public int nextEvidenceId() {
        return nextId(kase.getEvidence().stream().mapToInt(Evidence::getId));
    }

    /** Smallest unused positive timeline id, for "new branch/root". */
    public int nextTimelineId() {
        return nextId(getTimelines().stream().mapToInt(Timeline::getId));
    }

    /** Smallest unused positive case id, for "new investigation". */
    public static int suggestCaseId() {
        return (int) (System.currentTimeMillis() % 100000);
    }

    // ----- helpers -----

    private void requireCurrent(String what) {
        if (current == null) {
            throw new ControllerException("Create a timeline first to " + what + " it");
        }
    }

    /** Moves to the given timeline, recording the visit in the history. */
    private void travelThroughNavigator(Timeline target) {
        if (navigator == null) {
            navigator = new TimelineNavigator(target);
        } else if (target != navigator.getCurrent()) {
            navigator.travelTo(target, LocalDateTime.now());
        }
        current = target;
    }

    private Timeline findTimelineById(int timelineId) {
        for (Timeline t : getTimelines()) {
            if (t.getId() == timelineId) {
                return t;
            }
        }
        return null;
    }

    private static int nextId(java.util.stream.IntStream usedIds) {
        Set<Integer> used = usedIds.boxed()
                .collect(java.util.stream.Collectors.toSet());
        int candidate = 1;
        while (used.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private String timestamp(Event e) {
        return e.getTimestamp().format(TIME_FORMAT);
    }
}
