package edu.gemini.giapi.inst.benchmark;

import edu.gemini.aspen.giapi.commands.Activity;
import edu.gemini.aspen.giapi.commands.ConfigPath;
import edu.gemini.aspen.giapi.commands.Configuration;
import edu.gemini.aspen.giapi.commands.DefaultConfiguration;
import edu.gemini.aspen.giapi.commands.SequenceCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses the small command line used by the benchmark jar.
 */
final class BenchmarkArgs {

    private final String mode;
    private boolean help;
    private String host = "localhost";
    private int port = 61616;
    private SequenceCommand sequenceCommand = SequenceCommand.INIT;
    private Activity activity = Activity.START;
    private int repetitions = 1;
    private int parallel = 1;
    private int warmup = 0;
    private long timeout = 5000L;
    private final List<String> configEntries = new ArrayList<String>();
    private final List<String> statusNames = new ArrayList<String>();
    private long durationSeconds = 60L;
    private long expectedPeriod = 1000L;
    private String csvFile;
    private String runId = "run";
    private String phase = "continuous";
    private int statusItemCount = -1;
    private long statusPostPeriod = -1L;

    private BenchmarkArgs(String mode) {
        this.mode = mode;
    }

    static BenchmarkArgs parse(String[] args) {
        if (args.length == 0 || isHelp(args[0])) {
            BenchmarkArgs benchmarkArgs = new BenchmarkArgs("command");
            benchmarkArgs.help = true;
            return benchmarkArgs;
        }

        BenchmarkArgs benchmarkArgs = new BenchmarkArgs(args[0]);
        for (int idx = 1; idx < args.length; idx++) {
            String arg = args[idx];
            if (isHelp(arg)) {
                benchmarkArgs.help = true;
            } else if ("-h".equals(arg) || "--host".equals(arg)) {
                benchmarkArgs.host = value(args, ++idx, arg);
            } else if ("--port".equals(arg)) {
                benchmarkArgs.port = parsePositiveInt(value(args, ++idx, arg), arg);
            } else if ("-sc".equals(arg) || "--command".equals(arg)) {
                benchmarkArgs.sequenceCommand = SequenceCommand.valueOf(value(args, ++idx, arg));
            } else if ("-activity".equals(arg) || "--activity".equals(arg)) {
                benchmarkArgs.activity = Activity.toActivity(value(args, ++idx, arg));
            } else if ("-n".equals(arg) || "--repetitions".equals(arg)) {
                benchmarkArgs.repetitions = parsePositiveInt(value(args, ++idx, arg), arg);
            } else if ("--parallel".equals(arg)) {
                benchmarkArgs.parallel = parsePositiveInt(value(args, ++idx, arg), arg);
            } else if ("--warmup".equals(arg)) {
                benchmarkArgs.warmup = parseNonNegativeInt(value(args, ++idx, arg), arg);
            } else if ("-timeout".equals(arg) || "--timeout".equals(arg)) {
                benchmarkArgs.timeout = parsePositiveLong(value(args, ++idx, arg), arg);
            } else if ("-config".equals(arg) || "--config".equals(arg)) {
                benchmarkArgs.addConfigEntries(value(args, ++idx, arg));
            } else if ("-status".equals(arg) || "--status".equals(arg)) {
                benchmarkArgs.addStatusNames(value(args, ++idx, arg));
            } else if ("--duration".equals(arg)) {
                benchmarkArgs.durationSeconds = parsePositiveLong(value(args, ++idx, arg), arg);
            } else if ("--expected-period".equals(arg)) {
                benchmarkArgs.expectedPeriod = parsePositiveLong(value(args, ++idx, arg), arg);
            } else if ("--csv".equals(arg)) {
                benchmarkArgs.csvFile = value(args, ++idx, arg);
            } else if ("--run-id".equals(arg)) {
                benchmarkArgs.runId = value(args, ++idx, arg);
            } else if ("--phase".equals(arg)) {
                benchmarkArgs.phase = value(args, ++idx, arg);
            } else if ("--status-item-count".equals(arg)) {
                benchmarkArgs.statusItemCount = parseNonNegativeInt(value(args, ++idx, arg), arg);
            } else if ("--status-post-period".equals(arg)) {
                benchmarkArgs.statusPostPeriod = parseNonNegativeLong(value(args, ++idx, arg), arg);
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        benchmarkArgs.validate();
        return benchmarkArgs;
    }

    private static boolean isHelp(String arg) {
        return "-?".equals(arg) || "--help".equals(arg) || "-help".equals(arg);
    }

    private static String value(String[] args, int idx, String option) {
        if (idx >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[idx];
    }

    private static int parsePositiveInt(String value, String option) {
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(option + " must be greater than zero");
        }
        return parsed;
    }

    private static int parseNonNegativeInt(String value, String option) {
        int parsed = Integer.parseInt(value);
        if (parsed < 0) {
            throw new IllegalArgumentException(option + " must be zero or greater");
        }
        return parsed;
    }

    private static long parsePositiveLong(String value, String option) {
        long parsed = Long.parseLong(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(option + " must be greater than zero");
        }
        return parsed;
    }

    private static long parseNonNegativeLong(String value, String option) {
        long parsed = Long.parseLong(value);
        if (parsed < 0) {
            throw new IllegalArgumentException(option + " must be zero or greater");
        }
        return parsed;
    }

    private void addConfigEntries(String value) {
        String[] entries = value.trim().split("\\s+");
        for (String entry : entries) {
            if (!entry.isEmpty()) {
                configEntries.add(entry);
            }
        }
    }

    private void addStatusNames(String value) {
        String[] entries = value.trim().split("[,\\s]+");
        for (String entry : entries) {
            if (!entry.isEmpty()) {
                statusNames.add(entry);
            }
        }
    }

    private void validate() {
        if ("status".equals(mode)) {
            if (statusNames.isEmpty()) {
                throw new IllegalArgumentException("Status benchmark requires at least one --status <name>");
            }
            if (csvFile == null || csvFile.isEmpty()) {
                throw new IllegalArgumentException("Status benchmark requires --csv <file>");
            }
        }
    }

    Configuration buildConfiguration() {
        DefaultConfiguration.Builder builder = DefaultConfiguration.configurationBuilder();
        for (String entry : configEntries) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalArgumentException("Invalid config entry. Expected key=value but received: " + entry);
            }
            builder.withPath(ConfigPath.configPath(entry.substring(0, separator)), entry.substring(separator + 1));
        }
        return builder.build();
    }

