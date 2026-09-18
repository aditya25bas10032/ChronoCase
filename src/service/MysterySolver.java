package service;

import model.Case;
import model.Character;
import model.Evidence;
import model.EvidencePriority;
import model.Event;
import model.Timeline;

import service.ContradictionDetector.Finding;
import service.ContradictionDetector.Verdict;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Transparent, rule-based scoring of who the culprit most likely is.
 * No machine learning: every point of suspicion comes from an
 * explainable rule, and every suspect's total is broken down into
 * reasons that can be printed.
 *
 * Inputs the solver weighs:
 * - collected evidence linked to a suspect,
 * - the suspect's role in the events of the timeline,
 * - priority of the evidence (CRITICAL weighs more than LOW),
 * - contradictions: evidence that clashes with the chosen timeline
 *   turns that evidence into a point FOR the linked suspect (the
 *   timeline is what the culprit wants us to believe), and evidence
 *   whose text asserts the suspect's absence counts as an alibi
 *   point AGAINST them.
 *
 * The scoring is relative: the strongest suspect of one evidence set
 * can be irrelevant in another, nothing is hard-coded.
 */
public class MysterySolver {

    /** Tunable rule weights, kept public so scenarios stay transparent. */
    public static final int POINTS_LINKED_EVIDENCE = 2;
    public static final int POINTS_PRIORITY_BONUS = 1;   // per priority rank below LOW
    public static final int POINTS_ACTED_AT_MOMENT = 3;  // acted in the decisive event
    public static final int POINTS_ACTED_ELSEWHERE = 1;  // acted in other events
    public static final int POINTS_CONTRADICTED_TIMELINE = 4; // evidence vs chosen timeline
    public static final int POINTS_ALIBI = -5;           // evidence asserts their absence

    /** Selects the timeline the investigation is judged against. */
    private final TimelineSelector timelineSelector;
    /** Shared, reused contradiction machinery (Step 8). */
    private final ContradictionDetector detector;

    public MysterySolver() {
        this(new DefaultTimelineSelector());
    }

    public MysterySolver(TimelineSelector selector) {
        this.timelineSelector = Objects.requireNonNull(selector, "timeline selector must not be null");
        this.detector = new ContradictionDetector(new RelationshipGraph());
    }

    // ----- scoring explanation -----

    /** One explainable reason why a suspect gained or lost points. */
    public static final class ScoreReason {
        private final int points;
        private final String explanation;

        ScoreReason(int points, String explanation) {
            this.points = points;
            this.explanation = explanation;
        }

        /** Signed contribution to the suspect's total. */
        public int getPoints() {
            return points;
        }

        public String getExplanation() {
            return explanation;
        }

        @Override
        public String toString() {
            return (points >= 0 ? "+" : "") + points + " " + explanation;
        }
    }

    /** A scored suspect: total plus the reasons behind it. */
    public static final class SuspectScore {
        private final Character suspect;
        private final List<ScoreReason> reasons;
        private final int total;

        SuspectScore(Character suspect, List<ScoreReason> reasons) {
            this.suspect = suspect;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
            int sum = 0;
            for (ScoreReason r : reasons) {
                sum += r.getPoints();
            }
            this.total = sum;
        }

        public Character getSuspect() {
            return suspect;
        }

        /** Why this suspect scored as they did, in rule order. */
        public List<ScoreReason> getReasons() {
            return reasons;
        }

        public int getTotal() {
            return total;
        }
    }

    /** The final result of one solve attempt. */
    public static final class MysteryResult implements io.TextReportable {
        private final Case kase;
        private final Timeline timeline;
        private final List<SuspectScore> rankings;
        private final List<Finding> supporting;
        private final List<Finding> contradictions;
        private final int confidence;
        private final String narrative;

        MysteryResult(Case kase, Timeline timeline, List<SuspectScore> rankings,
                      List<Finding> supporting, List<Finding> contradictions,
                      int confidence, String narrative) {
            this.kase = kase;
            this.timeline = timeline;
            this.rankings = Collections.unmodifiableList(rankings);
            this.supporting = Collections.unmodifiableList(supporting);
            this.contradictions = Collections.unmodifiableList(contradictions);
            this.confidence = confidence;
            this.narrative = narrative;
        }

