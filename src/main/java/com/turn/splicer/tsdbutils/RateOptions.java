/**
 * Copyright 2015-2016 The Splicer Query Engine Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.turn.splicer.tsdbutils;

/**
 * Provides additional options that will be used when calculating rates. These
 * options are useful when working with metrics that are raw counter values,
 * where a counter is defined by a value that always increases until it hits
 * a maximum value and then it "rolls over" to start back at 0.
 * <p/>
 * These options will only be utilized if the query is for a rate calculation
 * and if the "counter" options is set to true.
 *
 * @since 2.0
 */
public class RateOptions {
	public static final long DEFAULT_RESET_VALUE = 0;

	/**
	 * If true, then when calculating a rate of change assume that the metric
	 * values are counters and thus non-zero, always increasing and wrap around at
	 * some maximum
	 */
	private boolean counter;

	/**
	 * If calculating a rate of change over a metric that is a counter, then this
	 * value specifies the maximum value the counter will obtain before it rolls
	 * over. This value will default to Long.MAX_VALUE.
	 */
	private long counter_max;

	/**
	 * Specifies the the rate change value which, if exceeded, will be considered
	 * a data anomaly, such as a system reset of the counter, and the rate will be
	 * returned as a zero value for a given data point.
	 */
	private long reset_value;

	/**
	 * Ctor
	 */
	public RateOptions() {
		this.counter = false;
		this.counter_max = Long.MAX_VALUE;
		this.reset_value = DEFAULT_RESET_VALUE;
	}

	/**
	 * Ctor
	 *
	 * @param counter     If true, indicates that the rate calculation should assume
	 *                    that the underlying data is from a counter
	 * @param counter_max Specifies the maximum value for the counter before it
	 *                    will roll over and restart at 0
	 * @param reset_value Specifies the largest rate change that is considered
	 *                    acceptable, if a rate change is seen larger than this value then the
	 *                    counter is assumed to have been reset
	 */
	public RateOptions(final boolean counter, final long counter_max,
	                   final long reset_value) {
		this.counter = counter;
		this.counter_max = counter_max;
		this.reset_value = reset_value;
	}

	/**
	 * @return Whether or not the counter flag is set
	 */
	public boolean isCounter() {
		return counter;
	}

	/**
	 * @return The counter max value
	 */
	public long getCounterMax() {
		return counter_max;
	}

	/**
	 * @return The optional reset value for anomaly suppression
	 */
	public long getResetValue() {
		return reset_value;
	}

	/**
	 * @param counter Whether or not the time series should be considered counters
	 */
	public void setIsCounter(boolean counter) {
		this.counter = counter;
	}

	/**
	 * @param counter_max The value at which counters roll over
	 */
	public void setCounterMax(long counter_max) {
		this.counter_max = counter_max;
	}

	/**
	 * @param reset_value A difference that may be an anomaly so suppress it
	 */
	public void setResetValue(long reset_value) {
		this.reset_value = reset_value;
	}

	/**
	 * Generates a String version of the rate option instance in a format that
	 * can be utilized in a query.
	 *
	 * @return string version of the rate option instance.
	 */
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append('{');
		buf.append(counter);
		buf.append(',').append(counter_max);
		buf.append(',').append(reset_value);
		buf.append('}');
		return buf.toString();
	}
}
