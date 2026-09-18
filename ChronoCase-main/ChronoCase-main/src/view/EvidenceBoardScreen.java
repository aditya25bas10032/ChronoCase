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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.Evidence;
import model.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Evidence Board: relationships between evidence, suspects,
 * locations and events. The left table lists every clue; picking one
 * fills the link panels on the right (suspect, location, linked
 * events) plus the graph-style connection list. Linking events uses
 * the session's {@code associateWithEvent} through the controller.
 */
class EvidenceBoardScreen {

    private final InvestigationController controller;
    private final VBox view;

    private final ComboBox<String> evidencePicker = new ComboBox<>();
    private final ComboBox<String> eventPicker = new ComboBox<>();
    private final ComboBox<String> suspectPicker = new ComboBox<>();
    private final ComboBox<String> locationPicker = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final Label searchResult = new Label();
    private final TableView<String[]> boardTable = new TableView<>();
    private final VBox relationPanel = new VBox(4);

    EvidenceBoardScreen(InvestigationController controller) {
        this.controller = controller;
        this.view = build();
        refresh();
    }

    Node getView() {
        return view;
    }

    void refresh() {
        evidencePicker.getItems().setAll(controller.getEvidence().stream()
                .map(item -> "#" + item.getId() + " " + item.getDescription())
                .toList());
        eventPicker.getItems().setAll(controller.getCaseEvents().stream()
                .map(e -> "#" + e.getId() + " " + e.getDescription())
                .toList());
        suspectPicker.getItems().setAll(controller.getSuspects().stream()
                .map(c -> "#" + c.getId() + " " + c.getName())
                .toList());
        locationPicker.getItems().setAll(controller.getLocations().stream()
                .map(l -> "#" + l.getId() + " " + l.getName())
                .toList());
        rebuildBoardTable();
        rebuildRelationPanel();
    }

