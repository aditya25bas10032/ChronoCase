package view;

import controller.InvestigationController;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.Character;
import model.Evidence;
import model.Event;
import model.Location;
import service.InvestigationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Investigation screen: the detective's field work. Three tabs -
 * Suspects (interview: their events and linked evidence), Locations
 * (survey: what happened there and what was found) and Events
 * (inspect a point in time). Every action goes through the
 * controller's {@code investigate*} wrappers around
 * InvestigationManager; results are shown as cards, collected clues
 * stay visible in the bag list on the right.
 */
class InvestigationScreen {

    private final InvestigationController controller;
    private final VBox view;

    private final ComboBox<String> suspectPicker = new ComboBox<>();
    private final ComboBox<String> locationPicker = new ComboBox<>();
    private final ComboBox<String> eventPicker = new ComboBox<>();
    private final ComboBox<String> collectPicker = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final VBox resultPanel = new VBox(6);
    private final VBox bagPanel = new VBox(6);

    InvestigationScreen(InvestigationController controller) {
        this.controller = controller;
        this.view = build();
        refresh();
    }

    Node getView() {
        return view;
    }

    void refresh() {
        suspectPicker.getItems().setAll(controller.getSuspects().stream()
                .map(c -> "#" + c.getId() + " " + c.getName() + " (" + c.getRole() + ")")
                .toList());
        locationPicker.getItems().setAll(controller.getLocations().stream()
                .map(l -> "#" + l.getId() + " " + l.getName())
                .toList());
        eventPicker.getItems().setAll(controller.getCaseEvents().stream()
                .map(e -> "#" + e.getId() + "  "
                        + e.getTimestamp().toLocalTime()
                        + "  " + e.getDescription())
                .toList());
        collectPicker.getItems().setAll(controller.getEvidence().stream()
                .map(item -> "#" + item.getId() + " [" + item.getPriority() + "] "
                        + item.getDescription())
                .toList());
        rebuildBag();
        // keep the last result panel; it refreshes on the next action
    }

