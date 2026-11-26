package vn.edu.tdtu.phlbanking.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SourceInspector {

    public record RetrofitEndpoint(String httpMethod, String path, String javaMethodName) {}

    private SourceInspector() {
    }

    public static String readFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return "";
        }
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            return "";
        }
    }

    public static List<Path> listJavaFiles(Path javaRoot) {
        if (javaRoot == null || !Files.isDirectory(javaRoot)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(javaRoot)) {
            return s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    public static List<Path> listFilesWithExtension(Path root, String ext) {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(ext))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    /**
     * Lexer-safe comment stripper: removes // and /* * / comments while preserving
     * string ("...") and char ('...') literals verbatim (so "http://..." and "phl://..." stay intact).
     */
    public static String stripComments(String src) {
        return lexStrip(src, false);
    }

    /**
     * Strips comments AND string/char literal interiors (leaving "" or '') so structural checks
     * like `new MaterialCardView(`, `Thread.sleep`, `Long.parseLong` never match inside strings.
     */
    public static String stripCommentsAndStrings(String src) {
        return lexStrip(src, true);
    }

    private static String lexStrip(String src, boolean blankStrings) {
        if (src == null || src.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(src.length());
        int n = src.length();
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                i += 2;
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
                continue;
            }
            if (c == '"') {
                out.append('"');
                i++;
                while (i < n) {
                    char sc = src.charAt(i);
                    if (sc == '\\' && i + 1 < n) {
                        if (!blankStrings) {
                            out.append(sc).append(src.charAt(i + 1));
                        }
                        i += 2;
                        continue;
                    }
                    if (sc == '"') {
                        out.append('"');
                        i++;
                        break;
                    }
                    if (!blankStrings) {
                        out.append(sc);
                    }
                    i++;
                }
                continue;
            }
            if (c == '\'') {
                out.append('\'');
                i++;
                while (i < n) {
                    char cc = src.charAt(i);
                    if (cc == '\\' && i + 1 < n) {
                        if (!blankStrings) {
                            out.append(cc).append(src.charAt(i + 1));
                        }
                        i += 2;
                        continue;
                    }
                    if (cc == '\'') {
                        out.append('\'');
                        i++;
                        break;
                    }
                    if (!blankStrings) {
                        out.append(cc);
                    }
                    i++;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    public static String extractMethodBody(String codeNoComments, String methodRegex) {
        Pattern p = Pattern.compile(methodRegex);
        Matcher m = p.matcher(codeNoComments);
        if (!m.find()) {
            return "";
        }
        int idx = m.end();
        while (idx < codeNoComments.length() && codeNoComments.charAt(idx) != '{') {
            if (codeNoComments.charAt(idx) == ';') {
                return "";
            }
            idx++;
        }
        if (idx >= codeNoComments.length()) {
            return "";
        }
        int depth = 0;
        int start = idx;
        for (int i = idx; i < codeNoComments.length(); i++) {
            char ch = codeNoComments.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return codeNoComments.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    /**
     * Checks that every occurrence of parseCallName (e.g. "Long.parseLong" or "Double.parseDouble")
     * inside code appears lexically inside a `try { ... } catch (... NumberFormatException ...)` block.
     */
    public static boolean allParseCallsGuardedByTryCatch(String rawSrc, String parseCallName) {
        String code = stripCommentsAndStrings(rawSrc);
        int idx = 0;
        int found = 0;
        while ((idx = code.indexOf(parseCallName, idx)) >= 0) {
            found++;
            if (!isIndexInsideNumberFormatTry(code, idx)) {
                return false;
            }
            idx += parseCallName.length();
        }
        return found > 0;
    }

    private static boolean isIndexInsideNumberFormatTry(String code, int targetIdx) {
        Pattern tryPat = Pattern.compile("\\btry\\s*\\{");
        Matcher m = tryPat.matcher(code);
        while (m.find()) {
            int tryBraceStart = m.end() - 1;
            int tryBraceEnd = findMatchingBrace(code, tryBraceStart);
            if (tryBraceEnd > tryBraceStart && targetIdx > tryBraceStart && targetIdx < tryBraceEnd) {
                String afterTry = code.substring(tryBraceEnd + 1, Math.min(code.length(), tryBraceEnd + 220));
                if (Pattern.compile("\\bcatch\\s*\\([^)]*(NumberFormatException|Exception|RuntimeException)").matcher(afterTry).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int findMatchingBrace(String code, int openBraceIdx) {
        if (openBraceIdx < 0 || openBraceIdx >= code.length() || code.charAt(openBraceIdx) != '{') {
            return -1;
        }
        int depth = 0;
        for (int i = openBraceIdx; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static Set<String> findMotionPrimitives(String rawSrc) {
        String code = stripCommentsAndStrings(rawSrc);
        Set<String> found = new LinkedHashSet<>();
        Matcher mh = Pattern.compile("MotionHelper\\.(\\w+)\\s*\\(").matcher(code);
        while (mh.find()) {
            found.add("MotionHelper." + mh.group(1));
        }
        String[] direct = {
                "ObjectAnimator", "ValueAnimator", "TransitionManager",
                ".animate()", "overridePendingTransition", "setCustomAnimations",
                "AnimationUtils.loadAnimation", "LayoutAnimationController"
        };
        for (String d : direct) {
            if (code.contains(d)) {
                found.add(d);
            }
        }
        return found;
    }

    public static boolean hasWebViewReference(String rawSrc) {
        String code = stripCommentsAndStrings(rawSrc);
        if (Pattern.compile("\\bWebView\\b|android\\.webkit|loadDataWithBaseURL|loadUrl\\s*\\(")
                .matcher(code)
                .find()) {
            return true;
        }
        String noComments = stripComments(rawSrc);
        return Pattern.compile("\"[^\"]*(?:android\\.webkit|loadDataWithBaseURL)[^\"]*\"")
                .matcher(noComments)
                .find();
    }

    public static boolean hasBlockingUiSleepOrClickableFalse(String rawSrc) {
        String code = stripCommentsAndStrings(rawSrc);
        return code.contains("Thread.sleep") || code.contains("setClickable(false)")
                || code.contains("runBlocking");
    }

    public static List<RetrofitEndpoint> parseRetrofitEndpoints(String rawSrc) {
        String code = stripComments(rawSrc);
        List<RetrofitEndpoint> list = new ArrayList<>();
        Pattern p = Pattern.compile("@(GET|POST|PATCH|PUT|DELETE)\\s*\\(\\s*\"([^\"]+)\"\\s*\\)\\s*Call\\s*<");
        Matcher m = p.matcher(code);
        while (m.find()) {
            int depth = 1;
            int i = m.end();
            while (i < code.length() && depth > 0) {
                char c = code.charAt(i);
                if (c == '<') {
                    depth++;
                } else if (c == '>') {
                    depth--;
                }
                i++;
            }
            if (depth == 0) {
                Matcher nameMatcher = Pattern.compile("^\\s*(\\w+)\\s*\\(").matcher(code.substring(i));
                if (nameMatcher.find()) {
                    list.add(new RetrofitEndpoint(m.group(1), m.group(2), nameMatcher.group(1)));
                }
            }
        }
        return list;
    }

    public static Set<String> extractBindingFields(String rawSrc, String bindingVarName) {
        String code = stripCommentsAndStrings(rawSrc);
        Set<String> fields = new LinkedHashSet<>();
        Pattern p = Pattern.compile("\\b" + Pattern.quote(bindingVarName) + "\\.(\\w+)\\b(?!\\s*\\()");
        Matcher m = p.matcher(code);
        while (m.find()) {
            String f = m.group(1);
            if (!"getRoot".equals(f)) {
                fields.add(f);
            }
        }
        return fields;
    }

    public static String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    public static String snakeToCamel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
