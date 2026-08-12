package edu.gemini.giapi.inst.benchmark;

/**
 * Entry point for standalone GIAPI instrument benchmarks.
 */
public final class GiapiInstBenchmark {

    private GiapiInstBenchmark() {
    }

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            BenchmarkArgs benchmarkArgs = BenchmarkArgs.parse(args);
            if (benchmarkArgs.isHelp()) {
                System.out.println(BenchmarkArgs.usage());
                return;
            }
            if ("command".equals(benchmarkArgs.getMode())) {
                exitCode = new CommandLatencyBenchmark(benchmarkArgs).run();
            } else {
                System.err.println("Unknown benchmark mode: " + benchmarkArgs.getMode());
                System.err.println(BenchmarkArgs.usage());
                exitCode = 2;
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(BenchmarkArgs.usage());
            exitCode = 2;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            exitCode = 1;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
