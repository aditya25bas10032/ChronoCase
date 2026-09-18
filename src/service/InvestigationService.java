package service;

import model.Event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Work queue for one investigator session: leads (events) are queued
 * in the order they come in and worked strictly first-come,
 * first-served.
 *
 * Data structures, each with a real job:
 * - Queue (ArrayDeque): the open leads, in arrival order. FIFO
 *   because a lead registered first must be investigated first.
 * - HashSet: ids of leads already worked. A lead that is queued
 *   twice (e.g. reported by two witnesses) is processed only once,
 *   and the worked-set also stops finished leads from being
 *   re-queued.
 */
public class InvestigationService {

    /** Result of working one lead. */
    public static final class LeadResult {
        private final Event lead;
        private final String conclusion;

        LeadResult(Event lead, String conclusion) {
            this.lead = lead;
            this.conclusion = conclusion;
        }

        public Event getLead() {
            return lead;
        }

        public String getConclusion() {
            return conclusion;
        }

        @Override
        public String toString() {
            return "Lead #" + lead.getId() + " -> " + conclusion;
        }
    }

    private final Queue<Event> openLeads = new ArrayDeque<>();
    private final Set<Integer> workedLeadIds = new HashSet<>();
    private final List<LeadResult> results = new ArrayList<>();

    /**
     * Adds a lead to the back of the queue. Returns false (and queues
     * nothing) if this lead was already worked or is already queued:
     * duplicates carry no new information.
     */
    public boolean submitLead(Event lead) {
        Objects.requireNonNull(lead, "lead event must not be null");
        int id = lead.getId();
        if (workedLeadIds.contains(id) || containsLeadWithId(id)) {
            return false;
        }
        return openLeads.offer(lead);
    }

    /**
     * Works the next lead in FIFO order. The conclusion notes whether
     * the lead led anywhere ("dead end") or produced a finding.
     * Returns null when the queue is empty.
     */
    public LeadResult workNextLead() {
        Event lead = openLeads.poll();
        if (lead == null) {
            return null;
        }
        workedLeadIds.add(lead.getId());
        LeadResult result = new LeadResult(lead, investigate(lead));
        results.add(result);
        return result;
    }

    /**
     * Works leads until the queue is empty. Returns the results in
     * processing order.
     */
    public List<LeadResult> workAllLeads() {
        List<LeadResult> all = new ArrayList<>();
        LeadResult r;
        while ((r = workNextLead()) != null) {
            all.add(r);
        }
        return all;
    }

    /** Number of leads still waiting to be worked. */
    public int getOpenLeadCount() {
        return openLeads.size();
    }

    /** Number of distinct leads worked so far. */
    public int getWorkedLeadCount() {
        return workedLeadIds.size();
    }

    /** True if this event id has already been worked. */
    public boolean isWorked(int eventId) {
        return workedLeadIds.contains(eventId);
    }

    /** All results so far, in processing order. Read-only. */
    public List<LeadResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    private boolean containsLeadWithId(int id) {
        for (Event e : openLeads) {
            if (e.getId() == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * The actual investigative step. Deliberately simple: a lead is a
     * dead end when the described action contains no hint of
     * evidence, otherwise it produces a finding. Real deduction is
     * the player's job; the service only tracks the process.
     */
    private String investigate(Event lead) {
        String d = lead.getDescription().toLowerCase();
        if (d.contains("found") || d.contains("witness") || d.contains("record")) {
            return "produced a finding: " + lead.getDescription();
        }
        return "dead end";
    }
}
