package edu.gemini.giapi.inst.benchmark;

import edu.gemini.aspen.giapi.status.StatusHandler;
import edu.gemini.aspen.giapi.status.StatusItem;
import edu.gemini.aspen.giapi.statusservice.StatusHandlerAggregate;
import edu.gemini.aspen.giapi.statusservice.jms.JmsStatusListener;
import edu.gemini.aspen.giapi.statusservice.jms.StatusConsumer;
import edu.gemini.jms.activemq.provider.ActiveMQJmsProvider;
import edu.gemini.jms.api.JmsProvider;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Measures subscriber-side StatusItem receive intervals.
 */
final class StatusIntervalBenchmark {

    private final BenchmarkArgs args;

    StatusIntervalBenchmark(BenchmarkArgs args) {
        this.args = args;
    }

    int run() throws Exception {
        printHeader();

        CsvWriter csvWriter = new CsvWriter(args.getCsvFile());
        StatusIntervalHandler handler = new StatusIntervalHandler(csvWriter);
        StatusHandlerAggregate aggregate = new StatusHandlerAggregate();
        aggregate.bindStatusHandler(handler);

        JmsProvider provider = new ActiveMQJmsProvider(args.getBrokerUrl());
        List<StatusConsumer> consumers = new ArrayList<StatusConsumer>();
        try {
            for (String statusName : args.getStatusNames()) {
                StatusConsumer consumer = new StatusConsumer(
                        "GIAPI status interval benchmark " + statusName,
                        statusName);
                consumer.setMessageListener(new JmsStatusListener(aggregate));
                consumer.startJms(provider);
                consumers.add(consumer);
                System.out.println("subscribed " + statusName);
            }

            Thread.sleep(args.getDurationMillis());
        } finally {
            for (StatusConsumer consumer : consumers) {
                consumer.stopJms();
            }
            csvWriter.close();
        }

        handler.printSummary();
        return 0;
    }

    private void printHeader() {
        System.out.println("GIAPI status interval benchmark");
        System.out.println("broker=" + args.getHost() + ":" + args.getPort()
                + " statuses=" + args.getStatusNames()
                + " durationSec=" + args.getDurationSeconds()
                + " expectedPeriodMs=" + args.getExpectedPeriod()
                + " csv=" + args.getCsvFile()
                + " runId=" + args.getRunId()
                + " statusItemCount=" + contextValue(args.getStatusItemCount())
                + " statusPostPeriodMs=" + contextValue(args.getStatusPostPeriod()));
    }

    private String contextValue(long value) {
        return value >= 0 ? Long.toString(value) : "";
    }

    private final class StatusIntervalHandler implements StatusHandler {
        private final CsvWriter csvWriter;
        private final long startNanos = System.nanoTime();
        private final long expectedPeriodNanos = args.getExpectedPeriod() * 1000000L;
        private final Map<String, Long> lastReceiveNanosByName = new HashMap<String, Long>();
        private final Map<String, Integer> updateCountByName = new HashMap<String, Integer>();
        private final Map<String, LatencyStats> intervalStatsByName = new HashMap<String, LatencyStats>();

        StatusIntervalHandler(CsvWriter csvWriter) {
            this.csvWriter = csvWriter;
        }

        @Override
        public String getName() {
            return "GIAPI status interval benchmark";
        }

        @Override
        public synchronized <T> void update(StatusItem<T> item) {
            long receiveNanos = System.nanoTime();
            long receiveEpochMillis = System.currentTimeMillis();
            String statusName = item.getName();
            int sequence = nextSequence(statusName);
            Long previousReceiveNanos = lastReceiveNanosByName.put(statusName, receiveNanos);
            Long intervalNanos = previousReceiveNanos == null ? null : receiveNanos - previousReceiveNanos;
            Long delayNanos = intervalNanos == null ? null : intervalNanos - expectedPeriodNanos;

            if (intervalNanos != null) {
                statsFor(statusName).add(intervalNanos);
            }

            csvWriter.write(new StatusIntervalSample(
                    args.getRunId(),
                    args.getPhase(),
                    statusName,
                    sequence,
                    receiveEpochMillis,
                    receiveNanos - startNanos,
                    intervalNanos,
                    args.getExpectedPeriod(),
                    delayNanos,
                    item.getValue(),
                    item.getTimestamp() == null ? null : item.getTimestamp().getTime(),
                    args.getStatusItemCount(),
                    args.getStatusPostPeriod()));
        }

