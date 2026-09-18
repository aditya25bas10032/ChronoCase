package io;

import model.Case;
import model.Character;
import model.Evidence;
import model.EvidencePriority;
import model.Event;
import model.Location;
import model.Timeline;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads and writes ChronoCase data as plain text files. All file
 * I/O lives here: the model classes know nothing about files.
 *
 * Two jobs:
 * 1. saveCase/loadCase - a pipe-delimited data format (CHRONOCASE-V1)
 *    that captures everything needed to rebuild a case: characters,
 *    locations, events, evidence, timelines (including their branch
 *    structure and per-timeline event copies) and which evidence a
 *    session has collected.
 * 2. exportInvestigation - a human-readable report covering case
 *    information, suspects, locations, events, evidence, timelines
 *    and an investigation result (any TextReportable, e.g. the
 *    solver's result).
 *
 * I/O details: NIO {@link Files}/{@link Path} with
 * {@link BufferedWriter} for writing and {@link BufferedReader} for
 * reading, always UTF-8. Every failure surfaces as a
 * {@link ChronoCaseIoException} naming the file and line number.
 *
 * Data format (one record per line, fields separated by '|'):
 *   CHRONOCASE-V1
 *   CASE|id|title|description
 *   CHARACTER|id|name|role
 *   LOCATION|id|name|description
 *   EVENT|id|timestamp(ISO)|description|characterId|locationId
 *   EVIDENCE|id|priority|description|characterIdOr-|locationIdOr-
 *   TIMELINE|id|parentTimelineIdOr0|label
 *   TL_EVENT|timelineId|eventId|timestamp(ISO)|description|characterId|locationId
 *   CURRENT|timelineId
 *   COLLECTED|evidenceId
 * '|' and '\' inside text fields are escaped with a backslash.
 */
public class FileManager {

    /** Thrown for any I/O or format problem; message names the file (and line). */
    public static final class ChronoCaseIoException extends IOException {
        ChronoCaseIoException(String message) {
            super(message);
        }

        ChronoCaseIoException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String HEADER = "CHRONOCASE-V1";
    private static final String ESCAPE = "\\";
    private static final String SEPARATOR = "|";
    private static final String EMPTY = "-";

    // ----- saving -----

    /**
     * Saves the case, its timelines and the session's collected
     * evidence to the given file in the CHRONOCASE-V1 format.
     * Missing parent directories are created.
     */
    public void saveCase(Case kase, Path file, Set<Evidence> collectedEvidence) throws IOException {
        this.saveCase(kase, file, collectedEvidence, 0);
    }

    /**
     * Saves the case plus the id of the timeline the investigation
     * was on, so a reload resumes on the same branch. A currentId of
     * 0 (or an id that is not part of the case) means "no position"
     * and the loader falls back to the first root.
     */
    public void saveCase(Case kase, Path file, Set<Evidence> collectedEvidence,
                         int currentTimelineId) throws IOException {
        Objects.requireNonNull(kase, "case must not be null");
        Objects.requireNonNull(file, "file must not be null");
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            writeLine(writer, "CASE", kase.getId(), esc(kase.getTitle()), esc(kase.getDescription()));
            for (Character c : kase.getCharacters()) {
                writeLine(writer, "CHARACTER", c.getId(), esc(c.getName()), esc(c.getRole()));
            }
            for (Location l : kase.getLocations()) {
                writeLine(writer, "LOCATION", l.getId(), esc(l.getName()), esc(l.getDescription()));
            }
            for (Event e : kase.getEvents()) {
                writeLine(writer, "EVENT", e.getId(), e.getTimestamp().format(ISO),
                        esc(e.getDescription()), e.getCharacter().getId(), e.getLocation().getId());
            }
            for (Evidence item : kase.getEvidence()) {
                Character linked = item.getLinkedCharacter();
                Location where = item.getLinkedLocation();
                writeLine(writer, "EVIDENCE", item.getId(), item.getPriority().name(),
                        esc(item.getDescription()),
                        linked == null ? EMPTY : linked.getId(),
                        where == null ? EMPTY : where.getId());
            }
            for (Timeline root : kase.getTimelines()) {
                writeTimelineTree(writer, root);
            }
            if (currentTimelineId > 0) {
                writeLine(writer, "CURRENT", currentTimelineId);
            }
            if (collectedEvidence != null) {
                for (Evidence item : collectedEvidence) {
                    writeLine(writer, "COLLECTED", item.getId());
                }
            }
        }
    }

    /** Convenience: save without collected-evidence bookkeeping. */
    public void saveCase(Case kase, Path file) throws IOException {
        saveCase(kase, file, null);
    }

    /**
     * Loads a case saved by {@link #saveCase(Case, Path, Set)}.
     * Character, location and evidence ids are resolved against the
     * records earlier in the file; unknown ids fail with the line
     * number.
     */
    public LoadResult loadCase(Path file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!Files.exists(file)) {
            throw new ChronoCaseIoException("File not found: " + file);
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (!HEADER.equals(header)) {
                throw new ChronoCaseIoException("Not a ChronoCase data file: " + file
                        + " (expected header " + HEADER + ")");
            }

            Case kase = null;
            List<TimelineLine> timelineLines = new ArrayList<>();
            Map<Integer, Integer> collectedIds = new LinkedHashMap<>();
            int collectedOrder = 0;
            int currentTimelineId = 0;

            String line;
            int lineNumber = 1; // header already consumed
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> parts = splitFields(line);
                String tag = parts.get(0);
                try {
                    switch (tag) {
                        case "CASE":
                            kase = new Case(parseInt(parts.get(1), "case id"),
                                    unesc(parts.get(2)), unesc(parts.get(3)));
                            break;
                        case "CHARACTER":
                            requireCase(kase, lineNumber, "CHARACTER");
                            kase.addCharacter(new Character(parseInt(parts.get(1), "character id"),
                                    unesc(parts.get(2)), unesc(parts.get(3))));
                            break;
                        case "LOCATION":
                            requireCase(kase, lineNumber, "LOCATION");
                            kase.addLocation(new Location(parseInt(parts.get(1), "location id"),
                                    unesc(parts.get(2)), unesc(parts.get(3))));
                            break;
                        case "EVENT":
                            requireCase(kase, lineNumber, "EVENT");
                            kase.addEvent(new Event(parseInt(parts.get(1), "event id"),
                                    unesc(parts.get(3)), parseDateTime(parts.get(2)),
                                    requireCharacter(kase, parseInt(parts.get(4), "character id"), lineNumber),
                                    requireLocation(kase, parseInt(parts.get(5), "location id"), lineNumber)));
                            break;
                        case "EVIDENCE": {
                            requireCase(kase, lineNumber, "EVIDENCE");
                            Character linked = parts.get(4).equals(EMPTY)
                                    ? null : requireCharacter(kase, parseInt(parts.get(4), "character id"), lineNumber);
                            Location where = parts.get(5).equals(EMPTY)
                                    ? null : requireLocation(kase, parseInt(parts.get(5), "location id"), lineNumber);
                            kase.addEvidence(new Evidence(parseInt(parts.get(1), "evidence id"),
                                    unesc(parts.get(3)), linked, where,
                                    EvidencePriority.fromString(parts.get(2))));
                            break;
                        }
                        case "TIMELINE":
                            requireCase(kase, lineNumber, "TIMELINE");
                            timelineLines.add(TimelineLine.timeline(
                                    parseInt(parts.get(1), "timeline id"),
                                    parseInt(parts.get(2), "parent timeline id"),
                                    unesc(parts.get(3))));
                            break;
                        case "TL_EVENT": {
                            requireCase(kase, lineNumber, "TL_EVENT");
                            timelineLines.add(TimelineLine.timelineEvent(
                                    parseInt(parts.get(1), "timeline id"),
                                    parseInt(parts.get(2), "event id"),
                                    parseDateTime(parts.get(3)),
                                    unesc(parts.get(4)),
                                    requireCharacter(kase, parseInt(parts.get(5), "character id"), lineNumber),
                                    requireLocation(kase, parseInt(parts.get(6), "location id"), lineNumber)));
                            break;
                        }
                        case "CURRENT":
                            currentTimelineId = parseInt(parts.get(1), "current timeline id");
                            break;
                        case "COLLECTED":
                            collectedIds.put(parseInt(parts.get(1), "evidence id"), collectedOrder++);
                            break;
                        default:
                            throw new ChronoCaseIoException("unknown record type '" + tag + "'");
                    }
                } catch (ChronoCaseIoException e) {
                    throw new ChronoCaseIoException(file + ", line " + lineNumber
                            + ": " + e.getMessage(), e.getCause());
                } catch (RuntimeException e) {
                    throw new ChronoCaseIoException(file + ", line " + lineNumber
                            + ": malformed " + tag + " record: " + e.getMessage(), e);
                }
            }
            if (kase == null) {
                throw new ChronoCaseIoException(file + ": no CASE record found");
            }
            rebuildTimelines(kase, timelineLines);
            Set<Evidence> collected = resolveCollected(kase, collectedIds);
            return new LoadResult(kase, collected, currentTimelineId);
        }
    }

    /** Everything loadCase recovered from one file. */
    public static final class LoadResult {
        private final Case kase;
        private final Set<Evidence> collectedEvidence;
        private final int currentTimelineId;

        LoadResult(Case kase, Set<Evidence> collectedEvidence, int currentTimelineId) {
            this.kase = kase;
            this.collectedEvidence = collectedEvidence;
            this.currentTimelineId = currentTimelineId;
        }

        public Case getCase() {
            return kase;
        }

        /** Evidence marked collected in the file, in saved order (empty if none). */
        public Set<Evidence> getCollectedEvidence() {
            return collectedEvidence;
        }

        /**
         * The timeline id the investigation was on when the file was
         * saved; 0 when the file did not name one (fall back to the
         * first root).
         */
        public int getCurrentTimelineId() {
            return currentTimelineId;
        }
    }

    // ----- report export -----

    /**
     * Exports the full investigation snapshot required by the spec:
     * case information, suspects, locations, events, evidence,
     * timelines and the investigation result (any TextReportable,
     * e.g. the MysterySolver's result).
     */
    public void exportInvestigation(Case kase, TextReportable investigationResult, Path file) throws IOException {
        Objects.requireNonNull(kase, "case must not be null");
        List<String> lines = new ArrayList<>();
        lines.add("CHRONOCASE INVESTIGATION REPORT - " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        lines.add("=".repeat(60));
        lines.add("CASE:");
        lines.add("  " + kase);
        lines.add("");
        lines.add("SUSPECTS:");
        for (Character c : kase.getCharacters()) {
            lines.add("  " + c);
        }
        lines.add("");
        lines.add("LOCATIONS:");
        for (Location l : kase.getLocations()) {
            lines.add("  " + l);
        }
        lines.add("");
        lines.add("EVENTS:");
        for (Event e : kase.getEvents()) {
            lines.add("  " + e);
        }
        lines.add("");
        lines.add("EVIDENCE:");
        for (Evidence item : kase.getEvidence()) {
            lines.add("  " + item);
        }
        lines.add("");
        lines.add("TIMELINES:");
        for (Timeline t : kase.getTimelines()) {
            lines.add("  " + t);
            for (Event e : t.getEvents()) {
                lines.add("    - " + e);
            }
        }
        lines.add("");
        lines.add("INVESTIGATION RESULT:");
        if (investigationResult != null) {
            for (String resultLine : investigationResult.renderReport()) {
                lines.add("  " + resultLine);
            }
        }
        writeAll(file, lines);
    }

    /** Exports any TextReportable (e.g. an InvestigationManager session) with a header. */
    public void exportReport(TextReportable reportable, Path file) throws IOException {
        Objects.requireNonNull(reportable, "reportable must not be null");
        List<String> lines = new ArrayList<>();
        lines.add("CHRONOCASE REPORT - " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        lines.add("=".repeat(60));
        lines.addAll(reportable.renderReport());
        writeAll(file, lines);
    }

    // ----- data-format internals -----

    /**
     * Rebuilds the timeline tree. Parents are created before their
     * children (saveCase walks the tree top-down), and each TL_EVENT
     * is reconstructed as the timeline's own copy - preserving the
     * model's snapshot rule that branch events are independent
     * instances even when they carry the same id.
     *
     * A saved branch repeats the events it inherited from its parent
     * (they are part of the branch's own list) and saves its divergent
     * versions after them. A TL_EVENT whose id already exists on its
     * timeline therefore replaces that copy - the branch's own saved
     * version wins, so a diverged branch reloads with its revised
     * events.
     */
    private static void rebuildTimelines(Case kase, List<TimelineLine> lines) throws ChronoCaseIoException {
        if (lines.isEmpty()) {
            return;
        }
        Map<Integer, Timeline> byId = new LinkedHashMap<>();
        for (TimelineLine tl : lines) {
            if (!tl.isTimelineRecord) {
                continue;
            }
            Timeline parent = tl.parentId == 0 ? null : byId.get(tl.parentId);
            if (tl.parentId != 0 && parent == null) {
                throw new ChronoCaseIoException("timeline " + tl.id
                        + " references parent " + tl.parentId + " that is not defined before it");
            }
            if (byId.containsKey(tl.id)) {
                throw new ChronoCaseIoException("duplicate timeline id " + tl.id);
            }
            byId.put(tl.id, parent == null ? new Timeline(tl.id, tl.label)
                    : parent.createBranch(tl.id, tl.label));
        }
        for (TimelineLine tl : lines) {
            if (tl.isTimelineRecord) {
                continue;
            }
            Timeline owner = byId.get(tl.ownerId);
            if (owner == null) {
                throw new ChronoCaseIoException("timeline event references unknown timeline id " + tl.ownerId);
            }
            Event restored = new Event(tl.eventId, tl.description, tl.timestamp,
                    tl.character, tl.location);
            boolean inherited = owner.getEvents().stream()
                    .anyMatch(e -> e.getId() == tl.eventId);
            if (inherited) {
                owner.replaceEvent(tl.eventId, restored);
            } else {
                owner.addEvent(restored);
            }
        }
        for (Timeline root : byId.values()) {
            if (root.isRoot()) {
                kase.addTimeline(root);
            }
        }
    }

    private static Set<Evidence> resolveCollected(Case kase, Map<Integer, Integer> collectedIds)
            throws ChronoCaseIoException {
        Set<Evidence> collected = new LinkedHashSet<>();
        for (Integer id : collectedIds.keySet()) {
            collected.add(kase.findEvidenceById(id)
                    .orElseThrow(() -> new ChronoCaseIoException(
                            "COLLECTED references unknown evidence id " + id)));
        }
        return collected;
    }

    private static void writeLine(BufferedWriter writer, Object... fields) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(fields[i]);
        }
        writer.write(sb.toString());
        writer.newLine();
    }

    /**
     * Writes one timeline and its whole branch subtree, depth-first:
     * the TIMELINE record, then the timeline's TL_EVENTs, then each
     * branch - so parents always appear before their children in the
     * file, as the loader requires.
     */
    private static void writeTimelineTree(BufferedWriter writer, Timeline timeline) throws IOException {
        writeLine(writer, "TIMELINE", timeline.getId(),
                timeline.getParent() == null ? 0 : timeline.getParent().getId(),
                esc(timeline.getLabel()));
        for (Event e : timeline.getEvents()) {
            writeLine(writer, "TL_EVENT", timeline.getId(), e.getId(),
                    e.getTimestamp().format(ISO), esc(e.getDescription()),
                    e.getCharacter().getId(), e.getLocation().getId());
        }
        for (Timeline branch : timeline.getBranches()) {
            writeTimelineTree(writer, branch);
        }
    }

    private static void writeAll(Path file, List<String> lines) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /** Escapes backslashes and pipes so text fields never break the format. */
    private static String esc(String value) {
        return value.replace(ESCAPE, ESCAPE + ESCAPE)
                .replace(SEPARATOR, ESCAPE + SEPARATOR);
    }

    /**
     * Splits a record line on unescaped '|' separators. Backslash
     * escapes stay in the fields untouched; {@link #unesc(String)}
     * removes them per field afterwards.
     */
    private static List<String> splitFields(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                field.append(c);
                escaped = false;
            } else if (c == ESCAPE.charAt(0)) {
                field.append(c);
                escaped = true;
            } else if (c == SEPARATOR.charAt(0)) {
                parts.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        parts.add(field.toString());
        return parts;
    }

    private static String unesc(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        boolean escaped = false;
        for (char c : value.toCharArray()) {
            if (!escaped && c == ESCAPE.charAt(0)) {
                escaped = true;
                continue;
            }
            sb.append(c);
            escaped = false;
        }
        return sb.toString();
    }

    private static void requireCase(Case kase, int lineNumber, String tag) throws ChronoCaseIoException {
        if (kase == null) {
            throw new ChronoCaseIoException("line " + lineNumber + ": " + tag
                    + " record appears before the CASE record");
        }
    }

    private static Character requireCharacter(Case kase, int id, int lineNumber) throws ChronoCaseIoException {
        return kase.findCharacterById(id)
                .orElseThrow(() -> new ChronoCaseIoException("unknown character id " + id));
    }

    private static Location requireLocation(Case kase, int id, int lineNumber) throws ChronoCaseIoException {
        return kase.findLocationById(id)
                .orElseThrow(() -> new ChronoCaseIoException("unknown location id " + id));
    }

    private static int parseInt(String value, String field) throws ChronoCaseIoException {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ChronoCaseIoException("invalid " + field + ": '" + value + "'");
        }
    }

    private static LocalDateTime parseDateTime(String value) throws ChronoCaseIoException {
        try {
            return LocalDateTime.parse(value, ISO);
        } catch (DateTimeParseException e) {
            throw new ChronoCaseIoException("invalid timestamp '" + value + "'");
        }
    }

    /** One parsed timeline-related line: TIMELINE or TL_EVENT. */
    private static final class TimelineLine {
        static TimelineLine timeline(int id, int parentId, String label) {
            return new TimelineLine(id, parentId, label, 0, 0, null, null, null, null);
        }

        static TimelineLine timelineEvent(int ownerId, int eventId, LocalDateTime timestamp,
                                          String description, Character character, Location location) {
            return new TimelineLine(0, 0, null, ownerId, eventId, timestamp, description,
                    character, location);
        }

        final int id;
        final int parentId;
        final String label;
        final int ownerId;
        final int eventId;
        final LocalDateTime timestamp;
        final String description;
        final Character character;
        final Location location;
        final boolean isTimelineRecord;

        private TimelineLine(int id, int parentId, String label, int ownerId, int eventId,
                             LocalDateTime timestamp, String description,
                             Character character, Location location) {
            this.id = id;
            this.parentId = parentId;
            this.label = label;
            this.ownerId = ownerId;
            this.eventId = eventId;
            this.timestamp = timestamp;
            this.description = description;
            this.character = character;
            this.location = location;
            this.isTimelineRecord = label != null;
        }
    }
}
