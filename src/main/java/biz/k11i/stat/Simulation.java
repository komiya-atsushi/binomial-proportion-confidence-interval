package biz.k11i.stat;

import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.stat.interval.AgrestiCoullInterval;
import org.apache.commons.math3.stat.interval.BinomialConfidenceInterval;
import org.apache.commons.math3.stat.interval.ConfidenceInterval;
import org.apache.commons.math3.stat.interval.ModifiedClopperPearsonInterval;
import org.apache.commons.math3.stat.interval.NormalApproximationInterval;
import org.apache.commons.math3.stat.interval.WilsonScoreInterval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * 二項比率の各種区間推定方法について、シミュレーション結果に基づいてそのカバレッジを計測します。
 */
public class Simulation {
	/**
	 * シミュレーション条件を表します。
	 */
	static class Condition {
		private final double p;
		private final int numTrials;
		private final double confidenceLevel;

		/**
		 * @param p               二項比率
		 * @param numTrials       試行回数
		 * @param confidenceLevel 信頼度
		 */
		Condition(double p, int numTrials, double confidenceLevel) {
			this.p = p;
			this.numTrials = numTrials;
			this.confidenceLevel = confidenceLevel;
		}

		Result simulate(RandomGenerator random) {
			int numSuccesses = 0;

			for (int i = 0; i < numTrials; i++) {
				if (random.nextDouble() < p) {
					numSuccesses++;
				}
			}

			return new Result(this, numSuccesses);
		}

		@Override
		public String toString() {
			return String.format("Condition: p = %.4f, %d trials, %.3f confidence level",
					p, numTrials, confidenceLevel);
		}
	}

	/**
	 * 1 回のシミュレーション結果を表します。
	 */
	static class Result {
		private final Condition condition;
		private final int numSuccesses;

		Result(Condition condition, int numSuccesses) {
			this.condition = condition;
			this.numSuccesses = numSuccesses;
		}

		boolean isCoveredBy(BinomialConfidenceInterval binomialCOnfidenceInterval) {
			ConfidenceInterval interval = binomialCOnfidenceInterval.createInterval(
					condition.numTrials,
					numSuccesses,
					condition.confidenceLevel);

			return interval.getLowerBound() <= condition.p &&
					condition.p <= interval.getUpperBound();
		}
	}

	/**
	 * 区間推定結果のカバレッジを表します。
	 */
	static class Coverage {
		private final BinomialConfidenceInterval binomialConfidenceInterval;

		private int numSimulations;
		private int numCovers;
		private int numFailure;

		private long totalElapsedMillis;

		Coverage(BinomialConfidenceInterval binomialConfidenceInterval) {
			this.binomialConfidenceInterval = binomialConfidenceInterval;
		}

		void updateCoverage(Result result) {
			numSimulations++;

			try {
				long begin = System.currentTimeMillis();
				boolean isCovered = result.isCoveredBy(binomialConfidenceInterval);
				totalElapsedMillis += (System.currentTimeMillis() - begin);

				if (isCovered) {
					numCovers++;
				}

			} catch (MathIllegalArgumentException e) {
				numFailure++;
			}
		}

		public String label() {
			return binomialConfidenceInterval.getClass().getSimpleName();
		}

		@Override
		public String toString() {
			double coverage = 1.0 * numCovers / numSimulations;
			return String.format("%s:\n  %d / %d (%.4f%%)\n  %d failures\n  %s milliseconds",
					label(),
					numCovers, numSimulations, coverage,
					numFailure,
					totalElapsedMillis);
		}
	}

	/**
	 * 一つのシミュレーション条件下における各種区間推定方法の結果を表します。
	 */
	static class Report {
		private Condition condition;
		private List<Coverage> coverages;

		public Report(Condition condition, List<Coverage> coverages) {
			this.condition = condition;
			this.coverages = coverages;
		}

		public void showReport() {
			System.out.println("----------");
			System.out.println(condition);
			coverages.forEach(System.out::println);
		}

		public String asTsv() {
			List<String> lines = new ArrayList<>();

			Stack<String> line = new Stack<>();
			line.push(Double.toString(condition.p));
			line.push(Integer.toString(condition.numTrials));
			line.push(Double.toString(condition.confidenceLevel));

			coverages.forEach(coverage -> {
				line.push(coverage.label());
				line.push(Integer.toString(coverage.numCovers));
				line.push(Integer.toString(coverage.numSimulations));
				line.push(Integer.toString(coverage.numFailure));
				line.push(Long.toString(coverage.totalElapsedMillis));

				lines.add(String.join("\t", line));

				line.pop();
				line.pop();
				line.pop();
				line.pop();
				line.pop();
			});

			return String.join("\n", lines);
		}
	}

	public static void main(String[] args) {
		final int NUM_SIMULATIONS = 1_000_000;

		List<Double> proportions = Arrays.asList(
				0.10, 0.09, 0.08, 0.07, 0.06, 0.05, 0.04, 0.03, 0.02, 0.01,
				0.009, 0.008, 0.007, 0.006, 0.005, 0.004, 0.003, 0.002, 0.001
		);
		List<Integer> sampleSizes = Arrays.asList(
				100, 200, 300, 400, 500, 600, 700, 800, 900,
				1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000,
				10000
		);

		double confidenceLevel = 0.95;

		List<Condition> conditions = proportions.stream()
				.flatMap(p -> sampleSizes.stream()
						.map(n -> new Condition(p, n, confidenceLevel)))
				.collect(Collectors.toList());

		List<BinomialConfidenceInterval> binomialConfidenceIntervals = Arrays.asList(
				new NormalApproximationInterval(),
				new ModifiedClopperPearsonInterval(),
				new WilsonScoreInterval(),
				new AgrestiCoullInterval());

		List<Report> reports = conditions.parallelStream()
				.map(condition -> {
					List<Coverage> coverages = binomialConfidenceIntervals.stream()
							.map(Coverage::new)
							.collect(Collectors.toList());

					MersenneTwister rng = new MersenneTwister(1);
					for (int i = 0; i < NUM_SIMULATIONS; i++) {
						Result result = condition.simulate(rng);
						coverages.forEach(coverage -> coverage.updateCoverage(result));
					}

					return new Report(condition, coverages);
				})
				.collect(Collectors.toList());

		reports.forEach(report -> System.out.println(report.asTsv()));
	}
}