    static String usage() {
        return "Usage:\n"
                + "  java -jar giapi-inst-benchmark.jar command -h <host> -sc <command> -activity <activity> [options]\n"
                + "  java -jar giapi-inst-benchmark.jar status -h <host> --status <name> --csv <file> [options]\n"
                + "\n"
                + "Command options:\n"
                + "  -h, --host <host>             GMP ActiveMQ host. Default: localhost\n"
                + "  --port <port>                 GMP ActiveMQ OpenWire port. Default: 61616\n"
                + "  -sc, --command <command>      GIAPI sequence command. Default: INIT\n"
                + "  -activity, --activity <act>   Activity: PRESET, START, PRESET/START, CANCEL. Default: START\n"
                + "  -config <key=value>           Command configuration entry. Can be repeated or quoted as a list.\n"
                + "  -n, --repetitions <count>     Number of measured commands. Default: 1\n"
                + "  --parallel <count>            Number of concurrent command workers. Default: 1\n"
                + "  --warmup <count>              Commands sent before measuring. Default: 0\n"
                + "  -timeout, --timeout <ms>      Initial and completion response timeout. Default: 5000\n"
                + "  --csv <file>                  Optional command CSV output file.\n"
                + "  --run-id <id>                 Identifier written to each CSV row. Default: run\n"
                + "  --status-item-count <count>   InstrumentSequencer StatusItem count context.\n"
                + "  --status-post-period <ms>     InstrumentSequencer status post period context.\n"
                + "\n"
                + "Status options:\n"
                + "  -status, --status <name>      Status item to monitor. Can be repeated or quoted as a list.\n"
                + "  --duration <seconds>          How long to monitor status updates. Default: 60\n"
                + "  --expected-period <ms>        Expected publish period used to calculate delay. Default: 1000\n"
                + "  --csv <file>                  CSV output file for measured status intervals. Required.\n"
                + "  --run-id <id>                 Identifier written to each CSV row. Default: run\n"
                + "  --phase <name>                Phase label written to each CSV row. Default: continuous\n"
                + "  --status-item-count <count>   InstrumentSequencer StatusItem count context.\n"
                + "  --status-post-period <ms>     InstrumentSequencer status post period context.\n";
    }

    String getMode() {
        return mode;
    }

    boolean isHelp() {
        return help;
    }

    String getBrokerUrl() {
        return "tcp://" + host + ":" + port;
    }

    String getHost() {
        return host;
    }

    int getPort() {
        return port;
    }

    SequenceCommand getSequenceCommand() {
        return sequenceCommand;
    }

    Activity getActivity() {
        return activity;
    }

    int getRepetitions() {
        return repetitions;
    }

    int getParallel() {
        return parallel;
    }

    int getWarmup() {
        return warmup;
    }

    long getTimeout() {
        return timeout;
    }

    int getConfigCount() {
        return configEntries.size();
    }

    List<String> getStatusNames() {
        return Collections.unmodifiableList(statusNames);
    }

    long getDurationSeconds() {
        return durationSeconds;
    }

    long getDurationMillis() {
        return durationSeconds * 1000L;
    }

    long getExpectedPeriod() {
        return expectedPeriod;
    }

    String getCsvFile() {
        return csvFile;
    }

    String getRunId() {
        return runId;
    }

    String getPhase() {
        return phase;
    }

    int getStatusItemCount() {
        return statusItemCount;
    }

    long getStatusPostPeriod() {
        return statusPostPeriod;
    }
}
