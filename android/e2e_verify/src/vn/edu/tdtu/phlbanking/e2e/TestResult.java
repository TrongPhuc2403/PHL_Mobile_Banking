package vn.edu.tdtu.phlbanking.e2e;

public record TestResult(
        String id,
        int tier,
        String feature,
        String name,
        boolean passed,
        String requirementRef,
        String evidence,
        String failureReason,
        long durationMs
) {
    @FunctionalInterface
    public interface AssertionAction {
        String run() throws Exception;
    }

    public static TestResult execute(
            String id,
            int tier,
            String feature,
            String name,
            String requirementRef,
            AssertionAction action
    ) {
        long start = System.nanoTime();
        try {
            String evidence = action.run();
            long elapsedMs = Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
            return new TestResult(id, tier, feature, name, true, requirementRef,
                    evidence == null ? "OK" : evidence, "", elapsedMs);
        } catch (AssertionError | Exception ex) {
            long elapsedMs = Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
            String msg = ex.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = ex.getClass().getSimpleName();
            }
            return new TestResult(id, tier, feature, name, false, requirementRef,
                    "", msg, elapsedMs);
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    public static void fail(String message) {
        throw new AssertionError(message);
    }
}
