package edu.gemini.giapi.inst.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calculates simple latency statistics from nanosecond samples.
 */
final class LatencyStats {

    private final List<Long> samples = new ArrayList<Long>();

    void add(long nanos) {
        samples.add(nanos);
    }

    String format(String label) {
        if (samples.isEmpty()) {
            return label + " count=0";
        }
        List<Long> sorted = new ArrayList<Long>(samples);
        Collections.sort(sorted);
        return label
                + " count=" + sorted.size()
                + " minMs=" + millis(sorted.get(0))
                + " avgMs=" + millis(average(sorted))
                + " p50Ms=" + millis(percentile(sorted, 50.0))
                + " p95Ms=" + millis(percentile(sorted, 95.0))
                + " p99Ms=" + millis(percentile(sorted, 99.0))
                + " maxMs=" + millis(sorted.get(sorted.size() - 1));
    }

    private long average(List<Long> sorted) {
        long total = 0L;
        for (Long sample : sorted) {
            total += sample;
        }
        return total / sorted.size();
    }

    private long percentile(List<Long> sorted, double percentile) {
        int idx = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    private String millis(long nanos) {
        return String.format("%.3f", nanos / 1000000.0);
    }
}
