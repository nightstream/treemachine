package opentree.synthesis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import opentree.TLongBitSet;
import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;
import org.opentree.graphdb.GraphDatabaseAgent;

import scala.actors.threadpool.Arrays;

public class RootwardSynthesisExpander extends SynthesisExpander implements PathExpander {
			
	private TopologicalOrder topologicalOrder;
	private Map<Long, HashSet<Relationship>> childRels;
	private Map<Long, TLongBitSet> nodeMrca;
	private GraphDatabaseAgent gdb;

	private boolean trivialTestCase = false;
	
	public RootwardSynthesisExpander(Node root) {
		topologicalOrder = new TopologicalOrder(root, RelType.MRCACHILDOF);
		childRels = new HashMap<Long, HashSet<Relationship>>();
		nodeMrca = new HashMap<Long, TLongBitSet>();
		gdb = new GraphDatabaseAgent(root.getGraphDatabase());
		
		for (Node n : topologicalOrder) {
			System.out.println("visiting node " + n);

			TLongBitSet descendants = new TLongBitSet();
			HashSet<Relationship> incomingRels = new HashSet<Relationship>();
			for (Long relId : findBestNonOverlapping(getCandidateRelationships(n))) {
				
				// testing
				Relationship r = n.getGraphDatabase().getRelationshipById(relId);
				long childId = r.getStartNode().getId();
				System.out.println("on rel " + relId + " between " + r.getStartNode() + " and " + r.getEndNode());

				incomingRels.add(n.getGraphDatabase().getRelationshipById(relId));
				descendants.add(childId);
				descendants.addAll(nodeMrca.get(childId));
			}

			long nodeId = n.getId();
			descendants.add(nodeId);
			nodeMrca.put(nodeId, descendants);

			childRels.put(nodeId, incomingRels); 
		}
	}
	
	public String getDescription() {
		return "rootward synthesis method";
	}

	public Iterable<Long> findBestNonOverlapping(Long[] relIds) {

		if (relIds.length < 1) {
			return new ArrayList<Long>();
		}
		
		TLongBitSet[] mrcaSetsForRels = new TLongBitSet[relIds.length];
		for (int i = 0; i < relIds.length; i++) {
			Long childNodeId = getStartNodeId(relIds[i]);
			mrcaSetsForRels[i] = new TLongBitSet();
			System.out.println("setting mrca for relationship " + relIds[i] + " to " + nodeMrca.get(childNodeId));
			mrcaSetsForRels[i].addAll(nodeMrca.get(childNodeId));
		}
		
		return new BruteWeightedIS(relIds, mrcaSetsForRels);
	}
		
	private static Long[] getCandidateRelationships(Node n) {
		List<Long> c = new ArrayList<Long>();
		Iterable<Relationship> R = n.getRelationships(RelType.STREECHILDOF, Direction.INCOMING);
		for (Relationship r : R) {
			c.add(r.getId());
		}
		return c.toArray(new Long[c.size()]);
	}
		
	private long getStartNodeId(Long relId) {
		return gdb.getRelationshipById(relId).getStartNode().getId();
	}

	@Override
	public Iterable<Relationship> expand(Path arg0, BranchState arg1) {

		// testing
		System.out.println("looking for rels starting at: " + arg0.endNode());
		System.out.println(childRels.get(arg0.endNode().getId()));
		
		return childRels.get(arg0.endNode().getId());
	}

	@Override
	public PathExpander reverse() {
		throw new UnsupportedOperationException();
	}

	/* THIS SECTION IN STASIS. =================================================================
	 * 
	 * Currently working with brute force non-graph version **/
	public Iterable<Long> findBestNonOverlappingGraph(Long[] relIds) {

		if (trivialTestCase) {
			return Arrays.asList(relIds);
		}
		
		// vertices in this graph represent candidate relationships
		// edges represent overlap in the mrca sets between pairs of candidate relationships
		WeightedUndirectedGraph G = new WeightedUndirectedGraph();

		for (long relId : relIds) {
			G.addNode(relId, getWeight(relId));
		}
		
		// attach this candidate rel to any others with overlapping descendant sets
		for (int i = 0; i < relIds.length; i++) {
			long a = getStartNodeId(relIds[i]);
			for (int j = i+1; j < relIds.length; j++) {
				long b = getStartNodeId(relIds[j]);
				if (nodeMrca.get(a).containsAny(nodeMrca.get(b))) {
					G.getNode(a).attachTo(b);
				}
			}
		}

		// find a set S' of vertices such that the total weight of S' is maximized, and no two vertices in S' share an edge
		// this corresponds to a set of non-overlapping candidate relationships with maximum weight
//		return new LPWeightedIS(G);
		return null; // garbage...
	}
	
	/** simple weight based only on the size of the mrca set of the node */
	private int getWeight(long id) {
		TLongBitSet m = nodeMrca.get(id);
		if (m == null) {
			return 1;
		} else {
			return m.size();
		}
	}

	// =============== END STASIS ===================
}
