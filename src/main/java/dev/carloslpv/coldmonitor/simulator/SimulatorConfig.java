package dev.carloslpv.coldmonitor.simulator;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Simulator settings. Each option can be given as a command-line flag or an environment variable; flags win.
 *
 * @param backendUrl base URL of the cold-monitor backend
 * @param interval time between two readings of the same sensor
 * @param excursionProbability chance, per reading, that an out-of-range excursion starts
 * @param equipmentFile JSON file with the equipment to simulate, or {@code null} for the bundled list
 * @param seed random seed for a reproducible run, or {@code null} for a random one
 * @param duration how long to run before stopping, or {@code null} to run until interrupted
 */
public record SimulatorConfig(
		URI backendUrl,
		Duration interval,
		double excursionProbability,
		Path equipmentFile,
		Long seed,
		Duration duration) {

	static final String USAGE = """
			Usage: java -jar cold-monitor-simulator.jar [options]

			Options (environment variable in brackets):
			  --backend-url <url>              Backend base URL [COLD_MONITOR_URL] (default: http://localhost:8080)
			  --interval <duration>            Time between readings per sensor [SIM_INTERVAL] (default: 5s)
			  --excursion-probability <0..1>   Chance per reading of starting an excursion [SIM_EXCURSION_PROBABILITY] (default: 0.02)
			  --equipment <file>               JSON file with the equipment to simulate [SIM_EQUIPMENT_FILE] (default: bundled list)
			  --seed <number>                  Random seed for a reproducible run [SIM_SEED]
			  --duration <duration>            Stop after this long instead of running until Ctrl+C [SIM_DURATION]
			  --help                           Show this message

			Durations accept 500ms, 5s, 2m, 1h or ISO-8601 (PT5S).
			""";

	private static final Map<String, String> ENV_BY_OPTION = Map.of(
			"backend-url", "COLD_MONITOR_URL",
			"interval", "SIM_INTERVAL",
			"excursion-probability", "SIM_EXCURSION_PROBABILITY",
			"equipment", "SIM_EQUIPMENT_FILE",
			"seed", "SIM_SEED",
			"duration", "SIM_DURATION");

	private static final Pattern SHORT_DURATION = Pattern.compile("(\\d+)(ms|s|m|h)");

	public SimulatorConfig {
		if (interval.isNegative() || interval.isZero()) {
			throw new IllegalArgumentException("interval must be positive");
		}
		if (excursionProbability < 0 || excursionProbability > 1) {
			throw new IllegalArgumentException("excursion-probability must be between 0 and 1");
		}
		if (duration != null && (duration.isNegative() || duration.isZero())) {
			throw new IllegalArgumentException("duration must be positive");
		}
	}

	/**
	 * Builds the configuration from command-line arguments and environment variables.
	 * @throws HelpRequestedException if {@code --help} was given
	 * @throws IllegalArgumentException if an option is unknown or invalid
	 */
	public static SimulatorConfig parse(String[] args, Map<String, String> env) {
		var options = new HashMap<String, String>();
		ENV_BY_OPTION.forEach((option, variable) -> {
			var value = env.get(variable);
			if (value != null && !value.isBlank()) {
				options.put(option, value.trim());
			}
		});
		parseArgs(args, options);

		var backendUrl = URI.create(stripTrailingSlash(options.getOrDefault("backend-url", "http://localhost:8080")));
		if (backendUrl.getScheme() == null || backendUrl.getHost() == null) {
			throw new IllegalArgumentException("backend-url must be an absolute http(s) URL");
		}
		return new SimulatorConfig(backendUrl,
				parseDuration("interval", options.getOrDefault("interval", "5s")),
				parseDouble("excursion-probability", options.getOrDefault("excursion-probability", "0.02")),
				options.containsKey("equipment") ? Path.of(options.get("equipment")) : null,
				options.containsKey("seed") ? parseLong("seed", options.get("seed")) : null,
				options.containsKey("duration") ? parseDuration("duration", options.get("duration")) : null);
	}

	private static void parseArgs(String[] args, Map<String, String> options) {
		for (var i = 0; i < args.length; i++) {
			var arg = args[i];
			if (arg.equals("--help") || arg.equals("-h")) {
				throw new HelpRequestedException();
			}
			if (!arg.startsWith("--")) {
				throw new IllegalArgumentException("Unexpected argument '" + arg + "'");
			}
			var name = arg.substring(2);
			String value;
			var equals = name.indexOf('=');
			if (equals >= 0) {
				value = name.substring(equals + 1);
				name = name.substring(0, equals);
			}
			else if (i + 1 < args.length) {
				value = args[++i];
			}
			else {
				throw new IllegalArgumentException("Missing value for --" + name);
			}
			if (!ENV_BY_OPTION.containsKey(name)) {
				throw new IllegalArgumentException("Unknown option --" + name);
			}
			options.put(name, value);
		}
	}

	static Duration parseDuration(String option, String value) {
		var matcher = SHORT_DURATION.matcher(value.trim().toLowerCase(Locale.ROOT));
		if (matcher.matches()) {
			var amount = Long.parseLong(matcher.group(1));
			return switch (matcher.group(2)) {
				case "ms" -> Duration.ofMillis(amount);
				case "s" -> Duration.ofSeconds(amount);
				case "m" -> Duration.ofMinutes(amount);
				default -> Duration.ofHours(amount);
			};
		}
		try {
			return Duration.parse(value.trim());
		}
		catch (DateTimeParseException ex) {
			throw new IllegalArgumentException("Invalid " + option + " '" + value + "', expected e.g. 5s or PT5S");
		}
	}

	private static double parseDouble(String option, String value) {
		try {
			return Double.parseDouble(value);
		}
		catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Invalid " + option + " '" + value + "'");
		}
	}

	private static long parseLong(String option, String value) {
		try {
			return Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Invalid " + option + " '" + value + "'");
		}
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	/**
	 * Signals that the user asked for the usage message.
	 */
	static final class HelpRequestedException extends RuntimeException {

		HelpRequestedException() {
			super(null, null, false, false);
		}

	}

}
