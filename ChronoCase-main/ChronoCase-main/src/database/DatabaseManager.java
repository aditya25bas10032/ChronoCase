package database;

import model.Case;
import model.Character;
import model.Evidence;
import model.EvidencePriority;
import model.Event;
import model.Location;
import model.Timeline;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JDBC persistence for ChronoCase (Step 11): saves and loads whole
 * cases against a MySQL database.
 *
 * Schema (matches the model classes exactly):
 *   cases      - one row per investigation
 *   characters - belong to a case
 *   locations  - belong to a case
 *   events     - character + location are required (model rule),
 *                so both foreign keys are NOT NULL
 *   evidence   - character/location links are optional (unlinked
 *                evidence is valid), so both foreign keys are nullable
 *   timelines  - a case has many root timelines; parent_timeline_id
 *                (self foreign key) encodes the branch relationships
 *   timeline_events - each timeline's own event snapshot (branching
 *                copies events, so the same event id can exist on
 *                several timelines)
 *
 * All ids are unique per case, not globally (the model hands out ids
 * like Character #1 to every case), so every table uses the composite
 * primary key (case_id, <entity>_id).
 *
 * Rules kept here:
 * - Every query goes through PreparedStatement; no string-built SQL
 *   ever contains user data.
 * - All JDBC code lives in this package; model classes know nothing
 *   about SQL.
 * - Every SQLException surfaces as a DataAccessException (unchecked)
 *   that names what the operation was doing.
 * - Credentials are read from the environment (CHRONOCASE_DB_URL /
 *   CHRONOCASE_DB_USER / CHRONOCASE_DB_PASSWORD) with safe defaults
 *   for url and user; no password is hard-coded anywhere.
 */
public class DatabaseManager {

    /** Thrown for any database problem; message names the operation. */
    public static final class DataAccessException extends RuntimeException {
        DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** One case and the evidence collected during an investigation session. */
    public static final class LoadResult {
        private final Case kase;
        private final List<Integer> collectedEvidenceIds;

        LoadResult(Case kase, List<Integer> collectedEvidenceIds) {
            this.kase = kase;
            this.collectedEvidenceIds = collectedEvidenceIds;
        }

        public Case getCase() {
            return kase;
        }

        /** Evidence ids marked collected, in saved order (empty if none). */
        public List<Integer> getCollectedEvidenceIds() {
            return collectedEvidenceIds;
        }
    }

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/chronocase";
    private static final String DEFAULT_USER = "root";

    private final String url;
    private final String user;
    private final String password;

    /**
     * Reads the connection settings from the environment. The
     * password is never hard-coded: it must come from the
     * CHRONOCASE_DB_PASSWORD environment variable (empty string means
     * "no password", e.g. a local dev server without authentication).
     */
    public DatabaseManager() {
        this(System.getenv().getOrDefault("CHRONOCASE_DB_URL", DEFAULT_URL),
                System.getenv().getOrDefault("CHRONOCASE_DB_USER", DEFAULT_USER),
                System.getenv().getOrDefault("CHRONOCASE_DB_PASSWORD", ""));
    }

    /** Explicit credentials, for tests or alternative deployments. */
    public DatabaseManager(String url, String user, String password) {
        this.url = Objects.requireNonNull(url, "url must not be null");
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.password = password == null ? "" : password;
    }

    // ----- connection & schema -----

    /** Opens a connection; the caller must close it. */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Creates the schema if it is not there yet (idempotent). Safe to
     * call before every save or load. The DDL contains no user data,
     * so a plain Statement is appropriate here.
     */
    public void createSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS cases ("
                + " case_id INT NOT NULL,"
                + " title VARCHAR(200) NOT NULL,"
                + " description TEXT NOT NULL,"
                + " PRIMARY KEY (case_id)"
                + ") ENGINE=InnoDB";

        String characters = "CREATE TABLE IF NOT EXISTS characters ("
                + " case_id INT NOT NULL,"
                + " character_id INT NOT NULL,"
                + " name VARCHAR(200) NOT NULL,"
                + " role VARCHAR(200) NOT NULL,"
                + " PRIMARY KEY (case_id, character_id),"
                + " FOREIGN KEY (case_id) REFERENCES cases(case_id)"
                + ") ENGINE=InnoDB";

        String locations = "CREATE TABLE IF NOT EXISTS locations ("
                + " case_id INT NOT NULL,"
                + " location_id INT NOT NULL,"
                + " name VARCHAR(200) NOT NULL,"
                + " description TEXT NOT NULL,"
                + " PRIMARY KEY (case_id, location_id),"
                + " FOREIGN KEY (case_id) REFERENCES cases(case_id)"
                + ") ENGINE=InnoDB";

        // Events must name a character and a location: the model
        // rejects Event(null character/location), so NOT NULL here.
        String events = "CREATE TABLE IF NOT EXISTS events ("
                + " case_id INT NOT NULL,"
                + " event_id INT NOT NULL,"
                + " description TEXT NOT NULL,"
                + " occurred_at DATETIME NOT NULL,"
                + " character_id INT NOT NULL,"
                + " location_id INT NOT NULL,"
                + " PRIMARY KEY (case_id, event_id),"
                + " FOREIGN KEY (case_id) REFERENCES cases(case_id),"
                + " FOREIGN KEY (case_id, character_id)"
                + "   REFERENCES characters(case_id, character_id),"
                + " FOREIGN KEY (case_id, location_id)"
                + "   REFERENCES locations(case_id, location_id)"
                + ") ENGINE=InnoDB";

        // Unlinked evidence is valid, so both link columns are nullable.
        String evidence = "CREATE TABLE IF NOT EXISTS evidence ("
                + " case_id INT NOT NULL,"
                + " evidence_id INT NOT NULL,"
                + " priority VARCHAR(20) NOT NULL,"
                + " description TEXT NOT NULL,"
                + " character_id INT NULL,"
                + " location_id INT NULL,"
                + " PRIMARY KEY (case_id, evidence_id),"
                + " FOREIGN KEY (case_id) REFERENCES cases(case_id),"
                + " FOREIGN KEY (case_id, character_id)"
                + "   REFERENCES characters(case_id, character_id),"
                + " FOREIGN KEY (case_id, location_id)"
                + "   REFERENCES locations(case_id, location_id)"
                + ") ENGINE=InnoDB";

        // parent_timeline_id = NULL for root timelines; the self
        // foreign key stores the branch relationships.
        String timelines = "CREATE TABLE IF NOT EXISTS timelines ("
                + " case_id INT NOT NULL,"
                + " timeline_id INT NOT NULL,"
                + " parent_timeline_id INT NULL,"
                + " label VARCHAR(200) NOT NULL,"
                + " PRIMARY KEY (case_id, timeline_id),"
                + " FOREIGN KEY (case_id) REFERENCES cases(case_id),"
                + " FOREIGN KEY (case_id, parent_timeline_id)"
                + "   REFERENCES timelines(case_id, timeline_id)"
                + ") ENGINE=InnoDB";

        // Every timeline keeps its own snapshot of events, including
        // copies inherited from the parent at branch time.
        String timelineEvents = "CREATE TABLE IF NOT EXISTS timeline_events ("
                + " case_id INT NOT NULL,"
                + " timeline_id INT NOT NULL,"
                + " event_id INT NOT NULL,"
                + " description TEXT NOT NULL,"
                + " occurred_at DATETIME NOT NULL,"
                + " character_id INT NOT NULL,"
                + " location_id INT NOT NULL,"
                + " PRIMARY KEY (case_id, timeline_id, event_id),"
                + " FOREIGN KEY (case_id, timeline_id)"
                + "   REFERENCES timelines(case_id, timeline_id),"
                + " FOREIGN KEY (case_id, character_id)"
                + "   REFERENCES characters(case_id, character_id),"
                + " FOREIGN KEY (case_id, location_id)"
                + "   REFERENCES locations(case_id, location_id)"
                + ") ENGINE=InnoDB";

        String collected = "CREATE TABLE IF NOT EXISTS collected_evidence ("
                + " case_id INT NOT NULL,"
                + " evidence_id INT NOT NULL,"
                + " position INT NOT NULL,"
                + " PRIMARY KEY (case_id, evidence_id),"
                + " FOREIGN KEY (case_id, evidence_id)"
                + "   REFERENCES evidence(case_id, evidence_id)"
                + ") ENGINE=InnoDB";

        String[] statements = {ddl, characters, locations, events,
                evidence, timelines, timelineEvents, collected};
        try (Connection connection = getConnection()) {
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("could not create the ChronoCase schema", e);
        }
    }

    // ----- save / update -----

    /**
     * Saves the whole case. If a case with the same id already
     * exists, it is updated in place; otherwise it is inserted.
     * Returns true when an existing case was updated.
     */
    public boolean saveCase(Case kase) {
        return saveCase(kase, null);
    }

    /**
     * Saves the case plus an ordered list of collected evidence ids
     * (an investigation session's bag). Updating means: the case row
     * is upserted and every child table is rewritten to match the
     * in-memory model exactly, so removed entities disappear.
     * Returns true when an existing case was updated.
     */
    public boolean saveCase(Case kase, List<Integer> collectedEvidenceIds) {
        Objects.requireNonNull(kase, "case must not be null");
        boolean updated = caseExists(kase.getId());
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (updated) {
                    deleteCaseRows(connection, kase.getId());
                }
                insertCase(connection, kase, collectedEvidenceIds);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("could not save case " + kase.getId(), e);
        }
        return updated;
    }

    /** True if a case row with this id exists. */
    public boolean caseExists(int caseId) {
        String sql = "SELECT 1 FROM cases WHERE case_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, caseId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("could not look up case " + caseId, e);
        }
    }

    /** Removes a case and all of its rows (children first). */
    public void deleteCase(int caseId) {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                deleteCaseRows(connection, caseId);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("could not delete case " + caseId, e);
        }
    }

    // ----- load -----

    /** Loads a case saved by {@link #saveCase(Case)}. */
    public Case loadCase(int caseId) {
        return loadCaseWithCollected(caseId).getCase();
    }

    /** Loads the case plus the saved collected-evidence ids. */
    public LoadResult loadCaseWithCollected(int caseId) {
        try (Connection connection = getConnection()) {
            Case kase = loadCaseRow(connection, caseId);
            List<Integer> collected = loadCollectedIds(connection, caseId);
            return new LoadResult(kase, collected);
        } catch (SQLException e) {
            throw new DataAccessException("could not load case " + caseId, e);
        }
    }

    /** Lists the ids of every saved case (ascending). */
    public List<Integer> listCaseIds() {
        String sql = "SELECT case_id FROM cases ORDER BY case_id";
        List<Integer> ids = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt("case_id"));
            }
            return ids;
        } catch (SQLException e) {
            throw new DataAccessException("could not list saved cases", e);
        }
    }

    // ----- inserts (private) -----

    private void insertCase(Connection connection, Case kase,
                            List<Integer> collectedEvidenceIds) throws SQLException {
        upsertCaseRow(connection, kase);
        for (Character c : kase.getCharacters()) {
            insertCharacter(connection, kase.getId(), c);
        }
        for (Location l : kase.getLocations()) {
            insertLocation(connection, kase.getId(), l);
        }
        for (Event e : kase.getEvents()) {
            insertEvent(connection, kase.getId(), e);
        }
        for (Evidence item : kase.getEvidence()) {
            insertEvidence(connection, kase.getId(), item);
        }
        for (Timeline root : kase.getTimelines()) {
            insertTimelineTree(connection, kase.getId(), root);
        }
        if (collectedEvidenceIds != null) {
            insertCollected(connection, kase.getId(), collectedEvidenceIds);
        }
    }

    private void upsertCaseRow(Connection connection, Case kase) throws SQLException {
        // Works on every MySQL setup; no ON DUPLICATE KEY assumptions.
        String update = "UPDATE cases SET title = ?, description = ? WHERE case_id = ?";
        String insert = "INSERT INTO cases (case_id, title, description) VALUES (?, ?, ?)";
        try (PreparedStatement updateStatement = connection.prepareStatement(update)) {
            updateStatement.setString(1, kase.getTitle());
            updateStatement.setString(2, kase.getDescription());
            updateStatement.setInt(3, kase.getId());
            if (updateStatement.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement insertStatement = connection.prepareStatement(insert)) {
            insertStatement.setInt(1, kase.getId());
            insertStatement.setString(2, kase.getTitle());
            insertStatement.setString(3, kase.getDescription());
            insertStatement.executeUpdate();
        }
    }

    private void insertCharacter(Connection connection, int caseId, Character c)
            throws SQLException {
        String sql = "INSERT INTO characters (case_id, character_id, name, role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, caseId);
            statement.setInt(2, c.getId());
            statement.setString(3, c.getName());
            statement.setString(4, c.getRole());
            statement.executeUpdate();
        }
    }

    private void insertLocation(Connection connection, int caseId, Location l)
            throws SQLException {
        String sql = "INSERT INTO locations (case_id, location_id, name, description)"
                + " VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, caseId);
            statement.setInt(2, l.getId());
            statement.setString(3, l.getName());
            statement.setString(4, l.getDescription());
            statement.executeUpdate();
        }
    }

    private void insertEvent(Connection connection, int caseId, Event e)
            throws SQLException {
        String sql = "INSERT INTO events (case_id, event_id, description, occurred_at,"
                + " character_id, location_id) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, caseId);
            statement.setInt(2, e.getId());
            statement.setString(3, e.getDescription());
            statement.setTimestamp(4, Timestamp.valueOf(e.getTimestamp()));
            statement.setInt(5, e.getCharacter().getId());
            statement.setInt(6, e.getLocation().getId());
            statement.executeUpdate();
        }
    }

    private void insertEvidence(Connection connection, int caseId, Evidence item)
            throws SQLException {
        String sql = "INSERT INTO evidence (case_id, evidence_id, priority, description,"
                + " character_id, location_id) VALUES (?, ?, ?, ?, ?, ?)";
        Character linked = item.getLinkedCharacter();
        Location where = item.getLinkedLocation();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, caseId);
            statement.setInt(2, item.getId());
            statement.setString(3, item.getPriority().name());
            statement.setString(4, item.getDescription());
            if (linked == null) {
                statement.setNull(5, Types.INTEGER);
            } else {
                statement.setInt(5, linked.getId());
            }
            if (where == null) {
                statement.setNull(6, Types.INTEGER);
            } else {
                statement.setInt(6, where.getId());
            }
            statement.executeUpdate();
        }
    }

    /** Depth-first: parents before children, as the foreign keys require. */
    private void insertTimelineTree(Connection connection, int caseId, Timeline timeline)
            throws SQLException {
        String sql = "INSERT INTO timelines (case_id, timeline_id, parent_timeline_id, label)"
                + " VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, caseId);
            statement.setInt(2, timeline.getId());
            if (timeline.getParent() == null) {
                statement.setNull(3, Types.INTEGER);
            } else {
                statement.setInt(3, timeline.getParent().getId());
            }
            statement.setString(4, timeline.getLabel());
            statement.executeUpdate();
        }
        for (Event e : timeline.getEvents()) {
            insertTimelineEvent(connection, caseId, timeline.getId(), e);
        }
        for (Timeline branch : timeline.getBranches()) {
            insertTimelineTree(connection, caseId, branch);
        }
    }

    private void insertTimelineEvent(Connection connection, int caseId,
                                     int timelineId, Event e) throws SQLException {
        String sql = "INSERT INTO timeline_events (case_id, timeline_id, event_id,"
                + " description, occurred_at, character_id, location_id)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, caseId);
            statement.setInt(2, timelineId);
            statement.setInt(3, e.getId());
            statement.setString(4, e.getDescription());
            statement.setTimestamp(5, Timestamp.valueOf(e.getTimestamp()));
            statement.setInt(6, e.getCharacter().getId());
            statement.setInt(7, e.getLocation().getId());
            statement.executeUpdate();
        }
    }

    private void insertCollected(Connection connection, int caseId,
                                 List<Integer> collectedEvidenceIds) throws SQLException {
        String sql = "INSERT INTO collected_evidence (case_id, evidence_id, position)"
                + " VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int position = 0;
            for (int evidenceId : collectedEvidenceIds) {
                statement.setInt(1, caseId);
                statement.setInt(2, evidenceId);
                statement.setInt(3, position++);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // ----- loads (private) -----

    private Case loadCaseRow(Connection connection, int caseId) throws SQLException {
        Case kase = loadCaseHeader(connection, caseId);
        Map<Integer, Character> characters = loadCharacters(connection, kase);
        Map<Integer, Location> locations = loadLocations(connection, kase);
        loadEvents(connection, kase, characters, locations);
        loadEvidence(connection, kase, characters, locations);
        loadTimelines(connection, kase, characters, locations);
        return kase;
    }

    private Case loadCaseHeader(Connection connection, int caseId) throws SQLException {
        String sql = "SELECT title, description FROM cases WHERE case_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, caseId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new DataAccessException("no saved case with id " + caseId, null);
                }
                return new Case(caseId, rs.getString("title"), rs.getString("description"));
            }
        }
    }

    private Map<Integer, Character> loadCharacters(Connection connection, Case kase)
            throws SQLException {
        String sql = "SELECT character_id, name, role FROM characters"
                + " WHERE case_id = ? ORDER BY character_id";
        Map<Integer, Character> byId = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, kase.getId());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Character c = new Character(rs.getInt("character_id"),
                            rs.getString("name"), rs.getString("role"));
                    kase.addCharacter(c);
                    byId.put(c.getId(), c);
                }
            }
        }
        return byId;
    }

    private Map<Integer, Location> loadLocations(Connection connection, Case kase)
            throws SQLException {
        String sql = "SELECT location_id, name, description FROM locations"
                + " WHERE case_id = ? ORDER BY location_id";
        Map<Integer, Location> byId = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, kase.getId());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Location l = new Location(rs.getInt("location_id"),
                            rs.getString("name"), rs.getString("description"));
                    kase.addLocation(l);
                    byId.put(l.getId(), l);
                }
            }
        }
        return byId;
    }

    private void loadEvents(Connection connection, Case kase,
                            Map<Integer, Character> characters,
                            Map<Integer, Location> locations) throws SQLException {
        String sql = "SELECT event_id, description, occurred_at, character_id, location_id"
                + " FROM events WHERE case_id = ? ORDER BY event_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, kase.getId());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    kase.addEvent(new Event(rs.getInt("event_id"),
                            rs.getString("description"),
                            rs.getTimestamp("occurred_at").toLocalDateTime(),
                            requireCharacter(characters, rs.getInt("character_id"), "event"),
                            requireLocation(locations, rs.getInt("location_id"), "event")));
                }
            }
        }
    }

    private void loadEvidence(Connection connection, Case kase,
                              Map<Integer, Character> characters,
                              Map<Integer, Location> locations) throws SQLException {
        String sql = "SELECT evidence_id, priority, description, character_id, location_id"
                + " FROM evidence WHERE case_id = ? ORDER BY evidence_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, kase.getId());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int characterId = rs.getInt("character_id");
                    boolean hasCharacter = !rs.wasNull();
                    int locationId = rs.getInt("location_id");
                    boolean hasLocation = !rs.wasNull();
                    kase.addEvidence(new Evidence(rs.getInt("evidence_id"),
                            rs.getString("description"),
                            hasCharacter
                                    ? requireCharacter(characters, characterId, "evidence") : null,
                            hasLocation
                                    ? requireLocation(locations, locationId, "evidence") : null,
                            EvidencePriority.fromString(rs.getString("priority"))));
                }
            }
        }
    }

    private void loadTimelines(Connection connection, Case kase,
                               Map<Integer, Character> characters,
                               Map<Integer, Location> locations) throws SQLException {
        // Row holder: [id, parent-or-0, hasParent, label]. Parents are
        // saved before children, so one ordered pass builds the tree.
        List<Object[]> timelineRows = new ArrayList<>();
        String timelineSql = "SELECT timeline_id, parent_timeline_id, label FROM timelines"
                + " WHERE case_id = ? ORDER BY timeline_id";
        try (PreparedStatement statement = connection.prepareStatement(timelineSql)) {
            statement.setInt(1, kase.getId());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int parentId = rs.getInt("parent_timeline_id");
                    boolean hasParent = !rs.wasNull();
                    timelineRows.add(new Object[] {rs.getInt("timeline_id"),
                            hasParent ? parentId : 0, hasParent, rs.getString("label")});
                }
            }
        }

        Map<Integer, Timeline> byId = new LinkedHashMap<>();
        for (Object[] row : timelineRows) {
            int id = (Integer) row[0];
            boolean hasParent = (Boolean) row[2];
            String label = (String) row[3];
            Timeline timeline;
            if (hasParent) {
                Timeline parent = byId.get((Integer) row[1]);
                if (parent == null) {
                    throw new DataAccessException("timeline " + id + " references parent "
                            + row[1] + " that is not saved yet", null);
                }
                timeline = parent.createBranch(id, label);
            } else {
                timeline = new Timeline(id, label);
                kase.addTimeline(timeline);
            }
            byId.put(id, timeline);
        }

        // Pass 2: restore each timeline's own event snapshot. A saved
        // branch repeats the events it inherited from its parent; the
        // branch's own version (saved last) must win, mirroring
        // FileManager's round-trip rule.
        String eventSql = "SELECT timeline_id, event_id, description, occurred_at,"
                + " character_id, location_id FROM timeline_events"
                + " WHERE case_id = ? ORDER BY timeline_id, event_id";
        try (PreparedStatement statement = connection.prepareStatement(eventSql)) {
            statement.setInt(1, kase.getId());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Timeline owner = byId.get(rs.getInt("timeline_id"));
                    if (owner == null) {
                        throw new DataAccessException("timeline event references unknown"
                                + " timeline " + rs.getInt("timeline_id"), null);
                    }
                    Event event = new Event(rs.getInt("event_id"),
                            rs.getString("description"),
                            rs.getTimestamp("occurred_at").toLocalDateTime(),
                            requireCharacter(characters, rs.getInt("character_id"), "timeline event"),
                            requireLocation(locations, rs.getInt("location_id"), "timeline event"));
                    boolean inherited = owner.getEvents().stream()
                            .anyMatch(e -> e.getId() == event.getId());
                    if (inherited) {
                        owner.replaceEvent(event.getId(), event);
                    } else {
                        owner.addEvent(event);
                    }
                }
            }
        }
    }

    private List<Integer> loadCollectedIds(Connection connection, int caseId)
            throws SQLException {
        String sql = "SELECT evidence_id FROM collected_evidence"
                + " WHERE case_id = ? ORDER BY position";
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, caseId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("evidence_id"));
                }
            }
        }
        return ids;
    }

    // ----- deletes & helpers (private) -----

    /** Deletes every row of a case, children before the case row. */
    private void deleteCaseRows(Connection connection, int caseId) throws SQLException {
        String[] sqls = {
                "DELETE FROM collected_evidence WHERE case_id = ?",
                "DELETE FROM timeline_events WHERE case_id = ?",
                "DELETE FROM timelines WHERE case_id = ?",
                "DELETE FROM evidence WHERE case_id = ?",
                "DELETE FROM events WHERE case_id = ?",
                "DELETE FROM locations WHERE case_id = ?",
                "DELETE FROM characters WHERE case_id = ?",
                "DELETE FROM cases WHERE case_id = ?",
        };
        for (String sql : sqls) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, caseId);
                statement.executeUpdate();
            }
        }
    }

    private static Character requireCharacter(Map<Integer, Character> byId, int id,
                                              String what) {
        Character c = byId.get(id);
        if (c == null) {
            throw new DataAccessException("saved " + what + " references unknown character id "
                    + id, null);
        }
        return c;
    }

    private static Location requireLocation(Map<Integer, Location> byId, int id,
                                            String what) {
        Location l = byId.get(id);
        if (l == null) {
            throw new DataAccessException("saved " + what + " references unknown location id "
                    + id, null);
        }
        return l;
    }
}
