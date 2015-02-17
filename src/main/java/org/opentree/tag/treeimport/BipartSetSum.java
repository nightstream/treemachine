package org.opentree.tag.treeimport;

import jade.tree.Tree;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.opentree.bitarray.TLongBitArraySet;

import static java.util.stream.Collectors.*;

public class BipartSetSum implements Iterable<TLongBipartition> {
	
	private TLongBipartition[] bipart;

	public BipartSetSum(Collection<TLongBipartition> A) {
		bipart = sum(A, A).toArray(new TLongBipartition[0]);
	}
	
	public BipartSetSum(TLongBipartition[] original) {
		Collection<TLongBipartition> A = new HashSet<TLongBipartition>();
		for (int i = 0; i < original.length; i++) {
			A.add(original[i]);
		}
		bipart = sum(A, A).toArray(new TLongBipartition[0]);
	}
	
	/**
	 * The bipart sum can be performed more efficiently if some biparts are known to originate from
	 * the same tree, because no two biparts within a single tree may be completely overlapping. In
	 * this constructor, biparts from within a single tree can be supplied in groups corresponding
	 * to collections within a list. No biparts from within the same collection will be compared.
	 * @param trees
	 */
	public BipartSetSum(List<Collection<TLongBipartition>> bipartsByTree) {
		Set<TLongBipartition> biparts = new HashSet<TLongBipartition>();
		for (int i = 0; i < bipartsByTree.size(); i++) {
			for (int j = i+1; j < bipartsByTree.size(); j++) {
				biparts.addAll(sum(bipartsByTree.get(i), bipartsByTree.get(j)));
			}
		}
		bipart = biparts.toArray(new TLongBipartition[0]);
	}

	private static Set<TLongBipartition> combineWithAll(TLongBipartition b, Collection<TLongBipartition> others) {
		Set<TLongBipartition> x =
				(others.parallelStream().map(a -> a.sum(b)).collect(toSet())) // sum this bipart against all others
					.stream().filter(r -> r != null).collect(toSet()); // and filter null entries from the results
		return x;
	}
	
	private Collection<TLongBipartition> sum(Collection<TLongBipartition> A, Collection<TLongBipartition> B) {
		// sum all biparts against all others, and collect all the results into one set
		return A.parallelStream().map(a -> combineWithAll(a, B))
				.collect(() -> new HashSet(), (x, y) -> x.addAll(y), (x, y) -> x.addAll(y));
	}

	@Override
	public Iterator<TLongBipartition> iterator() {
		return new ArrayIterator();
	}
	
	public TLongBipartition[] toArray() {
		return bipart;
	}
	
	public static void main(String[] args) {
		testSimpleConflict();
		testSimplePartialOverlap();
		testLargerOutgroup();
		testFiveSymmetrical();
		testDuplicateSum();
		testDuplicateInputs();
		testNoOverlap();
//		testManyRandomAllByAll();
		testManyRandomGroups();
	}
	
