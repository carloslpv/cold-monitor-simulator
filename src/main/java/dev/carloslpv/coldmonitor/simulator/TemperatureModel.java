package dev.carloslpv.coldmonitor.simulator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.random.RandomGenerator;

/**
 * Generates a plausible temperature series for one piece of equipment.
 * <p>
 * The baseline is a mean-reverting random walk around the setpoint plus a slow compressor cycle, sized relative to
 * the threshold band so that normal operation stays comfortably inside it. On each tick an excursion may start with
 * the configured probability: most are warm (door left open, power cut), some are cold (thermostat overcooling). An
 * excursion ramps past the threshold, holds for a few ticks and then recovers back to the baseline.
 */
public final class TemperatureModel {

	private static final double MEAN_REVERSION = 0.15;

	private static final double NOISE_FRACTION = 0.04;

	private static final double CYCLE_FRACTION = 0.08;

	private static final int CYCLE_TICKS = 24;

	private static final double WARM_EXCURSION_SHARE = 0.8;

	private final double min;

	private final double max;

	private final double setpoint;

	private final double noise;

	private final double cycleAmplitude;

	private final double excursionProbability;

	private final RandomGenerator random;

	private double baseline;

	private long tick;

	private Excursion excursion;

	public TemperatureModel(EquipmentProfile profile, double excursionProbability, RandomGenerator random) {
		if (excursionProbability < 0 || excursionProbability > 1) {
			throw new IllegalArgumentException("excursionProbability must be between 0 and 1");
		}
		this.min = profile.minTemperature().doubleValue();
		this.max = profile.maxTemperature().doubleValue();
		this.setpoint = profile.effectiveSetpoint();
		var band = this.max - this.min;
		this.noise = band * NOISE_FRACTION;
		this.cycleAmplitude = band * CYCLE_FRACTION;
		this.excursionProbability = excursionProbability;
		this.random = random;
		this.baseline = this.setpoint + random.nextGaussian() * this.noise;
		this.tick = random.nextInt(CYCLE_TICKS);
	}

	/**
	 * Advances the model by one tick and returns the temperature in °C, rounded to two decimals.
	 */
	public BigDecimal next() {
		this.baseline += MEAN_REVERSION * (this.setpoint - this.baseline) + this.noise * this.random.nextGaussian();
		var cycle = this.cycleAmplitude * Math.sin(2 * Math.PI * this.tick / CYCLE_TICKS);
		this.tick++;

		if (this.excursion == null && this.random.nextDouble() < this.excursionProbability) {
			this.excursion = startExcursion();
		}
		var offset = 0.0;
		if (this.excursion != null) {
			offset = this.excursion.nextOffset();
			if (this.excursion.isOver()) {
				this.excursion = null;
			}
		}

		var temperature = Math.clamp(this.baseline + cycle + offset, -100.0, 100.0);
		return BigDecimal.valueOf(temperature).setScale(2, RoundingMode.HALF_UP);
	}

	public boolean isInExcursion() {
		return this.excursion != null;
	}

	private Excursion startExcursion() {
		var band = this.max - this.min;
		var overshoot = band * (0.1 + 0.4 * this.random.nextDouble());
		var peakOffset = (this.random.nextDouble() < WARM_EXCURSION_SHARE) ? (this.max - this.setpoint) + overshoot
				: -((this.setpoint - this.min) + overshoot);
		return new Excursion(peakOffset, 2 + this.random.nextInt(3), 1 + this.random.nextInt(3),
				3 + this.random.nextInt(4));
	}

	/**
	 * Offset added to the baseline during an excursion: a linear ramp up to the peak, a plateau and a linear decay.
	 */
	private static final class Excursion {

		private final double peakOffset;

		private final int rise;

		private final int hold;

		private final int decay;

		private int step;

		Excursion(double peakOffset, int rise, int hold, int decay) {
			this.peakOffset = peakOffset;
			this.rise = rise;
			this.hold = hold;
			this.decay = decay;
		}

		double nextOffset() {
			this.step++;
			if (this.step <= this.rise) {
				return this.peakOffset * this.step / this.rise;
			}
			if (this.step <= this.rise + this.hold) {
				return this.peakOffset;
			}
			var decayed = this.step - this.rise - this.hold;
			return this.peakOffset * (this.decay + 1 - decayed) / (this.decay + 1);
		}

		boolean isOver() {
			return this.step >= this.rise + this.hold + this.decay;
		}

	}

}
