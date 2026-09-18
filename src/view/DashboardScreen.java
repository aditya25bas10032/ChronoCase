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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.Timeline;

import java.util.Optional;

/**
 * Investigation Dashboard: the home screen of a case. Shows the case
 * identity, the state of the investigation (stat cards, current
 * timeline, current investigation time) and quick navigation into
 * the major workflows. Evidence can also be collected and linked
 * here. All data comes from the controller; the handlers only parse
 * a picker selection and forward the ids.
 */
class DashboardScreen {

    private final InvestigationController controller;
    private final VBox view;

    private final FlowPane statsPane = new FlowPane(10, 10);
    private final VBox timelinePanel = new VBox(4);
    private final ComboBox<String> evidencePicker = new ComboBox<>();
    private final ComboBox<String> suspectPicker = new ComboBox<>();
    private final ComboBox<String> locationPicker = new ComboBox<>();
    private final TableView<String[]> suspectTable = new TableView<>();
    private final TableView<String[]> locationTable = new TableView<>();
    private final TableView<String[]> evidenceTable = new TableView<>();

    DashboardScreen(InvestigationController controller) {
        this.controller = controller;
        this.view = build();
        refresh();
    }

    Node getView() {
        return view;
    }

    /** Re-reads everything from the controller. */
    void refresh() {
        fillStats();
        rebuildTimelinePanel();
        evidencePicker.getItems().setAll(controller.getEvidence().stream()
                .map(DashboardScreen::labelEvidence)
                .toList());
        suspectPicker.getItems().setAll(controller.getSuspects().stream()
                .map(DashboardScreen::labelSuspect)
                .toList());
        locationPicker.getItems().setAll(controller.getLocations().stream()
                .map(DashboardScreen::labelLocation)
                .toList());

        suspectTable.setItems(FXCollections.observableArrayList(
                controller.getSuspects().stream()
                        .map(c -> new String[] {String.valueOf(c.getId()), c.getName(), c.getRole()})
                        .toList()));
        locationTable.setItems(FXCollections.observableArrayList(
                controller.getLocations().stream()
                        .map(l -> new String[] {String.valueOf(l.getId()), l.getName(), l.getDescription()})
                        .toList()));
        evidenceTable.setItems(FXCollections.observableArrayList(
                controller.getEvidence().stream()
                        .map(item -> new String[] {
                                String.valueOf(item.getId()),
                                item.getDescription(),
                                item.getPriority().name(),
                                item.getLinkedCharacter() == null ? "-" : item.getLinkedCharacter().getName(),
                                item.getLinkedLocation() == null ? "-" : item.getLinkedLocation().getName(),
                                controller.isCollected(item.getId()) ? "collected" : "-"})
                        .toList()));
    }

    /** Rebuilds the stat cards so counts stay current. */
    private void fillStats() {
        statsPane.getChildren().setAll(
                statCard("Suspects", controller.getSuspects().size()),
                statCard("Locations", controller.getLocations().size()),
                statCard("Events", controller.getCaseEvents().size()),
                statCard("Evidence", controller.getEvidence().size()),
                statCard("Collected", controller.getCollectedCount()),
                statCard("Timelines", controller.getTimelineCount()));
    }

