package io.polity4j.scratch;

public final class AttemptRecord {
    private final int attemptNumber;
    private final long timestampMs;
    private final FaultType faultType;
    private final boolean isTerminal;

    public AttemptRecord(int attemptNumber, long timestampMs, FaultType faultType, boolean isTerminal) {
        this.attemptNumber = attemptNumber;
        this.timestampMs = timestampMs;
        this.faultType = faultType;
        this.isTerminal = isTerminal;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public FaultType getFaultType() {
        return faultType;
    }

    public boolean isTerminal() {
        return isTerminal;
    }

    @Override
    public String toString() {
        return "AttemptRecord{" +
                "attempt=" + attemptNumber +
                ", timeMs=" + timestampMs +
                ", fault=" + faultType +
                ", terminal=" + isTerminal +
                '}';
    }
}
