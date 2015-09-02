/**
 * Copyright (C) 2015 Turn Inc. All Rights Reserved.
 * Proprietary and confidential.
 */
package com.turn.splicer.tsdbutils;

import com.google.common.collect.Lists;
import com.turn.splicer.merge.TsdbResult;
import com.turn.splicer.tsdbutils.expression.AggregationIterator;
import com.turn.splicer.tsdbutils.expression.Expression;
import com.turn.splicer.tsdbutils.expression.SeekableViewDataPointImpl;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Functions {

	private static final Logger logger = Logger.getLogger(Functions.class);

	private static enum MaxExpressionType {
		CURRENT, MAX
	}

	public static class MovingAverageFunction implements Expression {

		@Override
		public TsdbResult[] evaluate(TsQuery dataQuery, List<TsdbResult[]> queryResults, List<String> params) {

			//TODO: Why can MovingAverageFunction return an empty set

			if (queryResults == null || queryResults.isEmpty()) {
				return new TsdbResult[]{};
			}

			if (params == null || params.isEmpty()) {
				throw new NullPointerException("Need aggregation window for moving average");
			}

			String param = params.get(0);
			if (param == null || param.length() == 0) {
				throw new NullPointerException("Invalid window='" + param + "'");
			}

			param = param.trim();

			long numPoints = -1;
			boolean isTimeUnit = false;
			if (param.matches("[0-9]+")) {
				numPoints = Integer.parseInt(param);
			} else if (param.startsWith("'") && param.endsWith("'")) {
				numPoints = parseParam(param);
				isTimeUnit = true;
			}

			if (numPoints <= 0) {
				throw new RuntimeException("numPoints <= 0");
			}

			int size = 0;
			for (TsdbResult[] results : queryResults) {
				size = size + results.length;
			}

			SeekableView[] views = new SeekableView[size];
			int index = 0;

			for (TsdbResult[] resultArray : queryResults) {
				for (TsdbResult oneResult : resultArray) {
					try {
						views[index] = new SeekableViewDataPointImpl(oneResult.getDps().getDataPointsFromTreeMap());
					} catch (Exception e) {
						e.printStackTrace();
					}
					index++;
				}
			}

			SeekableView view = new AggregationIterator(views,
					dataQuery.startTime() / 1000, dataQuery.endTime() / 1000,
					new Aggregators.MovingAverage(Aggregators.Interpolation.LERP, "movingAverage", numPoints, isTimeUnit),
					Aggregators.Interpolation.LERP, false);

			TsdbResult singleResult;

			if (queryResults.size() > 0 && queryResults.get(0).length > 0) {
				singleResult = TsdbResult.copyMeta(queryResults.get(0)[0]);
				while (view.hasNext()) {
					singleResult.getDps().addPoint(view.next());
				}
			} else {
				singleResult = new TsdbResult();
			}


			TsdbResult[] finalResult = new TsdbResult[1];
			finalResult[0] = singleResult;

			return finalResult;


		}

		public long parseParam(String param) {
			char[] chars = param.toCharArray();
			int tuIndex = 0;
			for (int c = 1; c < chars.length; c++) {
				if (Character.isDigit(chars[c])) {
					tuIndex++;
				} else {
					break;
				}
			}

			if (tuIndex == 0) {
				throw new RuntimeException("Invalid Parameter: " + param);
			}

			int time = Integer.parseInt(param.substring(1, tuIndex + 1));
			String unit = param.substring(tuIndex + 1, param.length() - 1);

			if ("min".equals(unit)) {
				return TimeUnit.MILLISECONDS.convert(time, TimeUnit.MINUTES);
			} else if ("hr".equals(unit)) {
				return TimeUnit.MILLISECONDS.convert(time, TimeUnit.HOURS);
			} else if ("sec".equals(unit)) {
				return TimeUnit.MILLISECONDS.convert(time, TimeUnit.SECONDS);
			} else {
				throw new RuntimeException("unknown time unit=" + unit);
			}

		}

		@Override
		public String writeStringField(List<String> queryParams, String innerExpression) {
			return "movingAverage(" + innerExpression + ")";
		}

	}
//
	public static class HighestMax implements Expression {
		@Override
		public TsdbResult[] evaluate(TsQuery query, List<TsdbResult[]> queryResults,
		                             List<String> params) {
			return evaluateHighestExpr(query, queryResults, params, MaxExpressionType.MAX);
		}

		@Override
		public String writeStringField(List<String> queryParams, String innerExpression) {
			return "highestMax(" + innerExpression + ")";
		}
	}

	private static TsdbResult[] evaluateHighestExpr(TsQuery query, List<TsdbResult[]> queryResults,
								 List<String> params, MaxExpressionType maxType) {
			if (queryResults == null || queryResults.isEmpty()) {
				throw new NullPointerException("Query results cannot be empty");
			}

			if (params == null || params.isEmpty()) {
				throw new NullPointerException("Need aggregation window for moving average");
			}

			String param = params.get(0);
			if (param == null || param.length() == 0) {
				throw new NullPointerException("Invalid window='" + param + "'");
			}

			int k = Integer.parseInt(param.trim());

			int size = 0;
			for (TsdbResult[] results : queryResults) {
				size = size + results.length;
			}

			//If we don't have enough time series we can just return all of them
			if (k >= size) {
				TsdbResult[] finalResults = new TsdbResult[size];
				int index = 0;
				for(TsdbResult[] resultArray: queryResults) {
					for(TsdbResult oneResult: resultArray) {
						try {
							finalResults[index] = oneResult;
						} catch (Exception e) {
							e.printStackTrace();
						}
						index++;
					}
				}
				return finalResults;
			}

			SeekableView[] views = new SeekableView[size];
			TsdbResult[] allResults = new TsdbResult[size];
			int index = 0;
			for(TsdbResult[] resultArray: queryResults) {
				for(TsdbResult oneResult: resultArray) {
					try {
						views[index] = new SeekableViewDataPointImpl(oneResult.getDps().getDataPointsFromTreeMap());
					} catch (Exception e) {
						e.printStackTrace();
					}
					allResults[index] = oneResult;
					index++;
				}
			}

			MaxAggregator aggregator;

			if(maxType.equals(MaxExpressionType.CURRENT)) {
				 aggregator = new
						Aggregators.MaxLatestAggregator(Aggregators.Interpolation.LERP,
						"maxLatest", size, query.startTime() / 1000, query.endTime() / 1000);
			} else {
				 aggregator = new Aggregators.MaxCacheAggregator(
						Aggregators.Interpolation.LERP, "maxCache", size, query.startTime() / 1000, query.endTime() / 1000);
			}


			SeekableView view = (new AggregationIterator(views,
					query.startTime() / 1000, query.endTime() / 1000,
					aggregator, Aggregators.Interpolation.LERP, false));

			// slurp all the points
			while (view.hasNext()) {
				DataPoint mdp = view.next();
				Object o = mdp.isInteger() ? mdp.longValue() : mdp.doubleValue();
			}

			class Entry {
				public Entry(double val, int pos) {
					this.val = val;
					this.pos = pos;
				}

				double val;
				int pos;
			}

			long[] maxLongs = aggregator.getLongMaxes();
			double[] maxDoubles = aggregator.getDoubleMaxes();
			Entry[] maxesPerTS = new Entry[size];
			if (aggregator.hasDoubles() && aggregator.hasLongs()) {
				for (int i = 0; i < size; i++) {
					maxesPerTS[i] = new Entry(Math.max((double) maxLongs[i], maxDoubles[i]), i);
				}
			} else if (aggregator.hasLongs() && !aggregator.hasDoubles()) {

				for (int i = 0; i < size; i++) {
					maxesPerTS[i] = new Entry((double) maxLongs[i], i);
				}
			} else if (aggregator.hasDoubles() && !aggregator.hasLongs()) {

				for (int i = 0; i < size; i++) {
					maxesPerTS[i] = new Entry(maxDoubles[i], i);
				}
			}

			logger.info("Before Sorting: " + Arrays.toString(maxesPerTS));

			Arrays.sort(maxesPerTS, new Comparator<Entry>() {
				@Override
				public int compare(Entry o1, Entry o2) {
					// we want in descending order
					if(o1 == null) {
						System.out.println("o1 is null");
					}
					if(o2 == null) {
						System.out.println("o2 is null");
					}
					return -1 * Double.compare(o1.val, o2.val);
				}
			});

			logger.info("After Sorting: " + Arrays.toString(maxesPerTS));


			//so if I understand it results[i] should be filled with one
			// of the k series with highest max
			//meaning maxesPerTS[i].pos should equal the index
			//of the TS with the a k highest max

			TsdbResult[] results = new TsdbResult[k];
			for (int i = 0; i < k; i++) {

				//makes sense! need to return the .pos TsdbResult in the List
				//not 100% sure how to do that

				results[i] = allResults[maxesPerTS[i].pos];
			}

			return results;
		}

	public static class HighestCurrent implements Expression {
		@Override
		public TsdbResult[] evaluate(TsQuery query, List<TsdbResult[]> queryResults,
		                             List<String> params) {
			return evaluateHighestExpr(query, queryResults, params, MaxExpressionType.CURRENT);
		}

		@Override
		public String writeStringField(List<String> queryParams, String innerExpression) {
			return "highestCurrent(" + innerExpression + ")";
		}
	}

	public static class DivideSeriesFunction implements Expression {

		@Override
		public TsdbResult[] evaluate(TsQuery dataQuery, List<TsdbResult[]> queryResults, List<String> params) {
			if (queryResults == null || queryResults.isEmpty()) {
				throw new NullPointerException("Query results cannot be empty");
			}

			//we'll end up with x + -y
			TsdbResult x, y;
			if (queryResults.size() == 2 && queryResults.get(0).length == 1
					&& queryResults.get(1).length == 1) {
				x = queryResults.get(0)[0];
				y = queryResults.get(1)[0];
			} else if (queryResults.size() == 1 && queryResults.get(0).length == 2) {
				x = queryResults.get(0)[0];
				y = queryResults.get(0)[1];
			} else {
				throw new RuntimeException("Expected two query results for difference");
			}

			int size = 2;
			SeekableView[] views = new SeekableView[size];

			//now add x to views

			try {
				views[0] = new SeekableViewDataPointImpl(x.getDps().getDataPointsFromTreeMap());
				views[1] = new SeekableViewDataPointImpl(y.getDps().getDataPointsFromTreeMapReciprocal());
			} catch (Exception e) {
				e.printStackTrace();
			}

			SeekableView view = (new AggregationIterator(views,
					dataQuery.startTime() / 1000, dataQuery.endTime() / 1000,
					Aggregators.MULTIPLY, Aggregators.Interpolation.LERP, false));

			List<DataPoint> points = Lists.newArrayList();

			TsdbResult singleResult;

			if (queryResults.size() > 0 && queryResults.get(0).length > 0) {
				singleResult = TsdbResult.copyMeta(queryResults.get(0)[0]);
				while(view.hasNext()) {
					singleResult.getDps().addPoint(view.next());
				}
			} else {
				singleResult = new TsdbResult();
			}


			TsdbResult[] finalResult = new TsdbResult[1];
			finalResult[0] = singleResult;

			return finalResult;
		}


		@Override
		public String writeStringField(List<String> queryParams, String innerExpression) {
			return "divideSeries(" + innerExpression + ")";
		}
	}

	public static class MultiplySeriesFunction implements Expression {

		@Override
		public TsdbResult[] evaluate(TsQuery dataQuery, List<TsdbResult[]> queryResults, List<String> queryParams) {
			if (queryResults == null || queryResults.isEmpty()) {
				throw new NullPointerException("Query results cannot be empty");
			}

			int size = 0;
			for(TsdbResult[] queryResult: queryResults) {
				size = size + queryResult.length;
			}

			SeekableView[] views = new SeekableView[size];
			int index = 0;
			for(TsdbResult[] resultArray: queryResults) {
				for(TsdbResult oneResult: resultArray) {
					try {
						views[index] = new SeekableViewDataPointImpl(oneResult.getDps().getDataPointsFromTreeMap());
					} catch (Exception e) {
						e.printStackTrace();
					}
					index++;
				}
			}

			SeekableView view = (new AggregationIterator(views,
					dataQuery.startTime() / 1000, dataQuery.endTime() / 1000,
					Aggregators.MULTIPLY, Aggregators.Interpolation.LERP, false));

			List<DataPoint> points = Lists.newArrayList();

			TsdbResult singleResult;

			if (queryResults.size() > 0 && queryResults.get(0).length > 0) {
				singleResult = TsdbResult.copyMeta(queryResults.get(0)[0]);
				while(view.hasNext()) {
					singleResult.getDps().addPoint(view.next());
				}
			} else {
				singleResult = new TsdbResult();
			}


			TsdbResult[] finalResult = new TsdbResult[1];
			finalResult[0] = singleResult;

			return finalResult;
		}

		@Override
		public String writeStringField(List<String> queryParams, String innerExpression) {
			return "multiplySeries(" + innerExpression + ")";
		}
	}

	public static class DifferenceSeriesFunction implements Expression {

		@Override
		public TsdbResult[] evaluate(TsQuery dataQuery, List<TsdbResult[]> queryResults, List<String> params) {
			if (queryResults == null || queryResults.isEmpty()) {
				throw new NullPointerException("Query results cannot be empty");
			}

			//we'll end up with x + -y
			TsdbResult x, y;
			if (queryResults.size() == 2 && queryResults.get(0).length == 1
					&& queryResults.get(1).length == 1) {
				x = queryResults.get(0)[0];
				y = queryResults.get(1)[0];
			} else if (queryResults.size() == 1 && queryResults.get(0).length == 2) {
				x = queryResults.get(0)[0];
				y = queryResults.get(0)[1];
			} else {
				throw new RuntimeException("Expected two query results for difference");
			}

			int size = 2;
			SeekableView[] views = new SeekableView[size];

			//now add x to views

			try {
				views[0] = new SeekableViewDataPointImpl(x.getDps().getDataPointsFromTreeMap());
				views[1] = new SeekableViewDataPointImpl(y.getDps().getDataPointsFromTreeMap(-1));
			} catch (Exception e) {
				e.printStackTrace();
			}

			SeekableView view = (new AggregationIterator(views,
					dataQuery.startTime() / 1000, dataQuery.endTime() / 1000,
					Aggregators.SUM, Aggregators.Interpolation.LERP, false));

			List<DataPoint> points = Lists.newArrayList();

			TsdbResult singleResult;

			if (queryResults.size() > 0 && queryResults.get(0).length > 0) {
				singleResult = TsdbResult.copyMeta(queryResults.get(0)[0]);
				while(view.hasNext()) {
					singleResult.getDps().addPoint(view.next());
				}
			} else {
				singleResult = new TsdbResult();
			}


			TsdbResult[] finalResult = new TsdbResult[1];
			finalResult[0] = singleResult;

			return finalResult;
		}

		@Override
		public String writeStringField(List<String> queryParams, String innerExpression) {
			return "differenceSeries(" + innerExpression + ")";
		}
	}

	public static class SumSeriesFunction implements Expression {

		@Override
		public TsdbResult[] evaluate(TsQuery dataQuery, List<TsdbResult[]> queryResults, List<String> params) {
			if (queryResults == null || queryResults.isEmpty()) {
				throw new NullPointerException("Query results cannot be empty");
			}

			int size = 0;
			for(TsdbResult[] queryResult: queryResults) {
				size = size + queryResult.length;
			}

			SeekableView[] views = new SeekableView[size];
			int index = 0;
			for(TsdbResult[] resultArray: queryResults) {
				for(TsdbResult oneResult: resultArray) {
					try {
						views[index] = new SeekableViewDataPointImpl(oneResult.getDps().getDataPointsFromTreeMap());
					} catch (Exception e) {
						e.printStackTrace();
					}
					index++;
				}
			}

			SeekableView view = (new AggregationIterator(views,
					dataQuery.startTime() / 1000, dataQuery.endTime() / 1000,
					Aggregators.SUM, Aggregators.Interpolation.LERP, false));

			//Ok now I just need to make the AggregationIterator spit out
			//Map elements or convert them to data points?

			TsdbResult singleResult;

			if (queryResults.size() > 0 && queryResults.get(0).length > 0) {
				singleResult = TsdbResult.copyMeta(queryResults.get(0)[0]);
				while(view.hasNext()) {
					singleResult.getDps().addPoint(view.next());
				}
			} else {
				singleResult = new TsdbResult();
			}


			TsdbResult[] finalResult = new TsdbResult[1];
			finalResult[0] = singleResult;

			return finalResult;
		}

		@Override
		public String writeStringField(List<String> queryParams, String innerExpression) {
			return "sumSeries(" + innerExpression + ")";
		}
	}

	public static class AbsoluteValueFunction implements Expression {

		@Override
		public TsdbResult[] evaluate(TsQuery dataQuery, List<TsdbResult[]> queryResults, List<String> params) {
			if (queryResults == null || queryResults.isEmpty()) {
				throw new NullPointerException("Query results cannot be empty");
			}

			TsdbResult[] inputPoints = queryResults.get(0);

			for (int i = 0; i < inputPoints.length; i++) {
				absoluteValue(inputPoints[i]);
			}

			return inputPoints;
		}

		private TsdbResult absoluteValue(TsdbResult input) {
			Map<String, Object> inputMap = input.getDps().getMap();
			for(String key : inputMap.keySet()) {
				Object val = inputMap.get(key);
				if(val instanceof Double) {
					double value = Math.abs((double) val);
					inputMap.put(key, value);
				} else if (val instanceof Long) {
					long value = Math.abs((long) val);
					inputMap.put(key, value);
				} else {
					throw new RuntimeException("Expected type Long or Double instead found: "
							+ val.getClass());
				}
			}

			return input;
		}


		@Override
		public String writeStringField(List<String> queryParams, String innerExpression) {
			return "abs(" + innerExpression + ")";
		}
	}

	public static class ScaleFunction implements Expression {

		@Override
		public TsdbResult[] evaluate(TsQuery dataQuery, List<TsdbResult[]> queryResults, List<String> params) {
			if (queryResults == null || queryResults.isEmpty()) {
				throw new NullPointerException("Query results cannot be empty");
			}

			if (params == null || params.isEmpty()) {
				throw new NullPointerException("Scaling parameter not available");
			}

			String factor = params.get(0);
			factor = factor.replaceAll("'|\"", "").trim();
			double scaleFactor = Double.parseDouble(factor);

			TsdbResult[] inputPoints = queryResults.get(0);

			for (int i = 0; i < inputPoints.length; i++) {
				scale(inputPoints[i], scaleFactor);
			}

			return inputPoints;
		}

		private TsdbResult scale(TsdbResult input, double scaleFactor) {
			//now iterate over all points in the input map and add them to output

			Map<String, Object> inputMap = input.getDps().getMap();
			for (String key : inputMap.keySet()) {
				Object val = inputMap.get(key);
				if (val instanceof Double) {
					inputMap.put(key, new Double(((Double) val).doubleValue() * scaleFactor));
				} else if (val instanceof Long) {
					inputMap.put(key, new Long(((Long) val).longValue() * (long) scaleFactor));
				} else {
					//throw an exception
					throw new RuntimeException("Expected type Long or Double instead found: "
							+ val.getClass());
				}
			}

			return input;
		}

		@Override
		public String writeStringField(List<String> queryParams, String innerExpression) {
			return "scale(" + innerExpression + ")";
		}
	}

}