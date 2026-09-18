package view;

import controller.InvestigationController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.Timeline;

import java.util.List;

/**
 * Contradiction detection: pick the timelines to interrogate, run
 * the ContradictionDetector and read the verdict - every finding
 * names the evidence, the affected timeline and the explanation.
 * Contradictions and consistent (supporting) evidence are shown in
 * two separate lists so the timeline that holds up is as visible as
 * the one that falls apart.
 */
class ContradictionScreen {

    private final InvestigationController controller;
    private final VBox view;

    private final ListView<String> timelineList = new ListView<>();
    private final VBox contradictionPanel = new VBox(6);
    private final VBox consistentPanel = new VBox(6);

    ContradictionScreen(InvestigationController controller) {
        this.controller = controller;
        this.view = build();
        refresh();
    }

    Node getView() {
        return view;
    }

    void refresh() {
        List<Timeline> timelines = controller.getTimelines();
        timelineList.getItems().setAll(timelines.stream()
                .map(t -> "#" + t.getId() + " " + t.getLabel()
                        + (t == controller.getCurrentTimeline() ? "   [current]" : ""))
                .toList());
        timelineList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        Timeline current = controller.getCurrentTimeline();
        if (current != null) {
            timelineList.getItems().stream()
                    .filter(item -> DashboardScreen.extractId(item) == current.getId())
                    .findFirst()
                    .ifPresent(timelineList.getSelectionModel()::select);
        }
    }

    private VBox build() {
        Label heading = new Label("Contradiction Detection");
        heading.getStyleClass().add("section-title");
        Label explainer = new Label("Every piece of evidence is checked against the selected "
                + "timelines: does it agree with what happened (SUPPORTS) or does it clash "
                + "(CONTRADICTS)? Neutral evidence asserts nothing comparable.");
        explainer.getStyleClass().add("caption");
        explainer.setWrapText(true);
        explainer.setMaxWidth(Double.MAX_VALUE);

        timelineList.setPrefHeight(140);
        timelineList.setPlaceholder(new Label("No timelines yet."));
        VBox listPanel = new VBox(6, new Label("Timelines to check (multi-select):"), timelineList);
        listPanel.getStyleClass().add("panel");
        listPanel.setPadding(new Insets(10));

        Button runButton = new Button("Run contradiction detection");
        runButton.getStyleClass().add("accent");
        runButton.setOnAction(e -> onRun());

        Label contradictionsCaption = new Label("Contradictions - evidence that clashes with a timeline:");
        contradictionsCaption.getStyleClass().add("caption");
        contradictionPanel.getStyleClass().add("panel");
        contradictionPanel.setPadding(new Insets(10));
        contradictionPanel.getChildren().add(new Label("Press \"Run contradiction detection\"."));

        Label consistentCaption = new Label("Consistent evidence - corroborated by a timeline:");
        consistentCaption.getStyleClass().add("caption");
        consistentPanel.getStyleClass().add("panel");
        consistentPanel.setPadding(new Insets(10));
        consistentPanel.getChildren().add(new Label("-"));

        VBox layout = new VBox(10, heading, explainer, listPanel,
                new HBox(runButton),
                contradictionsCaption, contradictionPanel,
                consistentCaption, consistentPanel);
        ((HBox) layout.getChildren().get(3)).setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(contradictionPanel, Priority.SOMETIMES);
        layout.setPadding(new Insets(10));
        return layout;
    }

    private void onRun() {
        List<String> selected = timelineList.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            app().showError("Select at least one timeline to check");
            return;
        }
        List<Timeline> targets = new java.util.ArrayList<>();
        for (String choice : selected) {
            int id = DashboardScreen.extractId(choice);
            for (Timeline t : controller.getTimelines()) {
                if (t.getId() == id) {
                    targets.add(t);
                    break;
                }
            }
        }
        if (targets.isEmpty()) {
            app().showError("Could not resolve the selected timelines");
            return;
        }
        try {
            List<InvestigationController.FindingRow> findings = controller.runContradictionCheck(targets);

            List<InvestigationController.FindingRow> contradictions = findings.stream()
                    .filter(f -> f.getVerdict() == service.ContradictionDetector.Verdict.CONTRADICTS)
                    .toList();
            List<InvestigationController.FindingRow> supporting = findings.stream()
                    .filter(f -> f.getVerdict() == service.ContradictionDetector.Verdict.SUPPORTS)
                    .toList();
            int neutral = findings.size() - contradictions.size() - supporting.size();

            contradictionPanel.getChildren().clear();
            Label contradictionHeader = new Label(contradictions.size() + " contradiction(s), "
                    + supporting.size() + " consistent finding(s), " + neutral + " neutral.");
            contradictionHeader.getStyleClass().add("section-title");
            contradictionPanel.getChildren().add(contradictionHeader);
            if (contradictions.isEmpty()) {
                contradictionPanel.getChildren().add(new Label(
                        "Nothing clashes - the selected timelines agree with the evidence."));
            }
            for (InvestigationController.FindingRow f : contradictions) {
                contradictionPanel.getChildren().add(findingRow("!!", f));
            }

            consistentPanel.getChildren().clear();
            if (supporting.isEmpty()) {
                consistentPanel.getChildren().add(new Label(
                        "No evidence actively supports the selected timelines."));
            }
            for (InvestigationController.FindingRow f : supporting) {
                consistentPanel.getChildren().add(findingRow("ok", f));
            }
        } catch (RuntimeException ex) {
            app().showError(ex.getMessage());
        }
    }

    private Label findingRow(String marker, InvestigationController.FindingRow f) {
        String evidenceText = f.getEvidence().getId() + " [" + f.getEvidence().getPriority() + "] "
                + f.getEvidence().getDescription();
        Label row = new Label(marker + "  Evidence #" + evidenceText
                + "  vs  timeline #" + f.getTimeline().getId() + " \""
                + f.getTimeline().getLabel() + "\":  " + f.getReason());
        row.setWrapText(true);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add(marker.equals("!!") ? "finding-contradiction" : "finding-support");
        return row;
    }

    private ChronoCaseApp app() {
        return ChronoCaseApp.getInstance();
    }
}
