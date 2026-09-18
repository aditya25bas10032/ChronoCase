package view;

import controller.InvestigationController;
import controller.MissingPrototypeCase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * ChronoCase GUI shell. Pure view code: every user action is
 * forwarded to {@link InvestigationController}, which owns the model
 * and services. No business logic lives in this class - handlers
 * only collect input, call the controller and show its results.
 *
 * Structure: a main menu (New / Load / Exit) leads into the
 * investigation shell - a header with the case identity and save
 * action, a left navigation sidebar for the seven investigation
 * screens, and the active screen in the centre.
 */
public class ChronoCaseApp extends Application {

    private static ChronoCaseApp instance;

    /** The running app, for screens to reach the shared dialogs. */
    static ChronoCaseApp getInstance() {
        return instance;
    }

    private Stage stage;
    private InvestigationController controller;
    private BorderPane investigationLayout;
    private VBox sidebar;
    private String activeScreenId = "dashboard";
    private String lastNavError = "";

    private DashboardScreen dashboardScreen;
    private TimelineScreen timelineScreen;
    private InvestigationScreen investigationScreen;
    private EvidenceBoardScreen evidenceScreen;
    private TimeTravelScreen timeTravelScreen;
    private ComparisonScreen comparisonScreen;
    private ContradictionScreen contradictionScreen;
    private ResultScreen resultScreen;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        instance = this;

