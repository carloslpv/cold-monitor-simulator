package dev.carloslpv.coldmonitor.simulator;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ColdMonitorClientTests {

	private static final UUID EQUIPMENT_ID = UUID.fromString("0b8f5d1e-4a43-4d3e-9a55-6f1f2c3b4d5e");

	private final List<String> requests = new CopyOnWriteArrayList<>();

	private HttpServer server;

	private ColdMonitorClient client;

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.start();
		this.client = new ColdMonitorClient(URI.create("http://127.0.0.1:" + this.server.getAddress().getPort()));
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	@Test
	void listsEquipment() throws Exception {
		respond("/api/v1/equipment", 200, """
				[{"id":"%s","code":"F-1","name":"Fridge","location":null,"type":"REFRIGERATOR",
				  "minTemperature":2.00,"maxTemperature":8.00,"active":true,
				  "createdAt":"2026-10-17T03:00:00Z","updatedAt":"2026-10-17T03:00:00Z"}]""".formatted(EQUIPMENT_ID));

		var equipment = this.client.listEquipment();

		assertThat(equipment).singleElement().satisfies((e) -> {
			assertThat(e.id()).isEqualTo(EQUIPMENT_ID);
			assertThat(e.code()).isEqualTo("F-1");
			assertThat(e.minTemperature()).isEqualByComparingTo("2");
			assertThat(e.active()).isTrue();
		});
	}

	@Test
	void registersEquipment() throws Exception {
		respond("/api/v1/equipment", 201, """
				{"id":"%s","code":"F-1","name":"Fridge","minTemperature":2.00,"maxTemperature":8.00,"active":true}"""
			.formatted(EQUIPMENT_ID));
		var profile = new EquipmentProfile("F-1", "Fridge", "Lab", "REFRIGERATOR", new BigDecimal("2.0"),
				new BigDecimal("8.0"), new BigDecimal("5.0"));

		var equipment = this.client.register(profile);

		assertThat(equipment.id()).isEqualTo(EQUIPMENT_ID);
		assertThat(this.requests).singleElement()
			.asString()
			.startsWith("POST /api/v1/equipment ")
			.contains("\"code\":\"F-1\"", "\"type\":\"REFRIGERATOR\"", "\"minTemperature\":2.0",
					"\"maxTemperature\":8.0")
			.doesNotContain("setpoint");
	}

	@Test
	void sendsReadingAndReturnsStatus() throws Exception {
		var path = "/api/v1/equipment/" + EQUIPMENT_ID + "/readings";
		respond(path, 201, """
				{"id":"%s","equipmentId":"%s","equipmentCode":"F-1","temperature":9.30,
				 "recordedAt":"2026-10-17T03:00:00Z","receivedAt":"2026-10-17T03:00:01Z","status":"ABOVE_MAX",
				 "minTemperature":2.00,"maxTemperature":8.00}""".formatted(UUID.randomUUID(), EQUIPMENT_ID));

		var reading = this.client.sendReading(EQUIPMENT_ID, new BigDecimal("9.30"),
				Instant.parse("2026-10-17T03:00:00Z"));

		assertThat(reading.status()).isEqualTo("ABOVE_MAX");
		assertThat(reading.temperature()).isEqualByComparingTo("9.3");
		assertThat(this.requests).singleElement()
			.asString()
			.isEqualTo("POST " + path + " {\"temperature\":9.30,\"recordedAt\":\"2026-10-17T03:00:00Z\"}");
	}

	@Test
	void reportsProblemDetailOnError() {
		respond("/api/v1/equipment/" + EQUIPMENT_ID + "/readings", 409, """
				{"type":"about:blank","title":"Conflict","status":409,
				 "detail":"Equipment is inactive and does not accept readings"}""");

		assertThatExceptionOfType(ColdMonitorClient.BackendException.class)
			.isThrownBy(() -> this.client.sendReading(EQUIPMENT_ID, BigDecimal.ONE, Instant.now()))
			.withMessageContaining("returned 409")
			.withMessageContaining("does not accept readings")
			.satisfies((ex) -> assertThat(ex.status()).isEqualTo(409));
	}

	private void respond(String path, int status, String body) {
		this.server.createContext(path, (exchange) -> {
			this.requests.add(describe(exchange));
			var bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			try (var output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		});
	}

	private static String describe(HttpExchange exchange) throws IOException {
		var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		return exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " " + body;
	}

}
