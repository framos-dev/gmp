package edu.gemini.giapi.inst.benchmark;

import edu.gemini.aspen.giapi.commands.HandlerResponse;

/**
 * Captures one measured command exchange.
 */
final class CommandSample {

    final int sampleNo;
    final long startTimestampMs;
    final long endTimestampMs;
    final HandlerResponse initialResponse;
    final HandlerResponse finalResponse;
    final long initialNanos;
    final long endToEndNanos;
    final boolean started;

    CommandSample(int sampleNo,
                  long startTimestampMs,
                  long endTimestampMs,
                  HandlerResponse initialResponse,
                  HandlerResponse finalResponse,
                  long initialNanos,
                  long endToEndNanos,
                  boolean started) {
        this.sampleNo = sampleNo;
        this.startTimestampMs = startTimestampMs;
        this.endTimestampMs = endTimestampMs;
        this.initialResponse = initialResponse;
        this.finalResponse = finalResponse;
        this.initialNanos = initialNanos;
        this.endToEndNanos = endToEndNanos;
        this.started = started;
    }
}
