package opentree.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import opentree.GraphExplorer;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import opentree.constants.GeneralConstants;

import java.net.URL;
import java.net.URLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.opentree.exceptions.TaxonNotFoundException;
import org.opentree.utils.GeneralUtils;
import org.opentree.graphdb.GraphDatabaseAgent;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.OTRepresentationConverter;

// Graph of Life Services 
public class graph extends ServerPlugin {
    
    // Don't deprecate: can use to list synthetic trees available
    @Description("Returns summary information about the draft synthetic tree(s) "
        + "currently contained within the graph database.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation about(@Source GraphDatabaseService graphDb) throws IllegalArgumentException {

        HashMap<String, Object> graphInfo = new HashMap<>();
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        ArrayList<String> synthTreeIDs = ge.getSynthTreeIDs();
        
        if (synthTreeIDs.size() > 0) {
            graphInfo.put("num_synth_trees", synthTreeIDs.size());
            LinkedList<HashMap<String, Object>> trees = new LinkedList<>();
            
            for (String treeID : synthTreeIDs) {
                HashMap<String, Object> draftTreeInfo = new HashMap<>();
                Node meta = ge.getSynthesisMetaNodeByName(treeID);
                for (String key : meta.getPropertyKeys()) {
                    draftTreeInfo.put(key, meta.getProperty(key));
                }
                trees.add(draftTreeInfo);
            }
            graphInfo.put("synth_trees", trees);
        } else {
            throw new IllegalArgumentException("Could not find any draft synthetic trees in the graph.");
        }
        ge.shutdownDB();

        return OTRepresentationConverter.convert(graphInfo);
    }
    
    
    // TODO: Possibility of replacing tip label ottids with names?!?
    @Description("Returns a processed source tree (corresponding to a tree in some [study](#studies)) used "
        + "as input for the synthetic tree. Although the result of this service is a tree corresponding directly to a "
        + "tree in a study, the representation of the tree in the graph may differ slightly from its "
        + "canonical representation in the study, due to changes made during tree import: 1) includes "
        + "only the curator-designated ingroup clade, and 2) both unmapped and duplicate tips are pruned "
        + "from the tree. The tree is returned in newick format, with terminal nodes labelled with ott ids.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation source_tree(
        @Source GraphDatabaseService graphDb,

        @Description("The study identifier. Will typically include a prefix (\"pg_\" or \"ot_\")")
        @Parameter(name = "study_id", optional = false)
        String studyID,

        @Description("The tree identifier for a given study.")
        @Parameter(name = "tree_id", optional = false)
        String treeID,

        @Description("The synthetic tree identifier (defaults to most recent).")
        @Parameter(name = "synth_tree_id", optional = true)
        String synthTreeID,

        @Description("The name of the return format. The only currently supported format is newick.")
        @Parameter(name = "format", optional = true)
        String format

        ) throws IllegalArgumentException {

        HashMap<String, Object> responseMap = new HashMap<>();
        String source = studyID + "_" + treeID;
        String synTreeID = null;
        Node meta = null;
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        if (synthTreeID != null) {
            synTreeID = synthTreeID;
            // check
            meta = ge.getSynthesisMetaNodeByName(synthTreeID);
            // invalid treeid
            if (meta == null) {
                ge.shutdownDB();
                String ret = "Could not find a synthetic tree corresponding to the 'synth_tree_id' arg: '"
                        + synTreeID + "'.";
                throw new IllegalArgumentException(ret);
            }
        } else {
            // get most recent tree
            synTreeID = ge.getMostRecentSynthTreeID();
        }
        ge.shutdownDB();
        
        String tree = getSourceTree(source, synTreeID);

        if (tree == null) {
            throw new IllegalArgumentException("Invalid source id '" + source + "' provided.");
        } else {
            responseMap.put("newick", tree);
            responseMap.put("synth_tree_id", synTreeID);
        }
        
        return OTRepresentationConverter.convert(responseMap);
    }

    
    // TODO: need to tie to specific synth tree
    @Description("Returns summary information about a node in the graph. The node of interest may be specified "
        + "using *either* a node id, or an ott id, **but not both**. If the specified node or ott id is not in "
        + "the graph, an error will be returned.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation node_info(
        @Source GraphDatabaseService graphDb,
        
        @Description("The node id of the node of interest. This argument may not be combined with `ott_id`.")
        @Parameter(name = "node_id", optional = true)
        Long queryNodeId,
        
        @Description("The ott id of the node of interest. This argument may not be combined with `node_id`.")
        @Parameter(name = "ott_id", optional = true)
        Long queryOttId,
        
        @Description("Include the ancestral lineage of the node in the draft tree. If this argument is `true`, then "
            + "a list of all the ancestors of this node in the draft tree, down to the root of the tree itself, "
            + "will be included in the results. Higher list indices correspond to more incluive (i.e. deeper) "
            + "ancestors, with the immediate parent of the specified node occupying position 0 in the list.") 
        @Parameter(name = "include_lineage", optional = true)
        Boolean includeLineage
        
        ) throws IllegalArgumentException {
        
        HashMap<String, Object> nodeIfo = new HashMap<>();
        
        Long ottId = null;
        String name = "";
        String rank = "";
        String taxSource = "";
        Long nodeId = null;
        boolean inSynthTree = false;
        Integer numSynthTips = 0;
        Integer numMRCA = 0;
        LinkedList<HashMap<String, Object>> synthSources = new LinkedList<>();
        LinkedList<HashMap<String, Object>> treeSources = new LinkedList<>();
        
        if (queryNodeId == null && queryOttId == null) {
            throw new IllegalArgumentException("Must provide a \"node_id\" or \"ott_id\" argument.");
        } else if (queryNodeId != null && queryOttId != null) {
            throw new IllegalArgumentException("Provide only one \"node_id\" or \"ott_id\" argument.");
        }
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        if (queryOttId != null) {
            Node n = null;
            try {
                n = ge.findGraphTaxNodeByUID(String.valueOf(queryOttId));
            } catch (TaxonNotFoundException e) {
            }
            if (n != null) {
                nodeId = n.getId();
            } else {
                throw new IllegalArgumentException("Could not find any graph nodes corresponding to the ott id provided.");
            }

        } else if (queryNodeId != null) {
            Node n = null;
            try {
                n = graphDb.getNodeById(queryNodeId);
            } catch (NotFoundException e) {

            } catch (NullPointerException e) {

            }
            if (n != null) {
                nodeId = queryNodeId;
            } else {
                throw new IllegalArgumentException("Could not find any graph nodes corresponding to the node id provided.");
            }
        }

        if (nodeId != null) {
            Node n = graphDb.getNodeById(nodeId);
            if (n.hasProperty(NodeProperty.NAME.propertyName)) {
                name = String.valueOf(n.getProperty(NodeProperty.NAME.propertyName));
                rank = String.valueOf(n.getProperty(NodeProperty.TAX_RANK.propertyName));
                taxSource = String.valueOf(n.getProperty(NodeProperty.TAX_SOURCE.propertyName));
                ottId = Long.valueOf((String) n.getProperty(NodeProperty.TAX_UID.propertyName));
            }
            numMRCA = ((long[]) n.getProperty(NodeProperty.MRCA.propertyName)).length;
            if (ge.nodeIsInSyntheticTree(n)) {
                inSynthTree = true;
                numSynthTips = ge.getSynthesisDescendantTips(n).size(); // may be faster to just use stored MRCA
                // get all the unique sources supporting this node
                ArrayList<String> sSources = ge.getSynthesisSupportingSources(n);
                ArrayList<String> tSources = ge.getSupportingTreeSources(n);
                
                for (String sStudy : sSources) {
                    HashMap<String, Object> indStudy = GeneralUtils.reformatSourceID(sStudy);
                    synthSources.add(indStudy);
                }
                
                for (String tStudy : tSources) {
                    HashMap<String, Object> indStudy = GeneralUtils.reformatSourceID(tStudy);
                    treeSources.add(indStudy);
                }
            }
        }
        
        // problem: can't pass null objects.
        nodeIfo.put("name", name);
        nodeIfo.put("rank", rank);
        nodeIfo.put("tax_source", taxSource);
        nodeIfo.put("node_id", nodeId);
        // a hack, since OTRepresentationConverter apparently cannot use null values
        if (ottId != null) {
            nodeIfo.put("ott_id", ottId);
        } else {
            nodeIfo.put("ott_id", "null");
        }
        nodeIfo.put("in_synth_tree", inSynthTree);
        nodeIfo.put("num_synth_tips", numSynthTips);
        nodeIfo.put("num_tips", numMRCA);
        nodeIfo.put("synth_sources", synthSources);
        nodeIfo.put("tree_sources", treeSources);

        if (includeLineage != null && includeLineage == true) {
            LinkedList<HashMap<String, Object>> lineage = new LinkedList<HashMap<String, Object>>();
            if (inSynthTree) {
                Node n = graphDb.getNodeById(nodeId);
                List<Long> nodeList = getDraftTreePathToRoot(n);
                
                for (Long node : nodeList) {
                    HashMap<String, Object> info = new HashMap<String, Object>();
                    addNodeInfo(graphDb.getNodeById(node), info);
                    lineage.add(info);
                }
            }
            nodeIfo.put("draft_tree_lineage", lineage);
        }
        // report treeID
        Node meta = ge.getMostRecentSynthesisMetaNode();
        nodeIfo.put("tree_id", meta.getProperty("name"));
        
        ge.shutdownDB();
        
        return OTRepresentationConverter.convert(nodeIfo);
    }


