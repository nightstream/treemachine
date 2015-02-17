package org.opentree.tag.treeimport;

import jade.tree.JadeNode;
import jade.tree.JadeTree;
import jade.tree.Tree;
import jade.tree.TreeNode;
import jade.tree.TreeReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;

import opentree.GraphInitializer;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;

import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.Index;
import org.neo4j.kernel.Traversal;
import org.opentree.graphdb.GraphDatabaseAgent;

public final class TipExploder {

	public static List<Tree> explodeTips(List<Tree> trees, GraphDatabaseAgent gdb) {
		Index<Node> ottIdIndex = gdb.getNodeIndex("graphTaxUIDNodes", "type", "exact", "to_lower_case", "true");

		for (Tree tree : trees) {
			for (TreeNode tip : tree.externalNodes()) {
			
				Object ottId = tip.getLabel();
				System.out.print("searching for ott id: " + ottId);

				Node hit = null;
				try {
					hit = ottIdIndex.get(NodeProperty.TAX_UID.propertyName, ottId).getSingle();
					if (hit == null) {
						System.out.println(". WARNING: could not find match this ott id.");
						continue;
					}
					System.out.print(". Found a node: " + hit + ". checking for tips below");
					JadeNode polytomy = new JadeNode();
					for (Node n : Traversal.description().breadthFirst().relationships(RelType.TAXCHILDOF, Direction.INCOMING).traverse(hit).nodes()) {
						if (! n.hasRelationship(RelType.TAXCHILDOF, Direction.INCOMING)) {
							Object label = n.getProperty(NodeProperty.TAX_UID.propertyName);
							JadeNode c = new JadeNode();
							c.setName((String) label);
							polytomy.addChild(c);
						}
					}
					if (polytomy.getChildCount() > 0) {
						System.out.println(". Found " + polytomy.getChildCount() + " tips, remapping.");
						TreeNode parent = tip.getParent();
						parent.removeChild(tip);
						parent.addChild(polytomy);
					}
				} catch (NoSuchElementException ex) {
					System.out.println("WARNING: more than one match was found for ott id " + ottId + ". this tip will not be exploded.");
				}
			}
			((JadeTree) tree).update();
		}
		return trees;
	}
	
	/**
	 * Instead of taking the tree, this just takes the tip and returns the hash with the 
	 * id and the list of ids that are in the hash
	 * @param identifier
	 * @param gdb
	 * @return
	 */
	public static HashMap<Object,HashSet<String> > explodeTipsReturnHash(List<Tree> trees, GraphDatabaseAgent gdb) {
		Index<Node> ottIdIndex = gdb.getNodeIndex("graphTaxUIDNodes", "type", "exact", "to_lower_case", "true");

		HashMap<Object,HashSet<String> > explodedTipsHash = new HashMap<Object,HashSet<String> >();
		
		for (Tree tree : trees) {
			for (TreeNode tip : tree.externalNodes()) {
			
				Object ottId = tip.getLabel();
				HashSet<String> hs = new HashSet<String> ();
				System.out.print("searching for ott id: " + ottId);

				Node hit = null;
				try {
					hit = ottIdIndex.get(NodeProperty.TAX_UID.propertyName, ottId).getSingle();
					if (hit == null) {
						System.out.println(". WARNING: could not find match this ott id.");
						hs.add((String) ottId);
						explodedTipsHash.put(tip, hs);
						continue;
					}
					System.out.print(". Found a node: " + hit + ". checking for tips below\n");
					for (Node n : Traversal.description().breadthFirst().relationships(RelType.TAXCHILDOF, Direction.INCOMING).traverse(hit).nodes()) {
						if (! n.hasRelationship(RelType.TAXCHILDOF, Direction.INCOMING)) {
							Object label = n.getProperty(NodeProperty.TAX_UID.propertyName);
							hs.add((String) label);
						}
					}
					explodedTipsHash.put(tip, hs);
				} catch (NoSuchElementException ex) {
					System.out.println("WARNING: more than one match was found for ott id " + ottId + ". this tip will not be exploded.");
				}
			}
		}
		return explodedTipsHash;
	}
	
	public static void main(String[] args) throws Exception {
		simpleTipExplodeTest();
		galliformesTipExplodeTest();
	}

	private static void simpleTipExplodeTest() throws Exception {
		
		List<Tree> t = new ArrayList<Tree>();

		t.add(TreeReader.readTree("((1,2),3);"));
		t.add(TreeReader.readTree("((1,3),2);"));
		
		String dbname = "test.db";
		String taxonomy = "test-synth/maptohigher/taxonomy.tsv";
		String synonyms = "test-synth/maptohigher/synonyms.tsv";

		runOTNewickTest(t, dbname, taxonomy, synonyms);
	}
	
	private static void galliformesTipExplodeTest() throws Exception {
		
		List<Tree> t = new ArrayList<Tree>();

		BufferedReader b = new BufferedReader(new FileReader("test-galliformes/pg_2577_5980.tre"));
		t.add(TreeReader.readTree(b.readLine()));
		b.close();
		
		String dbname = "test-galliformes/test.db";
		String taxonomy = "test-galliformes/taxonomy.tsv";
		String synonyms = "test-galliformes/synonyms.tsv";

		runOTNewickTest(t, dbname, taxonomy, synonyms);		
	}
	
	private static void runOTNewickTest(List<Tree> t, String dbname, String taxonomy, String synonyms) throws Exception {
		
		String version = "1";
		
		FileUtils.deleteDirectory(new File(dbname));
		
		GraphInitializer tl = new GraphInitializer(dbname);
		tl.addInitialTaxonomyTableIntoGraph(taxonomy, synonyms, version);
		tl.shutdownDB();

		GraphDatabaseAgent gdb = new GraphDatabaseAgent(dbname);
		
		System.out.println("incoming trees: ");
		for (Tree tree : t) { System.out.println(tree); }
		t = (ArrayList<Tree>) explodeTips(t, gdb);

		System.out.println("exploded trees: ");
		for (Tree tree : t) { System.out.println(tree); }
	}
}