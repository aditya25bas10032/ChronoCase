package view;

import javafx.application.Application;

/**
 * Command-line entry point for the GUI.
 *
 * The JDK launcher refuses to start a main class that extends
 * {@link javafx.application.Application} unless the JavaFX jars are
 * on the MODULE path ("JavaFX runtime components are missing").
 * This plain class bypasses that check: it can be started with the
 * jars on the ordinary classpath, which is also what IntelliJ's
 * default run configuration does.
 *
 * Preferred invocation (see run-gui.sh / run-gui.bat):
 *   java -cp "out;lib/javafx-*-26.jar" view.ChronoCaseLauncher
 *
 * The module-path form for ChronoCaseApp itself keeps working too:
 *   java --module-path lib/... --add-modules javafx.controls -cp out view.ChronoCaseApp
 */
public class ChronoCaseLauncher {

    public static void main(String[] args) {
        Application.launch(ChronoCaseApp.class, args);
    }
}
