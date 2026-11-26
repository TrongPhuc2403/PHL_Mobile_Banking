package vn.edu.tdtu.phlbanking.e2e;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public final class XmlInspector {

    public record StyleDef(String name, String parent, Map<String, String> items) {}

    public record SelectorItem(String stateChecked, String stateEnabled, String colorRef, String resolvedHex) {}

    public record MenuItemInfo(String id, String icon, String title) {}

    public record ActivityInfo(String name, boolean isMainLauncher) {}

    public record ManifestInfo(
            boolean exists,
            Set<String> permissions,
            String applicationTheme,
            List<ActivityInfo> activities
    ) {
        public boolean hasActivity(String suffixOrFqcn) {
            for (ActivityInfo a : activities) {
                if (a.name().equals(suffixOrFqcn) || a.name().endsWith(suffixOrFqcn)) {
                    return true;
                }
            }
            return false;
        }

        public boolean isLauncher(String suffixOrFqcn) {
            for (ActivityInfo a : activities) {
                if ((a.name().equals(suffixOrFqcn) || a.name().endsWith(suffixOrFqcn))
                        && a.isMainLauncher()) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class LayoutInfo {
        public final boolean exists;
        public final String fileName;
        public final Document doc;
        public final Element root;
        public final String rootTag;
        public final Map<String, Element> idToElement;
        public final Map<String, String> idToTag;
        public final List<Element> allElements;

        public LayoutInfo(boolean exists, String fileName, Document doc) {
            this.exists = exists;
            this.fileName = fileName;
            this.doc = doc;
            this.root = doc != null ? doc.getDocumentElement() : null;
            this.rootTag = this.root != null ? simpleTag(this.root.getTagName()) : "";
            this.idToElement = new LinkedHashMap<>();
            this.idToTag = new LinkedHashMap<>();
            this.allElements = new ArrayList<>();
            if (this.root != null) {
                collect(this.root);
            }
        }

        private void collect(Element el) {
            allElements.add(el);
            String rawId = getAttr(el, "id");
            if (!rawId.isEmpty()) {
                String cleanId = stripIdPrefix(rawId);
                idToElement.put(cleanId, el);
                idToTag.put(cleanId, simpleTag(el.getTagName()));
            }
            NodeList children = el.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element child) {
                    collect(child);
                }
            }
        }

        public boolean hasId(String id) {
            return idToElement.containsKey(id);
        }

        public Element element(String id) {
            return idToElement.get(id);
        }

        public String tagOf(String id) {
            return idToTag.getOrDefault(id, "");
        }

        public boolean rootIsScrollable() {
            return "ScrollView".equals(rootTag)
                    || "NestedScrollView".equals(rootTag)
                    || "RecyclerView".equals(rootTag);
        }

        public boolean hasScrollContainerAnywhere() {
            if (rootIsScrollable()) {
                return true;
            }
            for (Element el : allElements) {
                String t = simpleTag(el.getTagName());
                if ("ScrollView".equals(t) || "NestedScrollView".equals(t) || "RecyclerView".equals(t)) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasScrollAncestor(String id) {
            Element el = idToElement.get(id);
            if (el == null) {
                return false;
            }
            Node cur = el.getParentNode();
            while (cur instanceof Element parent) {
                String t = simpleTag(parent.getTagName());
                if ("ScrollView".equals(t)
                        || "NestedScrollView".equals(t)
                        || "HorizontalScrollView".equals(t)) {
                    return true;
                }
                cur = parent.getParentNode();
            }
            return false;
        }

        public boolean hasAncestorTag(String id, String tagName) {
            Element el = idToElement.get(id);
            if (el == null) {
                return false;
            }
            Node cur = el.getParentNode();
            while (cur instanceof Element parent) {
                if (tagName.equals(simpleTag(parent.getTagName()))) {
                    return true;
                }
                cur = parent.getParentNode();
            }
            return false;
        }

        public boolean hasWebView() {
            for (Element el : allElements) {
                String t = el.getTagName();
                if (t.contains("WebView") || t.contains("android.webkit")) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasElementWithIdOrDrawableContaining(String needleLower) {
            for (Element el : allElements) {
                String id = stripIdPrefix(getAttr(el, "id")).toLowerCase();
                String bg = getAttr(el, "background").toLowerCase();
                String src = getAttr(el, "src").toLowerCase();
                String srcCompat = getAttr(el, "srcCompat").toLowerCase();
                if (id.contains(needleLower) || bg.contains(needleLower)
                        || src.contains(needleLower) || srcCompat.contains(needleLower)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Path resDir;
    private final Path manifestPath;
    private Map<String, String> cachedColors;
    private Map<String, String> cachedDimens;
    private Map<String, String> cachedStrings;
    private Map<String, StyleDef> cachedStyles;

    public XmlInspector(Path resDir, Path manifestPath) {
        this.resDir = resDir;
        this.manifestPath = manifestPath;
    }

    public Path resDir() {
        return resDir;
    }

    public static String simpleTag(String tag) {
        if (tag == null) {
            return "";
        }
        int dot = tag.lastIndexOf('.');
        return dot >= 0 && dot + 1 < tag.length() ? tag.substring(dot + 1) : tag;
    }

    public static String stripIdPrefix(String rawId) {
        if (rawId == null) {
            return "";
        }
        if (rawId.startsWith("@+id/")) {
            return rawId.substring(5);
        }
        if (rawId.startsWith("@id/")) {
            return rawId.substring(4);
        }
        return rawId;
    }

    public static String getAttr(Element el, String localName) {
        if (el == null) {
            return "";
        }
        String v = el.getAttribute("android:" + localName);
        if (!v.isEmpty()) {
            return v;
        }
        v = el.getAttribute("app:" + localName);
        if (!v.isEmpty()) {
            return v;
        }
        v = el.getAttribute(localName);
        if (!v.isEmpty()) {
            return v;
        }
        NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node n = attrs.item(i);
            String name = n.getNodeName();
            int colon = name.indexOf(':');
            String local = colon >= 0 ? name.substring(colon + 1) : name;
            if (local.equals(localName)) {
                return n.getNodeValue();
            }
        }
        return "";
    }

    public Optional<Document> parseXml(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return Optional.of(builder.parse(path.toFile()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public List<Path> listXmlFiles(String subdir) {
        Path dir = resDir.resolve(subdir);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    public synchronized Map<String, String> loadColors() {
        if (cachedColors != null) {
            return cachedColors;
        }
        Map<String, String> map = new LinkedHashMap<>();
        parseXml(resDir.resolve("values/colors.xml")).ifPresent(doc -> {
            NodeList nodes = doc.getElementsByTagName("color");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String name = el.getAttribute("name");
                String val = el.getTextContent().trim();
                if (!name.isEmpty()) {
                    map.put(name, val);
                }
            }
        });
        cachedColors = map;
        return cachedColors;
    }

    public synchronized Map<String, String> loadDimens() {
        if (cachedDimens != null) {
            return cachedDimens;
        }
        Map<String, String> map = new LinkedHashMap<>();
        parseXml(resDir.resolve("values/dimens.xml")).ifPresent(doc -> {
            NodeList nodes = doc.getElementsByTagName("dimen");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String name = el.getAttribute("name");
                String val = el.getTextContent().trim();
                if (!name.isEmpty()) {
                    map.put(name, val);
                }
            }
        });
        cachedDimens = map;
        return cachedDimens;
    }

    public synchronized Map<String, String> loadStrings() {
        if (cachedStrings != null) {
            return cachedStrings;
        }
        Map<String, String> map = new LinkedHashMap<>();
        parseXml(resDir.resolve("values/strings.xml")).ifPresent(doc -> {
            NodeList nodes = doc.getElementsByTagName("string");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String name = el.getAttribute("name");
                String val = el.getTextContent().trim();
                if (!name.isEmpty()) {
                    map.put(name, val);
                }
            }
        });
        cachedStrings = map;
        return cachedStrings;
    }

    public synchronized Map<String, StyleDef> loadStyles() {
        if (cachedStyles != null) {
            return cachedStyles;
        }
        Map<String, StyleDef> map = new LinkedHashMap<>();
        for (String file : List.of("values/styles.xml", "values/themes.xml")) {
            parseXml(resDir.resolve(file)).ifPresent(doc -> {
                NodeList styles = doc.getElementsByTagName("style");
                for (int i = 0; i < styles.getLength(); i++) {
                    Element s = (Element) styles.item(i);
                    String name = s.getAttribute("name");
                    String parent = s.getAttribute("parent");
                    if (parent.isEmpty() && name.contains(".")) {
                        int dot = name.lastIndexOf('.');
                        parent = name.substring(0, dot);
                    }
                    Map<String, String> items = new LinkedHashMap<>();
                    NodeList children = s.getElementsByTagName("item");
                    for (int j = 0; j < children.getLength(); j++) {
                        Element item = (Element) children.item(j);
                        String itemName = item.getAttribute("name");
                        String itemVal = item.getTextContent().trim();
                        if (!itemName.isEmpty()) {
                            items.put(itemName, itemVal);
                        }
                    }
                    if (!name.isEmpty()) {
                        map.put(name, new StyleDef(name, parent, items));
                    }
                }
            });
        }
        cachedStyles = map;
        return cachedStyles;
    }

    public String resolveStyleItem(String styleNameOrRef, String itemName) {
        if (styleNameOrRef == null || styleNameOrRef.isEmpty()) {
            return "";
        }
        String cur = styleNameOrRef.startsWith("@style/") ? styleNameOrRef.substring(7) : styleNameOrRef;
        Map<String, StyleDef> styles = loadStyles();
        Set<String> visited = new HashSet<>();
        while (cur != null && !cur.isEmpty() && visited.add(cur)) {
            StyleDef def = styles.get(cur);
            if (def == null) {
                break;
            }
            if (def.items().containsKey(itemName)) {
                return def.items().get(itemName);
            }
            if (def.items().containsKey("android:" + itemName)) {
                return def.items().get("android:" + itemName);
            }
            String plain = itemName.startsWith("android:") ? itemName.substring(8) : itemName;
            if (def.items().containsKey(plain)) {
                return def.items().get(plain);
            }
            cur = def.parent();
        }
        return "";
    }

    public String resolveColorHex(String refOrHex) {
        if (refOrHex == null) {
            return "";
        }
        String cur = refOrHex.trim();
        Map<String, String> colors = loadColors();
        Set<String> visited = new HashSet<>();
        while (cur.startsWith("@color/") && visited.add(cur)) {
            String key = cur.substring(7);
            cur = colors.getOrDefault(key, "");
        }
        return cur.toUpperCase();
    }

    public OptionalDouble parseDp(String valueOrDimenRef) {
        if (valueOrDimenRef == null || valueOrDimenRef.isBlank()) {
            return OptionalDouble.empty();
        }
        String cur = valueOrDimenRef.trim();
        if (cur.startsWith("@dimen/")) {
            cur = loadDimens().getOrDefault(cur.substring(7), "");
        }
        if (cur.endsWith("dp") || cur.endsWith("dip")) {
            String num = cur.replace("dip", "").replace("dp", "").trim();
            try {
                return OptionalDouble.of(Double.parseDouble(num));
            } catch (NumberFormatException ignored) {
                return OptionalDouble.empty();
            }
        }
        return OptionalDouble.empty();
    }

    public String getEffectiveAttr(Element el, String attrLocalName) {
        String direct = getAttr(el, attrLocalName);
        if (!direct.isEmpty()) {
            return direct;
        }
        String styleRef = getAttr(el, "style");
        if (!styleRef.isEmpty()) {
            String fromStyle = resolveStyleItem(styleRef, attrLocalName);
            if (!fromStyle.isEmpty()) {
                return fromStyle;
            }
        }
        String textApp = getAttr(el, "textAppearance");
        if (!textApp.isEmpty()) {
            String fromTa = resolveStyleItem(textApp, attrLocalName);
            if (!fromTa.isEmpty()) {
                return fromTa;
            }
        }
        if (!styleRef.isEmpty()) {
            String taInStyle = resolveStyleItem(styleRef, "android:textAppearance");
            if (!taInStyle.isEmpty()) {
                String fromTa = resolveStyleItem(taInStyle, attrLocalName);
                if (!fromTa.isEmpty()) {
                    return fromTa;
                }
            }
        }
        return "";
    }

    public boolean hasTnum(Element el) {
        String val = getEffectiveAttr(el, "fontFeatureSettings");
        return val.toLowerCase().contains("tnum");
    }

    public boolean hasEllipsizeEnd(Element el) {
        return "end".equalsIgnoreCase(getEffectiveAttr(el, "ellipsize"));
    }

    public List<SelectorItem> parseColorSelector(String colorRefOrFileName) {
        String name = colorRefOrFileName;
        if (name.startsWith("@color/")) {
            name = name.substring(7);
        }
        if (!name.endsWith(".xml")) {
            name = name + ".xml";
        }
        Optional<Document> opt = parseXml(resDir.resolve("color/" + name));
        if (opt.isEmpty()) {
            return List.of();
        }
        Document doc = opt.get();
        if (!"selector".equals(doc.getDocumentElement().getTagName())) {
            return List.of();
        }
        List<SelectorItem> items = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName("item");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element item = (Element) nodes.item(i);
            String checked = getAttr(item, "state_checked");
            String enabled = getAttr(item, "state_enabled");
            String color = getAttr(item, "color");
            items.add(new SelectorItem(checked, enabled, color, resolveColorHex(color)));
        }
        return items;
    }

    public LayoutInfo parseLayout(String layoutFileName) {
        String fname = layoutFileName.endsWith(".xml") ? layoutFileName : layoutFileName + ".xml";
        Optional<Document> opt = parseXml(resDir.resolve("layout/" + fname));
        return new LayoutInfo(opt.isPresent(), fname, opt.orElse(null));
    }

    public Map<String, MenuItemInfo> parseMenu(String menuFileName) {
        String fname = menuFileName.endsWith(".xml") ? menuFileName : menuFileName + ".xml";
        Map<String, MenuItemInfo> map = new LinkedHashMap<>();
        parseXml(resDir.resolve("menu/" + fname)).ifPresent(doc -> {
            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element el = (Element) items.item(i);
                String id = stripIdPrefix(getAttr(el, "id"));
                String icon = getAttr(el, "icon");
                String title = getAttr(el, "title");
                if (!id.isEmpty()) {
                    map.put(id, new MenuItemInfo(id, icon, title));
                }
            }
        });
        return map;
    }

    public ManifestInfo parseManifest() {
        Optional<Document> opt = parseXml(manifestPath);
        if (opt.isEmpty()) {
            return new ManifestInfo(false, Set.of(), "", List.of());
        }
        Document doc = opt.get();
        Set<String> perms = new HashSet<>();
        NodeList permNodes = doc.getElementsByTagName("uses-permission");
        for (int i = 0; i < permNodes.getLength(); i++) {
            String name = getAttr((Element) permNodes.item(i), "name");
            if (!name.isEmpty()) {
                perms.add(name);
            }
        }
        String appTheme = "";
        NodeList appNodes = doc.getElementsByTagName("application");
        if (appNodes.getLength() > 0) {
            appTheme = getAttr((Element) appNodes.item(0), "theme");
        }
        List<ActivityInfo> activities = new ArrayList<>();
        NodeList actNodes = doc.getElementsByTagName("activity");
        for (int i = 0; i < actNodes.getLength(); i++) {
            Element act = (Element) actNodes.item(i);
            String name = getAttr(act, "name");
            boolean isMain = false;
            boolean isLauncher = false;
            NodeList actions = act.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                if ("android.intent.action.MAIN".equals(getAttr((Element) actions.item(j), "name"))) {
                    isMain = true;
                }
            }
            NodeList cats = act.getElementsByTagName("category");
            for (int j = 0; j < cats.getLength(); j++) {
                if ("android.intent.category.LAUNCHER".equals(getAttr((Element) cats.item(j), "name"))) {
                    isLauncher = true;
                }
            }
            activities.add(new ActivityInfo(name, isMain && isLauncher));
        }
        return new ManifestInfo(true, perms, appTheme, activities);
    }

    public boolean drawableExists(String refOrName) {
        String name = refOrName;
        if (name.startsWith("@drawable/")) {
            name = name.substring(10);
        }
        Path xml = resDir.resolve("drawable/" + name + ".xml");
        Path png = resDir.resolve("drawable/" + name + ".png");
        return Files.isRegularFile(xml) || Files.isRegularFile(png);
    }

    private static final Pattern XML_COMMENT_PATTERN = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    public static String stripXmlComments(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return XML_COMMENT_PATTERN.matcher(raw).replaceAll("");
    }

    public String readRawXml(Path xmlPath) {
        try {
            return stripXmlComments(Files.readString(xmlPath));
        } catch (IOException ex) {
            return "";
        }
    }

    public static List<Element> elementsByTag(Document doc, String tagName) {
        List<Element> list = new ArrayList<>();
        if (doc == null) {
            return list;
        }
        NodeList nl = doc.getElementsByTagName(tagName);
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i) instanceof Element el) {
                list.add(el);
            }
        }
        return list;
    }

    public static Map<String, String> allValuesCombined(Map<String, String> m1, Map<String, String> m2) {
        Map<String, String> out = new HashMap<>(m1);
        out.putAll(m2);
        return out;
    }
}
