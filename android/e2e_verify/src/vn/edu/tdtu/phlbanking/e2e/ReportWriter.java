package vn.edu.tdtu.phlbanking.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReportWriter {

    private ReportWriter() {
    }

    public static void writeReports(
            Path reportDir,
            E2ERunner.RunContext ctx,
            String tierFilter,
            List<TestResult> results,
            LintInspector.LintSummary lintSummary
    ) throws IOException {
        Files.createDirectories(reportDir);
        String timestampUtc = Instant.now().toString();
        writeMarkdown(reportDir.resolve("e2e_report.md"), ctx, tierFilter, timestampUtc, results, lintSummary);
        writeJson(reportDir.resolve("e2e_report.json"), ctx, tierFilter, timestampUtc, results, lintSummary);
    }

    private static void writeMarkdown(
            Path out,
            E2ERunner.RunContext ctx,
            String tierFilter,
            String timestampUtc,
            List<TestResult> results,
            LintInspector.LintSummary lintSummary
    ) throws IOException {
        long total = results.size();
        long passed = results.stream().filter(TestResult::passed).count();
        long failed = total - passed;

        StringBuilder sb = new StringBuilder();
        sb.append("# PHL Mobile Banking Android E2E Verification Report\n\n");
        sb.append("- **timestampUtc**: `").append(timestampUtc).append("`\n");
        sb.append("- **tierFilter**: `").append(tierFilter).append("`\n");
        sb.append("- **jdkVersion**: `").append(ctx.jdkVersion()).append("`\n");
        sb.append("- **skipGradle**: `").append(ctx.skipGradle()).append("`\n");
        sb.append("- **buildExitCode**: `").append(ctx.buildExitCode()).append("`\n");
        sb.append("- **lintExitCode**: `").append(ctx.lintExitCode()).append("`\n");
        sb.append("- **lintErrors**: `").append(lintSummary.fatalCount() + lintSummary.errorCount())
                .append("` (Fatal=").append(lintSummary.fatalCount())
                .append(", Error=").append(lintSummary.errorCount())
                .append(", Warning=").append(lintSummary.warningCount()).append(")\n");
        sb.append("- **overallPassed**: `").append(!results.isEmpty() && failed == 0).append("` (").append(passed)
                .append("/").append(total).append(" passed, ").append(failed).append(" failed)\n\n");

        sb.append("## Tier Summary\n\n");
        sb.append("| Tier | Total | Passed | Failed |\n");
        sb.append("|---|---:|---:|---:|\n");
        for (int t = 1; t <= 4; t++) {
            final int tier = t;
            List<TestResult> sub = results.stream().filter(r -> r.tier() == tier).toList();
            if (!sub.isEmpty()) {
                long tp = sub.stream().filter(TestResult::passed).count();
                sb.append("| Tier ").append(tier).append(" | ").append(sub.size())
                        .append(" | ").append(tp).append(" | ").append(sub.size() - tp).append(" |\n");
            }
        }
        sb.append("| **Total** | **").append(total).append("** | **").append(passed)
                .append("** | **").append(failed).append("** |\n\n");

        sb.append("## Feature Matrix (F1-F19)\n\n");
        sb.append("| Feature | Total | Passed | Failed |\n");
        sb.append("|---|---:|---:|---:|\n");
        Map<String, long[]> featCounts = new LinkedHashMap<>();
        for (TestResult r : results) {
            long[] arr = featCounts.computeIfAbsent(r.feature(), k -> new long[2]);
            arr[0]++;
            if (r.passed()) {
                arr[1]++;
            }
        }
        for (Map.Entry<String, long[]> e : featCounts.entrySet()) {
            long tot = e.getValue()[0];
            long pas = e.getValue()[1];
            sb.append("| ").append(e.getKey()).append(" | ").append(tot)
                    .append(" | ").append(pas).append(" | ").append(tot - pas).append(" |\n");
        }
        sb.append("\n");

        List<TestResult> failures = results.stream().filter(r -> !r.passed()).toList();
        sb.append("## Failed Tests (").append(failures.size()).append(")\n\n");
        if (failures.isEmpty()) {
            sb.append("None — all executed tests passed.\n\n");
        } else {
            for (TestResult r : failures) {
                sb.append("- **").append(r.id()).append("** [Tier ").append(r.tier())
                        .append(" | ").append(r.feature()).append("] `").append(r.name())
                        .append("` (").append(r.requirementRef()).append("): ")
                        .append(r.failureReason()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Full Test Ledger\n\n");
        sb.append("| ID | Tier | Feature | Name | Status | Req | Duration (ms) | Detail |\n");
        sb.append("|---|---:|---|---|---|---|---:|---|\n");
        for (TestResult r : results) {
            String status = r.passed() ? "PASS" : "FAIL";
            String detail = r.passed() ? r.evidence() : r.failureReason();
            detail = detail.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
            if (detail.length() > 160) {
                detail = detail.substring(0, 157) + "...";
            }
            sb.append("| ").append(r.id())
                    .append(" | ").append(r.tier())
                    .append(" | ").append(r.feature())
                    .append(" | ").append(r.name())
                    .append(" | ").append(status)
                    .append(" | ").append(r.requirementRef())
                    .append(" | ").append(r.durationMs())
                    .append(" | ").append(detail)
                    .append(" |\n");
        }

        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void writeJson(
            Path out,
            E2ERunner.RunContext ctx,
            String tierFilter,
            String timestampUtc,
            List<TestResult> results,
            LintInspector.LintSummary lintSummary
    ) throws IOException {
        long total = results.size();
        long passed = results.stream().filter(TestResult::passed).count();
        long failed = total - passed;

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"timestampUtc\": ").append(jsonStr(timestampUtc)).append(",\n");
        sb.append("  \"tierFilter\": ").append(jsonStr(tierFilter)).append(",\n");
        sb.append("  \"jdkVersion\": ").append(jsonStr(ctx.jdkVersion())).append(",\n");
        sb.append("  \"overallPassed\": ").append(!results.isEmpty() && failed == 0).append(",\n");
        sb.append("  \"summary\": {\n");
        sb.append("    \"total\": ").append(total).append(",\n");
        sb.append("    \"passed\": ").append(passed).append(",\n");
        sb.append("    \"failed\": ").append(failed).append(",\n");
        sb.append("    \"byTier\": {\n");
        for (int t = 1; t <= 4; t++) {
            final int tier = t;
            List<TestResult> sub = results.stream().filter(r -> r.tier() == tier).toList();
            long tp = sub.stream().filter(TestResult::passed).count();
            sb.append("      \"").append(t).append("\": {\"total\": ").append(sub.size())
                    .append(", \"passed\": ").append(tp)
                    .append(", \"failed\": ").append(sub.size() - tp).append("}");
            sb.append(t < 4 ? ",\n" : "\n");
        }
        sb.append("    }\n");
        sb.append("  },\n");
        sb.append("  \"gradle\": {\n");
        sb.append("    \"skipGradle\": ").append(ctx.skipGradle()).append(",\n");
        sb.append("    \"assembleDebugExit\": ").append(ctx.buildExitCode()).append(",\n");
        sb.append("    \"lintDebugExit\": ").append(ctx.lintExitCode()).append(",\n");
        sb.append("    \"lintErrors\": ").append(lintSummary.fatalCount() + lintSummary.errorCount()).append(",\n");
        sb.append("    \"lintWarnings\": ").append(lintSummary.warningCount()).append("\n");
        sb.append("  },\n");
        sb.append("  \"results\": [\n");
        for (int i = 0; i < results.size(); i++) {
            TestResult r = results.get(i);
            sb.append("    {\n");
            sb.append("      \"id\": ").append(jsonStr(r.id())).append(",\n");
            sb.append("      \"tier\": ").append(r.tier()).append(",\n");
            sb.append("      \"feature\": ").append(jsonStr(r.feature())).append(",\n");
            sb.append("      \"name\": ").append(jsonStr(r.name())).append(",\n");
            sb.append("      \"passed\": ").append(r.passed()).append(",\n");
            sb.append("      \"requirementRef\": ").append(jsonStr(r.requirementRef())).append(",\n");
            sb.append("      \"evidence\": ").append(jsonStr(r.evidence())).append(",\n");
            sb.append("      \"failureReason\": ").append(jsonStr(r.failureReason())).append(",\n");
            sb.append("      \"durationMs\": ").append(r.durationMs()).append("\n");
            sb.append("    }").append(i + 1 < results.size() ? ",\n" : "\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");

        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String jsonStr(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
