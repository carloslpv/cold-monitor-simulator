package dev.carloslpv.coldmonitor.simulator;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SimulatorConfigTests {

	@Test
	void usesDefaults() {
		var config = SimulatorConfig.parse(new String[0], Map.of());

		assertThat(config.backendUrl()).isEqualTo(URI.create("http://localhost:8080"));
		assertThat(config.apiKey()).isNull();
		assertThat(config.interval()).isEqualTo(Duration.ofSeconds(5));
		assertThat(config.excursionProbability()).isEqualTo(0.02);
		assertThat(config.equipmentFile()).isNull();
		assertThat(config.seed()).isNull();
		assertThat(config.duration()).isNull();
	}

	@Test
	void readsEnvironmentVariables() {
		var config = SimulatorConfig.parse(new String[0],
				Map.of("COLD_MONITOR_URL", "http://backend:8080/", "COLD_MONITOR_API_KEY", "key-from-env",
						"SIM_INTERVAL", "500ms",
						"SIM_EXCURSION_PROBABILITY", "0.1", "SIM_EQUIPMENT_FILE", "my.json", "SIM_SEED", "7",
						"SIM_DURATION", "PT2M"));

		assertThat(config.backendUrl()).isEqualTo(URI.create("http://backend:8080"));
		assertThat(config.apiKey()).isEqualTo("key-from-env");
		assertThat(config.interval()).isEqualTo(Duration.ofMillis(500));
		assertThat(config.excursionProbability()).isEqualTo(0.1);
		assertThat(config.equipmentFile()).isEqualTo(Path.of("my.json"));
		assertThat(config.seed()).isEqualTo(7L);
		assertThat(config.duration()).isEqualTo(Duration.ofMinutes(2));
	}

	@Test
	void flagsOverrideEnvironment() {
		var config = SimulatorConfig.parse(new String[] { "--interval=2s", "--backend-url", "http://other:9090" },
				Map.of("SIM_INTERVAL", "10s", "COLD_MONITOR_URL", "http://backend:8080"));

		assertThat(config.interval()).isEqualTo(Duration.ofSeconds(2));
		assertThat(config.backendUrl()).isEqualTo(URI.create("http://other:9090"));
	}

	@Test
	void keepsTheApiKeyOutOfToString() {
		var config = parse("--api-key", "secret-device-key");

		assertThat(config.apiKey()).isEqualTo("secret-device-key");
		assertThat(config.toString()).doesNotContain("secret-device-key");
	}

	@Test
	void parsesShortDurations() {
		assertThat(SimulatorConfig.parseDuration("interval", "250ms")).isEqualTo(Duration.ofMillis(250));
		assertThat(SimulatorConfig.parseDuration("interval", "3m")).isEqualTo(Duration.ofMinutes(3));
		assertThat(SimulatorConfig.parseDuration("interval", "1h")).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void rejectsInvalidOptions() {
		assertThatIllegalArgumentException().isThrownBy(() -> parse("--nope=1")).withMessageContaining("--nope");
		assertThatIllegalArgumentException().isThrownBy(() -> parse("--interval=soon"));
		assertThatIllegalArgumentException().isThrownBy(() -> parse("--interval=0s"));
		assertThatIllegalArgumentException().isThrownBy(() -> parse("--excursion-probability=2"));
		assertThatIllegalArgumentException().isThrownBy(() -> parse("--backend-url=localhost"));
		assertThatIllegalArgumentException().isThrownBy(() -> parse("--seed"));
	}

	@Test
	void recognizesHelp() {
		assertThatExceptionOfType(SimulatorConfig.HelpRequestedException.class).isThrownBy(() -> parse("--help"));
	}

	private static SimulatorConfig parse(String... args) {
		return SimulatorConfig.parse(args, Map.of());
	}

}
