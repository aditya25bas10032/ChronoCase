package view;

import controller.InvestigationController;
import controller.MissingPrototypeCase;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * GUI-level smoke test (temporary, run manually): starts the real
 * JavaFX toolkit, opens the app, enters a Missing Prototype
 * investigation and navigates through every screen, then saves
 * through the controller. Fails loudly if any screen construction,
 * refresh or navigation throws.
 */
public class ViewSmokeTest {

    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> progress = new AtomicReference<>("");

        Platform.startup(() -> { });
        Platform.runLater(() -> {
            try {
                ChronoCaseApp app = new ChronoCaseApp();
                Stage stage = new Stage();
                app.start(stage); // sets instance, shows the main menu

                model.Case caseData = InvestigationController.newCase(
                        InvestigationController.suggestCaseId(),
                        MissingPrototypeCase.TITLE, MissingPrototypeCase.DESCRIPTION);
                MissingPrototypeCase.populate(caseData);
                app.enterInvestigation(new InvestigationController(caseData));
                progress.set(progress.get() + "enterInvestigation ok; ");

                String[] screens = {"dashboard", "investigate", "evidence", "timeline",
                        "travel", "compare", "contradictions", "result", "dashboard"};
                for (String id : screens) {
                    app.showScreen(id);
                    if (!id.equals(app.getActiveScreenId())) {
                        throw new IllegalStateException("navigation to " + id + " failed");
                    }
                    progress.set(progress.get() + id + " ok; ");
                }

                // save through the controller (the Save dialog path's core)
                Path file = Files.createTempFile("chronocase-viewsmoke", ".txt");
                new InvestigationController(caseData).saveCase(file); // session copy sanity
                app.getController().saveCase(file);
                boolean exists = Files.size(file) > 0;
                Files.deleteIfExists(file);
                if (!exists) {
                    throw new IllegalStateException("save produced an empty file");
                }
                progress.set(progress.get() + "save ok; ");

                stage.close();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        if (failure.get() != null) {
            System.out.println("VIEW SMOKE TEST FAILED: " + failure.get());
            failure.get().printStackTrace();
            System.exit(1);
        }
        System.out.println("VIEW SMOKE TEST PASSED: " + progress.get());
        System.exit(0);
    }
}
