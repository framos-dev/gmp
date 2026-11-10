package edu.gemini.giapi.inst.benchmark;

import edu.gemini.aspen.giapi.commands.Command;
import edu.gemini.aspen.giapi.commands.Configuration;
import edu.gemini.aspen.giapi.commands.HandlerResponse;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measures end-to-end latency for GIAPI sequence commands.
 */
final class CommandLatencyBenchmark {

    private final BenchmarkArgs args;

    CommandLatencyBenchmark(BenchmarkArgs args) {
        this.args = args;
    }

    int run() throws Exception {
        Configuration config = args.buildConfiguration();
        Command command = new Command(args.getSequenceCommand(), args.getActivity(), config);

        printHeader();
        runWarmup(command);

        List<CommandSample> samples;
        if (args.getParallel() == 1) {
            samples = runSequential(command);
        } else {
            samples = runParallel(command);
        }
        Collections.sort(samples, new Comparator<CommandSample>() {
            @Override
            public int compare(CommandSample left, CommandSample right) {
                return left.sampleNo - right.sampleNo;
            }
        });
        writeCsv(samples);

        LatencyStats initialStats = new LatencyStats();
        LatencyStats endToEndStats = new LatencyStats();
        int startedCount = 0;
        int directCount = 0;
        int failureCount = 0;

        for (CommandSample sample : samples) {
            initialStats.add(sample.initialNanos);
            endToEndStats.add(sample.endToEndNanos);
            if (sample.started) {
                startedCount++;
            } else {
                directCount++;
            }
            if (isFailure(sample.finalResponse)) {
                failureCount++;
                System.out.println("failure sample=" + sample.sampleNo + " response=" + sample.finalResponse);
            }
        }

        System.out.println(initialStats.format("initial-response"));
        System.out.println(endToEndStats.format("end-to-end"));
        System.out.println("responses started=" + startedCount
                + " direct=" + directCount
                + " failures=" + failureCount);
        return failureCount == 0 ? 0 : 1;
    }

    private void runWarmup(Command command) throws Exception {
        if (args.getWarmup() == 0) {
            return;
        }
        try (JmsCommandClient commandClient = new JmsCommandClient(args.getBrokerUrl())) {
            for (int idx = 0; idx < args.getWarmup(); idx++) {
                commandClient.send(command, args.getTimeout(), 0);
            }
        }
    }

    private List<CommandSample> runSequential(Command command) throws Exception {
        List<CommandSample> samples = new ArrayList<CommandSample>();
        try (JmsCommandClient commandClient = new JmsCommandClient(args.getBrokerUrl())) {
            for (int idx = 0; idx < args.getRepetitions(); idx++) {
                samples.add(commandClient.send(command, args.getTimeout(), idx + 1));
            }
        }
        return samples;
    }

    private List<CommandSample> runParallel(final Command command) throws Exception {
        int workerCount = Math.min(args.getParallel(), args.getRepetitions());
        final AtomicInteger nextSample = new AtomicInteger(1);
        final CountDownLatch readyLatch = new CountDownLatch(workerCount);
        final CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<Future<List<CommandSample>>> futures = new ArrayList<Future<List<CommandSample>>>();

        try {
            for (int idx = 0; idx < workerCount; idx++) {
                futures.add(executor.submit(new Callable<List<CommandSample>>() {
                    @Override
                    public List<CommandSample> call() throws Exception {
                        List<CommandSample> samples = new ArrayList<CommandSample>();
                        boolean ready = false;
                        try (JmsCommandClient commandClient = new JmsCommandClient(args.getBrokerUrl())) {
                            readyLatch.countDown();
                            ready = true;
                            startLatch.await();
                            while (true) {
                                int sampleNo = nextSample.getAndIncrement();
                                if (sampleNo > args.getRepetitions()) {
                                    break;
                                }
                                samples.add(commandClient.send(command, args.getTimeout(), sampleNo));
                            }
                        }
                        finally {
                            if (!ready) {
                                readyLatch.countDown();
                            }
                        }
                        return samples;
                    }
                }));
            }

            readyLatch.await();
            startLatch.countDown();

            List<CommandSample> samples = new ArrayList<CommandSample>();
            for (Future<List<CommandSample>> future : futures) {
                samples.addAll(future.get());
            }
            return samples;
        } finally {
            executor.shutdownNow();
        }
    }

    private void printHeader() {
        System.out.println("GIAPI command latency benchmark");
        System.out.println("broker=" + args.getHost() + ":" + args.getPort()
                + " command=" + args.getSequenceCommand()
                + " activity=" + args.getActivity().getName()
                + " repetitions=" + args.getRepetitions()
                + " parallel=" + args.getParallel()
                + " warmup=" + args.getWarmup()
                + " timeoutMs=" + args.getTimeout()
                + " configEntries=" + args.getConfigCount()
                + " csv=" + optionalValue(args.getCsvFile())
                + " runId=" + args.getRunId());
    }

    private boolean isFailure(HandlerResponse response) {
        return response.getResponse().equals(HandlerResponse.Response.ERROR)
                || response.getResponse().equals(HandlerResponse.Response.NOANSWER);
    }

    private void writeCsv(List<CommandSample> samples) throws IOException {
        if (args.getCsvFile() == null) {
            return;
        }
        CommandCsvWriter csvWriter = new CommandCsvWriter(args.getCsvFile());
        try {
            for (CommandSample sample : samples) {
                csvWriter.write(sample);
            }
        } finally {
            csvWriter.close();
        }
    }

    private String optionalValue(String value) {
        return value == null ? "" : value;
    }

    private String contextValue(long value) {
        return value >= 0 ? Long.toString(value) : "";
    }

    private final class CommandCsvWriter implements AutoCloseable {
        private final PrintWriter writer;

        CommandCsvWriter(String csvFile) throws IOException {
            File file = new File(csvFile);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            writer = new PrintWriter(new FileWriter(file));
            writer.println("runId,command,activity,parallel,warmup,timeoutMs,configEntries,"
                    + "statusItemCount,statusPostPeriodMs,sampleNo,startTimestampMs,endTimestampMs,"
                    + "initialMs,endToEndMs,initialResponse,finalResponse,started,success,error");
        }

        void write(CommandSample sample) {
            boolean success = !isFailure(sample.finalResponse);
            writer.println(csv(args.getRunId())
                    + "," + args.getSequenceCommand()
                    + "," + csv(args.getActivity().getName())
                    + "," + args.getParallel()
                    + "," + args.getWarmup()
                    + "," + args.getTimeout()
                    + "," + args.getConfigCount()
                    + "," + contextValue(args.getStatusItemCount())
                    + "," + contextValue(args.getStatusPostPeriod())
                    + "," + sample.sampleNo
                    + "," + sample.startTimestampMs
                    + "," + sample.endTimestampMs
                    + "," + millis(sample.initialNanos)
                    + "," + millis(sample.endToEndNanos)
                    + "," + sample.initialResponse.getResponse().getTag()
                    + "," + sample.finalResponse.getResponse().getTag()
                    + "," + sample.started
                    + "," + success
                    + "," + csv(errorMessage(sample.finalResponse)));
        }

        @Override
        public void close() {
            writer.close();
        }

        private String errorMessage(HandlerResponse response) {
            if (!isFailure(response)) {
                return "";
            }
            return response.getMessage() == null ? "" : response.getMessage();
        }

        private String millis(long nanos) {
            return String.format(Locale.US, "%.3f", nanos / 1000000.0);
        }

        private String csv(String value) {
            if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                    && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
                return value;
            }
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
    }
}
