package service;

import model.Evidence;
import model.EvidencePriority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The investigator's evidence board: incoming evidence is triaged by
 * priority, the lab examines the most urgent items first, and links
 * between evidence items are tracked without duplicates.
 *
 * Data structures, each with a real job:
 * - PriorityQueue: the unexamined backlog, ordered by evidence
 *   priority (CRITICAL first, then id as tiebreaker). The lab works
 *   from the top, so the most promising clue is always next.
 * - HashSet: ids of already-examined items. Examining is expensive,
 *   so an item must never go through the lab twice, and re-adding an
 *   examined item to the board is rejected.
 * - HashSet per evidence id: the cross-links on the board (red
 *   strings). Storing them as sets makes "is A already linked to B?"
 *   a constant-time check and makes duplicates impossible; ordering
 *   carries no meaning for a link, so a set is the honest type.
 */
public class EvidenceBoard {

    /** Comparator order: lower rank first, then lower id. */
    private static int compareEvidence(Evidence a, Evidence b) {
        int byPriority = Integer.compare(a.getPriority().getRank(), b.getPriority().getRank());
        return byPriority != 0 ? byPriority : Integer.compare(a.getId(), b.getId());
    }

    private final PriorityQueue<Evidence> backlog =
            new PriorityQueue<>(EvidenceBoard::compareEvidence);
    private final Set<Integer> examinedIds = new HashSet<>();
    private final List<Evidence> examinationOrder = new ArrayList<>();
    private final Map<Integer, Set<Integer>> links = new HashMap<>();

    /**
     * Puts an item on the board's triage backlog. Returns false (and
     * queues nothing) if the item was already examined or is already
     * waiting: an item is either pending or done, never both.
     */
    public boolean submit(Evidence item) {
        Objects.requireNonNull(item, "evidence must not be null");
        if (examinedIds.contains(item.getId()) || containsWithId(item.getId())) {
            return false;
        }
        return backlog.offer(item);
    }

    /**
     * Sends the highest-priority pending item to the lab. Returns the
     * examined item, or null when the backlog is empty. Re-triaging
     * is impossible by construction: the PriorityQueue pops the
     * smallest element as defined by compareEvidence.
     */
    public Evidence examineNext() {
        Evidence item = backlog.poll();
        if (item == null) {
            return null;
        }
        examinedIds.add(item.getId());
        examinationOrder.add(item);
        return item;
    }

    /** Links two evidence items on the board. Idempotent: linking the same pair twice changes nothing. */
    public boolean link(int evidenceIdA, int evidenceIdB) {
        if (evidenceIdA == evidenceIdB) {
            return false; // an item is never linked to itself
        }
        return links.computeIfAbsent(evidenceIdA, k -> new HashSet<>()).add(evidenceIdB)
                | links.computeIfAbsent(evidenceIdB, k -> new HashSet<>()).add(evidenceIdA);
    }

    /** True if the two items are linked on the board. Constant time. */
    public boolean areLinked(int evidenceIdA, int evidenceIdB) {
        Set<Integer> direct = links.get(evidenceIdA);
        return direct != null && direct.contains(evidenceIdB);
    }

    /** Read-only view of everything linked to the given item. */
    public Set<Integer> getLinkedIds(int evidenceId) {
        Set<Integer> direct = links.get(evidenceId);
        return direct == null ? Collections.emptySet() : Collections.unmodifiableSet(direct);
    }

    /** Number of items still waiting for the lab. */
    public int getPendingCount() {
        return backlog.size();
    }

    /** Number of items the lab has finished. */
    public int getExaminedCount() {
        return examinedIds.size();
    }

    /** True if the lab has examined this item. */
    public boolean isExamined(int evidenceId) {
        return examinedIds.contains(evidenceId);
    }

    /** All examined items, in the order they were examined. Read-only. */
    public List<Evidence> getExaminationOrder() {
        return Collections.unmodifiableList(examinationOrder);
    }

    private boolean containsWithId(int id) {
        for (Evidence e : backlog) {
            if (e.getId() == id) {
                return true;
            }
        }
        return false;
    }
}
