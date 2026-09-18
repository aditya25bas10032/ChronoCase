import controller.InvestigationController;
import controller.MissingPrototypeCase;
import io.FileManager;
import model.Case;
import model.Event;
import model.Timeline;
import service.ContradictionDetector;
import service.InvestigationManager;
import service.MysterySolver;
import service.TimelineManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Headless re-enactment of the demo workflow for CASE-001 - the
 * seeded "Missing Prototype" investigation. Every step calls exactly
 * the controller/service methods the GUI handlers call; the GUI is a
 * thin layer over this path. Exits non-zero on the first failed step.
 */
public class GuiFlowTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("chronocase-guitest", ".txt");
        try {
            // 1-2. Start ChronoCase -> New Investigation (same order as
            // the fixed GUI: populate the case first, then construct
            // the controller over it).
            model.Case caseData = InvestigationController.newCase(
                    InvestigationController.suggestCaseId(),
                    MissingPrototypeCase.TITLE,
                    MissingPrototypeCase.DESCRIPTION);
            MissingPrototypeCase.populate(caseData);
            InvestigationController controller = new InvestigationController(caseData);

            // 3-4. Open Missing Prototype -> dashboard data present
            Case kase = controller.getCase();
            check("dashboard events count = 13", controller.getCaseEvents().size() == 13);
            check("case has 4 suspects", kase.getCharacters().size() == 4);
            check("case has 5 locations", kase.getLocations().size() == 5);
            check("case has 8 evidence items", kase.getEvidence().size() == 8);
            check("4 timelines seeded (ROOT, A, B, C)",
                    controller.getTimelineCount() == 4);
            Timeline root = controller.getTimelines().get(0);
            check("ROOT holds all 13 original events", root.getEventCount() == 13);
            check("ROOT has two children (TIMELINE-A and TIMELINE-B)",
                    root.getBranches().size() == 2);
            Timeline timelineC = controller.getTimelines().get(3);
            check("TIMELINE-C is a branch of TIMELINE-B (depth 3)",
                    timelineC.getParent().getId() == 3 && timelineC.getDepth() == 3);
            check("investigation starts on ROOT",
                    controller.getCurrentTimeline().getId() == 1);

            // 5. Open Timeline -> the canonical sequence is on ROOT
            List<Event> rootEvents = controller.getTimelineEvents(root);
            check("ROOT event at 09:40 is the camera going offline",
                    rootEvents.stream()
                            .filter(e -> e.getTimestamp().getHour() == 9
                                    && e.getTimestamp().getMinute() == 40)
                            .allMatch(e -> e.getDescription().contains("offline")));
            check("ROOT still contains 'Maya enters Prototype Lab' at 10:00",
                    rootEvents.stream()
                            .filter(e -> e.getId() == 7)
                            .allMatch(e -> e.getDescription().equals("Maya enters Prototype Lab")
                                    && e.getTimestamp().getMinute() == 0
                                    && e.getTimestamp().getHour() == 10));

            // 6-7. Select the 09:40 event -> travel back to it
            Event cameraEvent = rootEvents.stream()
                    .filter(e -> e.getTimestamp().getHour() == 9 && e.getTimestamp().getMinute() == 40)
                    .findFirst().orElseThrow();
            Event traveled = controller.travelTo(cameraEvent.getId());
            check("travelTo lands on the 09:40 event", traveled.getId() == cameraEvent.getId());
            check("investigation time is now the 09:40 event",
                    controller.getCurrentEvent()
                            .map(e -> e.getId() == cameraEvent.getId()).orElse(false));

            // 8-9. Change the event -> alternate timeline created
            Timeline userBranch = controller.reviseEvent(cameraEvent.getId(),
                    "Security camera keeps recording",
                    cameraEvent.getTimestamp());
            check("branch created and became current",
                    userBranch == controller.getCurrentTimeline());
            check("branch revises event 6",
                    userBranch.getEvents().stream()
                            .filter(e -> e.getId() == cameraEvent.getId())
                            .allMatch(e -> e.getDescription()
                                    .equals("Security camera keeps recording")));
            check("ORIGINAL ROOT UNCHANGED (core invariant)",
                    rootEvents.stream()
                            .filter(e -> e.getId() == cameraEvent.getId())
                            .allMatch(e -> e.getDescription().contains("offline")));

            // 10-11. Return to timeline view -> five timelines now exist
            check("5 timelines exist after the user's change",
                    controller.getTimelineCount() == 5);
            check("user branch is a child of ROOT",
                    userBranch.getParent().getId() == 1
                    && root.getBranches().contains(userBranch));

            // 12. Switch between them
            controller.switchToTimeline(1);
            check("switch back to ROOT", controller.getCurrentTimeline().isRoot());
            check("investigation time cleared after switch",
                    controller.getCurrentEvent().isEmpty());
            controller.switchToTimeline(userBranch.getId());
            check("switch to the user branch again",
                    controller.getCurrentTimeline() == userBranch);
            controller.switchToTimeline(timelineC.getId());
            check("switch to TIMELINE-C",
                    controller.getCurrentTimeline().getId() == 4);

            // 13. Compare ROOT vs TIMELINE-C
            List<InvestigationController.EventDiff> diffs =
                    controller.diffTimelines(root, timelineC);
            check("diff lists all 13 event positions", diffs.size() == 13);
            check("exactly 2 events differ (09:40 camera, 10:00 Maya entry)",
                    diffs.stream().filter(d -> !d.isIdentical()).count() == 2
                    && diffs.stream().anyMatch(d -> d.getEventId() == 6 && !d.isIdentical())
                    && diffs.stream().anyMatch(d -> d.getEventId() == 7 && !d.isIdentical()));
            InvestigationController.ComparisonResult comparison = controller.compareTimelines(
                    root, timelineC);
            check("comparison produced changed-event lines",
                    !comparison.getChangedEvents().isEmpty());

            // 14. Inspect evidence (interview, survey, collect, search)
            InvestigationManager.CharacterInvestigation interview =
                    controller.investigateCharacter(2);
            check("interview of Maya returns her evidence and events",
                    interview.getSubject().getName().equals("Maya Kapoor")
                    && interview.getLinkedEvidence().size() == 3);
            InvestigationManager.LocationInvestigation survey =
                    controller.investigateLocation(3);
            check("Security Room survey finds camera evidence",
                    survey.getLinkedEvidence().size() >= 2);
            controller.collectEvidence(1);
            controller.collectEvidence(2);
            controller.collectEvidence(6);
            controller.collectEvidence(3);
            check("collected 4 clues", controller.getCollectedCount() == 4);
            check("evidence search works",
                    controller.searchEvidence("camera").size() >= 2);

            // 15. Contradiction detection: the seeded demonstrations
            List<InvestigationController.FindingRow> findingsRoot =
                    controller.runContradictionCheck(List.of(root));
            check("E001 (Maya entry 10:00) SUPPORTS ROOT - the consistent demo",
                    findingsRoot.stream()
                            .anyMatch(f -> f.getEvidence().getId() == 1
                                    && f.getVerdict() == ContradictionDetector.Verdict.SUPPORTS));
            List<InvestigationController.FindingRow> findingsC =
                    controller.runContradictionCheck(List.of(timelineC));
            check("E001 CONTRADICTS TIMELINE-C (Maya absent) - the contradiction demo",
                    findingsC.stream()
                            .anyMatch(f -> f.getEvidence().getId() == 1
                                    && f.getVerdict() == ContradictionDetector.Verdict.CONTRADICTS));
            List<InvestigationController.FindingRow> findingsA =
                    controller.runContradictionCheck(List.of(controller.getTimelines().get(1)));
            check("E002 (camera offline 09:40) CONTRADICTS TIMELINE-A (camera repaired)",
                    findingsA.stream()
                            .anyMatch(f -> f.getEvidence().getId() == 2
                                    && f.getVerdict() == ContradictionDetector.Verdict.CONTRADICTS));
            List<InvestigationController.FindingRow> findingsB =
                    controller.runContradictionCheck(List.of(controller.getTimelines().get(2)));
            check("E002 SUPPORTS TIMELINE-B (camera remains offline)",
                    findingsB.stream()
                            .anyMatch(f -> f.getEvidence().getId() == 2
                                    && f.getVerdict() == ContradictionDetector.Verdict.SUPPORTS));

            // 16. Solve the mystery (result must come from MysterySolver)
            MysterySolver.MysteryResult verdict = controller.solveCase();
            check("solver names Maya Kapoor as the culprit on ROOT's evidence",
                    verdict.getCulprit().getSuspect().getName().equals("Maya Kapoor"));
            check("confidence in 0-100",
                    verdict.getConfidence() >= 0 && verdict.getConfidence() <= 100);
            check("verdict names ROOT as the timeline used",
                    verdict.getTimelineUsed().getId() == 1);
            System.out.println("  solver verdict: " + verdict.getCulprit().getSuspect().getName()
                    + " (" + verdict.getConfidence() + "%), timeline '"
                    + verdict.getTimelineUsed().getLabel() + "'");

            // TimelineManager mirror (Step 5 service API through the controller)
            TimelineManager manager = controller.buildTimelineManager();
            check("manager mirror holds all 5 timelines",
                    manager.getTimelines().size() == 5
                    && manager.getCurrent().getId() == timelineC.getId());
            manager.returnToPreviousTimeline();
            check("manager history walks back down the ancestry",
                    manager.getCurrent().getId() == 3);

            // 17. Save
            controller.saveCase(file);

            // 18-19. Close -> Load the saved investigation
            FileManager.LoadResult loaded = new FileManager().loadCase(file);
            InvestigationController reloaded = InvestigationController.loadCase(file);

            // 20. Hierarchy and investigation state survived
            check("reload: 5 timelines (branch tree restored)",
                    reloaded.getTimelineCount() == 5);
            check("reload: ROOT has three children (A, B and the user branch)",
                    reloaded.getTimelines().get(0).getBranches().size() == 3);
            check("reload: TIMELINE-C is still TIMELINE-B's child",
                    reloaded.getTimelines().get(3).getParent().getId() == 3);
            check("reload: resumes on TIMELINE-C (the saved position)",
                    reloaded.getCurrentTimeline() != null
                    && reloaded.getCurrentTimeline().getId() == 4);
            check("reload: TIMELINE-C's revised event still revised",
                    reloaded.getTimelines().get(3).getEvents().stream()
                            .filter(e -> e.getId() == 7)
                            .allMatch(e -> e.getDescription()
                                    .equals("Maya does NOT enter Prototype Lab")));
            check("reload: ROOT still intact (all 13 original events)",
                    reloaded.getTimelines().get(0).getEventCount() == 13
                    && reloaded.getTimelines().get(0).getEvents().stream()
                            .filter(e -> e.getId() == 6)
                            .allMatch(e -> e.getDescription().contains("offline")));
            check("reload: collected clues restored", reloaded.getCollectedCount() == 4);

            // Solver still runs on the reloaded session (round trip sanity)
            MysterySolver.MysteryResult verdict2 = reloaded.solveCase();
            check("reload: solver still works",
                    verdict2.getCulprit().getSuspect() != null);

            if (failures == 0) {
                System.out.println("GUI FLOW TEST: all steps passed.");
            } else {
                System.out.println("GUI FLOW TEST: " + failures + " check(s) FAILED.");
            }
        } finally {
            Files.deleteIfExists(file);
        }
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (!ok) {
            failures++;
        }
    }
}
