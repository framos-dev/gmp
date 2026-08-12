package edu.gemini.giapi.inst.benchmark;

import edu.gemini.aspen.giapi.commands.HandlerResponse;

/**
 * Captures one measured command exchange.
 */
final class CommandSample {

    final int sampleNo;
    final HandlerResponse finalResponse;
    final long initialNanos;
    final long endToEndNanos;
    final boolean started;

    CommandSample(int sampleNo,
                  HandlerResponse finalResponse,
                  long initialNanos,
                  long endToEndNanos,
                  boolean started) {
        this.sampleNo = sampleNo;
        this.finalResponse = finalResponse;
        this.initialNanos = initialNanos;
        this.endToEndNanos = endToEndNanos;
        this.started = started;
    }
}
