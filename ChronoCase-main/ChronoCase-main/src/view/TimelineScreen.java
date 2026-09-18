package view;

import controller.InvestigationController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import model.Event;
import model.Timeline;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Visual Timeline: the branching timeline map plus the event list of
 * the selected timeline. The map is drawn on a Canvas - one clickable
 * box per timeline (color by depth, glowing border for the current
 * one), arrows from parent to branch. Clicking a box selects that
 * timeline (switching goes through the controller only); the info
 * panel shows its id, parent and children.
 */
class TimelineScreen {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** One box per depth level; index 0 is the root's color. */
    private static final Color[] DEPTH_COLORS = {
            Color.web("#d4a017"), // root: gold
            Color.web("#4a90d9"), // first branch level: blue
            Color.web("#9b59b6"), // deeper branches: purple
            Color.web("#e67e22")  // even deeper: orange
    };

    private final InvestigationController controller;
    private final VBox view;
    private final ComboBox<String> timelinePicker = new ComboBox<>();
    private final VBox eventList = new VBox(6);
    private final VBox infoPanel = new VBox(4);
    private final Canvas branchMap = new Canvas(1020, 220);
    private final Map<Integer, double[]> hitBoxes = new HashMap<>();
    private double boxWidth = 170;
    private double boxHeight = 48;

    TimelineScreen(InvestigationController controller) {
        this.controller = controller;
        this.view = build();
        refresh();
    }

    Node getView() {
        return view;
    }

    void refresh() {
        List<Timeline> timelines = controller.getTimelines();
        timelinePicker.getItems().setAll(timelines.stream()
                .map(t -> "#" + t.getId() + " " + t.getLabel()
                        + (t == controller.getCurrentTimeline() ? "  [current]" : ""))
                .toList());
        Timeline current = controller.getCurrentTimeline();
        if (current != null) {
            timelinePicker.setValue("#" + current.getId() + " " + current.getLabel()
                    + "  [current]");
            drawBranchMap();
            rebuildInfoPanel(current);
            rebuildEventList(current);
        } else {
            infoPanel.getChildren().setAll(new Label("No timeline yet."));
            eventList.getChildren().setAll(new Label("No timeline yet."));
        }
    }

