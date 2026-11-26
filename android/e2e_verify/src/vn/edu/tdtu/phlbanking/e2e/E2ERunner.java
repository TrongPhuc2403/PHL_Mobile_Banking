package vn.edu.tdtu.phlbanking.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class E2ERunner {

    public record RunContext(
            Path repoRoot,
            Path androidDir,
            Path mainDir,
            Path javaDir,
            Path resDir,
            Path manifestPath,
            Path appBuildGradle,
            Path apkPath,
            Path lintXmlPath,
            Path gitStatusFile,
            Path scopeViolationsFile,
            boolean skipGradle,
            int buildExitCode,
            int lintExitCode,
            String jdkVersion,
            XmlInspector xml
    ) {
        public Path javaFile(String relPathFromPkg) {
            return javaDir.resolve("vn/edu/tdtu/phlbanking/" + relPathFromPkg);
        }

        public String readJava(String relPathFromPkg) {
            return SourceInspector.readFile(javaFile(relPathFromPkg));
        }

        public List<Path> allJavaFiles() {
            return SourceInspector.listJavaFiles(javaDir);
        }

        public List<Path> allLayoutFiles() {
            return xml.listXmlFiles("layout");
        }

        public List<Path> allDrawableFiles() {
            return xml.listXmlFiles("drawable");
        }

        public List<Path> allAnimFiles() {
            return xml.listXmlFiles("anim");
        }

        public List<Path> allColorSelectorFiles() {
            return xml.listXmlFiles("color");
        }

        public long latestMainSourceModifiedMillis() {
            long latest = 0L;
            if (Files.isDirectory(mainDir)) {
                try (Stream<Path> s = Files.walk(mainDir)) {
                    for (Path p : s.filter(Files::isRegularFile).toList()) {
                        long m = Files.getLastModifiedTime(p).toMillis();
                        if (m > latest) {
                            latest = m;
                        }
                    }
                } catch (IOException ignored) {
                }
            }
            return latest;
        }
    }

    private E2ERunner() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        Path androidDir = Paths.get(opts.getOrDefault("android-dir", "android")).toAbsolutePath().normalize();
        Path repoRoot = Paths.get(opts.getOrDefault("repo-root", androidDir.getParent().toString())).toAbsolutePath().normalize();
        String tier = opts.getOrDefault("tier", "all").toLowerCase();
        if (!Set.of("all", "1", "2", "3", "4").contains(tier)) {
            System.err.println("ERROR: Invalid --tier value '" + tier + "'. Allowed values: all, 1, 2, 3, 4.");
            System.exit(1);
            return;
        }
        boolean skipGradle = Boolean.parseBoolean(opts.getOrDefault("skip-gradle", "false"));
        int buildExit = Integer.parseInt(opts.getOrDefault("build-exit", "-1"));
        int lintExit = Integer.parseInt(opts.getOrDefault("lint-exit", "-1"));
        Path gitStatusFile = Paths.get(opts.getOrDefault("git-status-file",
                androidDir.resolve("e2e_verify/build/git_status.txt").toString())).toAbsolutePath().normalize();
        Path scopeFile = Paths.get(opts.getOrDefault("scope-violations-file",
                androidDir.resolve("e2e_verify/build/scope_violations.txt").toString())).toAbsolutePath().normalize();
        Path reportDir = Paths.get(opts.getOrDefault("report-dir",
                androidDir.resolve("e2e_verify/reports").toString())).toAbsolutePath().normalize();
        String jdkVersion = opts.getOrDefault("jdk-version", System.getProperty("java.version"));

        Path mainDir = androidDir.resolve("app/src/main");
        Path javaDir = mainDir.resolve("java");
        Path resDir = mainDir.resolve("res");
        Path manifestPath = mainDir.resolve("AndroidManifest.xml");
        Path appBuildGradle = androidDir.resolve("app/build.gradle");
        Path apkPath = androidDir.resolve("app/build/outputs/apk/debug/app-debug.apk");
        Path lintXmlPath = androidDir.resolve("app/build/reports/lint-results-debug.xml");

        XmlInspector xml = new XmlInspector(resDir, manifestPath);
        RunContext ctx = new RunContext(
                repoRoot, androidDir, mainDir, javaDir, resDir, manifestPath,
                appBuildGradle, apkPath, lintXmlPath, gitStatusFile, scopeFile,
                skipGradle, buildExit, lintExit, jdkVersion, xml
        );

        List<TestResult> results = new ArrayList<>();
        if ("all".equals(tier) || "1".equals(tier)) {
            results.addAll(Tier1FeatureTests.runAll(ctx));
        }
        if ("all".equals(tier) || "2".equals(tier)) {
            results.addAll(Tier2BoundaryTests.runAll(ctx));
        }
        if ("all".equals(tier) || "3".equals(tier)) {
            results.addAll(Tier3CrossFeatureTests.runAll(ctx));
        }
        if ("all".equals(tier) || "4".equals(tier)) {
            results.addAll(Tier4ScenarioTests.runAll(ctx));
        }

        LintInspector.LintSummary lintSummary = LintInspector.inspect(lintXmlPath);
        ReportWriter.writeReports(reportDir, ctx, tier, results, lintSummary);

        long total = results.size();
        long passed = results.stream().filter(TestResult::passed).count();
        long failed = total - passed;

        System.out.println("=== PHL Mobile Banking E2E Verification Summary ===");
        System.out.println("Tier Filter : " + tier);
        System.out.println("Total Tests : " + total);
        System.out.println("Passed      : " + passed);
        System.out.println("Failed      : " + failed);
        for (int t = 1; t <= 4; t++) {
            final int ti = t;
            List<TestResult> sub = results.stream().filter(r -> r.tier() == ti).toList();
            if (!sub.isEmpty()) {
                long tp = sub.stream().filter(TestResult::passed).count();
                System.out.printf("  Tier %d: %d/%d passed (%d failed)%n", t, tp, sub.size(), sub.size() - tp);
            }
        }
        System.out.println("Reports     : " + reportDir.resolve("e2e_report.md") + " & e2e_report.json");

        if (failed > 0) {
            System.out.println("--- Sample Failures (up to 15) ---");
            results.stream().filter(r -> !r.passed()).limit(15).forEach(r ->
                    System.out.printf("  [FAIL] %s (%s): %s%n", r.id(), r.feature(), r.failureReason()));
        }

        if (results.isEmpty()) {
            System.err.println("ERROR: 0 tests executed for tier '" + tier + "'.");
            System.exit(1);
            return;
        }
        System.exit(failed == 0 ? 0 : 1);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String key = a.substring(2);
                int eq = key.indexOf('=');
                if (eq > 0) {
                    map.put(key.substring(0, eq), key.substring(eq + 1));
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    map.put(key, args[++i]);
                } else {
                    map.put(key, "true");
                }
            }
        }
        return map;
    }
}
