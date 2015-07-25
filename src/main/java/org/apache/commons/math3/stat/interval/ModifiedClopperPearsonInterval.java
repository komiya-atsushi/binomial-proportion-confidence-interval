package org.apache.commons.math3.stat.interval;

import org.apache.commons.math3.distribution.FDistribution;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.OutOfRangeException;

/**
 * numberOfSuccesses が 0 もしくは numberOfTrials と等しい場合でも、
 * とりあえず区間の一方のエンドポイントを算出できるようにした
 * Clopper-Pearson 'Exact' の実装です。
 */
public class ModifiedClopperPearsonInterval implements BinomialConfidenceInterval {

	@Override
	public ConfidenceInterval createInterval(int numberOfTrials, int numberOfSuccesses, double confidenceLevel) throws NotStrictlyPositiveException, NotPositiveException, NumberIsTooLargeException, OutOfRangeException {
		double lowerBound = 0;
		double upperBound = 1;
		final double alpha = (1.0 - confidenceLevel) / 2.0;

		if (numberOfSuccesses > 0) {
			final FDistribution distributionLowerBound = new FDistribution(2 * (numberOfTrials - numberOfSuccesses + 1),
					2 * numberOfSuccesses);
			final double fValueLowerBound = distributionLowerBound.inverseCumulativeProbability(1 - alpha);
			lowerBound = numberOfSuccesses /
					(numberOfSuccesses + (numberOfTrials - numberOfSuccesses + 1) * fValueLowerBound);
		}

		if (numberOfSuccesses < numberOfTrials) {
			final FDistribution distributionUpperBound = new FDistribution(2 * (numberOfSuccesses + 1),
					2 * (numberOfTrials - numberOfSuccesses));
			final double fValueUpperBound = distributionUpperBound.inverseCumulativeProbability(1 - alpha);
			upperBound = (numberOfSuccesses + 1) * fValueUpperBound /
					(numberOfTrials - numberOfSuccesses + (numberOfSuccesses + 1) * fValueUpperBound);
		}

		return new ConfidenceInterval(lowerBound, upperBound, confidenceLevel);
	}
}