    private VBox build() {
        Label heading = new Label("Investigation");
        heading.getStyleClass().add("section-title");

        // ----- suspects tab -----
        Button interviewButton = new Button("Interview suspect");
        interviewButton.getStyleClass().add("accent");
        interviewButton.setOnAction(e -> onInterview());
        HBox suspectRow = new HBox(8, new Label("Suspect:"), suspectPicker, interviewButton);
        suspectRow.setAlignment(Pos.CENTER_LEFT);
        VBox suspectTab = new VBox(8, suspectRow);

        // ----- locations tab -----
        Button surveyButton = new Button("Survey location");
        surveyButton.getStyleClass().add("accent");
        surveyButton.setOnAction(e -> onSurvey());
        HBox locationRow = new HBox(8, new Label("Location:"), locationPicker, surveyButton);
        locationRow.setAlignment(Pos.CENTER_LEFT);
        VBox locationTab = new VBox(8, locationRow);

        // ----- events tab -----
        Button inspectButton = new Button("Inspect event");
        inspectButton.getStyleClass().add("accent");
        inspectButton.setOnAction(e -> onInspect());
        HBox eventRow = new HBox(8, new Label("Event:"), eventPicker, inspectButton);
        eventRow.setAlignment(Pos.CENTER_LEFT);
        VBox eventTab = new VBox(8, eventRow);

        // ----- evidence tab -----
        Button collectButton = new Button("Collect evidence");
        collectButton.getStyleClass().add("accent");
        collectButton.setOnAction(e -> onCollect());
        Button searchButton = new Button("Search");
        searchButton.setOnAction(e -> onSearch());
        searchField.setPromptText("Search evidence by text...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        HBox collectRow = new HBox(8, new Label("Evidence:"), collectPicker, collectButton);
        collectRow.setAlignment(Pos.CENTER_LEFT);
        HBox searchRow = new HBox(8, searchField, searchButton);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        VBox evidenceTab = new VBox(8, collectRow, searchRow);

        TabPane tabs = new TabPane();
        Tab suspects = new Tab("Suspects", suspectTab);
        Tab locations = new Tab("Locations", locationTab);
        Tab events = new Tab("Events", eventTab);
        Tab evidence = new Tab("Evidence", evidenceTab);
        suspects.setClosable(false);
        locations.setClosable(false);
        events.setClosable(false);
        evidence.setClosable(false);
        tabs.getTabs().addAll(suspects, locations, events, evidence);

        resultPanel.getStyleClass().add("panel");
        resultPanel.setPadding(new Insets(10));
        resultPanel.getChildren().add(new Label("Pick someone - or something - to investigate."));

        Label bagCaption = new Label("Collected evidence (" + controller.getCollectedCount() + ")");
        bagCaption.getStyleClass().add("caption");
        bagPanel.getStyleClass().add("panel");
        bagPanel.setPadding(new Insets(10));

        // The result panel sits BELOW the tab pane so every tab (suspects,
        // locations, events, evidence) shows it - previously it lived inside
        // the suspects tab only, so survey/inspect/search results were
        // written to a panel the user could not see.
        VBox left = new VBox(10, heading, tabs, resultPanel);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        VBox.setVgrow(resultPanel, Priority.SOMETIMES);

        HBox columns = new HBox(12, left, bagPanel);
        HBox.setHgrow(left, Priority.ALWAYS);
        bagPanel.setPrefWidth(340);
        columns.setPadding(new Insets(10));
        VBox layout = new VBox(columns);
        return layout;
    }

    private void onInterview() {
        int id = DashboardScreen.extractId(suspectPicker.getValue());
        if (id < 0) {
            app().showError("Pick a suspect first");
            return;
        }
        try {
            InvestigationManager.CharacterInvestigation interview =
                    controller.investigateCharacter(id);
            showCharacterResult(interview);
            rebuildBag();
        } catch (InvestigationController.ControllerException ex) {
            app().showError(ex.getMessage());
        }
    }

    private void onSurvey() {
        int id = DashboardScreen.extractId(locationPicker.getValue());
        if (id < 0) {
            app().showError("Pick a location first");
            return;
        }
        try {
            InvestigationManager.LocationInvestigation survey =
                    controller.investigateLocation(id);
            showLocationResult(survey);
            rebuildBag();
        } catch (InvestigationController.ControllerException ex) {
            app().showError(ex.getMessage());
        }
    }

    private void onInspect() {
        int id = DashboardScreen.extractId(eventPicker.getValue());
        if (id < 0) {
            app().showError("Pick an event first");
            return;
        }
        try {
            Event event = controller.inspectEvent(id);
            showEventResult(event);
            rebuildBag();
        } catch (InvestigationController.ControllerException ex) {
            app().showError(ex.getMessage());
        }
    }

    private void onCollect() {
        int id = DashboardScreen.extractId(collectPicker.getValue());
        if (id < 0) {
            app().showError("Pick a piece of evidence first");
            return;
        }
        boolean added = controller.collectEvidence(id);
        rebuildBag();
        if (added) {
            app().showInfo("Collected evidence #" + id + " - "
                    + controller.getCollectedCount() + " clue(s) in the bag");
        } else {
            app().showInfo("Evidence #" + id + " was already collected");
        }
    }

    private void onSearch() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            app().showInfo("Type a search text first");
            return;
        }
        List<Evidence> hits = controller.searchEvidence(query);
        showSearchResult(query, hits);
    }

    private void showCharacterResult(InvestigationManager.CharacterInvestigation interview) {
        Character subject = interview.getSubject();
        resultPanel.getChildren().setAll(
                new Label("INTERVIEW: " + subject.getName() + " - " + subject.getRole()));
        Label eventsCaption = new Label("  Events they took part in ("
                + interview.getEvents().size() + "):");
        eventsCaption.getStyleClass().add("caption");
        resultPanel.getChildren().add(eventsCaption);
        for (Event e : interview.getEvents()) {
            resultPanel.getChildren().add(row("    " + e.getTimestamp().toLocalTime()
                    + "  #" + e.getId() + "  " + e.getDescription()
                    + " @ " + e.getLocation().getName()));
        }
        Label evidenceCaption = new Label("  Evidence linked to them ("
                + interview.getLinkedEvidence().size() + "):");
        evidenceCaption.getStyleClass().add("caption");
        resultPanel.getChildren().add(evidenceCaption);
        for (Evidence item : interview.getLinkedEvidence()) {
            resultPanel.getChildren().add(row("    #" + item.getId() + " ["
                    + item.getPriority() + "] " + item.getDescription()
                    + (controller.isCollected(item.getId()) ? "  (collected)" : "")));
        }
    }

    private void showLocationResult(InvestigationManager.LocationInvestigation survey) {
        Location subject = survey.getSubject();
        resultPanel.getChildren().setAll(
                new Label("SURVEY: " + subject.getName() + " - " + subject.getDescription()));
        Label eventsCaption = new Label("  Events at this location ("
                + survey.getEvents().size() + "):");
        eventsCaption.getStyleClass().add("caption");
        resultPanel.getChildren().add(eventsCaption);
        for (Event e : survey.getEvents()) {
            resultPanel.getChildren().add(row("    " + e.getTimestamp().toLocalTime()
                    + "  #" + e.getId() + "  " + e.getDescription()
                    + " - " + e.getCharacter().getName()));
        }
        Label evidenceCaption = new Label("  Evidence found here ("
                + survey.getLinkedEvidence().size() + "):");
        evidenceCaption.getStyleClass().add("caption");
        resultPanel.getChildren().add(evidenceCaption);
        for (Evidence item : survey.getLinkedEvidence()) {
            resultPanel.getChildren().add(row("    #" + item.getId() + " ["
                    + item.getPriority() + "] " + item.getDescription()
                    + (controller.isCollected(item.getId()) ? "  (collected)" : "")));
        }
    }

    private void showEventResult(Event event) {
        resultPanel.getChildren().setAll(
                new Label("EVENT INSPECTED"));
        resultPanel.getChildren().addAll(
                row("  #" + event.getId() + "  " + event.getTimestamp()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))),
                row("  " + event.getDescription()),
                row("  " + event.getCharacter().getName() + " @ "
                        + event.getLocation().getName()),
                row("  A 'Travel Here' action for this event lives in the Time Travel screen."));
    }

    private void showSearchResult(String query, List<Evidence> hits) {
        resultPanel.getChildren().setAll(
                new Label("SEARCH \"" + query + "\" - " + hits.size() + " hit(s)"));
        for (Evidence item : hits) {
            String links = (item.getLinkedCharacter() == null ? "" : " - "
                    + item.getLinkedCharacter().getName())
                    + (item.getLinkedLocation() == null ? "" : " @ "
                            + item.getLinkedLocation().getName());
            resultPanel.getChildren().add(row("    #" + item.getId() + " ["
                    + item.getPriority() + "] " + item.getDescription() + links
                    + (controller.isCollected(item.getId()) ? "  (collected)" : "")));
        }
    }

    private Label row(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private void rebuildBag() {
        bagPanel.getChildren().clear();
        Label caption = new Label("Evidence bag (" + controller.getCollectedCount() + ")");
        caption.getStyleClass().add("section-title");
        bagPanel.getChildren().add(caption);
        List<Evidence> bag = controller.getCollectedEvidenceList();
        if (bag.isEmpty()) {
            bagPanel.getChildren().add(new Label("Nothing collected yet. Interview suspects, "
                    + "survey locations and bag the clues you find."));
            return;
        }
        for (Evidence item : bag) {
            Label row = new Label("#" + item.getId() + " [" + item.getPriority() + "] "
                    + item.getDescription());
            row.setWrapText(true);
            row.setMaxWidth(Double.MAX_VALUE);
            row.getStyleClass().add("event-row");
            bagPanel.getChildren().add(row);
        }
    }

    private ChronoCaseApp app() {
        return ChronoCaseApp.getInstance();
    }
}
