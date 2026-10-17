package dev.carloslpv.coldmonitor.simulator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.carloslpv.coldmonitor.simulator.ColdMonitorClient.BackendException;
import dev.carloslpv.coldmonitor.simulator.ColdMonitorClient.RemoteEquipment;

/**
 * Entry point: registers the simulated equipment in the backend if needed, then runs one virtual thread per sensor.
 */
public final class SimulatorApplication {

	private static final int CONNECT_ATTEMPTS = 10;

	private static final Duration CONNECT_RETRY_DELAY = Duration.ofSeconds(3);

	private SimulatorApplication() {
	}

	public static void main(String[] args) throws InterruptedException {
		SimulatorConfig config;
		List<EquipmentProfile> profiles;
		try {
			config = SimulatorConfig.parse(args, System.getenv());
			profiles = EquipmentProfiles.load(config.equipmentFile());
		}
		catch (SimulatorConfig.HelpRequestedException ex) {
			System.out.print(SimulatorConfig.USAGE);
			return;
		}
		catch (IllegalArgumentException | IllegalStateException | UncheckedIOException ex) {
			System.err.println("Error: " + ex.getMessage());
			System.err.println();
			System.err.print(SimulatorConfig.USAGE);
			System.exit(2);
			return;
		}
		System.exit(run(config, profiles));
	}

	static int run(SimulatorConfig config, List<EquipmentProfile> profiles) throws InterruptedException {
		var client = new ColdMonitorClient(config.backendUrl(), config.apiKey());
		var random = (config.seed() != null) ? new SplittableRandom(config.seed()) : new SplittableRandom();
		var stats = new ReadingStats();

		Console.info("cold-monitor simulator: %d equipment, interval %s, excursion probability %s, backend %s",
				profiles.size(), format(config.interval()), config.excursionProbability(), config.backendUrl());

		List<RemoteEquipment> existing;
		try {
			existing = connect(client);
		}
		catch (BackendException ex) {
			if (ex.status() == 401 || ex.status() == 403) {
				Console.error("The backend refused the API key (HTTP %d). Set --api-key or COLD_MONITOR_API_KEY to the "
						+ "backend's DEVICE_API_KEY.", ex.status());
				return 1;
			}
			Console.error("Backend unavailable at %s: %s", config.backendUrl(), ex.getMessage());
			return 1;
		}
		catch (IOException ex) {
			Console.error("Backend unavailable at %s: %s", config.backendUrl(), ex.getMessage());
			return 1;
		}

		var sensors = new ArrayList<SensorSimulator>();
		for (var profile : profiles) {
			var remote = ensureRegistered(client, profile, existing);
			if (remote.isEmpty()) {
				continue;
			}
			var equipment = remote.get();
			var sensorRandom = random.split();
			var model = new TemperatureModel(profile.withThresholds(equipment.minTemperature(),
					equipment.maxTemperature()), config.excursionProbability(), sensorRandom);
			var initialDelay = Duration.ofMillis(sensorRandom.nextLong(Math.max(1, config.interval().toMillis())));
			sensors.add(new SensorSimulator(equipment.id(), equipment.code(), model, client, config.interval(),
					initialDelay, stats));
		}
		if (sensors.isEmpty()) {
			Console.error("No equipment available to simulate");
			return 1;
		}

		var executor = Executors.newVirtualThreadPerTaskExecutor();
		var stopped = new AtomicBoolean();
		Runnable stop = () -> {
			if (stopped.compareAndSet(false, true)) {
				shutdown(executor);
				Console.info("Stopped. %s", stats.summary());
			}
		};
		Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(stop));

		sensors.forEach(executor::submit);
		Console.info("Simulating %d sensors%s", sensors.size(),
				(config.duration() != null) ? " for " + format(config.duration()) : ", press Ctrl+C to stop");

		if (config.duration() != null) {
			Thread.sleep(config.duration());
			stop.run();
		}
		else {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
		return 0;
	}

	/**
	 * Waits for the backend to answer, since it is often started at the same time as the simulator.
	 */
	private static List<RemoteEquipment> connect(ColdMonitorClient client) throws IOException, InterruptedException {
		for (var attempt = 1;; attempt++) {
			try {
				return client.listEquipment();
			}
			catch (ConnectException | HttpTimeoutException ex) {
				if (attempt == CONNECT_ATTEMPTS) {
					throw ex;
				}
				Console.warn("Backend not reachable yet (attempt %d/%d), retrying in %s", attempt, CONNECT_ATTEMPTS,
						format(CONNECT_RETRY_DELAY));
				Thread.sleep(CONNECT_RETRY_DELAY);
			}
		}
	}

	private static Optional<RemoteEquipment> ensureRegistered(ColdMonitorClient client, EquipmentProfile profile,
			List<RemoteEquipment> existing) throws InterruptedException {
		try {
			var found = findByCode(existing, profile.code());
			if (found.isEmpty()) {
				try {
					found = Optional.of(client.register(profile));
					Console.info("Registered %s (%s, %s..%s C)", profile.code(), profile.name(),
							profile.minTemperature().toPlainString(), profile.maxTemperature().toPlainString());
				}
				catch (BackendException ex) {
					if (ex.status() != 409) {
						throw ex;
					}
					// Registered by someone else in the meantime
					found = findByCode(client.listEquipment(), profile.code());
				}
			}
			else {
				Console.info("Using existing %s (%s..%s C)", profile.code(),
						found.get().minTemperature().toPlainString(), found.get().maxTemperature().toPlainString());
			}
			if (found.isPresent() && !found.get().active()) {
				Console.warn("Skipping %s: equipment is inactive and does not accept readings", profile.code());
				return Optional.empty();
			}
			return found;
		}
		catch (IOException ex) {
			Console.error("Skipping %s: %s", profile.code(), ex.getMessage());
			return Optional.empty();
		}
	}

	private static Optional<RemoteEquipment> findByCode(List<RemoteEquipment> equipment, String code) {
		return equipment.stream().filter((candidate) -> candidate.code().equals(code)).findFirst();
	}

	private static void shutdown(ExecutorService executor) {
		executor.shutdownNow();
		try {
			executor.awaitTermination(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static String format(Duration duration) {
		return duration.toString().substring(2).toLowerCase(Locale.ROOT);
	}

}
