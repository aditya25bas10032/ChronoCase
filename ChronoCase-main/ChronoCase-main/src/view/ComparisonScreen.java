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
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.Event;
import model.Timeline;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Timeline Comparison: pick two timelines and see them side by side.
 * Every event position is a row - identical events in green,
 * changed events in amber, events missing on one side in red - so
 * the divergence between the branches is visible at a glance. Below,
 * the evidence verdicts against either timeline.
 */
class ComparisonScreen {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InvestigationController controller;
    private final VBox view;

    private final ComboBox<String> firstPicker = new ComboBox<>();
    private final ComboBox<String> secondPicker = new ComboBox<>();
    private final TableView<String[]> diffTable = new TableView<>();
    private final VBox contradictionPanel = new VBox(4);
    private boolean compared = false;

    ComparisonScreen(InvestigationController root) {
        this.controller = root;
        this.view = build();
        refresh();
    }

    Node getView() {
        return view;
    }

    void refresh() {
        List<Timeline> timelines = controller.getTimelines();
        List<String> labels = timelines.stream()
                .map(t -> "#" + t.getId() + " " + t.getLabel())
                .toList();
        firstPicker.getItems().setAll(labels);
        secondPicker.getItems().setAll(labels);
        if (!timelines.isEmpty()) {
            if (firstPicker.getValue() == null) {
                firstPicker.setValue(labels.get(0));
            }
            if (secondPicker.getValue() == null) {
                secondPicker.setValue(labels.size() > 1 ? labels.get(1) : labels.get(0));
            }
        }
        if (compared) {
            onCompare();
        }
    }

    private VBox build() {
        Label heading = new Label("Timeline Comparison");
        heading.getStyleClass().add("section-title");

        firstPicker.setMaxWidth(340);
        secondPicker.setMaxWidth(340);
        Button compareButton = new Button("Compare");
        compareButton.getStyleClass().add("accent");
        compareButton.setOnAction(e -> onCompare());

        HBox pickers = new HBox(8, new Label("Timeline A:"), firstPicker,
                new Label("Timeline B:"), secondPicker, compareButton);
        pickers.setAlignment(Pos.CENTER_LEFT);

        Label legend = new Label("green = identical event    amber = changed event    red = missing on one side");
        legend.getStyleClass().add("caption");

        buildDiffTable();

        Label contradictionCaption = new Label("Evidence verdicts (contradictions against either timeline):");
        contradictionCaption.getStyleClass().add("caption");
        contradictionPanel.getStyleClass().add("panel");
        contradictionPanel.setPadding(new Insets(10));
        contradictionPanel.getChildren().add(new Label("Press Compare to analyze."));

        VBox layout = new VBox(10, heading, pickers, legend, diffTable,
                contradictionCaption, contradictionPanel);
        VBox.setVgrow(diffTable, Priority.ALWAYS);
        layout.setPadding(new Insets(10));
        return layout;
    }

    private void buildDiffTable() {
        diffTable.setPlaceholder(new Label("Pick two timelines and press Compare"));
        String[] columns = {"Event", "Timeline A", "Timeline B", "Verdict"};
        for (int i = 0; i < columns.length; i++) {
            final int colIndex = i;
            TableColumn<String[], String> column = new TableColumn<>(columns[i]);
            column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[colIndex]));
            column.setPrefWidth(i == 0 ? 70 : i == 3 ? 150 : 330);
            diffTable.getColumns().add(column);
        }
        diffTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(String[] row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll("diff-identical", "diff-changed", "diff-missing");
                if (empty || row == null) {
                    return;
                }
                switch (row[3]) {
                    case "identical" -> getStyleClass().add("diff-identical");
                    case "changed" -> getStyleClass().add("diff-changed");
                    case "missing" -> getStyleClass().add("diff-missing");
                    default -> {
                    }
                }
            }
        });
    }

    private void onCompare() {
        Timeline first = findTimeline(firstPicker.getValue());
        Timeline second = findTimeline(secondPicker.getValue());
        if (first == null || second == null) {
            app().showError("Pick two timelines to compare");
            return;
        }
        try {
            List<InvestigationController.EventDiff> diffs = controller.diffTimelines(first, second);
            List<String[]> rows = new java.util.ArrayList<>();
            for (InvestigationController.EventDiff diff : diffs) {
                Event a = diff.getFirst();
                Event b = diff.getSecond();
                String verdict;
                if (a != null && b == null) {
                    verdict = "missing";
                } else if (a == null) {
                    verdict = "missing";
                } else if (diff.isIdentical()) {
                    verdict = "identical";
                } else {
                    verdict = "changed";
                }
                rows.add(new String[] {
                        "#" + diff.getEventId(),
                        a == null ? "-" : describe(a),
                        b == null ? "-" : describe(b),
                        verdict});
            }
            diffTable.setItems(FXCollections.observableArrayList(rows));

            InvestigationController.ComparisonResult result = controller.compareTimelines(first, second);
            contradictionPanel.getChildren().clear();
            contradictionPanel.getChildren().add(new Label(result.getContradictions().isEmpty()
                    ? "No contradictions against either timeline."
                    : result.getContradictions().size() + " contradiction(s):"));
            for (String line : result.getContradictions()) {
                Label row = new Label("  !! " + line);
                row.setWrapText(true);
                row.setMaxWidth(Double.MAX_VALUE);
                contradictionPanel.getChildren().add(row);
            }
            compared = true;
        } catch (RuntimeException ex) {
            app().showError(ex.getMessage());
        }
    }

    private String describe(Event e) {
        return e.getTimestamp().format(TIME_FORMAT) + " " + e.getDescription()
                + " [" + e.getCharacter().getName() + "]";
    }

    private Timeline findTimeline(String choice) {
        int id = DashboardScreen.extractId(choice);
        if (id < 0) {
            return null;
        }
        for (Timeline t : controller.getTimelines()) {
            if (t.getId() == id) {
                return t;
            }
        }
        return null;
    }

    private ChronoCaseApp app() {
        return ChronoCaseApp.getInstance();
    }
}
