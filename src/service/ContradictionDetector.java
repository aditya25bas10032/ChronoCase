package service;

import model.Case;
import model.Character;
import model.Evidence;
import model.Event;
import model.Location;
import model.Timeline;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a piece of evidence SUPPORTS a timeline,
 * CONTRADICTS it, or is NEUTRAL towards it - and registers every
 * finding as typed edges in a {@link RelationshipGraph}.
 *
 * Detection rules (deliberately simple and explainable):
 * 1. Time rule: if the evidence description states a clock time
 *    (e.g. "10:00") and contains no negation, and its linked
 *    character acts in an event on the timeline, matching times
 *    support and clashing times contradict the timeline. A stated
 *    time inside a negated clause ("testimony: X never entered at
 *    10:00") is unreliable, so the time rule is skipped then.
 * 2. Presence rule: evidence that mentions its linked character by
 *    name asserts their presence - unless the negation in the text
 *    is attributed to them (the closest character name before the
 *    negation word). Evidence asserting presence contradicts a
 *    timeline that only asserts the character's absence, and vice
 *    versa; agreeing sides support each other.
 * 3. Anything else stays neutral: no invented conclusions.
 *
 * The relation direction is always evidence -> timeline, so the
 * clue is what "speaks" about the timeline.
 */
public class ContradictionDetector {

    /** Verdict of one evidence item towards one timeline. */
    public enum Verdict {
        SUPPORTS, CONTRADICTS, NEUTRAL
    }

    /** One evidence-vs-timeline classification. */
    public static final class Finding {
        private final Evidence evidence;
        private final Timeline timeline;
        private final Verdict verdict;
        private final String reason;

        Finding(Evidence evidence, Timeline timeline, Verdict verdict, String reason) {
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

        public Verdict getVerdict() {
            return verdict;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return "Evidence #" + evidence.getId() + " " + verdict + " '"
                    + timeline.getLabel() + "': " + reason;
        }
    }

    /** Full report across every evidence item in a case. */
    public static final class Report {
        private final List<Finding> findings;

        Report(List<Finding> findings) {
            this.findings = Collections.unmodifiableList(findings);
        }

        public List<Finding> getFindings() {
            return findings;
        }

        /** Only the contradictions, in report order. */
        public List<Finding> getContradictions() {
            return byVerdict(Verdict.CONTRADICTS);
        }

        /** Only the supporting evidence, in report order. */
        public List<Finding> getSupporting() {
            return byVerdict(Verdict.SUPPORTS);
        }

        private List<Finding> byVerdict(Verdict verdict) {
            List<Finding> out = new ArrayList<>();
            for (Finding f : findings) {
                if (f.getVerdict() == verdict) {
                    out.add(f);
                }
            }
            return out;
        }

        /** Human-readable, contradiction-first rendering of the report. */
        public List<String> render() {
            List<String> lines = new ArrayList<>();
            List<Finding> contradictions = getContradictions();
            lines.add("Contradictions found: " + contradictions.size());
            for (Finding f : contradictions) {
                lines.add("  !! " + f);
            }
            lines.add("Supporting evidence: " + getSupporting().size());
            for (Finding f : getSupporting()) {
                lines.add("  ok " + f);
            }
            int neutral = findings.size() - contradictions.size() - getSupporting().size();
            lines.add("Neutral: " + neutral + " evidence item(s)");
            return lines;
        }
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    /** Matches clock times like 09:30, 10:00 inside a description. */
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
    /** Matches "X never ..." / "X did not ..." / "X never entered ...". */
    private static final Pattern NEGATION_PATTERN =
            Pattern.compile("\\bnever\\b|\\bdid not\\b|\\bdidn't\\b|\\bdoes not\\b|\\bdoesn't\\b|\\bnot present\\b");

    private final RelationshipGraph graph;

    public ContradictionDetector(RelationshipGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
    }

    /** The graph this detector writes its findings into. */
    public RelationshipGraph getGraph() {
        return graph;
    }

    /**
     * Classifies every evidence item in the case against every
     * timeline in the given collection and registers SUPPORTS /
     * CONTRADICTS / LINKED_TO edges in the graph.
     */
    public Report analyze(Case kase, Collection<Timeline> timelines) {
        Objects.requireNonNull(kase, "case must not be null");
        Objects.requireNonNull(timelines, "timelines must not be null");

        // Register all five entity kinds as graph nodes first.
        registerNodes(kase, timelines);

        List<Finding> findings = new ArrayList<>();
        for (Evidence item : kase.getEvidence()) {
            for (Timeline timeline : timelines) {
                Finding finding = classify(item, timeline, kase.getCharacters());
                findings.add(finding);
                graph.addEdge(RelationshipGraph.NodeType.EVIDENCE, item.getId(),
                        finding.getVerdict() == Verdict.CONTRADICTS
                                ? RelationshipGraph.Relation.CONTRADICTS
                                : finding.getVerdict() == Verdict.SUPPORTS
                                        ? RelationshipGraph.Relation.SUPPORTS
                                        : RelationshipGraph.Relation.LINKED_TO,
                        RelationshipGraph.NodeType.TIMELINE, timeline.getId());
            }
        }
        return new Report(findings);
    }

    private void registerNodes(Case kase, Collection<Timeline> timelines) {
        for (Character c : kase.getCharacters()) {
            graph.addNode(RelationshipGraph.NodeType.CHARACTER, c.getId());
        }
        for (Location l : kase.getLocations()) {
            graph.addNode(RelationshipGraph.NodeType.LOCATION, l.getId());
        }
        for (Event e : kase.getEvents()) {
            registerEvent(e);
        }
        for (Evidence item : kase.getEvidence()) {
            graph.addNode(RelationshipGraph.NodeType.EVIDENCE, item.getId());
        }
        for (Timeline t : timelines) {
            graph.addNode(RelationshipGraph.NodeType.TIMELINE, t.getId());
            for (Event e : t.getEvents()) {
                registerEvent(e);
                graph.addEdge(RelationshipGraph.NodeType.TIMELINE, t.getId(),
                        RelationshipGraph.Relation.CONTAINS,
                        RelationshipGraph.NodeType.EVENT, e.getId());
            }
        }
    }

    private void registerEvent(Event e) {
        graph.addNode(RelationshipGraph.NodeType.EVENT, e.getId());
        graph.addNode(RelationshipGraph.NodeType.CHARACTER, e.getCharacter().getId());
        graph.addNode(RelationshipGraph.NodeType.LOCATION, e.getLocation().getId());
        graph.addEdge(RelationshipGraph.NodeType.EVENT, e.getId(),
                RelationshipGraph.Relation.INVOLVES,
                RelationshipGraph.NodeType.CHARACTER, e.getCharacter().getId());
        graph.addEdge(RelationshipGraph.NodeType.EVENT, e.getId(),
                RelationshipGraph.Relation.OCCURRED_AT,
                RelationshipGraph.NodeType.LOCATION, e.getLocation().getId());
    }

    /**
     * Classifies one evidence item against one timeline without
     * touching the graph. Uses only the evidence's own text for
     * attribution; use the three-argument overload (or analyze) when
     * the case's full character list is available.
     */
    public Finding classify(Evidence item, Timeline timeline) {
        return classify(item, timeline, java.util.Collections.emptyList());
    }

    /**
     * Classifies one evidence item against one timeline, using the
     * case's character names to attribute negations in the text to
     * the right person.
     */
    public Finding classify(Evidence item, Timeline timeline, java.util.Collection<Character> knownCharacters) {
        if (item.getLinkedCharacter() == null) {
            return new Finding(item, timeline, Verdict.NEUTRAL,
                    "evidence is not linked to any character");
        }
        String evidenceText = item.getDescription().toLowerCase(Locale.ROOT);
        Character actor = item.getLinkedCharacter();
        String actorName = actor.getName().toLowerCase(Locale.ROOT);

        Integer statedTime = firstStatedTime(evidenceText);
        boolean anyNegation = NEGATION_PATTERN.matcher(evidenceText).find();
        boolean evidenceMentionsActor = evidenceText.contains(actorName);
        boolean evidenceAssertsActorAbsence = anyNegation && negationTargets(
                evidenceText, actorName, namesOf(knownCharacters), evidenceMentionsActor);

        // Collect what the timeline says about this character: every
        // event kind they appear in, with their times.
        List<Integer> actorAbsenceTimes = new ArrayList<>();
        List<Integer> actorPresenceTimes = new ArrayList<>();
        for (Event e : timeline.getEvents()) {
            boolean sameActor = e.getCharacter().getId() == actor.getId();
            if (!sameActor) {
                continue;
            }
            String eventText = e.getDescription().toLowerCase(Locale.ROOT);
            Integer stated = firstStatedTime(eventText);
            int structuredTime = e.getTimestamp().getHour() * 60 + e.getTimestamp().getMinute();
            int eventTime = stated != null ? stated : structuredTime;
            if (NEGATION_PATTERN.matcher(eventText).find()) {
                actorAbsenceTimes.add(eventTime);
            } else {
                actorPresenceTimes.add(eventTime);
            }
        }

        // Rule 1: stated clock times, evidence vs timeline event.
        // Skipped when the evidence contains a negation: a time inside
        // a negated clause does not assert the event happened then.
        // The comparison uses the actor's event CLOSEST IN TIME to the
        // stated time, so a character with several entries in a day is
        // judged against the entry the evidence refers to.
        if (statedTime != null && !anyNegation
                && (!actorPresenceTimes.isEmpty() || !actorAbsenceTimes.isEmpty())) {
            List<Integer> allTimes = new ArrayList<>(actorPresenceTimes);
            allTimes.addAll(actorAbsenceTimes);
            int nearest = allTimes.stream()
                    .min((a, b) -> Integer.compare(Math.abs(a - statedTime),
                            Math.abs(b - statedTime)))
                    .orElseThrow();
            if (actorAbsenceTimes.contains(nearest)
                    && !actorPresenceTimes.contains(nearest)) {
                return new Finding(item, timeline, Verdict.CONTRADICTS, "evidence places "
                        + actor.getName() + " there at " + minutesToText(statedTime)
                        + ", but the timeline asserts their absence at "
                        + minutesToText(nearest));
            }
            if (nearest == statedTime) {
                return new Finding(item, timeline, Verdict.SUPPORTS, "stated time "
                        + minutesToText(statedTime) + " matches the timeline event");
            }
            return new Finding(item, timeline, Verdict.CONTRADICTS, "stated time "
                    + minutesToText(statedTime) + " clashes with the timeline's "
                    + minutesToText(nearest));
        }

        boolean actorAbsentSomewhere = !actorAbsenceTimes.isEmpty();
        boolean actorPresent = !actorPresenceTimes.isEmpty();

        // Rule 2: presence vs asserted absence (e.g. "X never entered").
        // Only evidence that names its linked character asserts anything
        // about their presence; a merely linked item asserts nothing.
        if (evidenceMentionsActor && !evidenceAssertsActorAbsence
                && actorAbsentSomewhere && !actorPresent) {
            return new Finding(item, timeline, Verdict.CONTRADICTS, "evidence places "
                    + actor.getName() + " there, but the timeline asserts their absence");
        }
        if (evidenceMentionsActor && !evidenceAssertsActorAbsence && actorPresent) {
            return new Finding(item, timeline, Verdict.SUPPORTS, "timeline contains "
                    + actor.getName() + " acting as the evidence describes");
        }
        if (evidenceAssertsActorAbsence && actorPresent) {
            return new Finding(item, timeline, Verdict.CONTRADICTS, "evidence denies "
                    + actor.getName() + " was there, but the timeline shows them acting");
        }
        if (evidenceAssertsActorAbsence && actorAbsentSomewhere) {
            return new Finding(item, timeline, Verdict.SUPPORTS, "evidence and timeline "
                    + "both assert the absence of " + actor.getName());
        }

        return new Finding(item, timeline, Verdict.NEUTRAL,
                "no time or presence information to compare");
    }

    /**
     * True if the given text contains a negation word ("never",
     * "did not", ...). Reused by the MysterySolver for its alibi rule.
     */
    public static boolean textAssertsAbsence(String text) {
        return text != null
                && NEGATION_PATTERN.matcher(text.toLowerCase(Locale.ROOT)).find();
    }

    private static List<String> namesOf(java.util.Collection<Character> characters) {
        List<String> names = new ArrayList<>();
        for (Character c : characters) {
            names.add(c.getName().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    /**
     * True if a negation in the text is attributed to the actor: the
     * closest known character name before the negation word is the
     * actor's. With no known names before it, fall back to "the actor
     * is mentioned at all".
     */
    private static boolean negationTargets(String text, String actorName,
                                           List<String> knownNames, boolean actorMentioned) {
        Matcher negation = NEGATION_PATTERN.matcher(text);
        while (negation.find()) {
            int pos = negation.start();
            String closest = null;
            int closestPos = -1;
            for (String name : knownNames) {
                int idx = text.lastIndexOf(name, Math.max(pos - 1, 0));
                if (idx >= 0 && (closest == null || idx > closestPos)) {
                    closest = name;
                    closestPos = idx;
                }
            }
            if (closest == null) {
                if (actorMentioned) {
                    return true;
                }
            } else if (closest.equals(actorName)) {
                return true;
            }
        }
        return false;
    }

    /** First "HH:mm" in the text as minutes since midnight, or null. */
    private Integer firstStatedTime(String text) {
        Matcher m = TIME_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        if (hour > 23 || minute > 59) {
            return null;
        }
        return hour * 60 + minute;
    }

    private String minutesToText(int minutes) {
        return TIME_FORMAT.format(LocalDateTime.of(2026, 9, 15, minutes / 60, minutes % 60));
    }

    /** Convenience: classify against a whole timeline family. */
    public Report analyze(Case kase, Timeline... timelines) {
        return analyze(kase, java.util.Arrays.asList(timelines));
    }
}
