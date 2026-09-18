package view;

import controller.InvestigationController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.Event;
import model.Timeline;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Time Travel: the heart of ChronoCase. Step 1 lists the current
 * timeline's events in chronological order, each with a "Travel
 * Here" button (travel = controller.travelTo). Step 2 shows the
 * chosen event and lets the detective change what happened - the
 * revision goes through controller.reviseEvent, which branches the
 * timeline; the original timeline is never touched. The status panel
 * shows the branch tree after every step.
 */
class TimeTravelScreen {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InvestigationController controller;
    private final VBox view;

    private final VBox eventCards = new VBox(8);
    private final Label chosenEventLabel = new Label("No event chosen yet - travel to one above.");
    private final TextField revisionText = new TextField();
    private final DatePicker revisionDate = new DatePicker();
    private final ComboBox<Integer> revisionHour = new ComboBox<>();
    private final ComboBox<Integer> revisionMinute = new ComboBox<>();
    private final VBox statusArea = new VBox(4);
    private int chosenEventId = -1;

    TimeTravelScreen(InvestigationController controller) {
        this.controller = controller;
        this.view = build();
        refresh();
    }

    Node getView() {
        return view;
    }

    void refresh() {
        Timeline current = controller.getCurrentTimeline();
        rebuildEventCards();
        if (current == null) {
            chosenEventLabel.setText("No timeline to travel on.");
            updateStatus("Create or load a case with a timeline first.");
            return;
        }
        if (chosenEventId >= 0) {
            var chosen = current.getEvents().stream()
                    .filter(e -> e.getId() == chosenEventId)
                    .findFirst();
            chosen.ifPresent(this::showChosenEvent);
        }
        updateStatus("On timeline #" + current.getId() + " '" + current.getLabel() + "'.");
    }

    private VBox build() {
        Label heading = new Label("Time Travel");
        heading.getStyleClass().add("section-title");
        Label explainer = new Label("Travel back to any moment, change what happened, and ChronoCase "
                + "creates a new timeline branch. The original timeline always survives.");
        explainer.getStyleClass().add("caption");
        explainer.setWrapText(true);
        explainer.setMaxWidth(Double.MAX_VALUE);

        // ----- step 1: events with Travel Here -----
        Label step1 = new Label("Step 1 - The events of the current timeline (chronological). "
                + "Travel to the moment you want to change:");
        step1.getStyleClass().add("caption");
        eventCards.getStyleClass().add("panel");
        eventCards.setPadding(new Insets(10));

        // ----- step 2: revise -----
        Label step2 = new Label("Step 2 - Change what happened:");
        step2.getStyleClass().add("caption");
        chosenEventLabel.getStyleClass().add("travel-choice");
        chosenEventLabel.setWrapText(true);

        revisionText.setPromptText("What really happened instead?");
        revisionText.setPrefColumnCount(40);
        HBox.setHgrow(revisionText, Priority.ALWAYS);

        revisionHour.getItems().setAll(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
                13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23));
        revisionMinute.getItems().setAll(List.of(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55));
        revisionDate.setValue(java.time.LocalDate.now());
        revisionHour.setValue(12);
        revisionMinute.setValue(0);

        HBox whenRow = new HBox(8,
                new Label("New time:"), revisionDate, revisionHour, new Label(":"), revisionMinute);
        whenRow.setAlignment(Pos.CENTER_LEFT);

        Button branchButton = new Button("Create alternate timeline with this change");
        branchButton.getStyleClass().add("accent");
        branchButton.setOnAction(e -> onRevise());

        VBox reviseBox = new VBox(8, chosenEventLabel, revisionText, whenRow, branchButton);
        reviseBox.getStyleClass().add("panel");
        reviseBox.setPadding(new Insets(10));

        TitledPane step2Pane = new TitledPane("Make your choice", reviseBox);
        step2Pane.setExpanded(true);

        // ----- status & history -----
        Button backButton = new Button("Return to previous timeline");
        backButton.setOnAction(e -> onReturn());
        HBox historyRow = new HBox(8, backButton);
        historyRow.setAlignment(Pos.CENTER_LEFT);

        statusArea.getStyleClass().add("panel");
        statusArea.setPadding(new Insets(10));

