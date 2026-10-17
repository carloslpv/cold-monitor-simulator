package dev.carloslpv.coldmonitor.simulator;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal HTTP client for the cold-monitor backend API ({@code /api/v1}).
 */
public final class ColdMonitorClient {

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final URI apiBase;

	private final String apiKey;

	private final HttpClient http;

	private final JsonMapper json;

	/**
	 * @param apiKey device API key sent in {@code X-API-Key} on every request, or {@code null} to send none
	 */
	public ColdMonitorClient(URI backendUrl, String apiKey) {
		this.apiBase = URI.create(backendUrl + "/api/v1/");
		this.apiKey = apiKey;
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		this.json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
	}

	public List<RemoteEquipment> listEquipment() throws IOException, InterruptedException {
		var response = send(HttpRequest.newBuilder(this.apiBase.resolve("equipment")).GET(), 200);
		return read(response, new TypeReference<>() {
		});
	}

	public RemoteEquipment register(EquipmentProfile profile) throws IOException, InterruptedException {
		var body = new EquipmentPayload(profile.code(), profile.name(), profile.location(), profile.type(),
				profile.minTemperature(), profile.maxTemperature());
		var response = send(post(this.apiBase.resolve("equipment"), body), 201);
		return read(response, new TypeReference<>() {
		});
	}

	public RecordedReading sendReading(UUID equipmentId, BigDecimal temperature, Instant recordedAt)
			throws IOException, InterruptedException {
		var uri = this.apiBase.resolve("equipment/" + equipmentId + "/readings");
		var response = send(post(uri, new ReadingPayload(temperature, recordedAt)), 201);
		return read(response, new TypeReference<>() {
		});
	}

	private HttpRequest.Builder post(URI uri, Object body) {
		return HttpRequest.newBuilder(uri)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(this.json.writeValueAsString(body)));
	}

	private HttpResponse<String> send(HttpRequest.Builder request, int expectedStatus)
			throws IOException, InterruptedException {
		request.timeout(REQUEST_TIMEOUT).header("Accept", "application/json");
		if (this.apiKey != null) {
			request.header("X-API-Key", this.apiKey);
		}
		var built = request.build();
		var response = this.http.send(built, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != expectedStatus) {
			throw new BackendException(built.method() + " " + built.uri().getPath(), response.statusCode(),
					problemDetail(response.body()));
		}
		return response;
	}

	private <T> T read(HttpResponse<String> response, TypeReference<T> type) throws IOException {
		try {
			return this.json.readValue(response.body(), type);
		}
		catch (JacksonException ex) {
			throw new IOException("Unexpected response from " + response.uri().getPath() + ": " + ex.getMessage(), ex);
		}
	}

	private String problemDetail(String body) {
		try {
			var problem = this.json.readValue(body, new TypeReference<Map<String, Object>>() {
			});
			var detail = String.valueOf(problem.getOrDefault("detail", body));
			return problem.containsKey("errors") ? detail + " " + problem.get("errors") : detail;
		}
		catch (JacksonException ex) {
			return body;
		}
	}

	/**
	 * Equipment as returned by the backend.
	 */
	public record RemoteEquipment(UUID id, String code, String name, BigDecimal minTemperature,
			BigDecimal maxTemperature, boolean active) {
	}

	/**
	 * A reading as stored and classified by the backend.
	 */
	public record RecordedReading(UUID id, String equipmentCode, BigDecimal temperature, Instant recordedAt,
			String status) {
	}

	/**
	 * The backend answered with an unexpected HTTP status.
	 */
	public static final class BackendException extends IOException {

		private final int status;

		BackendException(String request, int status, String detail) {
			super(request + " returned " + status + (detail.isBlank() ? "" : ": " + detail));
			this.status = status;
		}

		public int status() {
			return this.status;
		}

	}

	private record EquipmentPayload(String code, String name, String location, String type,
			BigDecimal minTemperature, BigDecimal maxTemperature) {
	}

	private record ReadingPayload(BigDecimal temperature, Instant recordedAt) {
	}

}
