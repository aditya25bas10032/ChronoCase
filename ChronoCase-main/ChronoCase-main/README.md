# ChronoCase

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A time-travel detective app for the desktop. You open a mystery case and collect
suspects, locations, events and evidence. You then go back in time and
change an event. The change does not destroy the old timeline. It creates
a new branch. You can compare every branch, and evidence can contradict
it. At the end, the rule-based solver names the most likely culprit and
shows the reasoning.

The app is plain Java and JavaFX, with separate layers:

```
JavaFX view  →  controller  →  services  →  model  →  file I/O / MySQL
```

---

## Features

- Investigation dashboard: the case, suspects, locations, events and evidence
- Visual timeline: a branch map with one box per timeline and a
  chronological event list
- Time travel: pick an earlier event and write a revision. The app creates
  a new branch. The original timeline stays unchanged
- Timeline comparison: the events that differ between two timelines, and
  the evidence that contradicts them
- Evidence board: link clues to suspects, locations and events. The
  board has a text search
- Mystery solver: suspicion points per suspect, a confidence percentage,
  supporting and contradicting evidence, and an exportable verdict report
- Save and load: CHRONOCASE-V1 text files. The files work without an
  internet connection
- Optional MySQL persistence: a relational schema with foreign keys

---

## Requirements

| Software | Version | Notes |
|---|---|---|
| JDK | 21 or newer. The project is tested on JDK 26 | If the compile step fails on an older JDK, install a newer JDK. `java -version` shows the installed version |
| Internet connection | once | To download the JavaFX and MySQL libraries |
| MySQL server | a recent 8.x | Optional. Only for database saving |

You do not need a build tool such as Maven or Gradle. The scripts use
`javac` and `java` directly.

---

## Install

### Windows (cmd, or double-click)

```bat
get-libraries.bat
build.bat   (or: bash build.sh from Git Bash)
```

### Windows, Linux and macOS (bash)

```bash
bash get-libraries.sh   # downloads JavaFX 26 + MySQL driver into lib/ (once)
bash build.sh           # compiles everything into out/
```

That is all. The scripts get the libraries from Maven Central on the first
use. After that, you do not need an internet connection.

## Run

### GUI

```bash
bash run-gui.sh          # Windows: double-click run-gui.bat
```

### Console demo

The console demo shows the model, timelines, solver, file I/O and database
layers.

```bash
java -cp "out;lib/mysql-connector-j-9.4.0.jar" Main        # Windows
java -cp "out:lib/mysql-connector-j-9.4.0.jar" Main        # Linux/macOS
```

### Tests

A normal `bash build.sh` compiles every test class together with the
sources. You do not need a separate compile step.

#### ModelTest (293 checks)

```bash
java -cp out ModelTest
```

The test covers the model, the services and CHRONOCASE-V1 file
round-trips.

#### GuiFlowTest (controller flow)

```bash
java -cp "out;lib/mysql-connector-j-9.4.0.jar" GuiFlowTest
```

The test replays the exact 20-step walkthrough (New, Travel, Branch,
Compare, Solve, Save, Load) through the controller APIs. It runs without
a window (headless).

#### JavaFX view tests

The three tests start the real JavaFX toolkit without a window. On Linux
and macOS, replace `;` with `:` in the classpath.

```bash
java -cp "out;lib/javafx-base-26.jar;lib/javafx-graphics-26.jar;lib/javafx-controls-26.jar" view.ViewSmokeTest
java -cp "out;lib/javafx-base-26.jar;lib/javafx-graphics-26.jar;lib/javafx-controls-26.jar" view.ViewPlaythrough
java -cp "out;lib/javafx-base-26.jar;lib/javafx-graphics-26.jar;lib/javafx-controls-26.jar" view.DialogFixTest
```

- ViewSmokeTest opens a case with seed data, walks all eight screens and
  saves. It ends with `VIEW SMOKE TEST PASSED`.
- ViewPlaythrough plays CASE-001 like a user. It presses buttons, changes
  pickers and selects tabs. An error dialog counts as a failure. It ends
  with `RESULT: 79 passed, 0 failed, 0 UX notes`.
- DialogFixTest covers two dialog regressions. The OK button of the New
  Investigation dialog starts enabled with a pre-filled title, and every
  dialog pane carries the app stylesheet. The dialogs then use the same
  dark theme as the main window.

> On Windows, the scripts use `;` as the classpath separator. On Linux and
> macOS, they use `:`. The provided scripts already use the right
> separator for their platform.

### Optional: MySQL database saving

1. Install MySQL and start it. Create the database:
   ```sql
   CREATE DATABASE chronocase;
   ```
2. Set the three environment variables. Do not put the password in the
   source code:
   ```bash
   export CHRONOCASE_DB_URL="jdbc:mysql://localhost:3306/chronocase"
   export CHRONOCASE_DB_USER="root"
   export CHRONOCASE_DB_PASSWORD="yourpassword"
   ```
