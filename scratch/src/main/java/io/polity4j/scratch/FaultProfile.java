package io.polity4j.scratch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Seeded-RNG-driven or explicit sequence of FaultType per attempt index.
 * Guarantees identical fault sequence every run across every library under test.
 */
public final class FaultProfile {

    private final List<FaultType> sequence;

    private FaultProfile(List<FaultType> sequence) {
        this.sequence = new ArrayList<>(sequence);
    }

    public static FaultProfile of(FaultType... faults) {
        return new FaultProfile(Arrays.asList(faults));
    }

    public static FaultProfile seeded(long seed, int totalAttempts, FaultType... possibleFaults) {
        Random random = new Random(seed);
        List<FaultType> seq = new ArrayList<>();
        FaultType[] pool = (possibleFaults == null || possibleFaults.length == 0)
                ? new FaultType[]{FaultType.RATE_LIMITED, FaultType.OVERLOADED, FaultType.TRANSIENT_5XX}
                : possibleFaults;

        for (int i = 0; i < totalAttempts - 1; i++) {
            seq.add(pool[random.nextInt(pool.length)]);
        }
        seq.add(FaultType.SUCCESS);
        return new FaultProfile(seq);
    }

    /**
     * Retrieves the FaultType for the given 1-indexed attempt number.
     * If attemptNumber exceeds the configured sequence length, defaults to SUCCESS.
     */
    public FaultType getFaultForAttempt(int attemptNumber) {
        int index = attemptNumber - 1;
        if (index < 0) {
            throw new IllegalArgumentException("Attempt number must be >= 1");
        }
        if (index >= sequence.size()) {
            return FaultType.SUCCESS;
        }
        return sequence.get(index);
    }

    public List<FaultType> getSequence() {
        return new ArrayList<>(sequence);
    }
}
