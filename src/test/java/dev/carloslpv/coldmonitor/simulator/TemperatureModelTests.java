package dev.carloslpv.coldmonitor.simulator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TemperatureModelTests {

	private static final EquipmentProfile FRIDGE = new EquipmentProfile("F-1", "Fridge", null, "REFRIGERATOR",
			new BigDecimal("2.0"), new BigDecimal("8.0"), new BigDecimal("5.0"));

	private static final EquipmentProfile FREEZER = new EquipmentProfile("Z-1", "Freezer", null, "FREEZER",
			new BigDecimal("-30.0"), new BigDecimal("-18.0"), null);

	@Test
	void staysWithinThresholdsWithoutExcursions() {
		for (var profile : new EquipmentProfile[] { FRIDGE, FREEZER }) {
			var model = new TemperatureModel(profile, 0, new SplittableRandom(42));
			for (var i = 0; i < 20_000; i++) {
				assertThat(model.next()).isBetween(profile.minTemperature(), profile.maxTemperature());
			}
		}
	}

	@Test
	void hoversAroundSetpoint() {
		var model = new TemperatureModel(FREEZER, 0, new SplittableRandom(7));
		var sum = 0.0;
		var ticks = 10_000;
		for (var i = 0; i < ticks; i++) {
			sum += model.next().doubleValue();
		}
		assertThat(sum / ticks).isBetween(-24.5, -23.5);
	}

	@Test
	void excursionsGoOutOfRangeAndRecover() {
		var model = new TemperatureModel(FRIDGE, 0.05, new SplittableRandom(1));
		var readings = new ArrayList<BigDecimal>();
		for (var i = 0; i < 2_000; i++) {
			readings.add(model.next());
		}
		assertThat(readings).anyMatch((t) -> t.compareTo(FRIDGE.maxTemperature()) > 0);
		assertThat(readings).anyMatch((t) -> t.compareTo(FRIDGE.minTemperature()) < 0);
		var inRange = readings.stream()
			.filter((t) -> t.compareTo(FRIDGE.minTemperature()) >= 0 && t.compareTo(FRIDGE.maxTemperature()) <= 0)
			.count();
		assertThat(inRange).isGreaterThan(readings.size() / 2);
	}

	@Test
	void sameSeedProducesSameSeries() {
		var first = new TemperatureModel(FRIDGE, 0.1, new SplittableRandom(99));
		var second = new TemperatureModel(FRIDGE, 0.1, new SplittableRandom(99));
		for (var i = 0; i < 500; i++) {
			assertThat(first.next()).isEqualTo(second.next());
		}
	}

	@Test
	void roundsToTwoDecimals() {
		var model = new TemperatureModel(FRIDGE, 0.1, new SplittableRandom(3));
		for (var i = 0; i < 100; i++) {
			assertThat(model.next().scale()).isEqualTo(2);
		}
	}

	@Test
	void rejectsInvalidProbability() {
		assertThatIllegalArgumentException().isThrownBy(() -> new TemperatureModel(FRIDGE, 1.5, new SplittableRandom()));
	}

}
