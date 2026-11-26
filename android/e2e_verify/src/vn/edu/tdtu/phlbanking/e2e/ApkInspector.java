package vn.edu.tdtu.phlbanking.e2e;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ApkInspector {

    public record ApkSnapshot(
            boolean exists,
            long sizeBytes,
            long lastModifiedMillis,
            Set<String> entryNames,
            List<String> dexStrings,
            List<String> layoutBinaryStrings,
            String error
    ) {
        public boolean hasEntry(String exactName) {
            return entryNames.contains(exactName);
        }

        public boolean hasResource(String type, String baseFileName) {
            String plain = "res/" + type + "/" + baseFileName;
            if (entryNames.contains(plain)) {
                return true;
            }
            String prefix = "res/" + type + "-";
            String suffix = "/" + baseFileName;
            for (String e : entryNames) {
                if (e.startsWith(prefix) && e.endsWith(suffix)) {
                    return true;
                }
            }
            return false;
        }

        public int countResourcesInType(String type) {
            String p1 = "res/" + type + "/";
            String p2 = "res/" + type + "-";
            Set<String> distinct = new HashSet<>();
            for (String e : entryNames) {
                if (e.startsWith(p1) || e.startsWith(p2)) {
                    int slash = e.lastIndexOf('/');
                    if (slash >= 0 && slash + 1 < e.length()) {
                        distinct.add(e.substring(slash + 1));
                    }
                }
            }
            return distinct.size();
        }

        public boolean hasDexClassDescriptor(String fqcn) {
            String descriptor = "L" + fqcn.replace('.', '/') + ";";
            for (String dex : dexStrings) {
                if (dex.contains(descriptor)) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasAnyWebAsset() {
            for (String e : entryNames) {
                String lower = e.toLowerCase();
                if (lower.endsWith(".html") || lower.endsWith(".htm")
                        || lower.endsWith(".css") || lower.endsWith(".js")) {
                    return true;
                }
            }
            for (String layoutContent : layoutBinaryStrings) {
                if (layoutContent.contains("WebView") || layoutContent.contains("android.webkit")) {
                    return true;
                }
            }
            return false;
        }
    }

    private ApkInspector() {
    }

    public static ApkSnapshot inspect(Path apkPath) {
        if (apkPath == null || !Files.isRegularFile(apkPath)) {
            return new ApkSnapshot(false, 0L, 0L, Set.of(), List.of(), List.of(),
                    "APK not found: " + apkPath);
        }
        try {
            long size = Files.size(apkPath);
            long mtime = Files.getLastModifiedTime(apkPath).toMillis();
            Set<String> entries = new HashSet<>();
            List<String> dexStrings = new ArrayList<>();
            List<String> layoutBinaryStrings = new ArrayList<>();
            try (ZipFile zip = new ZipFile(apkPath.toFile())) {
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry entry = en.nextElement();
                    String name = entry.getName();
                    entries.add(name);
                    if (name.startsWith("classes") && name.endsWith(".dex")) {
                        try (InputStream is = zip.getInputStream(entry)) {
                            byte[] bytes = is.readAllBytes();
                            dexStrings.add(new String(bytes, StandardCharsets.UTF_8));
                        }
                    } else if (name.startsWith("res/layout") && name.endsWith(".xml")) {
                        try (InputStream is = zip.getInputStream(entry)) {
                            byte[] bytes = is.readAllBytes();
                            layoutBinaryStrings.add(new String(bytes, StandardCharsets.UTF_8));
                            layoutBinaryStrings.add(new String(bytes, StandardCharsets.UTF_16LE));
                        }
                    }
                }
            }
            return new ApkSnapshot(true, size, mtime, entries, dexStrings, layoutBinaryStrings, "");
        } catch (IOException ex) {
            return new ApkSnapshot(false, 0L, 0L, Set.of(), List.of(), List.of(),
                    "Failed reading APK: " + ex.getMessage());
        }
    }
}