	private static void testManyRandomAllByAll() {

		int maxId = 1000000;
		int count = 1000;
		int size = 100; // 1000 x 1000 takes a long time. should check into pure bitset implementation
		
		Set<TLongBipartition> input = new HashSet<TLongBipartition>();
		
		for (int i = 0; i < count; i++) {
			TLongBitArraySet ingroup = new TLongBitArraySet ();
			TLongBitArraySet outgroup = new TLongBitArraySet ();
			while(ingroup.size() + outgroup.size() < size) {
				int id = (int) Math.round(Math.random() * maxId);
				if (! (ingroup.contains(id) || outgroup.contains(id))) {
					if (Math.random() > 0.5) { outgroup.add(id); } else { ingroup.add(id); };
				}
			}
			input.add(new TLongBipartition(new TLongBitArraySet(ingroup), new TLongBitArraySet(outgroup)));
		}
		System.out.println("attempting " + count + " random bipartitions of size " + size + " (ingroup + outgroup)");
		long z = new Date().getTime();
		new BipartSetSum(input);
		System.out.println("elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
	}

	private static void testManyRandomGroups() {

		int maxId = 1000000;
		int n = 100;
		int count = 10;
		int size = 1000;
		
		//  on macbook pro 2.5Ghz i7 x 8 cores, running in Eclipse debugger
		//    3.0 secs : n =  60, count =  10, size =   10
		//    3.9 secs : n =  70, count =  10, size =   10
		//    5.9 secs : n =  80, count =  10, size =   10
		//    8.2 secs : n =  90, count =  10, size =   10
		//   11.9 secs : n = 100, count =  10, size =   10

		//   22   secs : n = 100, count =  20, size =   10
		//   52   secs : n = 100, count =  30, size =   10
		//   92   secs : n = 100, count =  40, size =   10
		//  146   secs : n = 100, count =  50, size =   10

		//    7.6 secs : n = 100, count =  10, size =   20
		//    8.1 secs : n = 100, count =  10, size =   30
		//    8.2 secs : n = 100, count =  10, size =   40
		//    8.6 secs : n = 100, count =  10, size =   50
		//        secs : n = 100, count =  10, size =  500
		//        secs : n = 100, count =  10, size = 1000  // out of memory, -Xmx16G
		
		
		List<Collection<TLongBipartition>> input = new ArrayList<Collection<TLongBipartition>>();
		
		for (int h = 0; h < n; h++) {
			HashSet<TLongBipartition> group = new HashSet<TLongBipartition>();
			for (int i = 0; i < count; i++) {
				TLongBitArraySet ingroup = new TLongBitArraySet ();
				TLongBitArraySet outgroup = new TLongBitArraySet ();
				while(ingroup.size() + outgroup.size() < size) {
					int id = (int) Math.round(Math.random() * maxId);
					if (! (ingroup.contains(id) || outgroup.contains(id))) {
						if (Math.random() > 0.5) { outgroup.add(id); } else { ingroup.add(id); };
					}
				}
				group.add(new TLongBipartition(new TLongBitArraySet(ingroup), new TLongBitArraySet(outgroup)));
			}
			input.add(group);
		}
		
		System.out.println("attempting " + n + " groups of "  + count + " random bipartitions (" + n*count + " total) of size " + size + " (ingroup + outgroup)");
		long z = new Date().getTime();
		new BipartSetSum(input);
		System.out.println("elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
	}

	private static void testNoOverlap() {
		long[][] in = new long[][]   {{1,2},  {4,5},  {7,8}};
		long[][] out = new long[][]  {{3},    {6},    {9}};
		long[][] inE = new long[][]  {};
		long[][] outE = new long[][] {};
		test("simple no overlap: sum should only contain original biparts", in, out, inE, outE);
	}

	private static void testSimplePartialOverlap() {
		long[][] in = new long[][]   {{1,3},     {1,5},   {1,3}};
		long[][] out = new long[][]  {{4},       {4},     {2}};
		long[][] inE = new long[][]  {{1, 3, 5}, {1, 3},  {1, 3, 5}};
		long[][] outE = new long[][] {{4}, 	     {2, 4},  {2, 4}};
		test("simple partial overlap", in, out, inE, outE);
	}

	private static void testLargerOutgroup() {
		long[][] in = new long[][]   {{2},   {3},   {1}};
		long[][] out = new long[][]  {{1,3,5,6,7}, {1,2,5,6,7}, {2,3,6,7}};
		long[][] inE = new long[][] {};
		long[][] outE = new long[][] {};
		test("bigger outgroups", in, out, inE, outE);
	}
	
	private static void testSimpleConflict() {
		long[][] in = new long[][]   {{1,2}, {1,3}, {2,3}};
		long[][] out = new long[][]  {{3},   {2},   {1}};
		long[][] inE = new long[][] {};
		long[][] outE = new long[][] {};
		test("simple conflict", in, out, inE, outE);
	}

	private static void testFiveSymmetrical() {
		long[][] in = new long[][]   {{1,2},  {3,4},  {5,6},  {7,8}, {9,10}};
		long[][] out = new long[][]  {{5,6},  {7,8},  {9,10}, {1,2}, {3,4}};
		long[][] inE = new long[][]  {};
		long[][] outE = new long[][] {};
		test("five symmetrical non overlap: sum should only contain original biparts", in, out, inE, outE);
	}
	
	private static void testDuplicateSum() {
		long[][] in = new long[][]   {{1,2}, {3,4}, {1,3}, {2,4}};
		long[][] out = new long[][]  {{5},   {5},   {5},   {5}};
		long[][] inE = new long[][]  {{1,2,3,4}, {1,2,3}, {1,2,4}, {1,3,4}, {2,3,4}};
		long[][] outE = new long[][] {{5},       {5},     {5},     {5},     {5},};
		test("duplication - looking for 2 instances of {1,2,3,4} | {5}", in, out, inE, outE);
	}
	
	private static void testDuplicateInputs() {
		long[][] in = new long[][]   {{1}, {2}, {1}, {2}};
		long[][] out = new long[][]  {{5}, {5}, {5}, {5}};
		long[][] inE = new long[][]  {{1,2}};
		long[][] outE = new long[][] {{5}};
		test("duplication - looking for more than one instance of any bipart", in, out, inE, outE);
	}

	private static void test(String name, long[][] in, long[][] out, long[][] inE, long[][] outE) {
		
		System.out.println("testing: " + name);
		
		List<TLongBipartition> inSet = makeBipartList(in, out);
		System.out.println("input:");
		for (TLongBipartition b : inSet) {
			System.out.println(b);
		}
		
		HashSet<TLongBipartition> expected = new HashSet<TLongBipartition>(makeBipartList(inE, outE));
		for (TLongBipartition bi : inSet) {
			expected.add(bi);
		}
		
		BipartSetSum b = new BipartSetSum(inSet.toArray(new TLongBipartition[0]));
		Set<TLongBipartition> observed = new HashSet<TLongBipartition>();
		for (TLongBipartition bi : b) {
			observed.add(bi);
		}
		
		for (TLongBipartition o : observed) {
			boolean found = false;
			for (TLongBipartition e : expected) {
				if (o.equals(e)) { found = true; break; }
			}
			if (! found) {
				System.out.println(o + " not in expected set.");
				throw new AssertionError();
			}
		}

		for (TLongBipartition e : expected) {
			boolean found = false;
			for (TLongBipartition o : observed) {
				if (o.equals(e)) { found = true; break; }
			}
			if (! found) {
				System.out.println(e + " not in expected set.");
				throw new AssertionError();
			}
		}
		
		if (observed.size() != expected.size()) {
			System.out.println("observed contains " + observed.size() + " but expected contains " + expected.size());
			throw new AssertionError();
		}

		System.out.println("output:");
		printBipartSum(b);
	}
		
	private static void printBipartSum(BipartSetSum b) {
		for (TLongBipartition bi : b) {
			System.out.println(bi);
		}
		System.out.println();
	}
	
	private static List<TLongBipartition> makeBipartList(long[][] ins, long[][] outs) {
		ArrayList<TLongBipartition> biparts = new ArrayList<TLongBipartition>();
		assert ins.length == outs.length;
		for (int i = 0; i < ins.length; i++) {
			biparts.add(new TLongBipartition(ins[i],outs[i]));
		}
		return biparts;
	}

	private class ArrayIterator implements Iterator<TLongBipartition> {
		int i = 0;
		public ArrayIterator() { }
		@Override
		public boolean hasNext() {
			return i < bipart.length;
		}
		@Override
		public TLongBipartition next() {
			return bipart[i++];
		}
		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}