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
java -jar giapi-inst-benchmark/target/giapi-inst-benchmark.jar command -h localhost -sc INIT -activity START -n 10 --parallel 10 -timeout 5000
```

Run an APPLY command benchmark with configuration:

```bash
java -jar giapi-inst-benchmark/target/giapi-inst-benchmark.jar command -h localhost -sc APPLY -activity START -config "sco:dummy.exprTime=40 sco:dummy.filter1=A" -n 100 --parallel 10 -timeout 5000
```
