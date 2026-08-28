package io.polity4j.scratch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AttemptRecorder {

    private final List<AttemptRecord> records = Collections.synchronizedList(new ArrayList<>());

    public void recordAttempt(int attemptNumber, FaultType faultType, boolean isTerminal) {
        records.add(new AttemptRecord(attemptNumber, System.currentTimeMillis(), faultType, isTerminal));
    }

    public List<AttemptRecord> getRecords() {
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }

    public int totalAttempts() {
        return records.size();
    }

    public boolean endedWithSuccess() {
        synchronized (records) {
            if (records.isEmpty()) return false;
            AttemptRecord last = records.get(records.size() - 1);
            return last.getFaultType() == FaultType.SUCCESS;
        }
    }

    public void reset() {
        records.clear();
    }
}
