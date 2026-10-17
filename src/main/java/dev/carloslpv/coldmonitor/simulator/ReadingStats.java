package dev.carloslpv.coldmonitor.simulator;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counts readings by the status the backend assigned to them, plus failed sends.
 */
final class ReadingStats {

	static final String FAILED = "FAILED";

	private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();

	void record(String status) {
		this.counts.computeIfAbsent(status, (key) -> new LongAdder()).increment();
	}

	Map<String, Long> snapshot() {
		var snapshot = new TreeMap<String, Long>();
		this.counts.forEach((status, count) -> snapshot.put(status, count.sum()));
		return snapshot;
	}

	String summary() {
		var snapshot = snapshot();
		if (snapshot.isEmpty()) {
			return "no readings sent";
		}
		var total = snapshot.entrySet()
			.stream()
			.filter((entry) -> !entry.getKey().equals(FAILED))
			.mapToLong(Map.Entry::getValue)
			.sum();
		var summary = new StringBuilder().append(total).append(" readings sent");
		snapshot.forEach((status, count) -> summary.append(", ").append(status).append('=').append(count));
		return summary.toString();
	}

}