3. The schema is created automatically on the first use. If no server is
   reachable, the console demo shows a message and continues without the
   database. The other parts keep working.

---

## How to play

1. Start the app. Select `New Investigation` on the main menu. The app
   opens the demo case CASE-001, "The Missing Prototype". The case
   contains 4 suspects (Arjun, Maya, Rohan, Neha), 5 locations and 13
   events between 08:00 and 11:00. It also contains 8 linked evidence
   items (E001 to E008) and the timeline tree
   ROOT / TIMELINE-A / TIMELINE-B / TIMELINE-C. TIMELINE-C is a branch of
   a branch, so the tree has real depth. The app never changes ROOT.
2. Dashboard. The home screen shows stat cards, the current timeline and
   the investigation time. You can collect clues in the evidence workbench
   and link them to suspects and locations. The
   `Add suspect/location/event/evidence…` dialogs create the items.
3. Investigate (sidebar). You interview suspects, survey locations,
   inspect events, and collect and search evidence. The evidence bag on
   the right shows the items you collected.
4. Timeline. The branch map shows one box per timeline. Gold is the root,
   and arrows point from parent to branch. Select a box to switch
   timelines. The info panel shows the id, the parent and the child
   timelines. The event list below marks your position in time.
5. Time Travel. Every event of the current timeline is a card with a
   `Travel Here` button. Travel to the 09:40 "camera goes offline" moment.
   Write what happened and press `Create alternate timeline`. The app
   never changes the original timeline. It creates a new branch on the
   map and makes it the current timeline. The `Return to previous
   timeline` button goes back through your travel history.

   The case contains two demo contradictions. E001, the 10:00 access log
   of Maya, SUPPORTS ROOT but CONTRADICTS TIMELINE-C ("Maya does NOT enter
   Prototype Lab"). E002, the camera offline at 09:40, SUPPORTS
   TIMELINE-B but CONTRADICTS TIMELINE-A ("camera is repaired"). Examine
   them on the Contradictions screen.
6. Compare. Pick two timelines. The events appear side by side. Green
   means identical, amber means changed and red means missing on one
   side. The evidence verdicts against either timeline appear below.
7. Contradictions. Select the timelines and press `Run contradiction
   detection`. The screen lists the contradictions and the consistent
   (supporting) evidence, with the affected timeline and the explanation.
8. Solve Mystery. Press `Solve the case`. The rule-based solver gives the
   most likely culprit, a confidence percentage, the full suspect ranking
   with the scoring reasons, the supporting evidence and the
   contradictions. You can export the verdict report on the same screen.
9. Save and continue later. `Save` in the header writes a CHRONOCASE-V1
   file with the case, every timeline branch, the collected clues and the
   timeline you were on. `Load Investigation` restores all of it and
   continues on the saved branch. `Load from Database…` does the same
   through MySQL.

To win the mystery, collect the clues E001, E002 and E006. The solver
weighs collected clues before other clues. All three point at the
security engineer. Look at the timeline that the evidence supports. The
solver names the person who does not tell the truth.

On the seeded ROOT timeline, the solver names Maya Kapoor. The reasons
are her card access to the Prototype Lab, her presence at the decisive
moments and the security administrator privilege that turned the camera
off. The solver computes the verdict from the evidence. Nothing is
hard-coded.

---

## Project layout

```
src/
├── model/       Case, Character, Location, Event, Evidence, Timeline (+branching)
├── service/     InvestigationManager, TimelineNavigator, ContradictionDetector,
│                MysterySolver, RelationshipGraph, EvidenceBoard, ...
├── io/          FileManager (CHRONOCASE-V1 save/load/export)
├── database/    DatabaseManager (JDBC/MySQL schema + save/load)
├── controller/  InvestigationController - the seam between GUI and logic;
│                MissingPrototypeCase - the ready-made scenario
└── view/        JavaFX app shell (main menu + sidebar), screens, styles.css
```

## Troubleshooting

| Problem | Fix |
|---|---|
| `package javafx does not exist` while compiling | Start `get-libraries.sh` (or `get-libraries.bat` on Windows) first. Then start `build.sh` |
| `JavaFX runtime components are missing` | Start the app with `run-gui.sh` or `run-gui.bat`. The launcher starts JavaFX from the plain classpath |
| A GUI button does nothing | A dialog can be behind the window. Look at the taskbar. The app prints uncaught JavaFX exceptions to the console with the `[ChronoCase]` prefix. Errors in dialogs do not go to the console |
| The database demo says "not reachable" | Start MySQL. Set the three `CHRONOCASE_DB_*` variables. Keep the driver jar on the classpath |
| The download fails | Make sure that you are connected to the internet, and start the fetch script again. Jars that are already downloaded are skipped |

---

## License

MIT. See [LICENSE](LICENSE). Anyone can use, change and share the code. The
only condition is that the license text stays with the code.
