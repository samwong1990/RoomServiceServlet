package hk.samwong.roomservice.servlet.classifierImplementation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.sf.javaml.classification.AbstractClassifier;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.Instance;
import net.sf.javaml.core.exception.TrainingRequiredException;
import net.sf.javaml.distance.DistanceMeasure;
import net.sf.javaml.distance.EuclideanDistance;

/**
 * Implementation of the Weighted K nearest neighbor (KNN) classification
 * algorithm according to Final Report (The one from 3 years ago).
 * 
 * Almost identical to the KNearestNeighbours by Thomas Abeel, but since fields
 * aren't protected, I just copied the whole thing over and modify the
 * classDistribution method (one line)
 * 
 * @author s@mwong.hk
 * 
 */
public class WeightedKNearestNeighbors extends AbstractClassifier {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5952567439019160446L;

	private Dataset training;

	private int k;

	private DistanceMeasure dm;

	/**
	 * Instantiate the k-nearest neighbors algorithm with a specified number of
	 * neighbors.
	 * 
	 * @param k
	 *            the number of neighbors to use
	 */
	public WeightedKNearestNeighbors(int k) {
		this(k, new EuclideanDistance());
	}

	/**
	 * Instantiate the k-nearest neighbors algorithm with a specified number of
	 * neighbors.
	 * 
	 * @param k
	 *            the number of neighbors to use
	 */
	public WeightedKNearestNeighbors(int k, DistanceMeasure dm) {
		this.k = k;
		this.dm = dm;
	}

	@Override
	public void buildClassifier(Dataset data) {
		this.training = data;
	}

	@Override
	public Map<Object, Double> classDistribution(Instance instance) {
		if (training == null)
			throw new TrainingRequiredException();
		/* Get nearest neighbors */
		Set<Instance> neighbors = training.kNearest(k, instance, dm);
		/* Build distribution map */
		HashMap<Object, Double> out = new HashMap<Object, Double>();
		for (Object o : training.classes())
			out.put(o, 0.0);
		for (Instance i : neighbors) {
			out.put(i.classValue(), out.get(i.classValue()) + (1/Math.pow(dm.measure(instance, i), 2)) ); // The only modified line
		}

		double min = k;
		double max = 0;
		for (Object key : out.keySet()) {
			double val = out.get(key);
			if (val > max)
				max = val;
			if (val < min)
				min = val;
		}
		/* Normalize distribution map */
		if (max != min) {
			for (Object key : out.keySet()) {
				out.put(key, (out.get(key) - min) / (max - min));
			}
		}

		return out;
	}
}