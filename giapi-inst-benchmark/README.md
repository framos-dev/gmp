# GIAPI Instrument Benchmark

Standalone benchmark tool for end-to-end GIAPI instrument tests.

This module is built as an executable jar, like `giapi-tester`. It is not added
to the GMP Pax runtime bundle directories, so `mvn pax:run` does not deploy it
as a GMP service bundle.

Build:

```bash
mvn -pl giapi-inst-benchmark -am package
```

Run a command benchmark:

```bash
java -jar giapi-inst-benchmark/target/giapi-inst-benchmark.jar command -h localhost -sc INIT -activity START -n 100 -timeout 5000
```

Run a parallel command benchmark:

```bash
java -jar giapi-inst-benchmark/target/giapi-inst-benchmark.jar command -h localhost -sc INIT -activity START -n 10 --parallel 10 -timeout 5000 --csv results/command_init_p10_s100.csv --run-id init-p10-s100 --status-item-count 100 --status-post-period 1000
```

Run an APPLY command benchmark with configuration:

```bash
java -jar giapi-inst-benchmark/target/giapi-inst-benchmark.jar command -h localhost -sc APPLY -activity START -config "sco:dummy.exprTime=40 sco:dummy.filter1=A" -n 100 --parallel 10 -timeout 5000 --csv results/command_apply_p10_s100.csv --run-id apply-p10-s100 --status-item-count 100 --status-post-period 1000
```

Run a status interval probe:

```bash
java -jar giapi-inst-benchmark/target/giapi-inst-benchmark.jar status \
  -h localhost \
  --status sco:dummy:status000 \
  --duration 120 \
  --expected-period 1000 \
  --csv results/status_s100_p1000.csv \
  --run-id status-s100-p1000 \
  --status-item-count 100 \
  --status-post-period 1000
```

The status probe is intended to run in a separate process from the command
benchmark. It measures subscriber-side receive intervals, so it does not require
clock synchronization between the instrument host and the benchmark host. If the
InstrumentSequencer publishes every `1000` ms, the CSV `intervalMs` column should
stay close to `1000`; `delayVsExpectedMs` shows how much each interval differs
from the expected period.

Typical combined test:

```bash
# Terminal 1: start the InstrumentSequencer dummy with 100, 500, or 1000 StatusItems.

# Terminal 1b: monitor the InstrumentSequencer CPU and memory.
pgrep -af 'InstrumentSequencer.py'
bash giapi-inst-benchmark/tools/monitor_process.sh --pid <pid> --interval 1 --duration 180 --out results/process_apply_p10_s100.csv

# Terminal 2: start the status interval probe and keep it running.
java -jar giapi-inst-benchmark/target/giapi-inst-benchmark.jar status -h localhost --status sco:dummy:status000 --duration 180 --expected-period 1000 --csv results/status.csv --run-id apply-p10-s100 --status-item-count 100 --status-post-period 1000

# Terminal 3: start the command benchmark while the status probe is running.
java -jar giapi-inst-benchmark/target/giapi-inst-benchmark.jar command -h localhost -sc APPLY -activity START -config "sco:dummy.exprTime=40 sco:dummy.filter1=A" -n 100 --parallel 10 --warmup 10 -timeout 5000 --csv results/command_apply_p10_s100.csv --run-id apply-p10-s100 --status-item-count 100 --status-post-period 1000
```

Create plots from the command and status CSV files:

```bash
python3 giapi-inst-benchmark/tools/plot_benchmark.py \
  --command-csv results/command_apply_p10_s100.csv \
  --status-csv results/status.csv \
  --out-dir results/plots \
  --name apply_p10_s100
```

The plotting tool writes:

```text
results/plots/apply_p10_s100_command_initial_latency.png
results/plots/apply_p10_s100_command_end_to_end_latency.png
results/plots/apply_p10_s100_status_interval.png
results/plots/apply_p10_s100_combined_timeline.png
results/plots/apply_p10_s100_summary.txt
```

The process monitor writes one CSV row per sample with CPU percentage, memory
percentage, RSS, VSZ, thread count, processor, and process state. That CSV can be
plotted later against the same wall-clock time used by the command and status
CSV files.

Create plots from the process monitor CSV:

```bash
python3 giapi-inst-benchmark/tools/plot_process_monitor.py \
  --process-csv results/process_apply_p10_s100.csv \
  --out-dir results/plots \
  --name apply_p10_s100
```

The process plotting tool writes:

```text
results/plots/apply_p10_s100_process_cpu.png
results/plots/apply_p10_s100_process_memory_pct.png
results/plots/apply_p10_s100_process_memory_kb.png
results/plots/apply_p10_s100_process_threads.png
results/plots/apply_p10_s100_process_summary.txt
```

Create a comparison summary from several benchmark runs:

```bash
python3 giapi-inst-benchmark/tools/summarize_benchmark.py \
  --run init-p5-s100:results/command_init_p5_s100.csv:results/status_init_p5_s100.csv \
  --run init-p10-s100:results/command_init_p10_s100.csv:results/status_init_p10_s100.csv \
  --run init-p20-s100:results/command_init_p20_s100.csv:results/status_init_p20_s100.csv \
  --run init-p10-s1000:results/command_init_p10_s1000.csv:results/status_init_p10_s1000.csv \
  --out-dir results/summary
```

Each `--run` value has this format:

```text
runId:commandCsv:statusCsv
```

The summarizer writes:

```text
results/summary/summary_table.csv
results/summary/summary_table.png
```
