package dev.carloslpv.coldmonitor.simulator;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One simulated sensor: generates a temperature, sends it to the backend and waits for the next tick, until its
 * thread is interrupted. Sending errors are logged and the sensor keeps going, like a real device would.
 */
final class SensorSimulator implements Runnable {

	private final UUID equipmentId;

	private final String code;

	private final TemperatureModel model;

	private final ColdMonitorClient client;

	private final Duration interval;

	private final Duration initialDelay;

	private final ReadingStats stats;

	SensorSimulator(UUID equipmentId, String code, TemperatureModel model, ColdMonitorClient client,
			Duration interval, Duration initialDelay, ReadingStats stats) {
		this.equipmentId = equipmentId;
		this.code = code;
		this.model = model;
		this.client = client;
		this.interval = interval;
		this.initialDelay = initialDelay;
		this.stats = stats;
	}

	@Override
	public void run() {
		try {
			Thread.sleep(this.initialDelay);
			var nextTick = System.nanoTime();
			while (!Thread.currentThread().isInterrupted()) {
				sendOne();
				nextTick += this.interval.toNanos();
				var wait = nextTick - System.nanoTime();
				if (wait > 0) {
					Thread.sleep(Duration.ofNanos(wait));
				}
				else {
					nextTick = System.nanoTime();
				}
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	void sendOne() throws InterruptedException {
		var temperature = this.model.next();
		var recordedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		try {
			var reading = this.client.sendReading(this.equipmentId, temperature, recordedAt);
			this.stats.record(reading.status());
			var flag = "IN_RANGE".equals(reading.status()) ? "" : "  <-- out of range";
			Console.info("%-18s %8s C  %-9s%s", this.code, reading.temperature().toPlainString(), reading.status(),
					flag);
		}
		catch (IOException ex) {
			this.stats.record(ReadingStats.FAILED);
			Console.warn("%-18s %8s C  not sent: %s", this.code, temperature.toPlainString(), describe(ex));
		}
	}

	private static String describe(IOException ex) {
		var message = ex.getMessage();
		return (message == null || message.isBlank()) ? ex.getClass().getSimpleName() : message;
	}

}
