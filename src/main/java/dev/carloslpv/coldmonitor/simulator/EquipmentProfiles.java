package dev.carloslpv.coldmonitor.simulator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the list of equipment to simulate, either from a JSON file or from the bundled {@code equipment.json}.
 */
public final class EquipmentProfiles {

	private static final String BUNDLED = "/equipment.json";

	private EquipmentProfiles() {
	}

	public static List<EquipmentProfile> load(Path file) {
		try (var input = (file != null) ? Files.newInputStream(file) : bundled()) {
			return parse(input, (file != null) ? file.toString() : BUNDLED);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read equipment file " + file, ex);
		}
	}

	static List<EquipmentProfile> parse(InputStream input, String source) {
		List<EquipmentProfile> profiles;
		try {
			profiles = JsonMapper.builder().build().readValue(input, new TypeReference<>() {
			});
		}
		catch (JacksonException ex) {
			var cause = (ex.getCause() instanceof IllegalArgumentException invalid) ? invalid.getMessage()
					: ex.getOriginalMessage();
			throw new IllegalArgumentException("Invalid equipment file " + source + ": " + cause, ex);
		}
		if (profiles == null || profiles.isEmpty()) {
			throw new IllegalArgumentException("Equipment file " + source + " lists no equipment");
		}
		var codes = new HashSet<String>();
		for (var profile : profiles) {
			if (!codes.add(profile.code())) {
				throw new IllegalArgumentException("Equipment file " + source + " repeats code " + profile.code());
			}
		}
		return List.copyOf(profiles);
	}

	private static InputStream bundled() {
		var input = EquipmentProfiles.class.getResourceAsStream(BUNDLED);
		if (input == null) {
			throw new IllegalStateException("Bundled " + BUNDLED + " is missing");
		}
		return input;
	}

}
