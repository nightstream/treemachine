package opentree.synthesis.mwis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import opentree.TLongBitArraySet;

public class GreedyApproximateWeightedIS extends BaseWeightedIS {

	HashMap<Long, Double> scores;

	public GreedyApproximateWeightedIS(Long[] ids, TLongBitArraySet[] descendants) {
		super(ids, descendants);
		scores = new HashMap<Long, Double>();
		findBestSet();
	}
	
	/**
	 * Repeatedly pick the most heavily weighted set from among the remaining sets
	 * and save it, then eliminate it and its adjacent vertices. Continue until no
	 * sets remain.
	 */
	private void findBestSet() {

		bestSet = new ArrayList<Long>();

		// all sets begin available -- open all sites in a bitmask
		BitMask available = getEmptyBitMask(ids.length);
		for (int i = 0; i < ids.length; i++) {
			available.open(i);
		}
		
		// repeatedly find the best set out of all the available sets
		while (available.openBits() > 0) {
			Integer bestRel = null;
			double bestScore = 0;

			// find the rel with the highest score
			for (int i : available) {
				double s = getScoreForRel(i);
				if (s > bestScore) {
					bestRel = i;
					bestScore = s;
				}
			}
			
			// found a best rel, now save it and exclude it and all overlapping rels
			bestSet.add(ids[bestRel]);
			available.close(bestRel);
			for (int i : available) {
				if (descendants[bestRel].containsAny(descendants[i])) {
					available.close(i);
				}
			}
		}

		Collections.sort(bestSet);
	}
	
	private double getScoreForRel(int i) {
		if (! scores.containsKey(ids[i])) {
			scores.put(ids[i], (double) descendants[i].size());
		}
		return scores.get(ids[i]);
	}
		
	public static void main(String[] args) {
		simpleTest1(); // fails greedy approximation!
		simpleTest2();
		randomInputTest(Integer.valueOf(args[0]));
	}
	
}
