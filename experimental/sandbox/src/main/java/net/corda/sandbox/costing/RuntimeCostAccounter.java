package net.corda.sandbox.costing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ben
 */
public class RuntimeCostAccounter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeCostAccounter.class);

    private static Thread primaryThread;

    private static final ThreadLocal<Long> allocationCost = ThreadLocal.withInitial(() -> 0L);

    private static final ThreadLocal<Long> jumpCost = ThreadLocal.withInitial(() -> 0L);

    private static final ThreadLocal<Long> invokeCost = ThreadLocal.withInitial(() -> 0L);

    private static final ThreadLocal<Long> throwCost = ThreadLocal.withInitial(() -> 0L);

    private static final long BASELINE_ALLOC_KILL_THRESHOLD = 1024 * 1024;

    private static final long BASELINE_JUMP_KILL_THRESHOLD = 100;

    private static final long BASELINE_INVOKE_KILL_THRESHOLD = 100;

    private static final long BASELINE_THROW_KILL_THRESHOLD = 50;

    public static void recordJump() {
        final Thread current = Thread.currentThread();
        if (current == primaryThread)
            return;

        LOGGER.debug("In recordJump() at {} on {}", System.currentTimeMillis(), current.getName());
        checkJumpCost(1);
    }

    public static void recordAllocation(final String typeName) {
        final Thread current = Thread.currentThread();
        if (current == primaryThread)
            return;

        LOGGER.debug("In recordAllocation() at {}, got object type: {} on {}",
                System.currentTimeMillis(), typeName, current.getName());

        // More sophistication is clearly possible, e.g. caching approximate sizes for types that we encounter
        checkAllocationCost(1);
    }

    public static void recordArrayAllocation(final int length, final int multiplier) {
        final Thread current = Thread.currentThread();
        if (current == primaryThread)
            return;

        LOGGER.debug("In recordArrayAllocation() at {}, got array element size: {} and size: {} on {}",
                System.currentTimeMillis(), multiplier, length, current.getName());

        checkAllocationCost(length * multiplier);
    }

    public static void recordMethodCall() {
        final Thread current = Thread.currentThread();
        if (current == primaryThread)
            return;

        LOGGER.debug("In recordMethodCall() at {} on {}", System.currentTimeMillis(), current.getName());

        checkInvokeCost(1);
    }

    public static void recordThrow() {
        final Thread current = Thread.currentThread();
        if (current == primaryThread)
            return;

        LOGGER.debug("In recordThrow() at {} on {}", System.currentTimeMillis(), current.getName());
        checkThrowCost(1);
    }

    public static void setPrimaryThread(final Thread toBeIgnored) {
        primaryThread = toBeIgnored;
    }

    private static void checkAllocationCost(final long additional) {
        final long newValue = additional + allocationCost.get();
        allocationCost.set(newValue);
        if (newValue > BASELINE_ALLOC_KILL_THRESHOLD) {
            final Thread current = Thread.currentThread();
            LOGGER.debug("Contract {} terminated for overallocation", current);
            throw new ThreadDeath();
        }
    }

    private static void checkJumpCost(final long additional) {
        final long newValue = additional + jumpCost.get();
        jumpCost.set(newValue);
        if (newValue > BASELINE_JUMP_KILL_THRESHOLD) {
            final Thread current = Thread.currentThread();
            LOGGER.debug("Contract {} terminated for excessive use of looping", current);
            throw new ThreadDeath();
        }
    }

    private static void checkInvokeCost(final long additional) {
        final long newValue = additional + invokeCost.get();
        invokeCost.set(newValue);
        if (newValue > BASELINE_INVOKE_KILL_THRESHOLD) {
            final Thread current = Thread.currentThread();
            LOGGER.debug("Contract {} terminated for excessive method calling", current);
            throw new ThreadDeath();
        }
    }

    private static void checkThrowCost(final long additional) {
        final long newValue = additional + throwCost.get();
        throwCost.set(newValue);
        if (newValue > BASELINE_THROW_KILL_THRESHOLD) {
            final Thread current = Thread.currentThread();
            LOGGER.debug("Contract {} terminated for excessive exception throwing", current);
            throw new ThreadDeath();
        }
    }

    public static long getAllocationCost() {
        return allocationCost.get();
    }

    public static long getJumpCost() {
        return jumpCost.get();
    }

    public static long getInvokeCost() {
        return invokeCost.get();
    }

    public static long getThrowCost() {
        return throwCost.get();
    }

    public static void resetCounters() {
        allocationCost.set(0L);
        jumpCost.set(0L);
        invokeCost.set(0L);
        throwCost.set(0L);
    }
}
