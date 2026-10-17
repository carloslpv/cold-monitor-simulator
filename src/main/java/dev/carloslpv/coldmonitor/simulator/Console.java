package dev.carloslpv.coldmonitor.simulator;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Timestamped console output shared by all sensor threads.
 */
final class Console {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	private Console() {
	}

	static void info(String format, Object... args) {
		System.out.println(prefix() + format.formatted(args));
	}

	static void warn(String format, Object... args) {
		System.out.println(prefix() + "WARN " + format.formatted(args));
	}

	static void error(String format, Object... args) {
		System.err.println(prefix() + "ERROR " + format.formatted(args));
	}

	private static String prefix() {
		return TIME.format(LocalTime.now().truncatedTo(ChronoUnit.MILLIS)) + "  ";
	}

}
