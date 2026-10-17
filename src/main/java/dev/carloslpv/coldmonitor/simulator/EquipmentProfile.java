package dev.carloslpv.coldmonitor.simulator;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A simulated piece of equipment. {@code setpoint} is the temperature the equipment regulates to; when omitted it
 * defaults to the middle of the threshold range.
 */
public record EquipmentProfile(
		String code,
		String name,
		String location,
		String type,
		BigDecimal minTemperature,
		BigDecimal maxTemperature,
		BigDecimal setpoint) {

	public EquipmentProfile {
		requireText(code, "code");
		requireText(name, "name");
		requireText(type, "type");
		Objects.requireNonNull(minTemperature, () -> "minTemperature is required for " + code);
		Objects.requireNonNull(maxTemperature, () -> "maxTemperature is required for " + code);
		if (minTemperature.compareTo(maxTemperature) >= 0) {
			throw new IllegalArgumentException("minTemperature must be lower than maxTemperature for " + code);
		}
		if (setpoint != null && !isWithinRange(setpoint, minTemperature, maxTemperature)) {
			throw new IllegalArgumentException("setpoint must be between the thresholds for " + code);
		}
	}

	/**
	 * Returns this profile with the thresholds actually registered in the backend, keeping the setpoint only if it
	 * still falls inside them.
	 */
	public EquipmentProfile withThresholds(BigDecimal min, BigDecimal max) {
		var keptSetpoint = (setpoint != null && isWithinRange(setpoint, min, max)) ? setpoint : null;
		return new EquipmentProfile(code, name, location, type, min, max, keptSetpoint);
	}

	public double effectiveSetpoint() {
		return (setpoint != null) ? setpoint.doubleValue()
				: (minTemperature.doubleValue() + maxTemperature.doubleValue()) / 2;
	}

	private static boolean isWithinRange(BigDecimal value, BigDecimal min, BigDecimal max) {
		return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
	}

	private static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Equipment profile is missing '" + field + "'");
		}
	}

}