        void printSummary() {
            int totalUpdates = 0;
            for (Map.Entry<String, Integer> entry : updateCountByName.entrySet()) {
                totalUpdates += entry.getValue();
                LatencyStats stats = intervalStatsByName.get(entry.getKey());
                String statsText = stats == null ? "interval count=0" : stats.format("interval");
                System.out.println("status=" + entry.getKey()
                        + " updates=" + entry.getValue()
                        + " " + statsText);
            }
            System.out.println("totalUpdates=" + totalUpdates);
        }

        private int nextSequence(String statusName) {
            Integer current = updateCountByName.get(statusName);
            int next = current == null ? 1 : current + 1;
            updateCountByName.put(statusName, next);
            return next;
        }

        private LatencyStats statsFor(String statusName) {
            LatencyStats stats = intervalStatsByName.get(statusName);
            if (stats == null) {
                stats = new LatencyStats();
                intervalStatsByName.put(statusName, stats);
            }
            return stats;
        }
    }

    private static final class StatusIntervalSample {
        final String runId;
        final String phase;
        final String statusName;
        final int sequence;
        final long receivedTimestampMs;
        final long elapsedNanos;
        final Long intervalNanos;
        final long expectedPeriodMs;
        final Long delayNanos;
        final Object value;
        final Long statusItemTimestampMs;
        final int statusItemCount;
        final long statusPostPeriodMs;

        StatusIntervalSample(String runId,
                             String phase,
                             String statusName,
                             int sequence,
                             long receivedTimestampMs,
                             long elapsedNanos,
                             Long intervalNanos,
                             long expectedPeriodMs,
                             Long delayNanos,
                             Object value,
                             Long statusItemTimestampMs,
                             int statusItemCount,
                             long statusPostPeriodMs) {
            this.runId = runId;
            this.phase = phase;
            this.statusName = statusName;
            this.sequence = sequence;
            this.receivedTimestampMs = receivedTimestampMs;
            this.elapsedNanos = elapsedNanos;
            this.intervalNanos = intervalNanos;
            this.expectedPeriodMs = expectedPeriodMs;
            this.delayNanos = delayNanos;
            this.value = value;
            this.statusItemTimestampMs = statusItemTimestampMs;
            this.statusItemCount = statusItemCount;
            this.statusPostPeriodMs = statusPostPeriodMs;
        }
    }

    private static final class CsvWriter implements AutoCloseable {
        private final PrintWriter writer;

        CsvWriter(String csvFile) throws IOException {
            File file = new File(csvFile);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            writer = new PrintWriter(new FileWriter(file));
            writer.println("runId,phase,statusItemName,sequence,receivedTimestampMs,elapsedMs,"
                    + "intervalMs,expectedPeriodMs,delayVsExpectedMs,value,statusItemTimestampMs,"
                    + "statusItemCount,statusPostPeriodMs");
        }

        synchronized void write(StatusIntervalSample sample) {
            writer.println(csv(sample.runId)
                    + "," + csv(sample.phase)
                    + "," + csv(sample.statusName)
                    + "," + sample.sequence
                    + "," + sample.receivedTimestampMs
                    + "," + millis(sample.elapsedNanos)
                    + "," + millis(sample.intervalNanos)
                    + "," + sample.expectedPeriodMs
                    + "," + millis(sample.delayNanos)
                    + "," + csv(sample.value == null ? "" : sample.value.toString())
                    + "," + (sample.statusItemTimestampMs == null ? "" : sample.statusItemTimestampMs)
                    + "," + (sample.statusItemCount >= 0 ? sample.statusItemCount : "")
                    + "," + (sample.statusPostPeriodMs >= 0 ? sample.statusPostPeriodMs : ""));
            writer.flush();
        }

        @Override
        public void close() {
            writer.close();
        }

        private static String millis(Long nanos) {
            if (nanos == null) {
                return "";
            }
            return millis(nanos.longValue());
        }

        private static String millis(long nanos) {
            return String.format(Locale.US, "%.3f", nanos / 1000000.0);
        }

        private static String csv(String value) {
            if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                    && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
                return value;
            }
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
    }
}