    // fetch the processed input source tree newick from files.opentree.org
    public String getSourceTree(String source, String synTreeID) {
        String tree = null;
        
        // synTreeID will be of format: "opentree4.0"
        String version = synTreeID.replace("opentree", "");
        
        String urlbase = "http://files.opentreeoflife.org/preprocessed/v"
            + version + "/trees/" + source + ".tre";
        System.out.println("Looking up study: " + urlbase);

        try {
            URL phurl = new URL(urlbase);
            URLConnection conn = (URLConnection) phurl.openConnection();
            conn.connect();
            try (BufferedReader un = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                tree = un.readLine();
            }
            return tree;
        } catch (Exception e) {
        }
        return tree;
    }
    
    
    // TODO: need to tie to specific synth tree
    public List<Long> getDraftTreePathToRoot(Node startNode) {

        ArrayList<Long> path = new ArrayList<Long>();
        String synthTreeName = (String) GeneralConstants.DRAFT_TREE_NAME.value;

        Node curParent = startNode;
        boolean atRoot = false;
        while (!atRoot) {
            Iterable<Relationship> parentRels = curParent.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING);
            atRoot = true; // assume we have hit the root until proven otherwise
            for (Relationship m : parentRels) {
                if (String.valueOf(m.getProperty("name")).equals(synthTreeName)) {
                    atRoot = false;
                    curParent = m.getEndNode();
                    path.add(curParent.getId());
                    break;
                }
            }
        }
        return path;
    }


    // add information from a node
    private void addNodeInfo(Node n, HashMap<String, Object> results) {

        String name = "";
        String uniqueName = "";
        String rank = "";
        Long ottId = null;

        if (n.hasProperty(NodeProperty.NAME.propertyName)) {
            name = String.valueOf(n.getProperty(NodeProperty.NAME.propertyName));
            uniqueName = String.valueOf(n.getProperty(NodeProperty.NAME_UNIQUE.propertyName));
            rank = String.valueOf(n.getProperty(NodeProperty.TAX_RANK.propertyName));
            ottId = Long.valueOf((String) n.getProperty(NodeProperty.TAX_UID.propertyName));
        }

        results.put("node_id", n.getId());
        results.put("name", name);
        results.put("unique_name", uniqueName);
        results.put("rank", rank);
        if (ottId != null) {
            results.put("ott_id", ottId);
        } else {
            results.put("ott_id", "null");
        }
    }
}
