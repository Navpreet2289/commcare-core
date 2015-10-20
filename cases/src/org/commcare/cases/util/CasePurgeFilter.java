/**
 *
 */
package org.commcare.cases.util;

import org.commcare.cases.model.Case;
import org.commcare.cases.model.CaseIndex;
import org.javarosa.core.services.storage.EntityFilter;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.DAG;
import org.javarosa.core.util.DAG.Edge;
import org.javarosa.core.util.DataUtil;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;

/**
 * @author ctsims
 */
public class CasePurgeFilter extends EntityFilter<Case> {
    /**
     * Owned by the user or a group
     */
    private static final int STATUS_OWNED = 1;

    /**
     * Should be included in the set of cases seen by the user
     */
    private static final int STATUS_RELEVANT= 2;

    /**
     * Isn't precluded from being included in a sync for any reason
     */
    private static final int STATUS_AVAILABLE = 4;

    /**
     * Should remain on the phone.
     */
    private static final int STATUS_ALIVE = 8;

    private static final int STATUS_OPEN = 16;

    Vector<Integer> idsToRemove = new Vector<Integer>();

    public CasePurgeFilter(IStorageUtilityIndexed<Case> caseStorage) {
        this(caseStorage, null);
    }

    /**
     * Create a filter for purging cases which should no longer be on the phone from
     * the database. Identifies liveness appropriately based on index dependencies,
     * along with cases which have no valid owner in the current context.
     *
     * @param caseStorage The storage which is to be cleaned up.
     * @param owners      A list of IDs for users whose cases should still be on the device.
     *                    Any cases which do not have a valid owner will be considered 'closed' when
     *                    determining the purge behavior. Null to not enable
     *                    this behavior
     */
    public CasePurgeFilter(IStorageUtilityIndexed<Case> caseStorage, Vector<String> owners) {
        setIdsToRemoveWithNewExtensions(caseStorage, owners);
    }

    private void setIdsToRemoveWithNewExtensions(IStorageUtilityIndexed<Case> caseStorage, Vector<String> owners) {
        //Create a DAG. The Index will be the case GUID. The Nodes will be a int array containing
        //[STATUS_FLAGS, storageid]
        DAG<String, int[], String> g = new DAG<String, int[], String>();

        Vector<CaseIndex> indexHolder = new Vector<CaseIndex>();

        //Pass 1:
        //Create a DAG which contains all of the cases on the phone as nodes, and has a directed
        //edge for each index (from the 'child' case pointing to the 'parent' case) with the
        //appropriate relationship tagged
        for (IStorageIterator<Case> i = caseStorage.iterate(); i.hasMore(); ) {
            Case c = i.nextRecord();
            boolean owned = true;
            if (owners != null) {
                owned = owners.contains(c.getUserId());
            }

            //In order to deal with multiple indices pointing to the same case
            //with different relationships, we'll need to traverse once to eliminate any
            //ambiguity (TODO: How do we speed this up? Do we need to?)
            for (CaseIndex index : c.getIndices()) {
                CaseIndex toReplace = null;
                boolean skip = false;
                for (CaseIndex existing : indexHolder) {
                    if (existing.getTarget().equals(index.getTarget())) {
                        if (existing.getRelationship().equals(CaseIndex.RELATIONSHIP_EXTENSION) && !index.getRelationship().equals(CaseIndex.RELATIONSHIP_EXTENSION)) {
                            toReplace = existing;
                        } else {
                            skip = true;
                        }
                        break;
                    }
                }
                if (toReplace != null) {
                    indexHolder.removeElement(toReplace);
                }
                if (!skip) {
                    indexHolder.addElement(index);
                }
            }
            int nodeStatus = 0;
            if (owned) {
                nodeStatus |= STATUS_OWNED;
            }

            if (!c.isClosed()) {
                nodeStatus |= STATUS_OPEN;
            }

            if (owned && !c.isClosed()) {
                nodeStatus |= STATUS_RELEVANT;
            }

            g.addNode(c.getCaseId(), new int[]{nodeStatus, c.getID()});

            for (CaseIndex index : indexHolder) {
                g.setEdge(c.getCaseId(), index.getTarget(), index.getRelationship());
            }
            indexHolder.removeAllElements();
        }

        markRelevant(g);
        markAvailable(g);
        markLive(g);
    
        //Ok, so now just go through all nodes and signal that we need to remove anything
        //that isn't live!
        for (Enumeration<int[]> iterator = g.getNodes(); iterator.hasMoreElements(); ) {
            int[] node = (int[])iterator.nextElement();
            if (!is(node, STATUS_ALIVE)) {
                idsToRemove.addElement(Integer.valueOf(node[1]));
            }
        }
    }