    /** Case name, description, current timeline and investigation time. */
    private void rebuildTimelinePanel() {
        timelinePanel.getChildren().clear();

        var kase = controller.getCase();
        Label info = new Label(kase.getDescription());
        info.setWrapText(true);
        info.getStyleClass().add("case-info");
        timelinePanel.getChildren().add(info);

        Timeline current = controller.getCurrentTimeline();
        HBox timelineRow = new HBox(10);
        timelineRow.setAlignment(Pos.CENTER_LEFT);
        if (current == null) {
            timelineRow.getChildren().add(new Label("Current timeline: none yet"));
        } else {
            Label timelineChip = new Label("Current timeline:  #" + current.getId()
                    + "  " + current.getLabel()
                    + (current.isRoot() ? "  (root)" : "  (branch of #"
                            + current.getParent().getId() + ")"));
            timelineChip.getStyleClass().add("timeline-chip");
            timelineRow.getChildren().add(timelineChip);
        }

        String atEvent = controller.getCurrentEvent()
                .map(e -> "Investigating time:  " + e.getTimestamp().toLocalDate()
                        + " " + e.getTimestamp().toLocalTime()
                        + "  -  #" + e.getId() + " " + e.getDescription())
                .orElse("Investigating time:  case start (pick an event in Time Travel)");
        Label timeChip = new Label(atEvent);
        timeChip.getStyleClass().add("time-chip");
        timeChip.setWrapText(true);

        timelinePanel.getChildren().addAll(timelineRow, timeChip);
    }

    private static String labelEvidence(model.Evidence item) {
        String links;
        if (item.getLinkedCharacter() != null && item.getLinkedLocation() != null) {
            links = item.getLinkedCharacter().getName() + " @ " + item.getLinkedLocation().getName();
        } else if (item.getLinkedCharacter() != null) {
            links = item.getLinkedCharacter().getName();
        } else if (item.getLinkedLocation() != null) {
            links = item.getLinkedLocation().getName();
        } else {
            links = "unlinked";
        }
        return "#" + item.getId() + " [" + item.getPriority() + "] " + item.getDescription()
                + " (" + links + ")";
    }

    private static String labelSuspect(model.Character c) {
        return "#" + c.getId() + " " + c.getName();
    }

    private static String labelLocation(model.Location l) {
        return "#" + l.getId() + " " + l.getName();
    }

    private VBox build() {
        Label heading = new Label("Investigation Dashboard");
        heading.getStyleClass().add("section-title");

        VBox casePanel = new VBox(8, heading, timelinePanel);
        casePanel.getStyleClass().add("panel");
        casePanel.setPadding(new Insets(12));

        fillStats();
        FlowPane stats = statsPane;

        buildTable(suspectTable, "Id", "Name", "Role");
        buildTable(locationTable, "Id", "Name", "Description");
        buildTable(evidenceTable, "Id", "Description", "Priority", "Linked suspect", "Linked location", "Bag");

        Button collectButton = new Button("Collect selected evidence");
        collectButton.getStyleClass().add("accent");
        collectButton.setOnAction(e -> onCollect());

        Button linkSuspectButton = new Button("Link to suspect");
        linkSuspectButton.setOnAction(e -> onLinkSuspect());

        Button linkLocationButton = new Button("Link to location");
        linkLocationButton.setOnAction(e -> onLinkLocation());

        HBox evidenceActions = new HBox(8,
                new Label("Evidence:"), evidencePicker,
                collectButton,
                new Label("Suspect:"), suspectPicker, linkSuspectButton,
                new Label("Location:"), locationPicker, linkLocationButton);
        evidenceActions.setAlignment(Pos.CENTER_LEFT);
        evidenceActions.setPadding(new Insets(8, 0, 8, 0));

        VBox evidenceSection = new VBox(6,
                new Label("Evidence workbench - collect a clue, then link it to a suspect or location"),
                evidenceActions,
                evidenceTable);
        VBox.setVgrow(evidenceTable, Priority.ALWAYS);

        HBox tables = new HBox(12, suspectTable, locationTable);
        HBox.setHgrow(suspectTable, Priority.ALWAYS);
        HBox.setHgrow(locationTable, Priority.ALWAYS);
        suspectTable.setMinHeight(150);
        locationTable.setMinHeight(150);

        // --- adding new items (dialogs keep the panel compact) ---
        Button addSuspect = new Button("Add suspect...");
        addSuspect.setOnAction(e -> onAddSuspect());
        Button addLocation = new Button("Add location...");
        addLocation.setOnAction(e -> onAddLocation());
        Button addEvidence = new Button("Add evidence...");
        addEvidence.setOnAction(e -> onAddEvidence());
        Button addEvent = new Button("Add event...");
        addEvent.setOnAction(e -> onAddEvent());
        HBox creationPanel = new HBox(8, addSuspect, addLocation, addEvent, addEvidence);
        creationPanel.getStyleClass().add("panel");
        creationPanel.setPadding(new Insets(10));
        creationPanel.setAlignment(Pos.CENTER_LEFT);

        VBox layout = new VBox(12, casePanel, stats, creationPanel, tables, evidenceSection);
        VBox.setVgrow(evidenceSection, Priority.ALWAYS);
        layout.setPadding(new Insets(10));
        return layout;
    }