        public Case getCase() {
            return kase;
        }

        /** The timeline the verdict was judged against. */
        public Timeline getTimelineUsed() {
            return timeline;
        }

        /** All suspects, best first. */
        public List<SuspectScore> getRankings() {
            return rankings;
        }

        /** The most likely culprit. */
        public SuspectScore getCulprit() {
            return rankings.get(0);
        }

        /** Evidence that supports the chosen timeline and implicates the culprit. */
        public List<Finding> getSupportingEvidence() {
            return supporting;
        }

        /** Evidence that clashes with the chosen timeline. */
        public List<Finding> getContradictions() {
            return contradictions;
        }

        /** 0-100, how strongly the culprit stands out from the runner-up. */
        public int getConfidence() {
            return confidence;
        }

        /** One-paragraph verdict, assembled from the actual scores. */
        public String getNarrative() {
            return narrative;
        }

        /** Human-readable full report; also the TextReportable export. */
        public List<String> renderReport() {
            List<String> lines = new ArrayList<>();
            lines.add("Verdict: " + getCulprit().getSuspect().getName()
                    + " (confidence " + confidence + "%)");
            lines.add("Judged against timeline: #" + timeline.getId()
                    + " '" + timeline.getLabel() + "'");
            lines.add("Suspicion ranking:");
            int place = 1;
            for (SuspectScore s : rankings) {
                lines.add("  " + place++ + ". " + s.getSuspect().getName()
                        + " -> " + s.getTotal() + " points");
                for (ScoreReason r : s.getReasons()) {
                    lines.add("       " + r);
                }
            }
            lines.add("Supporting evidence (" + supporting.size() + "):");
            for (Finding f : supporting) {
                lines.add("  ok " + f);
            }
            lines.add("Contradictions (" + contradictions.size() + "):");
            for (Finding f : contradictions) {
                lines.add("  !! " + f);
            }
            lines.add("Summary: " + narrative);
            return lines;
        }

        /** Compatibility alias for earlier steps' callers. */
        public List<String> render() {
            return renderReport();
        }
    }

    /**
     * Solves the case against the selected timeline. Suspects are
     * every character in the case; the winner is the highest total.
     * Throws if the case has no characters or no timeline.
     */
    public MysteryResult solve(Case kase, Collection<Timeline> timelines,
                               Set<Evidence> collectedEvidence) {
        Objects.requireNonNull(kase, "case must not be null");
        Objects.requireNonNull(timelines, "timelines must not be null");
        if (kase.getCharacters().isEmpty()) {
            throw new IllegalStateException("Cannot solve a case without characters");
        }
        if (timelines.isEmpty()) {
            throw new IllegalStateException("Cannot solve a case without timelines");
        }

        // 1. Pick the timeline to judge everything against.
        Timeline timeline = timelineSelector.select(kase, timelines);

        // 2. Classify every evidence item against that timeline once.
        List<Finding> findings = new ArrayList<>();
        for (Evidence item : evidenceToScore(kase, collectedEvidence)) {
            findings.add(detector.classify(item, timeline, kase.getCharacters()));
        }

        // 3. The decisive event: the earliest non-negation event on the
        //    timeline - the moment the mystery turns. Acting there is
        //    the heaviest single circumstance.
        Event decisive = findDecisiveEvent(timeline);

        // 4. Score every suspect with plain, explainable rules.
        Map<Integer, SuspectScore> scores = new LinkedHashMap<>();
        for (Character suspect : kase.getCharacters()) {
            scores.put(suspect.getId(), scoreSuspect(suspect, kase, timeline,
                    decisive, findings, collectedEvidence));
        }

        // 5. Rank: highest total first, ties broken by lower id for
        //    determinism (same facts must produce the same verdict).
        List<SuspectScore> ranked = new ArrayList<>(scores.values());
        ranked.sort(Comparator.comparingInt(SuspectScore::getTotal).reversed()
                .thenComparingInt(s -> s.getSuspect().getId()));

        SuspectScore culprit = ranked.get(0);
        int confidence = computeConfidence(ranked);
        String narrative = buildNarrative(kase, timeline, culprit, ranked, findings, decisive);

        List<Finding> supporting = new ArrayList<>();
        List<Finding> contradictions = new ArrayList<>();
        for (Finding f : findings) {
            if (f.getVerdict() == Verdict.SUPPORTS) {
                supporting.add(f);
            } else if (f.getVerdict() == Verdict.CONTRADICTS) {
                contradictions.add(f);
            }
        }

        return new MysteryResult(kase, timeline, ranked, supporting, contradictions,
                confidence, narrative);
    }

