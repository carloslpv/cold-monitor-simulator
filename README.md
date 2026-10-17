# cold-monitor-simulator

[English](#english) · [Português (Brasil)](#português-brasil)

---

## English

Sensor simulator for **cold-monitor**. It plays the part of the temperature sensors in fridges, freezers and cold rooms:
it registers a set of equipment in the [cold-monitor backend](https://github.com/carloslpv/cold-monitor-backend) and
then sends each one a temperature reading on a fixed interval, so the backend, alerts and dashboard have realistic data
to work with.

### How it simulates

- Each piece of equipment runs on its own **virtual thread** and sends readings with its own clock, starting at a
  random offset so the sensors do not all report at the same instant.
- Normal operation is a mean-reverting random walk around the equipment's **setpoint** plus a slow compressor cycle,
  sized relative to the threshold band so it stays comfortably inside the limits.
- On each reading an **excursion** may start with the configured probability. Most are warm (door left open, power
  cut), some are cold (thermostat overcooling). An excursion ramps past the threshold, holds for a few readings and
  recovers, so the backend sees a run of `ABOVE_MAX` or `BELOW_MIN` readings followed by a return to `IN_RANGE`.
- Equipment is matched by `code`. If it does not exist in the backend it is registered; if it exists, the thresholds
  stored in the backend are used. Inactive equipment is skipped.
- If the backend is not up yet, the simulator retries for about 30 seconds. Failed readings are logged and the sensor
  keeps going.

### Requirements

JDK 25 and a running cold-monitor backend (see its README).

### Running

The backend only accepts readings from devices that send its `DEVICE_API_KEY`, so pass that key:

```bash
./mvnw package
java -jar target/cold-monitor-simulator.jar --api-key <DEVICE_API_KEY>
```

If the key is missing or wrong, the simulator stops right away and says so.

Example with faster readings and more excursions, for a demo:

```bash
java -jar target/cold-monitor-simulator.jar --interval 1s --excursion-probability 0.1
```

Each reading is printed with the status the backend assigned to it:

```
01:08:38.123  SIM-FRIDGE-01          8.96 C  ABOVE_MAX  <-- out of range
01:08:38.430  SIM-FREEZER-01       -25.51 C  IN_RANGE
```

Press Ctrl+C to stop; a summary of readings by status is printed on exit.

### Options

Every option can be passed as a flag (`--name value` or `--name=value`) or as an environment variable. Flags win.

| Flag | Variable | Default | Description |
| --- | --- | --- | --- |
| `--backend-url` | `COLD_MONITOR_URL` | `http://localhost:8080` | Backend base URL |
| `--api-key` | `COLD_MONITOR_API_KEY` | none | The backend's `DEVICE_API_KEY`, sent in the `X-API-Key` header |
| `--interval` | `SIM_INTERVAL` | `5s` | Time between readings of each sensor |
| `--excursion-probability` | `SIM_EXCURSION_PROBABILITY` | `0.02` | Chance per reading that an excursion starts (0 to 1) |
| `--equipment` | `SIM_EQUIPMENT_FILE` | bundled list | JSON file with the equipment to simulate |
| `--seed` | `SIM_SEED` | random | Seed for a reproducible temperature series |
| `--duration` | `SIM_DURATION` | until Ctrl+C | Stop after this long |

Durations accept `500ms`, `5s`, `2m`, `1h` or ISO-8601 (`PT5S`). `--help` prints the full usage.

### Equipment

By default the simulator uses [`src/main/resources/equipment.json`](src/main/resources/equipment.json): two vaccine and
sample fridges (2 to 8 °C), a plasma freezer (-30 to -18 °C) and a produce cold room (0 to 4 °C). Codes start with
`SIM-` so they do not collide with real equipment.

To simulate your own, pass a JSON file with the same shape:

```json
[
  {
    "code": "SIM-FRIDGE-01",
    "name": "Vaccine fridge",
    "location": "Pharmacy",
    "type": "REFRIGERATOR",
    "minTemperature": 2.0,
    "maxTemperature": 8.0,
    "setpoint": 5.0
  }
]
```

`type` is one of `REFRIGERATOR`, `FREEZER`, `COLD_ROOM`, `OTHER`. `location` and `setpoint` are optional; the setpoint
defaults to the middle of the range.

### Docker

```bash
docker build -t cold-monitor-simulator .
docker run --rm cold-monitor-simulator --backend-url http://host.docker.internal:8080 --api-key <DEVICE_API_KEY>
```

Options can also be passed as environment variables (`-e SIM_INTERVAL=1s`). The compose file in
[cold-monitor-infra](https://github.com/carloslpv/cold-monitor-infra) runs the simulator next to the rest of the system
with `--profile simulator`.

### Tests

```bash
./mvnw test
```

The tests do not need the backend: the HTTP client is tested against an in-process fake server. GitHub Actions
(`.github/workflows/ci.yml`) runs them and builds the Docker image on every push and pull request.

### Project layout

```
src/main/java/dev/carloslpv/coldmonitor/simulator
├── SimulatorApplication   entry point: registration and one virtual thread per sensor
├── SimulatorConfig        flags and environment variables
├── EquipmentProfile(s)    equipment to simulate and the JSON loader
├── TemperatureModel       temperature series with drift and excursions
├── SensorSimulator        send loop of one sensor
└── ColdMonitorClient      HTTP client for the backend API
```

---

## Português (Brasil)

Simulador de sensores do **cold-monitor**. Ele faz o papel dos sensores de temperatura de geladeiras, freezers e câmaras
frias: cadastra um conjunto de equipamentos no [backend do cold-monitor](https://github.com/carloslpv/cold-monitor-backend)
e depois envia, para cada um, uma leitura de temperatura em intervalo fixo, para que o backend, os alertas e o dashboard
tenham dados realistas para trabalhar.

### Como a simulação funciona

- Cada equipamento roda na sua própria **virtual thread** e envia leituras no seu próprio ritmo, começando com um
  atraso aleatório para que os sensores não reportem todos no mesmo instante.
- A operação normal é um passeio aleatório com reversão à média em torno do **setpoint** do equipamento, mais um ciclo
  lento de compressor, dimensionados em relação à faixa de limites para ficar com folga dentro dela.
- A cada leitura pode começar uma **excursão**, com a probabilidade configurada. A maioria é de calor (porta aberta,
  queda de energia) e algumas são de frio (termostato resfriando demais). A excursão passa do limite, se mantém por
  algumas leituras e depois volta ao normal, então o backend recebe uma sequência de `ABOVE_MAX` ou `BELOW_MIN` seguida
  da volta para `IN_RANGE`.
- Os equipamentos são identificados pelo `code`. Se não existirem no backend, são cadastrados; se já existirem, valem os
  limites gravados no backend. Equipamentos inativos são ignorados.
- Se o backend ainda não estiver no ar, o simulador tenta de novo por cerca de 30 segundos. Leituras que falham são
  registradas no log e o sensor continua.

### Requisitos

JDK 25 e o backend do cold-monitor rodando (veja o README dele).

### Como rodar

O backend só aceita leituras de dispositivos que enviam a `DEVICE_API_KEY` dele, então passe essa chave:

```bash
./mvnw package
java -jar target/cold-monitor-simulator.jar --api-key <DEVICE_API_KEY>
```

Se a chave estiver faltando ou errada, o simulador para na hora e avisa.

Exemplo com leituras mais rápidas e mais excursões, para demonstração:

```bash
java -jar target/cold-monitor-simulator.jar --interval 1s --excursion-probability 0.1
```

Cada leitura aparece com o status que o backend atribuiu a ela:

```
01:08:38.123  SIM-FRIDGE-01          8.96 C  ABOVE_MAX  <-- out of range
01:08:38.430  SIM-FREEZER-01       -25.51 C  IN_RANGE
```

Use Ctrl+C para parar; ao sair, o simulador mostra um resumo das leituras por status.

### Opções

Toda opção pode ser passada como flag (`--nome valor` ou `--nome=valor`) ou como variável de ambiente. A flag tem
prioridade.

| Flag | Variável | Padrão | Descrição |
| --- | --- | --- | --- |
| `--backend-url` | `COLD_MONITOR_URL` | `http://localhost:8080` | URL base do backend |
| `--api-key` | `COLD_MONITOR_API_KEY` | nenhuma | A `DEVICE_API_KEY` do backend, enviada no cabeçalho `X-API-Key` |
| `--interval` | `SIM_INTERVAL` | `5s` | Tempo entre leituras de cada sensor |
| `--excursion-probability` | `SIM_EXCURSION_PROBABILITY` | `0.02` | Chance, por leitura, de começar uma excursão (0 a 1) |
| `--equipment` | `SIM_EQUIPMENT_FILE` | lista embutida | Arquivo JSON com os equipamentos a simular |
| `--seed` | `SIM_SEED` | aleatória | Semente para repetir a mesma série de temperaturas |
| `--duration` | `SIM_DURATION` | até o Ctrl+C | Para depois desse tempo |

As durações aceitam `500ms`, `5s`, `2m`, `1h` ou ISO-8601 (`PT5S`). `--help` mostra o uso completo.

### Equipamentos

Por padrão o simulador usa o [`src/main/resources/equipment.json`](src/main/resources/equipment.json): duas geladeiras,
de vacinas e de amostras (2 a 8 °C), um freezer de plasma (-30 a -18 °C) e uma câmara fria de hortifrúti (0 a 4 °C). Os
códigos começam com `SIM-` para não colidir com equipamentos reais.

Para simular os seus, passe um arquivo JSON no mesmo formato:

```json
[
  {
    "code": "SIM-FRIDGE-01",
    "name": "Geladeira de vacinas",
    "location": "Farmácia",
    "type": "REFRIGERATOR",
    "minTemperature": 2.0,
    "maxTemperature": 8.0,
    "setpoint": 5.0
  }
]
```

`type` aceita `REFRIGERATOR`, `FREEZER`, `COLD_ROOM` ou `OTHER`. `location` e `setpoint` são opcionais; sem setpoint,
vale o meio da faixa.

### Docker

```bash
docker build -t cold-monitor-simulator .
docker run --rm cold-monitor-simulator --backend-url http://host.docker.internal:8080 --api-key <DEVICE_API_KEY>
```

As opções também podem ir como variáveis de ambiente (`-e SIM_INTERVAL=1s`). O compose do
[cold-monitor-infra](https://github.com/carloslpv/cold-monitor-infra) roda o simulador junto com o resto do sistema
com `--profile simulator`.

### Testes

```bash
./mvnw test
```

Os testes não precisam do backend: o cliente HTTP é testado contra um servidor falso dentro do próprio processo. O
GitHub Actions (`.github/workflows/ci.yml`) roda os testes e gera a imagem Docker a cada push e pull request.

### Estrutura do projeto

```
src/main/java/dev/carloslpv/coldmonitor/simulator
├── SimulatorApplication   ponto de entrada: cadastro e uma virtual thread por sensor
├── SimulatorConfig        flags e variáveis de ambiente
├── EquipmentProfile(s)    equipamentos a simular e leitura do JSON
├── TemperatureModel       série de temperaturas com variação e excursões
├── SensorSimulator        laço de envio de um sensor
└── ColdMonitorClient      cliente HTTP da API do backend
```