        VBox layout = new VBox(10, heading, explainer,
                step1, eventCards,
                step2Pane,
                historyRow,
                new Label("Branch status:"), statusArea);
        VBox.setVgrow(statusArea, Priority.SOMETIMES);
        layout.setPadding(new Insets(10));
        return layout;
    }

    /** One card per event, each with its own Travel Here button. */
    private void rebuildEventCards() {
        eventCards.getChildren().clear();
        Timeline current = controller.getCurrentTimeline();
        if (current == null) {
            eventCards.getChildren().add(new Label("No timeline to travel on."));
            return;
        }
        List<Event> events = controller.getTimelineEvents(current);
        if (events.isEmpty()) {
            eventCards.getChildren().add(new Label("This timeline has no events yet."));
            return;
        }
        int atEventId = controller.getCurrentEventId();
        for (Event e : events) {
            boolean here = e.getId() == atEventId;
            Label when = new Label(e.getTimestamp().format(TIME_FORMAT));
            when.getStyleClass().add("travel-time");
            Label what = new Label("#" + e.getId() + "  " + e.getDescription()
                    + "   [" + e.getCharacter().getName() + " @ " + e.getLocation().getName() + "]");
            what.getStyleClass().add("travel-desc");
            what.setWrapText(true);
            what.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(what, Priority.ALWAYS);
            Button travel = new Button(here ? "You are here" : "Travel Here");
            travel.getStyleClass().add(here ? "travel-here" : "travel-button");
            travel.setDisable(here);
            travel.setOnAction(ev -> onTravel(e.getId()));
            HBox card = new HBox(12, when, what, travel);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("travel-card");
            eventCards.getChildren().add(card);
        }
    }

    private void onTravel(int eventId) {
        try {
            Event target = controller.travelTo(eventId);
            chosenEventId = eventId;
            showChosenEvent(target);
            prefillRevisionTime(target);
            refresh(); // rebuild cards/status first...
            updateStatus("Traveled back to " + target.getTimestamp().format(TIME_FORMAT)
                    + " - #" + target.getId() + " \"" + target.getDescription() + "\". "
                    + "Make your choice below."); // ...so this message survives
        } catch (InvestigationController.ControllerException ex) {
            app().showError(ex.getMessage());
            refresh();
        }
    }

    /** Default the revision time to the chosen event's time, so "09:40" stays 09:40 unless edited. */
    private void prefillRevisionTime(Event target) {
        revisionDate.setValue(target.getTimestamp().toLocalDate());
        revisionHour.setValue(target.getTimestamp().getHour());
        revisionMinute.setValue(target.getTimestamp().getMinute() / 5 * 5);
    }

    private void showChosenEvent(Event target) {
        chosenEventLabel.setText("Changing event #" + target.getId() + " at "
                + target.getTimestamp().format(TIME_FORMAT) + ":  \"" + target.getDescription() + "\"");
    }

    private void onRevise() {
        if (chosenEventId < 0) {
            app().showError("Travel to an event first (Step 1)");
            return;
        }
        if (revisionDate.getValue() == null) {
            app().showError("Pick a date for the revised event");
            return;
        }
        Integer hour = revisionHour.getValue();
        Integer minute = revisionMinute.getValue();
        if (hour == null || minute == null) {
            app().showError("Pick hour and minute for the revised event");
            return;
        }
        LocalDateTime newTime = LocalDateTime.of(revisionDate.getValue(),
                LocalTime.of(hour, minute));
        try {
            Timeline branch = controller.reviseEvent(chosenEventId, revisionText.getText(), newTime);
            chosenEventId = -1;
            revisionText.clear();
            chosenEventLabel.setText("No event chosen yet - travel to one above.");
            refresh(); // rebuild cards/status first...
            updateStatus("The past changed: created timeline #" + branch.getId() + " \""
                    + branch.getLabel() + "\". The original timeline is untouched and still "
                    + "selectable on the Timeline screen."); // ...so this message survives
        } catch (InvestigationController.ControllerException ex) {
            app().showError(ex.getMessage());
            refresh();
        }
    }

    private void onReturn() {
        boolean wentBack = controller.returnToPreviousTimeline();
        refresh(); // rebuild cards/status first...
        updateStatus(wentBack
                ? "Returned one step through the navigation history (depth now "
                        + controller.getHistoryDepth() + ")."
                : "No navigation history to return through."); // ...so this message survives
    }

    private void updateStatus(String line) {
        statusArea.getChildren().setAll(new Label(line));
        for (String branchLine : controller.describeBranches()) {
            Label branch = new Label("  " + branchLine);
            branch.getStyleClass().add("caption");
            statusArea.getChildren().add(branch);
        }
    }

    private ChronoCaseApp app() {
        return ChronoCaseApp.getInstance();
    }
}