    /** Convenience overload: score all case evidence. */
    public MysteryResult solve(Case kase, Collection<Timeline> timelines) {
        return solve(kase, timelines, java.util.Collections.emptySet());
    }

    // ----- rules -----

    private SuspectScore scoreSuspect(Character suspect, Case kase, Timeline timeline,
                                      Event decisive, List<Finding> findings,
                                      Set<Evidence> collected) {
        List<ScoreReason> reasons = new ArrayList<>();
        String name = suspect.getName().toLowerCase(Locale.ROOT);

        // Rule A: evidence linked to this suspect.
        for (Evidence item : kase.getEvidence()) {
            if (item.getLinkedCharacter() != null
                    && item.getLinkedCharacter().getId() == suspect.getId()) {
                int points = POINTS_LINKED_EVIDENCE + priorityBonus(item);
                String source = collected.contains(item) ? "collected" : "case-file";
                reasons.add(new ScoreReason(points, "for " + source + " evidence #"
                        + item.getId() + " (priority " + item.getPriority() + "): "
                        + item.getDescription()));
            }
        }

        // Rule B: where did the suspect act on the timeline?
        boolean actedAtMoment = false;
        boolean actedElsewhere = false;
        for (Event e : timeline.getEvents()) {
            if (e.getCharacter().getId() != suspect.getId()
                    || ContradictionDetector.textAssertsAbsence(e.getDescription())) {
                continue;
            }
            if (decisive != null && e.getId() == decisive.getId()) {
                actedAtMoment = true;
            } else {
                actedElsewhere = true;
            }
        }
        if (actedAtMoment) {
            reasons.add(new ScoreReason(POINTS_ACTED_AT_MOMENT,
                    "acted in the decisive event #" + decisive.getId()
                    + " ('" + decisive.getDescription() + "')"));
        }
        if (actedElsewhere) {
            reasons.add(new ScoreReason(POINTS_ACTED_ELSEWHERE,
                    "acted in other timeline events"));
        }

        // Rule C: contradictions against the chosen timeline incriminate
        // the suspect they are linked to - the false story exists to
        // hide something about them.
        for (Finding f : findings) {
            if (f.getVerdict() == Verdict.CONTRADICTS
                    && f.getEvidence().getLinkedCharacter() != null
                    && f.getEvidence().getLinkedCharacter().getId() == suspect.getId()) {
                reasons.add(new ScoreReason(POINTS_CONTRADICTED_TIMELINE,
                        "their evidence #" + f.getEvidence().getId()
                        + " contradicts the accepted timeline: " + f.getReason()));
            }
        }

        // Rule D: alibi - evidence whose text asserts this suspect was
        // not there lowers their suspicion.
        for (Evidence item : kase.getEvidence()) {
            String text = item.getDescription().toLowerCase(Locale.ROOT);
            if (text.contains(name) && ContradictionDetector.textAssertsAbsence(item.getDescription())) {
                reasons.add(new ScoreReason(POINTS_ALIBI,
                        "evidence #" + item.getId() + " asserts they were absent (alibi)"));
            }
        }

        return new SuspectScore(suspect, reasons);
    }

