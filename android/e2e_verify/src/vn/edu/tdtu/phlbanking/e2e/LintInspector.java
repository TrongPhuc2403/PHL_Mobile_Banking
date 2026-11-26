package vn.edu.tdtu.phlbanking.e2e;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public final class LintInspector {

    public record LintSummary(
            boolean reportExists,
            int fatalCount,
            int errorCount,
            int warningCount,
            int infoCount,
            List<String> fatalAndErrorDetails
    ) {}

    private LintInspector() {
    }

    public static LintSummary inspect(Path lintXmlPath) {
        if (lintXmlPath == null || !Files.isRegularFile(lintXmlPath)) {
            return new LintSummary(false, 0, 0, 0, 0,
                    List.of("Missing lint report: " + lintXmlPath));
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(lintXmlPath.toFile());
            NodeList issues = doc.getElementsByTagName("issue");
            int fatal = 0;
            int error = 0;
            int warning = 0;
            int info = 0;
            List<String> details = new ArrayList<>();
            for (int i = 0; i < issues.getLength(); i++) {
                Element el = (Element) issues.item(i);
                String severity = el.getAttribute("severity");
                String id = el.getAttribute("id");
                String message = el.getAttribute("message");
                if ("Fatal".equalsIgnoreCase(severity)) {
                    fatal++;
                    details.add("[Fatal] " + id + ": " + message);
                } else if ("Error".equalsIgnoreCase(severity)) {
                    error++;
                    details.add("[Error] " + id + ": " + message);
                } else if ("Warning".equalsIgnoreCase(severity)) {
                    warning++;
                } else {
                    info++;
                }
            }
            return new LintSummary(true, fatal, error, warning, info, details);
        } catch (Exception ex) {
            return new LintSummary(true, 1, 0, 0, 0,
                    List.of("Failed parsing lint XML: " + ex.getMessage()));
        }
    }
}