        // Diagnostic logging (checklist: no silently swallowed exceptions).
        // Any exception inside an event handler or a rendering pass used
        // to vanish into the FX thread's default handler - now it is
        // printed AND surfaced in a dialog, so a dead-looking button
        // always explains itself.
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("[ChronoCase] Uncaught exception on " + thread.getName() + ":");
            throwable.printStackTrace();
            try {
                showError("Unexpected error: " + throwable);
            } catch (Throwable secondary) {
                // dialog itself failed; the console trace above remains
            }
        });
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("[ChronoCase] Uncaught exception on " + thread.getName() + ":");
            throwable.printStackTrace();
        });

        stage.setTitle("ChronoCase");
        showMainMenu();
        stage.show();
    }

    // ----- main menu -----

    private void showMainMenu() {
        Label title = new Label("ChronoCase");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Branching timelines. Time travel. One truth.");
        subtitle.getStyleClass().add("app-subtitle");

        Button newButton = new Button("New Investigation");
        newButton.setMaxWidth(Double.MAX_VALUE);
        newButton.getStyleClass().add("accent");
        newButton.setOnAction(e -> onNewInvestigation());

        Button loadButton = new Button("Load Investigation");
        loadButton.setMaxWidth(Double.MAX_VALUE);
        loadButton.setOnAction(e -> onLoadInvestigation());

        Button loadDbButton = new Button("Load from Database...");
        loadDbButton.setMaxWidth(Double.MAX_VALUE);
        loadDbButton.setOnAction(e -> onLoadFromDatabase());

        Button exitButton = new Button("Exit");
        exitButton.setMaxWidth(Double.MAX_VALUE);
        exitButton.getStyleClass().add("danger");
        exitButton.setOnAction(e -> stage.close());

        Label hint = new Label("New Investigation opens CASE-001 \"The Missing Prototype\":\n"
                + "4 suspects, 5 locations, 13 events, 8 evidence items and the\n"
                + "timeline tree ROOT / TIMELINE-A / TIMELINE-B / TIMELINE-C -\n"
                + "ready for time travel, contradiction checks and the solver.");
        hint.getStyleClass().add("caption");
        hint.setWrapText(true);
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox menu = new VBox(12, title, subtitle, newButton, loadButton, loadDbButton, exitButton, hint);
        menu.setAlignment(Pos.CENTER);
        menu.setPadding(new Insets(40));
        menu.setMaxWidth(360);

        BorderPane root = new BorderPane(menu);
        BorderPane.setAlignment(menu, Pos.CENTER);
        Scene scene = new Scene(root, 900, 640);
        scene.getStylesheets().add(getClass().getResource("/view/styles.css").toExternalForm());
        stage.setScene(scene);
    }

    private void onNewInvestigation() {
        // Test hook: headless runs supply the title/description directly.
        if (newCaseHook != null) {
            String[] prefill = newCaseHook.get();
            openCase(prefill[0], prefill[1]);
            return;
        }
        Dialog<NewCaseData> dialog = buildNewCaseDialog();
        dialog.initOwner(stage); // keep the dialog in front of the main window
        Optional<NewCaseData> result = dialog.showAndWait();
        result.ifPresent(data -> openCase(data.title, data.description));
    }

    /**
     * Builds the "New Investigation" dialog without showing it. Static
     * and side-effect free so headless tests can assert the OK button's
     * initial state against the pre-filled fields without a stage.
     */
    static Dialog<NewCaseData> buildNewCaseDialog() {
        Dialog<NewCaseData> dialog = new Dialog<>();
        dialog.setTitle("New Investigation");
        dialog.setHeaderText("Open the \"Missing Prototype\" case");
        styleDialog(dialog); // dialogs build their own scene; keep the dark theme

        javafx.scene.control.TextField titleField = new javafx.scene.control.TextField(
                MissingPrototypeCase.TITLE);
        titleField.setPromptText("Case title");
        javafx.scene.control.TextArea descriptionArea = new javafx.scene.control.TextArea(
                MissingPrototypeCase.DESCRIPTION);
        descriptionArea.setPromptText("What happened?");
        descriptionArea.setPrefRowCount(4);
        descriptionArea.setWrapText(true);

        dialog.getDialogPane().setContent(new VBox(8,
                new Label("Title:"), titleField,
                new Label("Description:"), descriptionArea));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        final Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        // The title is pre-filled, so the initial state must reflect its current
        // value - the listener below only fires on later edits.
        okButton.setDisable(titleField.getText().isBlank());
        titleField.textProperty().addListener(
                (obs, old, value) -> okButton.setDisable(value.isBlank()));

        dialog.setResultConverter(button ->
                button == ButtonType.OK ? new NewCaseData(titleField.getText(), descriptionArea.getText()) : null);
        return dialog;
    }

    /** Builds the seeded case and enters the investigation shell. */
    private void openCase(String title, String description) {
        try {
            // Build the case data first (including its root timeline),
            // then hand it to the controller - the constructor adopts
            // the first root as the investigation's starting timeline.
            model.Case caseData = InvestigationController.newCase(
                    InvestigationController.suggestCaseId(), title, description);
            MissingPrototypeCase.populate(caseData);
            enterInvestigation(new InvestigationController(caseData));
        } catch (InvestigationController.ControllerException ex) {
            showError(ex.getMessage());
        }
    }

    private void onLoadInvestigation() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load ChronoCase file");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("ChronoCase files", "*.txt", "*"));
        java.io.File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) {
            return;
        }
        Path file = chosen.toPath();
        try {
            enterInvestigation(InvestigationController.loadCase(file));
        } catch (InvestigationController.ControllerException ex) {
            showError(ex.getMessage());
        }
    }

    private void onLoadFromDatabase() {
        List<Integer> ids = InvestigationController.listDatabaseCaseIds();
        if (ids.isEmpty()) {
            showInfo("No saved cases are reachable in MySQL.\n\n"
                    + "Set CHRONOCASE_DB_URL / _USER / _PASSWORD, start the server\n"
                    + "and save an investigation into it first.");
            return;
        }
        TextInputDialog picker = new TextInputDialog(String.valueOf(ids.get(0)));
        picker.setTitle("Load from Database");
        picker.setHeaderText("Saved case ids: " + ids);
        picker.setContentText("Case id to load:");
        picker.initOwner(stage);
        styleDialog(picker);
        Optional<String> answer = picker.showAndWait();
        if (answer.isEmpty()) {
            return;
        }
        try {
            int id = Integer.parseInt(answer.get().trim());
            InvestigationController loaded = InvestigationController.loadCaseFromDatabase(id);
            if (loaded == null) {
                showInfo("No saved case with id " + id + " in the database.");
                return;
            }
            enterInvestigation(loaded);
        } catch (NumberFormatException ex) {
            showError("'" + answer.get() + "' is not a case id.");
        } catch (RuntimeException ex) {
            showError("Could not load from the database: " + ex.getMessage());
        }
    }

    // ----- investigation shell -----

    void enterInvestigation(InvestigationController controller) {
        this.controller = controller;
        this.activeScreenId = "dashboard";

        dashboardScreen = new DashboardScreen(controller);
        timelineScreen = new TimelineScreen(controller);
        investigationScreen = new InvestigationScreen(controller);
        evidenceScreen = new EvidenceBoardScreen(controller);
        timeTravelScreen = new TimeTravelScreen(controller);
        comparisonScreen = new ComparisonScreen(controller);
        contradictionScreen = new ContradictionScreen(controller);
        resultScreen = new ResultScreen(controller);

        investigationLayout = new BorderPane();
        investigationLayout.setTop(buildHeader());
        investigationLayout.setLeft(buildSidebar());
        investigationLayout.setCenter(dashboardScreen.getView());
        BorderPane.setMargin(investigationLayout.getCenter(), new Insets(10));

        Scene scene = new Scene(investigationLayout, 1180, 760);
        scene.getStylesheets().add(getClass().getResource("/view/styles.css").toExternalForm());
        stage.setScene(scene);
        showScreen("dashboard");
    }

    private javafx.scene.Node buildHeader() {
        var kase = controller.getCase();
        Label caseHeading = new Label("Case #" + kase.getId() + ": " + kase.getTitle());
        caseHeading.getStyleClass().add("case-heading");

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add("accent");
        saveButton.setOnAction(e -> onSaveInvestigation());

        Button menuButton = new Button("Main Menu");
        menuButton.setOnAction(e -> {
            if (confirm("Leave the investigation? Unsaved changes are kept only in memory.")) {
                showMainMenu();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, caseHeading, spacer, saveButton, menuButton);
        header.setPadding(new Insets(10, 14, 10, 14));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header-bar");
        return header;
    }

    private javafx.scene.Node buildSidebar() {
        sidebar = new VBox(4);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(12, 8, 12, 8));
        sidebar.setPrefWidth(190);

        Label navTitle = new Label("INVESTIGATION");
        navTitle.getStyleClass().add("sidebar-title");
        sidebar.getChildren().add(navTitle);
        sidebar.getChildren().addAll(
                navButton("Dashboard", "dashboard"),
                navButton("Investigate", "investigate"),
                navButton("Evidence Board", "evidence"),
                navButton("Solve Mystery", "result"));

        Label timeTitle = new Label("TIME");
        timeTitle.getStyleClass().add("sidebar-title");
        timeTitle.setPadding(new Insets(14, 0, 0, 0));
        sidebar.getChildren().addAll(
                timeTitle,
                navButton("Timeline", "timeline"),
                navButton("Time Travel", "travel"),
                navButton("Compare", "compare"),
                navButton("Contradictions", "contradictions"));

        Region filler = new Region();
        VBox.setVgrow(filler, Priority.ALWAYS);
        Label footer = new Label("changing the past\nnever destroys\nthe old timeline");
        footer.getStyleClass().add("sidebar-footer");
        sidebar.getChildren().addAll(filler, footer);
        return sidebar;
    }

    private Button navButton(String label, String screenId) {
        Button button = new Button(label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-item");
        button.setUserData(screenId);
        button.setOnAction(e -> showScreen(screenId));
        return button;
    }

    /** Switches the centre panel to the given screen and refreshes everything. */
    void showScreen(String screenId) {
        if (investigationLayout == null) {
            return;
        }
        javafx.scene.Node target;
        try {
            target = screenFor(screenId);
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
            return;
        }
        if (target == null) {
            return;
        }
        activeScreenId = screenId;
        refreshAllScreens();
        investigationLayout.setCenter(target);
        BorderPane.setMargin(target, new Insets(10));
        for (javafx.scene.Node child : sidebar.getChildren()) {
            if (child instanceof Button button && screenId.equals(button.getUserData())) {
                button.getStyleClass().add("nav-active");
            } else if (child instanceof Button button) {
                button.getStyleClass().removeAll("nav-active");
            }
        }
    }

    String getActiveScreenId() {
        return activeScreenId;
    }

    String getLastNavError() {
        return lastNavError;
    }

    private javafx.scene.Node screenFor(String screenId) {
        lastNavError = "";
        return switch (screenId) {
            case "dashboard" -> dashboardScreen.getView();
            case "investigate" -> investigationScreen.getView();
            case "evidence" -> evidenceScreen.getView();
            case "timeline" -> timelineScreen.getView();
            case "travel" -> timeTravelScreen.getView();
            case "compare" -> comparisonScreen.getView();
            case "contradictions" -> contradictionScreen.getView();
            case "result" -> resultScreen.getView();
            default -> {
                lastNavError = "Unknown screen: " + screenId;
                yield null;
            }
        };
    }

    /**
     * Saves the case through the controller (FileManager does the
     * file work); errors surface as a dialog, never as a dead button.
     */
    private void onSaveInvestigation() {
        java.io.File chosen;
        if (savePathHook != null) {
            java.nio.file.Path hooked = savePathHook.get();
            chosen = hooked == null ? null : hooked.toFile();
        } else {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save ChronoCase file");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("ChronoCase files", "*.txt"));
            chooser.setInitialFileName(
                    controller.getCase().getTitle().replaceAll("[^a-zA-Z0-9-]+", "-") + ".txt");
            chosen = chooser.showSaveDialog(stage);
        }
        if (chosen == null) {
            return;
        }
        try {
            controller.saveCase(chosen.toPath());
            showInfo("Case saved to " + chosen.getPath() + "\n\nThe file stores the case, every timeline "
                    + "branch, the collected clues and the timeline you were on.");
        } catch (InvestigationController.ControllerException ex) {
            showError(ex.getMessage());
        }
    }

    void refreshAllScreens() {
        dashboardScreen.refresh();
        timelineScreen.refresh();
        investigationScreen.refresh();
        evidenceScreen.refresh();
        timeTravelScreen.refresh();
        comparisonScreen.refresh();
        contradictionScreen.refresh();
        resultScreen.refresh();
    }

    /**
     * Applies the app stylesheet to a dialog pane. Alerts and Dialogs
     * create their own Scene, so without this they render with the
     * default white Modena theme instead of the dark theme.
     */
    static void styleDialog(javafx.scene.control.Dialog<?> dialog) {
        var css = ChronoCaseApp.class.getResource("/view/styles.css");
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
    }

    /** Error dialog used by every screen; safe to call from any handler. */
    void showError(String message) {
        if (dialogHook != null) {
            dialogHook.accept("ERROR", message);
            return;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        styleDialog(alert);
        alert.setHeaderText("Something went wrong");
        alert.initOwner(stage); // without an owner the dialog can open BEHIND
        alert.showAndWait();    // the main window and look like a dead button
    }

    /** Info dialog. */
    void showInfo(String message) {
        if (dialogHook != null) {
            dialogHook.accept("INFO", message);
            return;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        styleDialog(alert);
        alert.setHeaderText(null);
        alert.initOwner(stage);
        alert.showAndWait();
    }

    /**
     * Confirmation dialog. Returns true only when the user accepted;
     * handlers stay free of dialog plumbing beyond this call.
     */
    boolean confirm(String message) {
        if (dialogHook != null) {
            dialogHook.accept("CONFIRM", message);
            return false;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        styleDialog(alert);
        alert.setHeaderText("Please confirm");
        alert.initOwner(stage);
        return alert.showAndWait().filter(r -> r == ButtonType.OK).isPresent();
    }

    /**
     * Test hook: when set, dialogs are captured instead of shown, so
     * headless test runs never block on a modal window. Null in the
     * real app.
     */
    private java.util.function.BiConsumer<String, String> dialogHook;

    void setDialogHook(java.util.function.BiConsumer<String, String> hook) {
        this.dialogHook = hook;
    }

    /** Test hook: supplies the save path instead of a FileChooser; null skips. */
    private java.util.function.Supplier<java.nio.file.Path> savePathHook;

    void setSavePathHook(java.util.function.Supplier<java.nio.file.Path> hook) {
        this.savePathHook = hook;
    }

    /** Test hook: supplies new-case title/description instead of a dialog; null skips. */
    private java.util.function.Supplier<String[]> newCaseHook;

    void setNewCaseHook(java.util.function.Supplier<String[]> hook) {
        this.newCaseHook = hook;
    }

    Stage getStage() {
        return stage;
    }

    /** The active session's controller (package-private for tests/screens). */
    InvestigationController getController() {
        return controller;
    }

    /** Carries the dialog result; package-private so tests can type against it. */
    static final class NewCaseData {
        final String title;
        final String description;

        NewCaseData(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
