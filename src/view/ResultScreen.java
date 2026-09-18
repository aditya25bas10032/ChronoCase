package view;

import controller.InvestigationController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import service.MysterySolver;

import java.util.List;

/**
 * Mystery Solver: runs the solver through the controller and shows
 * the actual result - culprit, confidence, the full suspect ranking
 * with the explainable scoring reasons, supporting evidence and
 * contradictions, plus the timeline the verdict was judged against.
 * Nothing about the culprit is decided in the view; the screen only
 * renders {@link MysterySolver.MysteryResult}.
 */
class ResultScreen {

    private final InvestigationController controller;
    private final VBox view;

    private Button exportButton;
    private final VBox verdictPanel = new VBox(6);
    private final VBox rankingPanel = new VBox(4);
    private final VBox supportingPanel = new VBox(4);
    private final VBox contradictionPanel = new VBox(4);
    private final TextArea reportArea = new TextArea();

    ResultScreen(InvestigationController controller) {
        this.controller = controller;
        this.view = build();
        refresh();
    }

    Node getView() {
        return view;
    }

    void refresh() {
        // Show the last result again without re-solving (solving is
        // explicit via the button).
    }

    private VBox build() {
        Label heading = new Label("Solve the Mystery");
        heading.getStyleClass().add("section-title");
        Label explainer = new Label("The solver weighs the collected clues (or the whole case file "
                + "if nothing is collected yet) against the timelines: linked evidence by priority, "
                + "who acted at the decisive moment, contradictions and alibis. Every point is explained.");
        explainer.getStyleClass().add("caption");
        explainer.setWrapText(true);
        explainer.setMaxWidth(Double.MAX_VALUE);

        Button solveButton = new Button("Solve the case");
        solveButton.getStyleClass().add("accent");
        solveButton.setOnAction(e -> onSolve());

        Button exportButton = new Button("Export verdict report...");
        exportButton.setOnAction(e -> onExport());
        exportButton.setDisable(true);
        this.exportButton = exportButton;

        HBox actions = new HBox(8, solveButton, exportButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        verdictPanel.getStyleClass().add("panel");
        verdictPanel.setPadding(new Insets(12));
        verdictPanel.getChildren().add(new Label("Press \"Solve the case\" to weigh the evidence."));

        Label rankingCaption = new Label("Suspect ranking:");
        rankingCaption.getStyleClass().add("caption");
        rankingPanel.getStyleClass().add("panel");
        rankingPanel.setPadding(new Insets(10));
        rankingPanel.getChildren().add(new Label("-"));

        Label supportingCaption = new Label("Supporting evidence:");
        supportingCaption.getStyleClass().add("caption");
        supportingPanel.getStyleClass().add("panel");
        supportingPanel.setPadding(new Insets(10));
        supportingPanel.getChildren().add(new Label("-"));

        Label contradictionCaption = new Label("Contradictions:");
        contradictionCaption.getStyleClass().add("caption");
        contradictionPanel.getStyleClass().add("panel");
        contradictionPanel.setPadding(new Insets(10));
        contradictionPanel.getChildren().add(new Label("-"));

        reportArea.setEditable(false);
        reportArea.setWrapText(true);
        reportArea.getStyleClass().add("report");
        reportArea.setPrefHeight(200);

        VBox layout = new VBox(10, heading, explainer, actions,
                verdictPanel,
                rankingCaption, rankingPanel,
                supportingCaption, supportingPanel,
                contradictionCaption, contradictionPanel,
                new Label("Full verdict report:"), reportArea);
        VBox.setVgrow(reportArea, Priority.ALWAYS);
        layout.setPadding(new Insets(10));
        return layout;
    }

    private void onSolve() {
        try {
            MysterySolver.MysteryResult result = controller.solveCase();
            showResult(result);
            exportButton.setDisable(false);
        } catch (InvestigationController.ControllerException ex) {
            app().showError(ex.getMessage());
        } catch (RuntimeException ex) {
            app().showError("Could not solve the case: " + ex.getMessage());
        }
    }

    /** Writes the verdict report through the controller's FileManager path. */
    private void onExport() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export verdict report");
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Text files", "*.txt"));
        chooser.setInitialFileName("verdict-report.txt");
        java.io.File chosen = chooser.showSaveDialog(app().getStage());
        if (chosen == null) {
            return;
        }
        try {
            new io.FileManager().exportReport(
                    () -> List.of(reportArea.getText()), chosen.toPath());
            app().showInfo("Verdict report exported to " + chosen.getPath());
        } catch (java.io.IOException ex) {
            app().showError("Could not export the report: " + ex.getMessage());
        }
    }

    private void showResult(MysterySolver.MysteryResult result) {
        var culprit = result.getCulprit().getSuspect();
        Label headline = new Label("Most likely culprit: " + culprit.getName()
                + " - confidence " + result.getConfidence() + "%");
        headline.getStyleClass().add("verdict-headline");
        verdictPanel.getChildren().setAll(
                headline,
                new Label("Timeline used: #" + result.getTimelineUsed().getId()
                        + " \"" + result.getTimelineUsed().getLabel() + "\""),
                new Label(result.getNarrative()));

        rankingPanel.getChildren().clear();
        int place = 1;
        for (MysterySolver.SuspectScore score : result.getRankings()) {
            Label name = new Label(place + ". " + score.getSuspect().getName()
                    + "   " + score.getTotal() + " points"
                    + (place == 1 ? "   ◄ most likely" : ""));
            name.getStyleClass().add(place == 1 ? "ranking-first" : "ranking-row");
            rankingPanel.getChildren().add(name);
            for (MysterySolver.ScoreReason reason : score.getReasons()) {
                Label line = new Label("       " + reason);
                line.setWrapText(true);
                line.setMaxWidth(Double.MAX_VALUE);
                line.getStyleClass().add("ranking-reason");
                rankingPanel.getChildren().add(line);
            }
            place++;
        }

        supportingPanel.getChildren().clear();
        if (result.getSupportingEvidence().isEmpty()) {
            supportingPanel.getChildren().add(new Label("Nothing supports the accepted timeline."));
        } else {
            for (var f : result.getSupportingEvidence()) {
                Label row = new Label("  ok " + f);
                row.setWrapText(true);
                supportingPanel.getChildren().add(row);
            }
        }

        contradictionPanel.getChildren().clear();
        if (result.getContradictions().isEmpty()) {
            contradictionPanel.getChildren().add(new Label("No contradictions."));
        } else {
            for (var f : result.getContradictions()) {
                Label row = new Label("  !! " + f);
                row.setWrapText(true);
                row.setMaxWidth(Double.MAX_VALUE);
                contradictionPanel.getChildren().add(row);
            }
        }

        reportArea.setText(String.join("\n", controller.renderVerdict(result)));
    }

    private ChronoCaseApp app() {
        return ChronoCaseApp.getInstance();
    }
}