    private VBox build() {
        Label heading = new Label("Evidence Board");
        heading.getStyleClass().add("section-title");

        // --- board table ---
        boardTable.setPlaceholder(new Label("No evidence in this case"));
        TableColumn<String[], String> idCol = new TableColumn<>("Id");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[0]));
        idCol.setPrefWidth(50);
        TableColumn<String[], String> descCol = new TableColumn<>("Evidence");
        descCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[1]));
        descCol.setPrefWidth(320);
        TableColumn<String[], String> suspectCol = new TableColumn<>("Suspect");
        suspectCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[2]));
        suspectCol.setPrefWidth(140);
        TableColumn<String[], String> locationCol = new TableColumn<>("Location");
        locationCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[3]));
        locationCol.setPrefWidth(140);
        TableColumn<String[], String> eventsCol = new TableColumn<>("Linked events");
        eventsCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[4]));
        eventsCol.setPrefWidth(160);
        boardTable.getColumns().setAll(List.of(idCol, descCol, suspectCol, locationCol, eventsCol));
        boardTable.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
            if (row != null) {
                evidencePicker.setValue("#" + row[0] + " " + row[1]);
                rebuildRelationPanel();
            }
        });

        // --- link actions ---
        Button linkSuspect = new Button("Link evidence → suspect");
        linkSuspect.setOnAction(e -> onLinkSuspect());
        Button linkLocation = new Button("Link evidence → location");
        linkLocation.setOnAction(e -> onLinkLocation());
        Button linkEvent = new Button("Link evidence → event");
        linkEvent.setOnAction(e -> onLinkEvent());

        HBox linkRow1 = new HBox(8, new Label("Evidence:"), evidencePicker,
                new Label("Suspect:"), suspectPicker, linkSuspect);
        HBox linkRow2 = new HBox(8, new Label("Location:"), locationPicker, linkLocation,
                new Label("Event:"), eventPicker, linkEvent);
        linkRow1.setAlignment(Pos.CENTER_LEFT);
        linkRow2.setAlignment(Pos.CENTER_LEFT);

        VBox linkPanel = new VBox(8, linkRow1, linkRow2);
        linkPanel.getStyleClass().add("panel");
        linkPanel.setPadding(new Insets(10));

        // --- search ---
        searchField.setPromptText("Search evidence by text...");
        Button searchButton = new Button("Search");
        searchButton.setOnAction(e -> onSearch());
        Button showAllButton = new Button("Show all");
        showAllButton.setOnAction(e -> {
            searchField.clear();
            rebuildBoardTable();
        });
        // NOTE: this is the field 'searchResult' (bugfix: a local with the
        // same name used to shadow it, so search updates went to a label
        // that was never on screen).
        searchResult.getStyleClass().add("caption");
        HBox searchRow = new HBox(8, searchField, searchButton, showAllButton, searchResult);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Label relationCaption = new Label("Connections of the selected evidence:");
        relationCaption.getStyleClass().add("caption");
        relationPanel.getStyleClass().add("panel");
        relationPanel.setPadding(new Insets(10));

        VBox leftColumn = new VBox(8,
                new Label("All evidence"), boardTable, searchRow, searchResult);
        VBox.setVgrow(boardTable, Priority.ALWAYS);

        VBox layout = new VBox(10, heading, linkPanel, relationCaption, relationPanel, leftColumn);
        VBox.setVgrow(leftColumn, Priority.ALWAYS);
        layout.setPadding(new Insets(10));
        return layout;
    }

    private void onLinkSuspect() {
        int evidenceId = DashboardScreen.extractId(evidencePicker.getValue());
        int suspectId = DashboardScreen.extractId(suspectPicker.getValue());
        if (evidenceId < 0 || suspectId < 0) {
            app().showError("Select both a piece of evidence and a suspect");
            return;
        }
        try {
            controller.associateWithSuspect(evidenceId, suspectId);
            refresh();
        } catch (InvestigationController.ControllerException ex) {
            app().showError(ex.getMessage());
        }
    }

    private void onLinkLocation() {
        int evidenceId = DashboardScreen.extractId(evidencePicker.getValue());
        int locationId = DashboardScreen.extractId(locationPicker.getValue());
        if (evidenceId < 0 || locationId < 0) {
            app().showError("Select both a piece of evidence and a location");
            return;
        }
        try {
            controller.associateWithLocation(evidenceId, locationId);
            refresh();
        } catch (InvestigationController.ControllerException ex) {
            app().showError(ex.getMessage());
        }
    }

    private void onLinkEvent() {
        int evidenceId = DashboardScreen.extractId(evidencePicker.getValue());
        int eventId = DashboardScreen.extractId(eventPicker.getValue());
        if (evidenceId < 0 || eventId < 0) {
            app().showError("Select both a piece of evidence and an event");
            return;
        }
        try {
            controller.associateWithEvent(evidenceId, eventId);
            refresh();
        } catch (InvestigationController.ControllerException ex) {
            app().showError(ex.getMessage());
        }
    }

    private void onSearch() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            app().showInfo("Type a search text first");
            return;
        }
        List<Evidence> hits = controller.searchEvidence(query);
        searchResult.setText(hits.size() + " hit(s) for \"" + query + "\"");
        boardTable.setItems(FXCollections.observableArrayList(hits.stream()
                .map(this::toRow)
                .toList()));
    }

    private String[] toRow(Evidence item) {
        List<Integer> eventIds = new ArrayList<>(controller.getLinkedEventIds(item.getId()));
        return new String[] {
                String.valueOf(item.getId()),
                item.getDescription(),
                item.getLinkedCharacter() == null ? "-" : item.getLinkedCharacter().getName(),
                item.getLinkedLocation() == null ? "-" : item.getLinkedLocation().getName(),
                eventIds.isEmpty() ? "-" : eventIds.stream().map(id -> "#" + id)
                        .reduce((a, b) -> a + ", " + b).orElse("-")};
    }

    private void rebuildBoardTable() {
        boardTable.setItems(FXCollections.observableArrayList(
                controller.getEvidence().stream().map(this::toRow).toList()));
    }

    /** Lists every relationship the session knows about for one clue. */
    private void rebuildRelationPanel() {
        relationPanel.getChildren().clear();
        int evidenceId = DashboardScreen.extractId(evidencePicker.getValue());
        if (evidenceId < 0) {
            relationPanel.getChildren().add(new Label("Select a clue to see its connections."));
            return;
        }
        var itemOpt = controller.getCase().findEvidenceById(evidenceId);
        if (itemOpt.isEmpty()) {
            relationPanel.getChildren().add(new Label("No such evidence."));
            return;
        }
        Evidence item = itemOpt.get();
        relationPanel.getChildren().add(new Label("Evidence #" + item.getId()
                + " [" + item.getPriority() + "] " + item.getDescription()));

        relationPanel.getChildren().add(new Label("  suspect: "
                + (item.getLinkedCharacter() == null ? "none"
                        : item.getLinkedCharacter().getName() + " ("
                                + item.getLinkedCharacter().getRole() + ")")));
        relationPanel.getChildren().add(new Label("  location: "
                + (item.getLinkedLocation() == null ? "none"
                        : item.getLinkedLocation().getName())));

        var linkedEvents = controller.getLinkedEventIds(evidenceId);
        if (linkedEvents.isEmpty()) {
            relationPanel.getChildren().add(new Label("  events: none linked"));
        } else {
            for (Integer eventId : linkedEvents) {
                String description = controller.getCase().findEventById(eventId)
                        .map(Event::getDescription).orElse("unknown event");
                relationPanel.getChildren().add(new Label(
                        "  event: #" + eventId + " " + description));
            }
        }
    }

    private ChronoCaseApp app() {
        return ChronoCaseApp.getInstance();
    }
}