    private void markRelevant(DAG<String, int[], String> g) {
        walk(g, true, STATUS_RELEVANT, STATUS_RELEVANT);
        walk(g, false, STATUS_RELEVANT, STATUS_RELEVANT, CaseIndex.RELATIONSHIP_EXTENSION);
    }

    private void markAvailable(DAG<String, int[], String> g) {
        for (Enumeration<String> e = g.getIndices(); e.hasMoreElements(); ) {
            String index = e.nextElement();
            int[] node = g.getNode(index);
            if (is(node, STATUS_OPEN | STATUS_RELEVANT) &&
                    !hasOutgoingExtension(g, index)) {
                node[0] |= STATUS_AVAILABLE;
            }
        }
        walk(g, false, STATUS_AVAILABLE, STATUS_AVAILABLE, CaseIndex.RELATIONSHIP_EXTENSION);
    }

    private void markLive(DAG<String, int[], String> g) {
        for (Enumeration<String> e = g.getIndices(); e.hasMoreElements(); ) {
            String index = e.nextElement();
            int[] node = g.getNode(index);
            if (is(node, STATUS_OWNED | STATUS_RELEVANT | STATUS_AVAILABLE)) {
                node[0] |= STATUS_ALIVE;
            }
        }

        walk(g, true, STATUS_ALIVE, STATUS_ALIVE);
        walk(g, false, STATUS_ALIVE, STATUS_ALIVE, CaseIndex.RELATIONSHIP_EXTENSION);
    }

    private boolean hasOutgoingExtension(DAG<String, int[], String> g, String index) {
        for (Edge<String, String> edge : g.getChildren(index)) {
            if (edge.e.equals(CaseIndex.RELATIONSHIP_EXTENSION)) {
                return true;
            }
        }
        return false;
    }

    private void walk(DAG<String, int[], String> g, boolean direction, int mask, int mark) {
        walk(g, direction, mask, mark, null);
    }

    private void walk(DAG<String, int[], String> g, boolean direction, int mask, int mark, String relationship) {
        Stack<String> toProcess = direction ? g.getSources() : g.getSinks();
        while (!toProcess.isEmpty()) {
            //current node
            String index = toProcess.pop();
            int[] node = g.getNode(index);

            for (Edge<String, String> edge : (direction ? g.getChildren(index) : g.getParents(index))) {
                if (is(node, mask) && (relationship == null || edge.e.equals(relationship))) {
                    g.getNode(edge.i)[0] |= mark;
                }
                toProcess.addElement(edge.i);
            }
        }
    }

    private boolean is(int[] node, int flag) {
        return (node[0] & flag) == flag;
    }

    private boolean hasExtension(Vector<CaseIndex> indexHolder) {
        for (CaseIndex index : indexHolder) {
            if (index.getRelationship().equals(CaseIndex.RELATIONSHIP_EXTENSION)) {
                return true;
            }
        }
        return false;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.services.storage.EntityFilter#preFilter(int, java.util.Hashtable)
     */
    public int preFilter(int id, Hashtable<String, Object> metaData) {
        if (idsToRemove.contains(DataUtil.integer(id))) {
            return PREFILTER_INCLUDE;
        } else {
            return PREFILTER_EXCLUDE;
        }
    }

    public boolean matches(Case e) {
        //We're doing everything with pre-filtering
        return false;
    }

}