    private void onCollect() {
        int id = extractId(evidencePicker.getValue());
        if (id < 0) {
            showError("Select a piece of evidence first");
            return;
        }
        boolean added = controller.collectEvidence(id);
        refresh();
        if (added) {
            showInfo("Collected evidence #" + id + " - "
                    + controller.getCollectedCount() + " clue(s) in the bag");
        } else {
            showInfo("Evidence #" + id + " was already collected");
        }
    }

    // ----- adding new items (all through the controller) -----

    private void onAddSuspect() {
        Optional<String[]> input = prompt("Add suspect", "Name:", "Role:");
        input.ifPresent(fields -> {
            try {
                controller.addCharacter(new model.Character(controller.nextCharacterId(),
                        fields[0], fields[1]));
                refresh();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
    }

    private void onAddLocation() {
        Optional<String[]> input = prompt("Add location", "Name:", "Description:");
        input.ifPresent(fields -> {
            try {
                controller.addLocation(new model.Location(controller.nextLocationId(),
                        fields[0], fields[1]));
                refresh();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
    }

    private void onAddEvidence() {
        Optional<String[]> input = prompt("Add evidence", "Description:",
                "Priority (LOW/MEDIUM/HIGH/CRITICAL):");
        input.ifPresent(fields -> {
            try {
                controller.addEvidence(new model.Evidence(controller.nextEvidenceId(),
                        fields[0], null, null,
                        model.EvidencePriority.fromString(fields.length > 1
                                ? fields[1].trim().toUpperCase(java.util.Locale.ROOT) : "")));
                refresh();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
    }

    private void onAddEvent() {
        Optional<String[]> input = prompt("Add event",
                "Description:", "Time (yyyy-MM-dd HH:mm):",
                "Suspect id:", "Location id:");
        input.ifPresent(fields -> {
            try {
                model.Character actor = controller.getCase()
                        .findCharacterById(Integer.parseInt(fields[2].trim()))
                        .orElseThrow(() -> new InvestigationController.ControllerException(
                                "No suspect with id " + fields[2]));
                model.Location where = controller.getCase()
                        .findLocationById(Integer.parseInt(fields[3].trim()))
                        .orElseThrow(() -> new InvestigationController.ControllerException(
                                "No location with id " + fields[3]));
                model.Event event = new model.Event(controller.nextEventId(), fields[0],
                        java.time.LocalDateTime.parse(fields[1].trim(),
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        actor, where);
                controller.addEvent(event);
                if (controller.hasCurrentTimeline()) {
                    controller.addEventToTimeline(event);
                }
                refresh();
            } catch (java.time.format.DateTimeParseException ex) {
                showError("Use the format yyyy-MM-dd HH:mm for the time");
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
    }

    /** Small multi-field input dialog; empty answers are rejected. */
    private Optional<String[]> prompt(String title, String... prompts) {
        javafx.scene.control.Dialog<String[]> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.initOwner(app().getStage()); // keep the dialog in front
        ChronoCaseApp.styleDialog(dialog); // keep the dark theme inside dialogs
        javafx.scene.control.ButtonType okType =
                new javafx.scene.control.ButtonType("OK", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, javafx.scene.control.ButtonType.CANCEL);
        VBox fields = new VBox(8);
        javafx.util.Pair<String, javafx.scene.control.TextField>[] pairs =
                new javafx.util.Pair[prompts.length];
        for (int i = 0; i < prompts.length; i++) {
            javafx.scene.control.TextField field = new javafx.scene.control.TextField();
            field.setPromptText(prompts[i]);
            pairs[i] = new javafx.util.Pair<>(prompts[i], field);
            fields.getChildren().add(new HBox(8, new Label(prompts[i]), field));
            HBox.setHgrow(field, Priority.ALWAYS);
        }
        dialog.getDialogPane().setContent(fields);
        dialog.setResultConverter(button -> {
            if (button != okType) {
                return null;
            }
            String[] answers = new String[pairs.length];
            for (int i = 0; i < pairs.length; i++) {
                String value = pairs[i].getValue().getText();
                if (value == null || value.isBlank()) {
                    return null; // treated as cancel
                }
                answers[i] = value;
            }
            return answers;
        });
        Optional<String[]> result = dialog.showAndWait();
        if (result.isEmpty()) {
            // Distinguish a real cancel from blank-input rejection,
            // which used to look like the button doing nothing.
            boolean anyBlank = false;
            for (javafx.util.Pair<String, javafx.scene.control.TextField> pair : pairs) {
                String text = pair.getValue().getText();
                if (text == null || text.isBlank()) {
                    anyBlank = true;
                    break;
                }
            }
            if (anyBlank) {
                showInfo(title + ": every field needs a value.");
            }
            return Optional.empty();
        }
        return result;
    }

    private void onLinkSuspect() {
        int evidenceId = extractId(evidencePicker.getValue());
        int suspectId = extractId(suspectPicker.getValue());
        if (evidenceId < 0 || suspectId < 0) {
            showError("Select both a piece of evidence and a suspect");
            return;
        }
        try {
            controller.associateWithSuspect(evidenceId, suspectId);
            refresh();
            showInfo("Evidence #" + evidenceId + " linked to suspect #" + suspectId);
        } catch (InvestigationController.ControllerException ex) {
            showError(ex.getMessage());
        }
    }

    private void onLinkLocation() {
        int evidenceId = extractId(evidencePicker.getValue());
        int locationId = extractId(locationPicker.getValue());
        if (evidenceId < 0 || locationId < 0) {
            showError("Select both a piece of evidence and a location");
            return;
        }
        try {
            controller.associateWithLocation(evidenceId, locationId);
            refresh();
            showInfo("Evidence #" + evidenceId + " linked to location #" + locationId);
        } catch (InvestigationController.ControllerException ex) {
            showError(ex.getMessage());
        }
    }

    /** Configures a String[]-backed table with the given column titles. */
    private void buildTable(TableView<String[]> table, String... columns) {
        table.setPlaceholder(new Label("Nothing here yet"));
        for (int i = 0; i < columns.length; i++) {
            final int index = i;
            TableColumn<String[], String> column = new TableColumn<>(columns[i]);
            column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[index]));
            if (i == 0) {
                column.setPrefWidth(50);
            } else if (columns.length == 3) {
                column.setPrefWidth(200);
            } else {
                column.setPrefWidth(140);
            }
            table.getColumns().add(column);
        }
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private Node statCard(String label, int value) {
        Label count = new Label(String.valueOf(value));
        count.getStyleClass().add("stat-value");
        Label caption = new Label(label);
        caption.getStyleClass().add("stat-caption");
        VBox card = new VBox(2, count, caption);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(10));
        return card;
    }

    /** Parses "#12 rest of the label" into 12; -1 when unparsable. */
    static int extractId(String choice) {
        if (choice == null || !choice.startsWith("#")) {
            return -1;
        }
        int end = 1;
        while (end < choice.length() && Character.isDigit(choice.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(choice.substring(1, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void showError(String message) {
        app().showError(message);
    }

    private void showInfo(String message) {
        app().showInfo(message);
    }

    private ChronoCaseApp app() {
        return ChronoCaseApp.getInstance();
    }
}