    private static int priorityBonus(Evidence item) {
        int rankBelowLow = EvidencePriority.LOW.getRank() - item.getPriority().getRank();
        return Math.max(0, rankBelowLow) * POINTS_PRIORITY_BONUS;
    }

    /**
     * The decisive event is the earliest event on the timeline whose
     * text does not assert an absence - the first thing that actually
     * happened. Returns null when no such event exists.
     */
    private static Event findDecisiveEvent(Timeline timeline) {
        Event best = null;
        for (Event e : timeline.getEvents()) {
            if (ContradictionDetector.textAssertsAbsence(e.getDescription())) {
                continue;
            }
            if (best == null || e.getTimestamp().isBefore(best.getTimestamp())) {
                best = e;
            }
        }
        return best;
    }

    /**
     * Confidence: how far the culprit is ahead of the runner-up,
     * scaled against the best possible margin and clamped to 0-100.
     */
    private static int computeConfidence(List<SuspectScore> ranked) {
        if (ranked.size() == 1) {
            return ranked.get(0).getTotal() > 0 ? 100 : 0;
        }
        int best = ranked.get(0).getTotal();
        int second = ranked.get(1).getTotal();
        if (best <= 0) {
            return 0;
        }
        int margin = best - second;
        return Math.min(100, margin * 20);
    }

    private static String buildNarrative(Case kase, Timeline timeline, SuspectScore culprit,
                                         List<SuspectScore> ranked, List<Finding> findings,
                                         Event decisive) {
        StringBuilder sb = new StringBuilder();
        sb.append("Judging the collected facts against '").append(timeline.getLabel())
          .append("', ").append(culprit.getSuspect().getName())
          .append(" accumulates the most suspicion (").append(culprit.getTotal())
          .append(" points)");
        if (decisive != null) {
            sb.append(", primarily for acting at the decisive moment (")
              .append(decisive.getDescription()).append(")");
        }
        sb.append(".");
        int contradictionCount = 0;
        for (Finding f : findings) {
            if (f.getVerdict() == Verdict.CONTRADICTS) {
                contradictionCount++;
            }
        }
        sb.append(" The evidence set holds ").append(contradictionCount)
          .append(" contradiction(s) against this timeline");
        if (ranked.size() > 1) {
            sb.append("; the runner-up is ").append(ranked.get(1).getSuspect().getName())
              .append(" with ").append(ranked.get(1).getTotal()).append(" points");
        }
        sb.append(".");
        return sb.toString();
    }

    /** Evidence to classify: collected items if any, else the whole case file. */
    private static List<Evidence> evidenceToScore(Case kase, Set<Evidence> collected) {
        if (collected == null || collected.isEmpty()) {
            return kase.getEvidence();
        }
        // Only collected evidence has been examined; the rest is noise.
        List<Evidence> out = new ArrayList<>();
        for (Evidence item : kase.getEvidence()) {
            if (collected.contains(item)) {
                out.add(item);
            }
        }
        return out;
    }

    // ----- timeline selection -----

    /** Strategy: which timeline does the verdict get judged against? */
    public interface TimelineSelector {
        Timeline select(Case kase, Collection<Timeline> timelines);
    }

    /**
     * Default: the timeline that the most evidence SUPPORTS (and the
     * fewest contradict) is treated as closest to the truth - the
     * story the facts agree with.
     */
    public static final class DefaultTimelineSelector implements TimelineSelector {
        @Override
        public Timeline select(Case kase, Collection<Timeline> timelines) {
            Timeline best = null;
            int bestScore = Integer.MIN_VALUE;
            ContradictionDetector d = new ContradictionDetector(new RelationshipGraph());
            for (Timeline t : timelines) {
                int score = 0;
                for (Evidence item : kase.getEvidence()) {
                    Verdict v = d.classify(item, t, kase.getCharacters()).getVerdict();
                    if (v == Verdict.SUPPORTS) {
                        score++;
                    } else if (v == Verdict.CONTRADICTS) {
                        score--;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = t;
                }
            }
            return best;
        }
    }
}
