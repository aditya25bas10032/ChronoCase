package view;

import controller.InvestigationController;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Play like a user" harness for CASE-001. Everything goes through the real
 * widget graph: fire() on buttons, value changes on pickers, tab selections,
 * and assertions against what is actually rendered. Dialogs and the save
 * chooser are captured through the app's test hooks so a headless run never
 * blocks; every ERROR dialog is treated as a finding.
 */
public final class ViewPlaythrough {

    private static final List<String> DIALOGS = new ArrayList<>();
    private static final List<String> NOTES = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static final AtomicInteger PASSES = new AtomicInteger();
    private static Stage stage;

    private ViewPlaythrough() {
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> { });
        Platform.runLater(() -> {
            try {
                run();
            } catch (Throwable t) {
                FAILURES.add("UNCAUGHT: " + t);
                t.printStackTrace(System.out);
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();

        System.out.println("\n=== UX NOTES ===");
        NOTES.forEach(n -> System.out.println("NOTE: " + n));
        System.out.println("\n=== RESULT: " + PASSES.get() + " passed, "
                + FAILURES.size() + " failed, " + NOTES.size() + " UX notes ===");
        FAILURES.forEach(f -> System.out.println("FAIL: " + f));
        System.exit(FAILURES.isEmpty() ? 0 : 1);
    }

    // ------------------------------------------------------------------
    // the playthrough
    // ------------------------------------------------------------------

    private static void run() throws Exception {
        ChronoCaseApp app = new ChronoCaseApp();
        app.setDialogHook((kind, message) -> DIALOGS.add(kind + ": " + message));
        app.setSavePathHook(() -> {
            try {
                return Files.createTempFile("chronocase-playthrough", ".txt");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        app.setNewCaseHook(() -> new String[] {
                controller.MissingPrototypeCase.TITLE, controller.MissingPrototypeCase.DESCRIPTION});
        stage = new Stage();
        app.start(stage);

        log("---- ACT 0: main menu ----");
        check("app title rendered", allLabels().contains("ChronoCase"), allLabels());
        check("New Investigation button", buttonWithText("New Investigation") != null, "missing");
        check("Load Investigation button", buttonWithText("Load Investigation") != null, "missing");
        check("Exit button", buttonWithText("Exit") != null, "missing");

        log("---- ACT 1: new investigation -> dashboard ----");
        fire(buttonWithText("New Investigation"));
        String labels = allLabels();
        check("case title in header", allLabelsFromRoot().contains("The Missing Prototype"),
                allLabelsFromRoot());
        Map<String, String> stats = statCards();
        check("4 suspects", "4".equals(stats.get("Suspects")), String.valueOf(stats));
        check("5 locations", "5".equals(stats.get("Locations")), String.valueOf(stats));
        check("13 events", "13".equals(stats.get("Events")), String.valueOf(stats));
        check("8 evidence", "8".equals(stats.get("Evidence")), String.valueOf(stats));
        check("0 collected", "0".equals(stats.get("Collected")), String.valueOf(stats));
        check("4 timelines", "4".equals(stats.get("Timelines")), String.valueOf(stats));
        check("current timeline chip = ROOT",
                labels.contains("Current timeline:  #1  ROOT  (root)"), labels);
        check("investigation time chip = case start",
                labels.contains("Investigating time:  case start"), labels);

        log("---- ACT 2: timeline screen, branch map and switching ----");
        nav("timeline");
        labels = allLabels();
        check("info panel: selected ROOT", labels.contains("Selected: Timeline #1  \"ROOT\""), labels);
        check("info panel: parent none", labels.contains("none - this is the root timeline"), labels);
        check("info panel: children A+B",
                labels.contains("#2 TIMELINE-A: camera repaired, #3 TIMELINE-B: camera offline"), labels);
        check("branch map canvas present", firstOf(javafx.scene.canvas.Canvas.class) != null, "no canvas");
        check("13 event rows", countLabelsContaining("2026-09-15") == 13,
                String.valueOf(countLabelsContaining("2026-09-15")));
        check("no 'you are here' before travel",
                allLabels().toLowerCase().contains("you are here") == false, allLabels());

        // switch to TIMELINE-C through the picker (a real user action)
        ComboBox<String> picker = firstOf(ComboBox.class);
        choose(picker, itemContaining(picker, "#4"));
        labels = allLabels();
        check("switched to TIMELINE-C", labels.contains("Selected: Timeline #4"), labels);
        check("C's parent is B", labels.contains("Parent: Timeline #3"), labels);
        check("C shows Maya absence event", labels.contains("Maya does NOT enter Prototype Lab"), labels);

        // back to ROOT through the picker
        choose(picker, itemContaining(picker, "#1"));
        check("back on ROOT", allLabels().contains("Selected: Timeline #1"), allLabels());

        log("---- ACT 3: time travel to 09:40 and branch ----");
        nav("travel");
        List<Button> travelButtons = buttonsWithText("Travel Here");
        check("13 Travel Here buttons", travelButtons.size() == 13, String.valueOf(travelButtons.size()));

        Button at0940 = travelButtonOnCardContaining("09:40");
        check("09:40 card has a Travel Here button", at0940 != null, "not found");
        fire(at0940);

        String travelLabels = allLabels();
        check("chosen event shown", travelLabels.contains("Changing event #"), travelLabels);
        DatePicker date = firstOf(DatePicker.class);
        List<ComboBox<?>> timeCombos = allCombos();
        check("revision date prefilled 2026-09-15",
                date.getValue() != null && date.getValue().toString().equals("2026-09-15"),
                String.valueOf(date.getValue()));
        check("revision hour prefilled 9",
                timeCombos.size() >= 2 && Integer.valueOf(9).equals(timeCombos.get(0).getValue()),
                "hour=" + (timeCombos.isEmpty() ? "?" : timeCombos.get(0).getValue()));
        check("revision minute prefilled 40",
                timeCombos.size() >= 2 && Integer.valueOf(40).equals(timeCombos.get(1).getValue()),
                "minute=" + (timeCombos.size() < 2 ? "?" : timeCombos.get(1).getValue()));

        TextInputControl revisionText = inputWithPrompt("What really happened instead?");
        revisionText.setText("Security camera is repaired early");
        fire(buttonWithText("Create alternate timeline"));
        String afterBranch = allLabels();
        check("branch creation confirmed", afterBranch.contains("The past changed"), afterBranch);
        check("new timeline is #5", afterBranch.contains("timeline #5"), afterBranch);
        check("choice label reset", afterBranch.contains("No event chosen yet"), afterBranch);

        fire(buttonWithText("Return to previous timeline"));
        String afterReturn = allLabels();
        check("returned through history", afterReturn.contains("Returned one step"), afterReturn);

        log("---- ACT 4: both timelines exist and hold their own truth ----");
        nav("timeline");
        ComboBox<String> timelinePicker = firstOf(ComboBox.class);
        choose(timelinePicker, itemContaining(timelinePicker, "#5"));
        String branchLabels = allLabels();
        check("branch #5 selected", branchLabels.contains("Selected: Timeline #5"), branchLabels);
        check("branch shows the revised event",
                branchLabels.contains("Security camera is repaired early"), branchLabels);

        choose(timelinePicker, itemContaining(timelinePicker, "#1"));
        String rootLabels = allLabels();
        check("ROOT unchanged: 09:40 camera still offline",
                rootLabels.contains("Security camera goes offline"), rootLabels);
        check("ROOT has no revised text",
                !rootLabels.contains("repaired early"), rootLabels);

        // back to the branch - the rest of the session continues there,
        // so the save at the end must resume on this timeline
        choose(timelinePicker, itemContaining(timelinePicker, "#5"));
        check("working on branch #5 again", allLabels().contains("Selected: Timeline #5"), allLabels());

        log("---- ACT 5: compare ROOT vs branch #5 ----");
        nav("compare");
        List<ComboBox<?>> compareCombos = allCombos();
        check("two timeline combos", compareCombos.size() == 2, String.valueOf(compareCombos.size()));
        @SuppressWarnings("unchecked")
        ComboBox<String> first = (ComboBox<String>) compareCombos.get(0);
        @SuppressWarnings("unchecked")
        ComboBox<String> second = (ComboBox<String>) compareCombos.get(1);
        choose(first, itemContaining(first, "#1"));
        choose(second, itemContaining(second, "#5"));
        fire(buttonWithText("Compare"));
        TableView<?> diffTable = firstOf(TableView.class);
        check("diff table has 13 rows", diffTable.getItems().size() == 13,
                String.valueOf(diffTable.getItems().size()));
        long identical = diffTable.getItems().stream()
                .filter(row -> ((String[]) row)[3].equals("identical")).count();
        long changed = diffTable.getItems().stream()
                .filter(row -> ((String[]) row)[3].equals("changed")).count();
        check("12 identical rows", identical == 12, String.valueOf(identical));
        check("1 changed row", changed == 1, String.valueOf(changed));
        boolean changedIs0940 = diffTable.getItems().stream()
                .anyMatch(row -> ((String[]) row)[0].equals("#6")
                        && ((String[]) row)[3].equals("changed"));
        check("the changed row is the 09:40 event", changedIs0940, "verdict rows wrong");

        log("---- ACT 6: contradiction detection ROOT + TIMELINE-C ----");
        nav("contradictions");
        ListView<String> timelineList = firstOf(ListView.class);
        timelineList.getSelectionModel().clearSelection();
        timelineList.getSelectionModel().selectIndices(0, 3); // ROOT, TIMELINE-C
        fire(buttonWithText("Run contradiction detection"));
        String findings = allLabels();
        check("exactly 1 contradiction reported", findings.contains("1 contradiction(s)"), findings);
        check("E001 named as the clashing evidence",
                findings.contains("Security Access Log"), findings);
        check("clash is against timeline #4", findings.contains("timeline #4"), findings);
        check("consistent findings include ROOT", findings.contains("timeline #1"), findings);

        log("---- ACT 7: investigate: interview, survey, inspect, collect, search ----");
        nav("investigate");
        fire(buttonWithText("Interview suspect"));
        check("interview without selection is explained",
                DIALOGS.contains("ERROR: Pick a suspect first"), String.valueOf(DIALOGS));

        ComboBox<String> suspectPicker = comboNextTo("Suspect:");
        choose(suspectPicker, itemContaining(suspectPicker, "Maya Kapoor"));
        fire(buttonWithText("Interview suspect"));
        String interview = allLabels();
        check("interview card rendered", interview.contains("INTERVIEW: Maya Kapoor"), interview);
        check("interview lists her 10:00 lab entry",
                interview.contains("10:00"), interview);
        check("interview lists linked evidence", interview.contains("Security Access Log"), interview);

        TabPane tabs = firstOf(TabPane.class);
        selectTab(tabs, "Locations");
        ComboBox<String> locationPicker = comboNextTo("Location:");
        choose(locationPicker, itemContaining(locationPicker, "Prototype Lab"));
        fire(buttonWithText("Survey location"));
        String survey = allLabels();
        check("survey card rendered", survey.contains("SURVEY: Prototype Lab"), survey);
        check("survey lists events at the lab", survey.contains("Events at this location"), survey);

        selectTab(tabs, "Events");
        ComboBox<String> eventPicker = comboNextTo("Event:");
        choose(eventPicker, itemContaining(eventPicker, "Prototype disappears"));
        fire(buttonWithText("Inspect event"));
        String inspected = allLabels();
        check("event inspection rendered", inspected.contains("EVENT INSPECTED"), inspected);
        check("inspection points to time travel", inspected.contains("Travel Here"), inspected);

        selectTab(tabs, "Evidence");
        TextInputControl searchField = inputWithPrompt("Search evidence by text...");
        searchField.setText("camera");
        fire(buttonWithText("Search"));
        String search = allLabels();
        check("search results rendered", search.contains("SEARCH \"camera\""), search);
        check("search found the camera clues", search.contains("Camera Failure Report"), search);

        ComboBox<String> collectPicker = comboNextTo("Evidence:");
        choose(collectPicker, itemContaining(collectPicker, "#1"));
        fire(buttonWithText("Collect evidence"));
        check("collect confirmed via dialog",
                DIALOGS.stream().anyMatch(d -> d.startsWith("INFO:") && d.contains("Collected evidence #1")),
                String.valueOf(DIALOGS));
        check("bag shows the clue", allLabels().contains("Evidence bag (1)"), allLabels());

        log("---- ACT 8: evidence board ----");
        nav("evidence");
        TableView<?> boardTable = firstOf(TableView.class);
        check("board lists 8 clues", boardTable.getItems().size() == 8,
                String.valueOf(boardTable.getItems().size()));
        boardTable.getSelectionModel().select(0);
        String relations = allLabels();
        check("relations panel filled from row selection",
                relations.contains("Evidence #1") && relations.contains("suspect: Maya Kapoor"),
                relations);
        check("relations show linked location", relations.contains("location: Prototype Lab"), relations);

        TextInputControl boardSearch = inputWithPrompt("Search evidence by text...");
        boardSearch.setText("prototype");
        fire(buttonWithText("Search"));
        String boardSearchLabels = allLabels();
        check("board search reports hits", boardSearchLabels.contains("hit(s) for \"prototype\""),
                boardSearchLabels);
        fire(buttonWithText("Show all"));
        check("show all restores the full board", boardTable.getItems().size() == 8,
                String.valueOf(boardTable.getItems().size()));

        log("---- ACT 9: solve the mystery ----");
        nav("result");
        fire(buttonWithText("Solve the case"));
        String verdict = allLabels();
        check("culprit is Maya Kapoor (from MysterySolver)", verdict.contains("Maya Kapoor"), verdict);
        check("confidence rendered", verdict.contains("confidence"), verdict);
        check("ranking names her first", verdict.contains("1. Maya Kapoor"), verdict);
        check("timeline used is shown", verdict.contains("Timeline used:"), verdict);
        javafx.scene.control.TextArea report = firstOf(javafx.scene.control.TextArea.class);
        check("verdict report filled", report.getText() != null && !report.getText().isBlank(),
                "empty report");

        log("---- ACT 10: save, reload, verify state ----");
        fire(buttonInHeader("Save"));
        check("save confirmed via dialog",
                DIALOGS.stream().anyMatch(d -> d.startsWith("INFO:") && d.contains("Case saved to")),
                String.valueOf(DIALOGS));
        Path saved = savePathFromDialogs();
        check("save file exists and is non-empty",
                saved != null && Files.exists(saved) && Files.size(saved) > 0,
                String.valueOf(saved));

        InvestigationController loaded = InvestigationController.loadCase(saved);
        check("reloaded case has 5 timelines", loaded.getTimelines().size() == 5,
                String.valueOf(loaded.getTimelines().size()));
        check("reload resumes on branch #5", loaded.getCurrentTimeline().getId() == 5,
                String.valueOf(loaded.getCurrentTimeline()));
        check("reloaded branch keeps the revised event",
                loaded.getTimelineEvents(loaded.getCurrentTimeline()).stream()
                        .anyMatch(e -> e.getDescription().contains("repaired early")),
                "revised event lost");
        check("reloaded ROOT intact",
                loaded.getTimelineEvents(loaded.getTimelines().get(0)).stream()
                        .anyMatch(e -> e.getDescription().contains("Security camera goes offline")),
                "ROOT modified");
        check("collected clue survived", loaded.getCollectedCount() == 1,
                String.valueOf(loaded.getCollectedCount()));
        Files.deleteIfExists(saved);

        log("---- ACT 11: main menu button declines without confirm ----");
        fire(buttonInHeader("Main Menu"));
        check("confirm dialog was raised", DIALOGS.stream().anyMatch(d -> d.startsWith("CONFIRM:")),
                String.valueOf(DIALOGS));
        check("still inside the investigation", allLabelsFromRoot().contains("The Missing Prototype"),
                allLabelsFromRoot());

        // ---- final: no unexpected errors ----
        long errors = DIALOGS.stream().filter(d -> d.startsWith("ERROR:")).count();
        check("exactly one error dialog (the intended no-selection one)", errors == 1,
                String.valueOf(DIALOGS));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void log(String line) {
        System.out.println(line);
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            PASSES.incrementAndGet();
            System.out.println("PASS: " + what);
        } else {
            FAILURES.add(what + "  [" + detail + "]");
            System.out.println("FAIL: " + what + "  [" + detail + "]");
        }
    }

    private static Parent root() {
        SEEN.clear(); // every walk starts fresh
        return stage.getScene().getRoot();
    }

    /** The active screen (the shell's centre panel), not the chrome. */
    private static Parent center() {
        SEEN.clear(); // every walk starts fresh
        Node r = root();
        if (r instanceof javafx.scene.layout.BorderPane shell && shell.getCenter() != null) {
            return (Parent) shell.getCenter();
        }
        return root(); // main menu has no shell yet
    }

    private static final java.util.IdentityHashMap<Node, Boolean> SEEN = new java.util.IdentityHashMap<>();

    private static void walk(Node node, java.util.function.Consumer<Node> consumer) {
        if (node == null || SEEN.put(node, Boolean.TRUE) != null) {
            return;
        }
        consumer.accept(node);
        if (node instanceof javafx.scene.control.TitledPane titled && titled.getContent() != null) {
            // TitledPane exposes its content only after a layout pulse;
            // walk it explicitly so headless runs see the widgets inside.
            walk(titled.getContent(), consumer);
        }
        if (node instanceof javafx.scene.control.ScrollPane scroll && scroll.getContent() != null) {
            walk(scroll.getContent(), consumer);
        }
        if (node instanceof TabPane tabPane) {
            // Only the selected tab's content is on screen.
            Tab selected = tabPane.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getContent() != null) {
                walk(selected.getContent(), consumer);
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                walk(child, consumer);
            }
        }
    }

    private static void nav(String screenId) {
        List<Button> nav = new ArrayList<>();
        walk(root(), n -> {
            if (n instanceof Button b && screenId.equals(b.getUserData())) {
                nav.add(b);
            }
        });
        if (nav.isEmpty()) {
            FAILURES.add("nav button not found: " + screenId);
            return;
        }
        nav.get(0).fire();
    }

    private static void fire(Button button) {
        if (button == null) {
            FAILURES.add("fire: button not found");
            return;
        }
        button.fire();
    }

    private static List<Button> buttonsWithText(String text) {
        List<Button> out = new ArrayList<>();
        walk(center(), n -> {
            if (n instanceof Button b && b.getText() != null && b.getText().contains(text)) {
                out.add(b);
            }
        });
        return out;
    }

    private static Button buttonWithText(String text) {
        List<Button> found = buttonsWithText(text);
        return found.isEmpty() ? null : found.get(0);
    }

    private static String allLabels() {
        return labelsOf(center());
    }

    /** Everything rendered, header included. */
    private static String allLabelsFromRoot() {
        return labelsOf(root());
    }

    private static String labelsOf(Parent scope) {
        StringBuilder sb = new StringBuilder();
        walk(scope, n -> {
            if (n instanceof Labeled l && l.getText() != null) {
                sb.append(l.getText()).append("\n");
            }
        });
        return sb.toString();
    }

    private static int countLabelsContaining(String needle) {
        int[] count = new int[1];
        walk(center(), n -> {
            if (n instanceof Label l && l.getText() != null && l.getText().contains(needle)) {
                count[0]++;
            }
        });
        return count[0];
    }

    private static <T extends Node> T firstOf(Class<T> type) {
        List<T> all = allOf(type);
        return all.isEmpty() ? null : all.get(0);
    }

    private static <T extends Node> List<T> allOf(Class<T> type) {
        List<T> out = new ArrayList<>();
        walk(center(), n -> {
            if (type.isInstance(n)) {
                out.add(type.cast(n));
            }
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> firstStringCombo() {
        for (ComboBox<?> combo : allOf(ComboBox.class)) {
            if (combo.getItems().stream().allMatch(i -> i instanceof String)) {
                return (ComboBox<String>) combo;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<ComboBox<?>> allCombos() {
        List<ComboBox<?>> out = new ArrayList<>();
        walk(center(), n -> {
            if (n instanceof ComboBox) {
                out.add((ComboBox<?>) n);
            }
        });
        return out;
    }

    private static TextInputControl inputWithPrompt(String prompt) {
        List<TextInputControl> hits = new ArrayList<>();
        walk(center(), n -> {
            if (n instanceof TextInputControl input && prompt.equals(input.getPromptText())) {
                hits.add(input);
            }
        });
        return hits.isEmpty() ? null : hits.get(0);
    }

    private static ComboBox<String> comboNextTo(String labelText) {
        List<ComboBox<String>> hits = new ArrayList<>();
        walk(center(), n -> {
            if (n instanceof HBox box) {
                boolean hasLabel = box.getChildren().stream().anyMatch(c ->
                        c instanceof Label l && labelText.equals(l.getText()));
                if (hasLabel) {
                    box.getChildren().stream()
                            .filter(c -> c instanceof ComboBox)
                            .findFirst()
                            .ifPresent(c -> hits.add((ComboBox<String>) c));
                }
            }
        });
        return hits.isEmpty() ? null : hits.get(0);
    }

    private static String itemContaining(ComboBox<String> combo, String needle) {
        if (combo == null) {
            FAILURES.add("combo not found for " + needle);
            return null;
        }
        for (String item : combo.getItems()) {
            if (item.equals(needle) || item.startsWith(needle + " ")) {
                return item; // precise id match first ("#1" must not hit "#10")
            }
        }
        for (String item : combo.getItems()) {
            if (item.contains(needle)) {
                return item;
            }
        }
        FAILURES.add("no combo item contains \"" + needle + "\" in " + combo.getItems());
        return null;
    }

    private static Button travelButtonOnCardContaining(String needle) {
        List<Button> hits = new ArrayList<>();
        walk(center(), n -> {
            if (n instanceof HBox card
                    && card.getStyleClass().contains("travel-card")
                    && rowText(card).contains(needle)) {
                card.getChildren().stream()
                        .filter(c -> c instanceof Button b && "Travel Here".equals(b.getText()))
                        .findFirst()
                        .ifPresent(c -> hits.add((Button) c));
            }
        });
        return hits.isEmpty() ? null : hits.get(0);
    }

    private static String rowText(HBox row) {
        StringBuilder sb = new StringBuilder();
        for (Node child : row.getChildren()) {
            if (child instanceof Labeled l && l.getText() != null) {
                sb.append(l.getText());
            }
        }
        return sb.toString();
    }

    /** Simulates a real user pick: value change + onAction commit. */
    private static void choose(ComboBox<String> combo, String item) {
        if (combo == null || item == null) {
            FAILURES.add("choose: combo or item missing");
            return;
        }
        combo.setValue(item);
        javafx.event.EventHandler<javafx.event.ActionEvent> handler = combo.getOnAction();
        if (handler != null) {
            handler.handle(new javafx.event.ActionEvent());
        }
    }

    private static Map<String, String> statCards() {
        Map<String, String> out = new LinkedHashMap<>();
        walk(center(), n -> {
            if (n instanceof VBox card && card.getStyleClass().contains("stat-card")) {
                String value = null;
                String caption = null;
                for (Node child : card.getChildren()) {
                    if (child instanceof Label l) {
                        if (l.getStyleClass().contains("stat-value")) {
                            value = l.getText();
                        } else if (l.getStyleClass().contains("stat-caption")) {
                            caption = l.getText();
                        }
                    }
                }
                if (caption != null) {
                    out.put(caption, value);
                }
            }
        });
        return out;
    }

    private static void selectTab(TabPane tabPane, String text) {
        for (Tab tab : tabPane.getTabs()) {
            if (text.equals(tab.getText())) {
                tabPane.getSelectionModel().select(tab);
                return;
            }
        }
        FAILURES.add("tab not found: " + text);
    }

    private static Button buttonInHeader(String text) {
        List<Button> found = new ArrayList<>();
        walk(root(), n -> {
            if (n instanceof Button b && text.equals(b.getText())) {
                found.add(b);
            }
        });
        return found.isEmpty() ? null : found.get(0);
    }

    private static Path savePathFromDialogs() {
        for (String dialog : DIALOGS) {
            if (dialog.startsWith("INFO:") && dialog.contains("Case saved to ")) {
                String line = dialog.substring(dialog.indexOf("Case saved to ") + "Case saved to ".length());
                String firstLine = line.split("\n", 2)[0].trim();
                return Path.of(firstLine);
            }
        }
        return null;
    }
}