    private VBox build() {
        Label heading = new Label("Branching Timeline");
        heading.getStyleClass().add("section-title");

        timelinePicker.setMaxWidth(440);
        timelinePicker.setOnAction(e -> onSwitchTimeline());
        timelinePicker.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });

        HBox pickerRow = new HBox(8, new Label("Timeline:"), timelinePicker);
        pickerRow.setAlignment(Pos.CENTER_LEFT);

        Label mapCaption = new Label("Branch map - click a box to select that timeline; "
                + "arrows point from parent to branch:");
        mapCaption.getStyleClass().add("caption");
        branchMap.setOnMouseClicked(this::onMapClick);

        infoPanel.getStyleClass().add("panel");
        infoPanel.setPadding(new Insets(10));

        Label eventsCaption = new Label("Events of the selected timeline (chronological):");
        eventsCaption.getStyleClass().add("caption");

        eventList.getStyleClass().add("panel");
        eventList.setPadding(new Insets(10));

        VBox layout = new VBox(10, heading, pickerRow, mapCaption, branchMap,
                infoPanel, eventsCaption, eventList);
        VBox.setVgrow(eventList, Priority.ALWAYS);
        layout.setPadding(new Insets(10));
        return layout;
    }

    private void onSwitchTimeline() {
        int id = DashboardScreen.extractId(timelinePicker.getValue());
        if (id < 0) {
            return;
        }
        try {
            controller.switchToTimeline(id);
        } catch (RuntimeException ex) {
            app().showError(ex.getMessage());
        }
        refresh();
    }

    /** Canvas hit-test: select the timeline whose box was clicked. */
    private void onMapClick(MouseEvent click) {
        for (Map.Entry<Integer, double[]> entry : hitBoxes.entrySet()) {
            double[] pos = entry.getValue();
            double x = click.getX();
            double y = click.getY();
            if (x >= pos[0] && x <= pos[0] + boxWidth
                    && y >= pos[1] && y <= pos[1] + boxHeight) {
                try {
                    controller.switchToTimeline(entry.getKey());
                } catch (RuntimeException ex) {
                    app().showError(ex.getMessage());
                }
                refresh();
                return;
            }
        }
    }

    /**
     * Draws the tree: one rounded box per timeline (color by depth,
     * glow for the current one), parent-to-branch arrows, and the
     * event count inside. Layout: depth bands top-down, boxes left to
     * right within a band.
     */
    private void drawBranchMap() {
        GraphicsContext g = branchMap.getGraphicsContext2D();
        g.clearRect(0, 0, branchMap.getWidth(), branchMap.getHeight());
        hitBoxes.clear();

        List<Timeline> timelines = controller.getTimelines();
        if (timelines.isEmpty()) {
            g.setFill(Color.GRAY);
            g.setFont(Font.font(14));
            g.fillText("No timelines yet.", 20, 30);
            return;
        }

        // Position: depth -> row; timelines in creation order, one
        // slot each, at least 190px apart horizontally.
        double[] nextXByDepth = new double[8];
        Map<Integer, double[]> positions = new HashMap<>();
        int maxDepth = 1;
        for (Timeline t : timelines) {
            int depth = Math.min(t.getDepth() - 1, nextXByDepth.length - 1);
            maxDepth = Math.max(maxDepth, t.getDepth());
            double x = 20 + nextXByDepth[depth];
            double y = 20 + depth * (boxHeight + 40);
            positions.put(t.getId(), new double[] {x, y});
            nextXByDepth[depth] += boxWidth + 20;
        }
        double neededWidth = 40;
        for (double used : nextXByDepth) {
            neededWidth = Math.max(neededWidth, used + boxWidth);
        }
        double neededHeight = 20 + maxDepth * (boxHeight + 40) + 10;
        if (neededWidth > branchMap.getWidth()) {
            branchMap.setWidth(neededWidth);
        }
        if (neededHeight > branchMap.getHeight()) {
            branchMap.setHeight(neededHeight);
        }

        // Edges first (under the boxes).
        g.setStroke(Color.web("#7f8c8d"));
        g.setLineWidth(1.6);
        for (Timeline t : timelines) {
            if (t.getParent() == null) {
                continue;
            }
            double[] from = positions.get(t.getParent().getId());
            double[] to = positions.get(t.getId());
            if (from == null || to == null) {
                continue;
            }
            double x1 = from[0] + boxWidth;
            double y1 = from[1] + boxHeight / 2;
            double x2 = to[0];
            double y2 = to[1] + boxHeight / 2;
            g.strokeLine(x1, y1, (x1 + x2) / 2, y1);
            g.strokeLine((x1 + x2) / 2, y1, (x1 + x2) / 2, y2);
            g.strokeLine((x1 + x2) / 2, y2, x2, y2);
            arrowHead(g, x2, y2);
        }

        // Boxes.
        for (Timeline t : timelines) {
            double[] pos = positions.get(t.getId());
            if (pos == null) {
                continue;
            }
            Color fill = DEPTH_COLORS[Math.min(t.getDepth() - 1, DEPTH_COLORS.length - 1)];
            boolean isCurrent = t == controller.getCurrentTimeline();
            g.setFill(fill);
            g.fillRoundRect(pos[0], pos[1], boxWidth, boxHeight, 10, 10);
            if (isCurrent) {
                g.setLineWidth(3);
                g.setStroke(Color.web("#f5f6fa"));
                g.strokeRoundRect(pos[0] - 3, pos[1] - 3, boxWidth + 6, boxHeight + 6, 12, 12);
                g.setLineWidth(1.6);
            }
            g.setFill(Color.WHITE);
            g.setFont(Font.font(12));
            String label = "Timeline #" + t.getId() + (t.isRoot() ? " (root)" : "");
            if (label.length() > 24) {
                label = label.substring(0, 23) + "…";
            }
            g.fillText(label, pos[0] + 8, pos[1] + 19);
            String second = t.getLabel();
            if (second.length() > 26) {
                second = second.substring(0, 25) + "…";
            }
            g.setFont(Font.font(10));
            g.fillText(second + " - " + t.getEventCount() + " ev"
                    + (isCurrent ? " •" : ""), pos[0] + 8, pos[1] + 36);
            hitBoxes.put(t.getId(), pos);
        }
    }

    private void arrowHead(GraphicsContext g, double x, double y) {
        g.fillPolygon(new double[] {x - 7, x - 7, x}, new double[] {y - 4, y + 4, y}, 3);
    }

    /** id, parent, children of the selected timeline. */
    private void rebuildInfoPanel(Timeline selected) {
        StringBuilder children = new StringBuilder();
        for (Timeline child : selected.getBranches()) {
            if (children.length() > 0) {
                children.append(", ");
            }
            children.append("#").append(child.getId()).append(" ").append(child.getLabel());
        }
        infoPanel.getChildren().setAll(
                new Label("Selected: Timeline #" + selected.getId() + "  \"" + selected.getLabel() + "\""
                        + (selected == controller.getCurrentTimeline() ? "   [CURRENT]" : "")),
                new Label("Parent: " + (selected.getParent() == null
                        ? "none - this is the root timeline"
                        : "Timeline #" + selected.getParent().getId() + " \""
                                + selected.getParent().getLabel() + "\"")),
                new Label("Child timelines (" + selected.getBranches().size() + "): "
                        + (children.length() == 0 ? "none" : children)),
                new Label("Events on this timeline: " + selected.getEventCount()
                        + "   Depth from root: " + selected.getDepth()));
    }

    private void rebuildEventList(Timeline current) {
        eventList.getChildren().clear();
        if (current == null) {
            eventList.getChildren().add(new Label("No timeline selected."));
            return;
        }
        List<Event> events = controller.getTimelineEvents(current);
        if (events.isEmpty()) {
            eventList.getChildren().add(new Label("This timeline has no events yet."));
            return;
        }
        for (Event e : events) {
            boolean atInvestigationTime = controller.getCurrentEvent()
                    .map(selected -> selected.getId() == e.getId())
                    .orElse(false);
            Label row = new Label(time(e) + "   #" + e.getId() + "   " + e.getDescription()
                    + "   [" + e.getCharacter().getName() + " @ " + e.getLocation().getName() + "]"
                    + (atInvestigationTime ? "   ◄ you are here" : ""));
            row.getStyleClass().add("event-row");
            row.getStyleClass().add(current.isRoot() ? "event-root" : "event-branch");
            if (atInvestigationTime) {
                row.getStyleClass().add("event-now");
            }
            row.setMaxWidth(Double.MAX_VALUE);
            eventList.getChildren().add(row);
        }
    }

    private String time(Event e) {
        return e.getTimestamp().format(TIME_FORMAT);
    }

    private ChronoCaseApp app() {
        return ChronoCaseApp.getInstance();
    }
}
