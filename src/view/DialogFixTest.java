package view;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression test (temporary, run manually) for the two dialog bugs:
 *
 * 1. The "New Investigation" OK button used to start disabled even
 *    though the title field is pre-filled, because only a text-change
 *    listener could enable it. It must start ENABLED and track edits.
 * 2. Alerts and dialogs built their own Scene without the app
 *    stylesheet and rendered in the default white Modena theme.
 *    Every dialog pane must now carry /view/styles.css.
 */
public class DialogFixTest {

    private static final String CSS_URL =
            DialogFixTest.class.getResource("/view/styles.css").toExternalForm();

    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> progress = new AtomicReference<>("");

        Platform.startup(() -> { });
        Platform.runLater(() -> {
            try {
                // --- Bug 1: OK button state vs. pre-filled title field ---
                javafx.scene.control.Dialog<ChronoCaseApp.NewCaseData> dialog =
                        ChronoCaseApp.buildNewCaseDialog();
                Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
                assertTrue(ok != null, "OK button must exist");
                assertTrue(!ok.isDisable(),
                        "OK must start ENABLED when the title is pre-filled");
                assertTrue(dialog.getDialogPane().getStylesheets().contains(CSS_URL),
                        "New Investigation dialog must use the app stylesheet");

                javafx.scene.control.TextField title = (javafx.scene.control.TextField)
                        ((javafx.scene.layout.VBox) dialog.getDialogPane().getContent()).getChildren().get(1);
                title.clear();
                assertTrue(ok.isDisable(),
                        "OK must disable while the title is blank");
                title.setText("Reopened Case");
                assertTrue(!ok.isDisable(),
                        "OK must re-enable once the title has text");
                progress.set(progress.get() + "ok-button ok; ");

                // --- Bug 2: alerts carry the stylesheet ---
                Alert alert = new Alert(Alert.AlertType.ERROR, "test", ButtonType.OK);
                ChronoCaseApp.styleDialog(alert);
                assertTrue(alert.getDialogPane().getStylesheets().contains(CSS_URL),
                        "Alert dialog pane must use the app stylesheet");
                progress.set(progress.get() + "alert-style ok; ");
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        if (failure.get() != null) {
            System.out.println("DIALOG FIX TEST FAILED: " + failure.get().getMessage());
            failure.get().printStackTrace();
            System.exit(1);
        }
        System.out.println("DIALOG FIX TEST PASSED: " + progress.get());
        System.exit(0);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
