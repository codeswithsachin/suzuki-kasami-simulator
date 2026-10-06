# Suzuki–Kasami Algorithm Simulator (Java Swing)

Interactive simulator of the Suzuki–Kasami token-based algorithm for distributed mutual exclusion.
Subject: Distributed Computing.

## Run (needs JDK 17 or newer, check with `java -version`)

| System  | Command |
|---------|---------|
| Windows | double-click `run.bat`   (or double-click `SuzukiKasami.jar`) |
| Linux / macOS | `sh run.sh`   (or `java -jar SuzukiKasami.jar`) |

`run.bat` / `run.sh` compile the source in `src/` and start the app. The jar is the same program, already compiled.

## Self test (proves the algorithm is correct)

```
javac -d out $(find src -name "*.java")
java -cp out skm.test.EngineSelfTest
```
It runs 400 random simulations (2 to 10 processes, FIFO and non-FIFO networks, heavy random workloads,
about 1.2 million events) and checks after every event that there is exactly one token and at most one process
in the critical section, that "step back" restores the exact previous state, and that every request is served.

## What you can do in the app

* **Play / Pause / Step / Step back** with a speed slider (0.25x to 4x). Step glides smoothly to the next event.
* **Click a process** (or use the Process box) to request or release the critical section.
* **Setup...** change number of processes (2 to 10), starting token holder, random seed, network delay range,
  FIFO or non-FIFO channels, time spent in the CS, and random workload.
* **Scenarios** ready-made demos, plus save / open your own `.skm` scenario files (repeatable demos).
* **Live panels** RN array of every process, token (LN and queue Q), event log, timeline of all processes,
  message counters, waiting times, and a safety monitor.

Keyboard: `Space` play/pause, `Right` step, `Left` step back.

## Project structure

```
src/skm/model    Site (process), Token, Message, Action, enums          - plain data
src/skm/engine   Simulation (THE ALGORITHM + event loop), Config, Metrics, Rng
src/skm/ui       MainFrame, NetworkPanel, TimelinePanel, InfoPanel, SetupDialog, Theme, Btn
src/skm/io       ScenarioIO (built-in scenarios, save / load)
src/skm/test     EngineSelfTest
```
The algorithm lives only in `Simulation.java` (`doRequest`, `doRelease`, `deliver`). The GUI only draws it.

## Why it does not lag
All animation uses one virtual clock. A message's position is computed from the clock every frame, so nothing
is animated separately from the algorithm. A paused simulation does not redraw at all.
