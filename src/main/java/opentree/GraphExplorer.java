package opentree;

import jade.tree.JadeNode;
import jade.tree.JadeTree;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.text.html.HTMLDocument.Iterator;

import opentree.GraphBase.RelTypes;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;

public class GraphExplorer extends GraphBase{
	public GraphExplorer (String graphname){
		graphDb = new EmbeddedGraphDatabase( graphname );
		graphNodeIndex = graphDb.index().forNodes("graphNamedNodes");
	}
	
	/*
	 * given a taxonomic name, construct a json object of the graph surrounding that name
	 */
	public void constructJSONGraph(String name){
		IndexHits<Node> hits = graphNodeIndex.get("name",name);
		Node firstNode = hits.getSingle();
		hits.close();
		if(firstNode == null){
			System.out.println("name not found");
			return;
		}
		//System.out.println(firstNode.getProperty("name"));
		TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( RelTypes.MRCACHILDOF,Direction.INCOMING );
		HashMap<Node,Integer> nodenumbers = new HashMap<Node,Integer>();
		HashMap<Integer,Node> numbernodes = new HashMap<Integer,Node>();
		int count = 0;
		for(Node friendnode: CHILDOF_TRAVERSAL.traverse(firstNode).nodes()){
			nodenumbers.put(friendnode, count);
			numbernodes.put(count,friendnode);
			count += 1;
		}
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter("graph_data.js"));
			outFile.write("{\"nodes\":[");
			for(int i=0; i<count;i++){
				Node tnode = numbernodes.get(i);
				if(tnode.hasRelationship(RelTypes.ISCALLED))
					outFile.write("{\"name\":\""+(tnode.getRelationships(RelTypes.ISCALLED)).iterator().next().getEndNode().getProperty("name")+"");
				else
					outFile.write("{\"name\":\"");
				//outFile.write("{\"name\":\""+tnode.getProperty("name")+"");
				outFile.write("\",\"group\":"+nodenumbers.get(tnode)+"");
				if(i+1<count)
					outFile.write("},");
				else
					outFile.write("}");
			}
			outFile.write("],\"links\":[");
			String outs = "";
			for(Node tnode: nodenumbers.keySet()){
				Iterable<Relationship> it = tnode.getRelationships(RelTypes.MRCACHILDOF,Direction.OUTGOING);
				for(Relationship trel : it){
					if(nodenumbers.get(trel.getStartNode())!= null && nodenumbers.get(trel.getEndNode())!=null){
						outs+="{\"source\":"+nodenumbers.get(trel.getStartNode())+"";
						outs+=",\"target\":"+nodenumbers.get(trel.getEndNode())+"";
						outs+=",\"value\":"+1+"";
						outs+="},";
					}
				}
			}
			outs = outs.substring(0, outs.length()-1);
			outFile.write(outs);
			outFile.write("]");
			outFile.write("}\n");
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * given a taxonomic name, construct a newick string, breaking ties based on a source name
	 * this is just one example of one type of synthesis
	 */
	public void constructNewickSourceTieBreaker(String taxname, String sourcename){
		IndexHits<Node> hits = graphNodeIndex.get("name",taxname);
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING), 100);
		Node firstNode = hits.getSingle();
		hits.close();
		if(firstNode == null){
			System.out.println("name not found");
			return;
		}
		JadeNode root = new JadeNode();
		System.out.println(firstNode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
		TraversalDescription MRCACHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( RelTypes.MRCACHILDOF,Direction.INCOMING );
		ArrayList<Node> visited = new ArrayList<Node>();
		ArrayList<Relationship> keepers = new ArrayList<Relationship>();
		HashMap<Node,JadeNode> nodejademap = new HashMap<Node,JadeNode>();
		HashMap<JadeNode,Node> jadeparentmap = new HashMap<JadeNode,Node>();
		visited.add(firstNode);
		nodejademap.put(firstNode, root);
		for(Node friendnode : MRCACHILDOF_TRAVERSAL.traverse(firstNode).nodes()){
			//if it is a tip, move back, 
			if(friendnode.hasRelationship(Direction.INCOMING, RelTypes.MRCACHILDOF))
				continue;
			else{
				Node curnode = friendnode;
				while(curnode.hasRelationship(Direction.OUTGOING, RelTypes.MRCACHILDOF)){
					//if it is visited continue
					if (visited.contains(curnode)){
						break;
					}else{
						JadeNode newnode = new JadeNode();
						if(curnode.hasRelationship(Direction.OUTGOING, RelTypes.ISCALLED)){
							newnode.setName((String)curnode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
							newnode.setName(newnode.getName().replace("(", "_").replace(")","_"));
						}
						nodejademap.put(curnode, newnode);
						visited.add(curnode);
						Relationship keep = null;
						for(Relationship rel: curnode.getRelationships(Direction.OUTGOING, RelTypes.STREECHILDOF)){
							if(keep == null)
								keep = rel;
							if (((String)rel.getProperty("source")).compareTo(sourcename) == 0){
								keep = rel;
								break;
							}
							if(pf.findSinglePath(rel.getEndNode(), firstNode) != null || visited.contains(rel.getEndNode())){
								keep = rel;
							}
						}
						keepers.add(keep);
						if(pf.findSinglePath(keep.getEndNode(), firstNode) != null){
							curnode = keep.getEndNode();
							jadeparentmap.put(newnode, curnode);
						}else
							break;
					}
				}
			}
		}
		for(JadeNode jn:jadeparentmap.keySet()){
			nodejademap.get(jadeparentmap.get(jn)).addChild(jn);
		}
		JadeTree tree = new JadeTree(root);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(taxname+".tre"));
			outFile.write(tree.getRoot().getNewick(false));
			outFile.write(";\n");
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}