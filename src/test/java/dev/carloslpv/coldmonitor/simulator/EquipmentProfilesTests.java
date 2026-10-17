package dev.carloslpv.coldmonitor.simulator;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EquipmentProfilesTests {

	@Test
	void loadsBundledEquipment() {
		var profiles = EquipmentProfiles.load(null);

		assertThat(profiles).extracting(EquipmentProfile::code)
			.containsExactly("SIM-FRIDGE-01", "SIM-FRIDGE-02", "SIM-FREEZER-01", "SIM-COLDROOM-01");
	}

	@Test
	void setpointDefaultsToMiddleOfRange() {
		var profiles = parse("""
				[{"code":"A","name":"A","type":"OTHER","minTemperature":-10,"maxTemperature":0}]""");

		assertThat(profiles.getFirst().effectiveSetpoint()).isEqualTo(-5.0);
	}

	@Test
	void rejectsInvalidThresholds() {
		assertThatIllegalArgumentException().isThrownBy(() -> parse("""
				[{"code":"A","name":"A","type":"OTHER","minTemperature":5,"maxTemperature":2}]"""))
			.withMessageContaining("minTemperature must be lower than maxTemperature");
	}

	@Test
	void rejectsDuplicateCodes() {
		assertThatIllegalArgumentException().isThrownBy(() -> parse("""
				[{"code":"A","name":"A","type":"OTHER","minTemperature":0,"maxTemperature":1},
				 {"code":"A","name":"B","type":"OTHER","minTemperature":0,"maxTemperature":1}]"""))
			.withMessageContaining("repeats code A");
	}

	@Test
	void rejectsEmptyList() {
		assertThatIllegalArgumentException().isThrownBy(() -> parse("[]")).withMessageContaining("no equipment");
	}

	private static List<EquipmentProfile> parse(String json) {
		return EquipmentProfiles.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test");
	}

}
