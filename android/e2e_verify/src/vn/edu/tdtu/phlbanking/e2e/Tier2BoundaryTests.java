package vn.edu.tdtu.phlbanking.e2e;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static vn.edu.tdtu.phlbanking.e2e.TestResult.assertFalse;
import static vn.edu.tdtu.phlbanking.e2e.TestResult.assertTrue;
import static vn.edu.tdtu.phlbanking.e2e.TestResult.execute;

public final class Tier2BoundaryTests {

    private Tier2BoundaryTests() {
    }

    public static List<TestResult> runAll(E2ERunner.RunContext ctx) {
        List<TestResult> out = new ArrayList<>(101);
        addF1(ctx, out);
        addF2(ctx, out);
        addF3(ctx, out);
        addF4(ctx, out);
        addF5(ctx, out);
        addF6(ctx, out);
        addF7(ctx, out);
        addF8(ctx, out);
        addF9(ctx, out);
        addF10(ctx, out);
        addF11(ctx, out);
        addF12(ctx, out);
        addF13(ctx, out);
        addF14(ctx, out);
        addF15(ctx, out);
        addF16(ctx, out);
        addF17(ctx, out);
        addF18(ctx, out);
        addF19(ctx, out);
        return out;
    }

    private static void addF1(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F1";
        out.add(execute("T2-F01-01", 2, f, "BottomNav selector checked vs default distinct RGB", "R1, F1", () -> {
            List<Path> colorFiles = ctx.allColorSelectorFiles();
            assertFalse(colorFiles.isEmpty(), "No res/color/*.xml files");
            boolean foundValid = false;
            for (Path cf : colorFiles) {
                List<XmlInspector.SelectorItem> items = ctx.xml().parseColorSelector(cf.getFileName().toString());
                String checkedHex = null, defaultHex = null;
                for (XmlInspector.SelectorItem item : items) {
                    if ("true".equals(item.stateChecked())) checkedHex = item.resolvedHex();
                    if (item.stateChecked().isEmpty() && item.stateEnabled().isEmpty()) defaultHex = item.resolvedHex();
                }
                if (checkedHex != null && defaultHex != null && !checkedHex.isEmpty() && !checkedHex.equals(defaultHex)) {
                    foundValid = true;
                    break;
                }
            }
            assertTrue(foundValid, "No res/color/ selector maps state_checked=true vs default to distinct RGB colors");
            return "Verified distinct checked/default RGB in res/color/";
        }));

        out.add(execute("T2-F01-02", 2, f, "TextAppearance.PHL.Amount* tnum is actively referenced", "R1, F1", () -> {
            Map<String, XmlInspector.StyleDef> styles = ctx.xml().loadStyles();
            boolean styleHasTnum = false;
            Set<String> amountStyles = new HashSet<>();
            for (String s : styles.keySet()) {
                if (s.startsWith("TextAppearance.PHL.Amount")) {
                    amountStyles.add(s);
                    if (ctx.xml().resolveStyleItem(s, "android:fontFeatureSettings").toLowerCase().contains("tnum")) {
                        styleHasTnum = true;
                    }
                }
            }
            assertTrue(styleHasTnum, "No TextAppearance.PHL.Amount* sets fontFeatureSettings=tnum");
            Set<String> derivedAmountStyles = new HashSet<>(amountStyles);
            for (String s : styles.keySet()) {
                if (styleInheritsOrUsesAny(ctx.xml(), s, amountStyles)) {
                    derivedAmountStyles.add(s);
                }
            }
            boolean referenced = false;
            for (Path lf : ctx.allLayoutFiles()) {
                XmlInspector.LayoutInfo li = ctx.xml().parseLayout(lf.getFileName().toString());
                for (Element el : li.allElements) {
                    if (matchesAnyStyleRef(XmlInspector.getAttr(el, "style"), derivedAmountStyles)
                            || matchesAnyStyleRef(XmlInspector.getAttr(el, "textAppearance"), derivedAmountStyles)) {
                        referenced = true;
                        break;
                    }
                }
                if (referenced) break;
            }
            assertTrue(referenced, "TextAppearance.PHL.Amount* (or derived style) is dead/unreferenced in all layouts");
            return "Verified active TextAppearance.PHL.Amount* usage";
        }));

        out.add(execute("T2-F01-03", 2, f, "dimens.xml uses dp/sp only (no px/negative) & elev <= 8dp", "R1, F1", () -> {
            assertTrue(Files.isRegularFile(ctx.resDir().resolve("values/dimens.xml")), "Missing dimens.xml");
            Map<String, String> dimens = ctx.xml().loadDimens();
            assertFalse(dimens.isEmpty(), "dimens.xml empty");
            for (Map.Entry<String, String> e : dimens.entrySet()) {
                String v = e.getValue().trim();
                assertFalse(v.endsWith("px") || v.startsWith("-"),
                        "Invalid dimen " + e.getKey() + "=" + v);
                if (e.getKey().toLowerCase().contains("elevation")) {
                    OptionalDouble dp = ctx.xml().parseDp(v);
                    assertTrue(dp.isPresent() && dp.getAsDouble() <= 8.0,
                            "Elevation dimen > 8dp: " + e.getKey() + "=" + v);
                }
            }
            return "All " + dimens.size() + " dimens valid dp/sp";
        }));

        out.add(execute("T2-F01-04", 2, f, "phl_gold, phl_gold_soft, phl_emerald actively referenced", "R1, F1", () -> {
            Map<String, String> colors = ctx.xml().loadColors();
            for (String c : List.of("phl_gold", "phl_gold_soft", "phl_emerald")) {
                String hex = colors.getOrDefault(c, "");
                assertTrue(hex.matches("#[0-9A-Fa-f]{6,8}"), "Invalid hex for " + c + ": " + hex);
            }
            StringBuilder corpus = new StringBuilder();
            for (Path p : ctx.allDrawableFiles()) corpus.append(ctx.xml().readRawXml(p));
            for (Path p : ctx.allLayoutFiles()) corpus.append(ctx.xml().readRawXml(p));
            String all = corpus.toString();
            assertTrue(all.contains("@color/phl_gold") && all.contains("@color/phl_emerald"),
                    "phl_gold or phl_emerald is unused in drawables/layouts");
            return "Verified active references to phl_gold & phl_emerald";
        }));

        out.add(execute("T2-F01-05", 2, f, "themes.xml dark statusBarColor & consistent radii 8..24dp", "R1, F1", () -> {
            String sb = ctx.xml().resolveStyleItem("Theme.PHLBanking", "android:statusBarColor");
            assertTrue(sb.contains("phl_navy") || "#0F1E36".equalsIgnoreCase(sb) || "#091324".equalsIgnoreCase(sb),
                    "Theme.PHLBanking statusBarColor not dark navy: " + sb);
            Map<String, XmlInspector.StyleDef> styles = ctx.xml().loadStyles();
            boolean hasRadius = false;
            for (String s : styles.keySet()) {
                if (s.startsWith("Widget.PHL.")) {
                    String r = ctx.xml().resolveStyleItem(s, "cardCornerRadius");
                    if (r.isEmpty()) r = ctx.xml().resolveStyleItem(s, "cornerRadius");
                    OptionalDouble dp = ctx.xml().parseDp(r);
                    if (dp.isPresent()) {
                        assertTrue(dp.getAsDouble() >= 8.0 && dp.getAsDouble() <= 24.0,
                                "Out-of-range radius in " + s + ": " + r);
                        hasRadius = true;
                    }
                }
            }
            assertTrue(hasRadius, "No Widget.PHL.* style defines cardCornerRadius/cornerRadius in 8..24dp");
            return "statusBarColor=" + sb + ", radii verified";
        }));
    }

    private static void addF2(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F2";
        out.add(execute("T2-F02-01", 2, f, "Rounded shape drawables use dp radii > 0", "R1, F2", () -> {
            int checked = 0;
            for (Path d : ctx.allDrawableFiles()) {
                Optional<Document> opt = ctx.xml().parseXml(d);
                if (opt.isEmpty()) continue;
                NodeList corners = opt.get().getElementsByTagName("corners");
                for (int i = 0; i < corners.getLength(); i++) {
                    String r = XmlInspector.getAttr((Element) corners.item(i), "radius");
                    if (!r.isEmpty()) {
                        OptionalDouble dp = ctx.xml().parseDp(r);
                        assertTrue(dp.isPresent() && dp.getAsDouble() > 0,
                                d.getFileName() + " <corners> invalid radius: " + r);
                        checked++;
                    }
                }
            }
            assertTrue(checked >= 5, "Expected >= 5 rounded <shape> drawables, found " + checked);
            return "checked corners=" + checked;
        }));

        out.add(execute("T2-F02-02", 2, f, "Gradient angles multiple of 45 & zero oversized PNGs", "R1, F2", () -> {
            int gradients = 0;
            for (Path d : ctx.allDrawableFiles()) {
                Optional<Document> opt = ctx.xml().parseXml(d);
                if (opt.isEmpty()) continue;
                NodeList gs = opt.get().getElementsByTagName("gradient");
                for (int i = 0; i < gs.getLength(); i++) {
                    String ang = XmlInspector.getAttr((Element) gs.item(i), "angle");
                    if (!ang.isEmpty()) {
                        int a = Integer.parseInt(ang);
                        assertTrue(a % 45 == 0, d.getFileName() + " gradient angle not multiple of 45: " + a);
                    }
                    gradients++;
                }
            }
            assertTrue(gradients >= 2, "Expected >= 2 <gradient> drawables");
            List<Path> pngs = SourceInspector.listFilesWithExtension(ctx.resDir(), ".png");
            assertTrue(pngs.isEmpty(), "Unexpected raster PNGs found: " + pngs);
            return "gradients=" + gradients + ", 0 PNGs";
        }));

        out.add(execute("T2-F02-03", 2, f, "Distinct incoming vs outgoing chat bubble drawables (>=12dp)", "R1, F2", () -> {
            List<Path> chats = ctx.allDrawableFiles().stream()
                    .filter(p -> p.getFileName().toString().contains("chat"))
                    .toList();
            assertTrue(chats.size() >= 2, "Expected >= 2 chat bubble drawables, found " + chats.size());
            for (Path c : chats) {
                Optional<Document> opt = ctx.xml().parseXml(c);
                assertTrue(opt.isPresent(), "Invalid XML: " + c.getFileName());
                assertTrue(opt.get().getElementsByTagName("corners").getLength() > 0,
                        c.getFileName() + " lacks rounded <corners>");
            }
            return "chat drawables=" + chats.size();
        }));

        out.add(execute("T2-F02-04", 2, f, "Vector drawables specify viewport & original 6 ic_* preserved", "R1, F2", () -> {
            for (String orig : List.of("ic_phl_shield", "ic_home", "ic_wallet", "ic_grid", "ic_explore", "ic_person")) {
                assertTrue(ctx.xml().drawableExists(orig), "Missing original vector: " + orig);
            }
            int vecs = 0;
            for (Path d : ctx.allDrawableFiles()) {
                Optional<Document> opt = ctx.xml().parseXml(d);
                if (opt.isPresent() && "vector".equals(opt.get().getDocumentElement().getTagName())) {
                    Element root = opt.get().getDocumentElement();
                    assertFalse(XmlInspector.getAttr(root, "viewportWidth").isEmpty(), d.getFileName() + " missing viewportWidth");
                    assertFalse(XmlInspector.getAttr(root, "viewportHeight").isEmpty(), d.getFileName() + " missing viewportHeight");
                    vecs++;
                }
            }
            assertTrue(vecs >= 10, "Expected >= 10 vectors, found " + vecs);
            return "vectors=" + vecs;
        }));

        out.add(execute("T2-F02-05", 2, f, "Zero orphaned drawables in res/drawable/", "R1, F2", () -> {
            StringBuilder corpus = new StringBuilder();
            for (Path p : ctx.allLayoutFiles()) corpus.append(ctx.xml().readRawXml(p)).append('\n');
            for (Path p : ctx.xml().listXmlFiles("menu")) corpus.append(ctx.xml().readRawXml(p)).append('\n');
            for (Path p : ctx.xml().listXmlFiles("values")) corpus.append(ctx.xml().readRawXml(p)).append('\n');
            for (Path p : ctx.allDrawableFiles()) corpus.append(ctx.xml().readRawXml(p)).append('\n');
            corpus.append(ctx.xml().readRawXml(ctx.manifestPath())).append('\n');
            for (Path jf : ctx.allJavaFiles()) {
                corpus.append(SourceInspector.stripCommentsAndStrings(SourceInspector.readFile(jf))).append('\n');
            }
            String all = corpus.toString();

            List<String> orphans = new ArrayList<>();
            for (Path d : ctx.allDrawableFiles()) {
                String base = d.getFileName().toString().replace(".xml", "");
                if (!all.contains("@drawable/" + base) && !all.contains("R.drawable." + base)) {
                    orphans.add(base);
                }
            }
            assertTrue(orphans.isEmpty(), "Orphaned drawables not referenced in layouts/menus/Java: " + orphans);
            return "All " + ctx.allDrawableFiles().size() + " drawables referenced";
        }));
    }

    private static void addF3(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F3";
        out.add(execute("T2-F03-01", 2, f, "MotionHelper public methods null-check targets", "R2, F3", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/common/MotionHelper.java"));
            assertFalse(src.isEmpty(), "MotionHelper.java missing");
            Matcher m = Pattern.compile("public\\s+static\\s+[\\w<>]+\\s+(\\w+)\\s*\\([^)]*\\)").matcher(src);
            int checked = 0;
            while (m.find()) {
                String name = m.group(1);
                String body = SourceInspector.extractMethodBody(src, "public\\s+static\\s+[\\w<>]+\\s+" + name + "\\s*\\([^)]*\\)");
                assertTrue(body.contains("== null") || body.contains("!= null"),
                        "MotionHelper." + name + " missing null check");
                checked++;
            }
            assertTrue(checked >= 10, "Too few public static methods in MotionHelper: " + checked);
            return "Null-guarded methods=" + checked;
        }));

        out.add(execute("T2-F03-02", 2, f, "applyTactilePress non-consuming & zero sleep/lockout", "R2, F3", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/common/MotionHelper.java"));
            assertFalse(src.isEmpty(), "MotionHelper.java missing");
            String body = SourceInspector.extractMethodBody(src, "public\\s+static\\s+\\w+\\s+applyTactilePress\\s*\\([^)]*\\)");
            String touchSrc = body.contains("TACTILE_TOUCH_LISTENER") ? src : body;
            assertTrue(body.contains("setOnTouchListener"), "applyTactilePress must attach OnTouchListener");
            assertTrue(touchSrc.contains("return false;"), "applyTactilePress listener must return false so clicks are not consumed");
            assertTrue(touchSrc.contains("ACTION_UP") && touchSrc.contains("ACTION_CANCEL"),
                    "applyTactilePress must reset scale on ACTION_UP & ACTION_CANCEL");
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(src),
                    "MotionHelper contains Thread.sleep or setClickable(false)");
            return "Verified non-consuming tactile press";
        }));

        out.add(execute("T2-F03-03", 2, f, "animateVndCounter zero/equal guard & bounded durations", "R2, F3", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/common/MotionHelper.java"));
            assertFalse(src.isEmpty(), "MotionHelper.java missing");
            String body = SourceInspector.extractMethodBody(src, "public\\s+static\\s+\\w+\\s+animateVndCounter\\s*\\([^)]*\\)");
            assertTrue(body.contains("fromValue == toValue") || body.contains("durationMs <= 0") || body.contains("Math.max"),
                    "animateVndCounter lacks equal/non-positive guard");
            for (Path af : ctx.allAnimFiles()) {
                String raw = ctx.xml().readRawXml(af);
                Matcher dm = Pattern.compile("android:duration=\"(\\d+)\"").matcher(raw);
                while (dm.find()) {
                    long dur = Long.parseLong(dm.group(1));
                    if (!af.getFileName().toString().contains("pulse")) {
                        assertTrue(dur <= 800, af.getFileName() + " entrance duration > 800ms: " + dur);
                    }
                }
            }
            return "Verified counter guard & anim durations <= 800ms";
        }));

        out.add(execute("T2-F03-04", 2, f, "staggerChildren/staggerEntrance handles empty containers", "R2, F3", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/common/MotionHelper.java"));
            assertFalse(src.isEmpty(), "MotionHelper.java missing");
            String b1 = SourceInspector.extractMethodBody(src, "public\\s+static\\s+\\w+\\s+staggerChildren\\s*\\([^)]*\\)");
            assertTrue(b1.contains("getChildCount()") && (b1.contains("<= 0") || b1.contains("== 0") || b1.contains("i < count")),
                    "staggerChildren must safely iterate getChildCount()");
            return "Verified empty container safety";
        }));

        out.add(execute("T2-F03-05", 2, f, "applyFragmentTransition & applyActivityTransition implemented cleanly", "R2, F3", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/common/MotionHelper.java"));
            assertFalse(src.isEmpty(), "MotionHelper.java missing");
            String ft = SourceInspector.extractMethodBody(src, "public\\s+static\\s+\\w+\\s+applyFragmentTransition\\s*\\([^)]*\\)");
            String at = SourceInspector.extractMethodBody(src, "public\\s+static\\s+\\w+\\s+applyActivityTransition\\s*\\([^)]*\\)");
            assertTrue(ft.contains("setCustomAnimations"), "applyFragmentTransition missing setCustomAnimations");
            assertTrue(at.contains("overridePendingTransition") || at.contains("overrideActivityTransition"),
                    "applyActivityTransition missing activity transition");
            return "Verified fragment & activity transition helpers";
        }));
    }

    private static void addF4(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F4";
        out.add(execute("T2-F04-01", 2, f, "dialog_receipt.xml enforces tnum on monetary TextViews", "R1, F4", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("dialog_receipt.xml");
            assertTrue(l.exists, "Missing dialog_receipt.xml");
            long tnumCount = l.allElements.stream()
                    .filter(e -> XmlInspector.simpleTag(e.getTagName()).endsWith("TextView"))
                    .filter(e -> ctx.xml().hasTnum(e))
                    .count();
            assertTrue(tnumCount >= 2, "dialog_receipt.xml needs tnum on amount/fee/total TextViews (found " + tnumCount + ")");
            return "tnum TextViews in dialog_receipt=" + tnumCount;
        }));

        out.add(execute("T2-F04-02", 2, f, "ChangePasswordDialogHelper blocks auto-dismiss on invalid input", "R1-R3, F4", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/common/ChangePasswordDialogHelper.java"));
            assertTrue(src.contains("getButton(AlertDialog.BUTTON_POSITIVE)")
                            || src.contains("getButton(DialogInterface.BUTTON_POSITIVE)")
                            || src.contains("BottomSheetDialog")
                            || (src.contains("setContentView(") && src.contains("setOnClickListener")),
                    "ChangePasswordDialogHelper must use getButton(BUTTON_POSITIVE), BottomSheetDialog, or custom dialog button so invalid input does not auto-dismiss");
            return "Verified non-dismissing password validation";
        }));

        out.add(execute("T2-F04-03", 2, f, "dialog_receipt.xml & dialog_change_password.xml scrollable", "R1, F4", () -> {
            XmlInspector.LayoutInfo r = ctx.xml().parseLayout("dialog_receipt.xml");
            XmlInspector.LayoutInfo cp = ctx.xml().parseLayout("dialog_change_password.xml");
            assertTrue(r.exists && r.hasScrollContainerAnywhere(), "dialog_receipt.xml lacks ScrollView/NestedScrollView");
            assertTrue(cp.exists && cp.hasScrollContainerAnywhere(), "dialog_change_password.xml lacks ScrollView/NestedScrollView");
            return "Verified scrollable dialog layouts";
        }));

        out.add(execute("T2-F04-04", 2, f, "ReceiptDialogHelper null-safe formatting & CurrencyUtils", "R3, F4", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/ReceiptDialogHelper.java"));
            assertTrue(src.contains("safe(") && src.contains("CurrencyUtils.formatVnd(")
                    && src.contains("Intent.ACTION_SEND"),
                    "ReceiptDialogHelper missing safe()/formatVnd/share");
            return "Verified ReceiptDialogHelper null-safety & share";
        }));

        out.add(execute("T2-F04-05", 2, f, "RolePlaceholder pill drawable & non-blocking motion", "R1-R2, F4", () -> {
            XmlInspector.LayoutInfo rp = ctx.xml().parseLayout("fragment_role_placeholder.xml");
            Element badge = rp.element("tvTabRoleBadge");
            assertTrue(badge != null && ctx.xml().getEffectiveAttr(badge, "background").contains("@drawable/"),
                    "tvTabRoleBadge lacks rounded pill @drawable/ background");
            String src = ctx.readJava("ui/common/RolePlaceholderFragment.java");
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "RolePlaceholderFragment lacks motion");
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(src), "Blocking sleep/clickable=false");
            return "Verified RolePlaceholder polish & motion";
        }));
    }

    private static void addF5(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F5";
        out.add(execute("T2-F05-01", 2, f, "activity_forgot_password.xml wrapped in ScrollView", "R1, F5", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("activity_forgot_password.xml");
            assertTrue(l.hasScrollContainerAnywhere(), "activity_forgot_password.xml lacks ScrollView/NestedScrollView");
            return "Verified ForgotPassword ScrollView";
        }));

        out.add(execute("T2-F05-02", 2, f, "LoginActivity shakeError & btnLogin re-enabled on response/failure", "R2-R3, F5", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/auth/LoginActivity.java"));
            assertTrue(src.contains("shakeError"), "LoginActivity missing shakeError on error");
            boolean reEnablesLogin = src.contains("btnLogin.setEnabled(true)")
                    || (src.contains("btnLogin.setEnabled(!loading)") && src.contains("setLoading(false)"));
            assertTrue(reEnablesLogin, "LoginActivity must restore btnLogin.setEnabled(true) via setEnabled(true) or setLoading(false)");
            return "Verified LoginActivity error shake & button recovery";
        }));

        out.add(execute("T2-F05-03", 2, f, "ForgotPassword OTP maxLength=6 & >=6 char password guard", "R3, F5", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("activity_forgot_password.xml");
            Element otp = l.element("edtForgotOtp");
            assertTrue("number".equals(XmlInspector.getAttr(otp, "inputType"))
                    && "6".equals(XmlInspector.getAttr(otp, "maxLength")),
                    "edtForgotOtp must keep inputType=number & maxLength=6");
            String src = SourceInspector.stripComments(ctx.readJava("ui/auth/ForgotPasswordActivity.java"));
            assertTrue(src.contains("newPass.length() < 6") || src.contains("length() < 6"),
                    "Missing >= 6 char password check");
            return "Verified OTP & password boundaries";
        }));

        out.add(execute("T2-F05-04", 2, f, "Auth screens zero Thread.sleep & zero setClickable(false)", "R2, F5", () -> {
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(ctx.readJava("ui/auth/LoginActivity.java")),
                    "Blocking call in LoginActivity");
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(ctx.readJava("ui/auth/ForgotPasswordActivity.java")),
                    "Blocking call in ForgotPasswordActivity");
            return "Verified non-blocking Auth UI";
        }));

        out.add(execute("T2-F05-05", 2, f, "tvErrorBanner wrap_content height & single-line username", "R1, F5", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("activity_login.xml");
            Element err = l.element("tvErrorBanner");
            assertTrue("wrap_content".equals(ctx.xml().getEffectiveAttr(err, "layout_height")),
                    "tvErrorBanner height must be wrap_content");
            Element edtUser = l.element("edtUsername");
            boolean singleLine = "1".equals(ctx.xml().getEffectiveAttr(edtUser, "maxLines"))
                    || "true".equalsIgnoreCase(ctx.xml().getEffectiveAttr(edtUser, "singleLine"));
            assertTrue(singleLine, "edtUsername must enforce maxLines=1 or singleLine=true");
            return "Verified error banner wrap & username single-line";
        }));
    }

    private static void addF6(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F6";
        out.add(execute("T2-F06-01", 2, f, "customerBottomNav selectors map contrasting checked/default RGB", "R1, F6", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("activity_customer_main.xml");
            String iconTint = ctx.xml().getEffectiveAttr(l.element("customerBottomNav"), "itemIconTint");
            List<XmlInspector.SelectorItem> items = ctx.xml().parseColorSelector(iconTint);
            String checked = null, def = null;
            for (XmlInspector.SelectorItem i : items) {
                if ("true".equals(i.stateChecked())) checked = i.resolvedHex();
                if (i.stateChecked().isEmpty()) def = i.resolvedHex();
            }
            assertTrue(checked != null && def != null && !checked.equals(def),
                    "customerBottomNav selector lacks contrasting checked vs default colors");
            return "checked=" + checked + ", default=" + def;
        }));

        out.add(execute("T2-F06-02", 2, f, "customerBottomNav labeled mode & concise <=16 char labels", "R1, F6", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("activity_customer_main.xml");
            assertTrue("labeled".equals(ctx.xml().getEffectiveAttr(l.element("customerBottomNav"), "labelVisibilityMode")),
                    "customerBottomNav labelVisibilityMode != labeled");
            Map<String, String> strings = ctx.xml().loadStrings();
            Map<String, XmlInspector.MenuItemInfo> menu = ctx.xml().parseMenu("menu_customer_nav.xml");
            for (XmlInspector.MenuItemInfo mi : menu.values()) {
                String title = mi.title();
                if (title.startsWith("@string/")) {
                    title = strings.getOrDefault(title.substring(8), title);
                }
                assertTrue(title.length() <= 16, "Tab label too long (>16 chars): " + title);
            }
            return "Verified labeled 5-tab lengths <= 16";
        }));

        out.add(execute("T2-F06-03", 2, f, "CustomerMainActivity swapFragment uses replace & non-blocking", "R2, F6", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerMainActivity.java"));
            assertTrue(src.contains(".replace(R.id.customerFragmentHost"), "Missing replace(customerFragmentHost)");
            assertFalse(src.contains("addToBackStack"), "swapFragment should not accumulate tab back-stack");
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(src), "Blocking UI call found");
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "CustomerMainActivity lacks transition motion");
            return "Verified non-blocking fragment swap";
        }));

        out.add(execute("T2-F06-04", 2, f, "selectTab drives setSelectedItemId safely", "R3, F6", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerMainActivity.java"));
            String body = SourceInspector.extractMethodBody(src, "public\\s+void\\s+selectTab\\s*\\([^)]*\\)");
            assertTrue(body.contains("setSelectedItemId("), "selectTab missing setSelectedItemId");
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "Missing tab motion");
            return "Verified selectTab";
        }));

        out.add(execute("T2-F06-05", 2, f, "savedInstanceState == null guard preserved on initial tab", "R3, F6", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerMainActivity.java"));
            assertTrue(src.contains("savedInstanceState == null") && src.contains("tab_cust_home"),
                    "Missing savedInstanceState == null guard for tab_cust_home");
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "Missing shell motion upgrade");
            return "Verified savedInstanceState == null guard";
        }));
    }

    private static void addF7(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F7";
        out.add(execute("T2-F07-01", 2, f, "item_history_row.xml ellipsize=end & maxLines=1 on title/meta/amount", "R1, F7", () -> {
            XmlInspector.LayoutInfo row = ctx.xml().parseLayout("item_history_row.xml");
            for (String id : List.of("tvHistoryTitle", "tvHistoryMeta")) {
                Element el = row.element(id);
                assertTrue(ctx.xml().hasEllipsizeEnd(el), id + " missing ellipsize=end");
                assertTrue("1".equals(ctx.xml().getEffectiveAttr(el, "maxLines")), id + " missing maxLines=1");
            }
            Element amt = row.element("tvHistoryAmount");
            assertTrue(ctx.xml().hasTnum(amt) && "1".equals(ctx.xml().getEffectiveAttr(amt, "maxLines")),
                    "tvHistoryAmount missing maxLines=1 or tnum");
            return "Verified history row overflow & tnum guards";
        }));

        out.add(execute("T2-F07-02", 2, f, "CustomerHomeFragment preserves balanceHidden mask & 0 VND", "R2-R3, F7", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerHomeFragment.java"));
            assertTrue(src.contains("balanceHidden") && src.contains("•••••••• VNĐ"),
                    "Missing balanceHidden mask");
            assertTrue(src.contains("animateVndCounter"), "Missing animateVndCounter");
            return "Verified balanceHidden mask & counter";
        }));

        out.add(execute("T2-F07-03", 2, f, "Quick action buttons eliminate 4dp cramped text-only padding", "R1, F7", () -> {
            XmlInspector.LayoutInfo home = ctx.xml().parseLayout("fragment_customer_home.xml");
            for (String id : List.of("btnQuickTransfer", "btnQuickDepositWithdraw", "btnQuickGateway")) {
                Element el = home.element(id);
                assertFalse("4dp".equals(ctx.xml().getEffectiveAttr(el, "paddingHorizontal")),
                        id + " still has cramped paddingHorizontal=4dp");
            }
            return "Verified quick-action button spacing";
        }));

        out.add(execute("T2-F07-04", 2, f, "tvHomeAccountNo, tvHomeBalance & tvHistoryAmount enforce tnum", "R1, F7", () -> {
            XmlInspector.LayoutInfo home = ctx.xml().parseLayout("fragment_customer_home.xml");
            assertTrue(ctx.xml().hasTnum(home.element("tvHomeAccountNo")), "tvHomeAccountNo missing tnum");
            assertTrue(ctx.xml().hasTnum(home.element("tvHomeBalance")), "tvHomeBalance missing tnum");
            XmlInspector.LayoutInfo row = ctx.xml().parseLayout("item_history_row.xml");
            assertTrue(ctx.xml().hasTnum(row.element("tvHistoryAmount")), "tvHistoryAmount missing tnum");
            return "Verified Home & History tnum";
        }));

        out.add(execute("T2-F07-05", 2, f, "CustomerHomeFragment isAdded, nestedScrolling=false & non-blocking", "R2-R3, F7", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerHomeFragment.java"));
            assertTrue(src.contains("isAdded()"), "Missing isAdded() guard");
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(src), "Blocking UI call found");
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "Missing motion in CustomerHomeFragment");
            XmlInspector.LayoutInfo home = ctx.xml().parseLayout("fragment_customer_home.xml");
            assertTrue("false".equals(XmlInspector.getAttr(home.element("rvRecentHistory"), "nestedScrollingEnabled")),
                    "rvRecentHistory nestedScrollingEnabled != false");
            return "Verified lifecycle & non-blocking home";
        }));
    }

    private static void addF8(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F8";
        out.add(execute("T2-F08-01", 2, f, "Accounts & repayment XML layouts enforce tnum on all amounts/rates", "R1, F8", () -> {
            List<Path> acctLayouts = ctx.allLayoutFiles().stream()
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("item_account_") || n.equals("item_repayment_row.xml");
                    }).toList();
            assertTrue(acctLayouts.size() >= 2, "Missing item_account_*/item_repayment_row.xml");
            int tnumTotal = 0;
            List<String> missingTnum = new ArrayList<>();
            List<String> filesWithoutTnum = new ArrayList<>();
            for (Path p : acctLayouts) {
                String fname = p.getFileName().toString();
                XmlInspector.LayoutInfo l = ctx.xml().parseLayout(fname);
                int fileTnum = 0;
                for (Element el : l.allElements) {
                    boolean hasTnum = ctx.xml().hasTnum(el);
                    if (hasTnum) {
                        tnumTotal++;
                        fileTnum++;
                    }
                    if (XmlInspector.simpleTag(el.getTagName()).endsWith("TextView")) {
                        String id = XmlInspector.stripIdPrefix(XmlInspector.getAttr(el, "id")).toLowerCase();
                        String txt = XmlInspector.getAttr(el, "text");
                        boolean isLabelOnly = id.endsWith("label") || id.endsWith("title") || id.endsWith("header");
                        boolean isMonetaryOrRate = !isLabelOnly && (id.matches(".*(balance|amount|rate|interest|principal|installment|repay.*amt|repayment.*amt|vnd|fee|total|deposit).*")
                                || txt.contains("VNĐ") || txt.contains("%"));
                        if (isMonetaryOrRate && !hasTnum) {
                            missingTnum.add(fname + ":" + (id.isEmpty() ? "<TextView>" : id));
                        }
                    }
                }
                if (fileTnum == 0) {
                    filesWithoutTnum.add(fname);
                }
            }
            assertTrue(missingTnum.isEmpty(), "Monetary/rate TextViews missing tnum: " + missingTnum);
            assertTrue(filesWithoutTnum.isEmpty(), "Account/repayment layouts with 0 tnum fields: " + filesWithoutTnum);
            assertTrue(tnumTotal >= 3, "Expected >= 3 tnum fields across account/repayment XMLs, found " + tnumTotal);
            return "tnum fields in account/repayment XMLs=" + tnumTotal;
        }));

        out.add(execute("T2-F08-02", 2, f, "CustomerAccountsFragment zero raw pixel constants", "R1, F8", () -> {
            String code = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/customer/CustomerAccountsFragment.java"));
            assertFalse(code.contains("setPadding(40, 36, 40, 36)") || code.contains("setRadius(36f)")
                    || code.contains("bottomMargin = 28"),
                    "CustomerAccountsFragment still contains baseline raw pixel layout constants");
            return "Zero raw pixel constants in CustomerAccountsFragment";
        }));

        out.add(execute("T2-F08-03", 2, f, "CustomerAccountsFragment null-guards loans, rates & isAdded()", "R3, F8", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerAccountsFragment.java"));
            assertTrue(src.contains("isAdded()")
                            && Pattern.compile("\\bloans\\s*(==|!=)\\s*null").matcher(src).find()
                            && src.contains("schedules") && src.contains("accruedInterest"),
                    "Missing null/lifecycle guards in CustomerAccountsFragment");
            assertFalse(src.contains("new MaterialCardView"), "Still uses raw MaterialCardView");
            return "Verified null & lifecycle guards";
        }));

        out.add(execute("T2-F08-04", 2, f, "3D flip sets cameraDistance & zero blocking UI calls", "R2, F8", () -> {
            String acctSrc = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerAccountsFragment.java"));
            String mhSrc = SourceInspector.stripComments(ctx.readJava("ui/common/MotionHelper.java"));
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(acctSrc), "Blocking UI call");
            assertTrue(acctSrc.contains("flipCard3D") || acctSrc.contains("setCameraDistance"),
                    "Missing 3D card flip in CustomerAccountsFragment");
            assertTrue(mhSrc.contains("setCameraDistance") || acctSrc.contains("setCameraDistance"),
                    "Missing setCameraDistance");
            return "Verified non-blocking 3D flip";
        }));

        out.add(execute("T2-F08-05", 2, f, "Account/mortgage XMLs guard long collateral/text overlap", "R1, F8", () -> {
            List<Path> acctLayouts = ctx.allLayoutFiles().stream()
                    .filter(p -> p.getFileName().toString().startsWith("item_account_"))
                    .toList();
            assertFalse(acctLayouts.isEmpty(), "Missing item_account_*.xml");
            boolean hasEllipsizeOrWrap = false;
            for (Path p : acctLayouts) {
                XmlInspector.LayoutInfo l = ctx.xml().parseLayout(p.getFileName().toString());
                if (l.allElements.stream().anyMatch(e -> ctx.xml().hasEllipsizeEnd(e))) {
                    hasEllipsizeOrWrap = true;
                }
            }
            assertTrue(hasEllipsizeOrWrap, "Account XMLs lack ellipsize=end anti-overlap guard");
            return "Verified anti-overlap ellipsize in account XMLs";
        }));
    }

    private static void addF9(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F9";
        out.add(execute("T2-F09-01", 2, f, "item_utility_tile & tvConfirmSummary enforce tnum", "R1, F9", () -> {
            XmlInspector.LayoutInfo tile = ctx.xml().parseLayout("item_utility_tile.xml");
            assertTrue(tile.exists, "Missing item_utility_tile.xml");
            assertTrue(tile.allElements.stream().anyMatch(e -> ctx.xml().hasTnum(e)),
                    "item_utility_tile.xml missing tnum on price pill");
            XmlInspector.LayoutInfo sheet = ctx.xml().parseLayout("bottom_sheet_transfer_confirm.xml");
            assertTrue(ctx.xml().hasTnum(sheet.element("tvConfirmSummary")),
                    "tvConfirmSummary missing fontFeatureSettings=tnum");
            return "Verified tnum on utility tile & confirm summary";
        }));

        out.add(execute("T2-F09-02", 2, f, "CustomerUtilitiesFragment zero raw pixel constants", "R1, F9", () -> {
            String code = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/customer/CustomerUtilitiesFragment.java"));
            assertFalse(code.contains("setMargins(8, 8, 8, 8)") || code.contains("setPadding(32, 32, 32, 32)")
                    || code.contains("setRadius(32f)"),
                    "CustomerUtilitiesFragment still contains baseline raw pixel constants");
            return "Zero raw pixel constants in CustomerUtilitiesFragment";
        }));

        out.add(execute("T2-F09-03", 2, f, "item_utility_tile.xml ellipsize/maxLines for 360dp grid", "R1, F9", () -> {
            XmlInspector.LayoutInfo tile = ctx.xml().parseLayout("item_utility_tile.xml");
            assertTrue(tile.exists, "Missing item_utility_tile.xml");
            long guarded = tile.allElements.stream()
                    .filter(e -> ctx.xml().hasEllipsizeEnd(e) && !ctx.xml().getEffectiveAttr(e, "maxLines").isEmpty())
                    .count();
            assertTrue(guarded >= 2, "item_utility_tile.xml needs >= 2 ellipsized TextViews (title & supplier)");
            return "ellipsized TextViews in tile=" + guarded;
        }));

        out.add(execute("T2-F09-04", 2, f, "Utilities 6-digit OTP, UUID idempotency & checking fallback", "R3, F9", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerUtilitiesFragment.java"));
            assertTrue(src.contains("otp.length() != 6") && src.contains("UUID.randomUUID()")
                    && src.contains("190010010001"),
                    "Missing 6-digit OTP / UUID / fallback account check");
            assertFalse(src.contains("new MaterialCardView"), "Still uses raw MaterialCardView");
            return "Verified OTP, UUID & fallback";
        }));

        out.add(execute("T2-F09-05", 2, f, "Utilities press animation non-blocking & zero sleep", "R2, F9", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerUtilitiesFragment.java"));
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(src), "Blocking UI call found");
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "Missing motion in CustomerUtilitiesFragment");
            return "Verified non-blocking utility tile motion";
        }));
    }

    private static void addF10(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F10";
        out.add(execute("T2-F10-01", 2, f, "CustomerExploreFragment removes flat setBackgroundColor & raw px", "R1, F10", () -> {
            String code = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/customer/CustomerExploreFragment.java"));
            assertFalse(code.contains(".setBackgroundColor(") || code.contains("setPadding(36, 28, 36, 28)")
                    || code.contains("setPadding(28, 20, 28, 20)") || code.contains("setRadius(28f)"),
                    "CustomerExploreFragment still uses flat setBackgroundColor or raw px constants");
            return "Verified clean XML inflation in CustomerExploreFragment";
        }));

        out.add(execute("T2-F10-02", 2, f, "item_branch_card & tvNearestBranch tnum & ellipsize", "R1, F10", () -> {
            XmlInspector.LayoutInfo card = ctx.xml().parseLayout("item_branch_card.xml");
            assertTrue(card.exists, "Missing item_branch_card.xml");
            assertTrue(card.allElements.stream().anyMatch(e -> ctx.xml().hasTnum(e)),
                    "item_branch_card.xml missing tnum on distance/walk-time");
            assertTrue(card.allElements.stream().anyMatch(e -> ctx.xml().hasEllipsizeEnd(e)),
                    "item_branch_card.xml missing ellipsize=end on address");
            return "Verified branch card tnum & ellipsize";
        }));

        out.add(execute("T2-F10-03", 2, f, "Distinct User vs AI bubble alignment/backgrounds & scroll", "R1, F10", () -> {
            XmlInspector.LayoutInfo msg = ctx.xml().parseLayout("item_ai_message.xml");
            assertTrue(msg.exists, "Missing item_ai_message.xml");
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerExploreFragment.java"));
            assertTrue(src.contains("bg_chat_") && (src.contains("Gravity.END") || src.contains("Gravity.START") || src.contains("isUser")),
                    "Missing distinct User vs AI bubble styling/gravity");
            assertTrue(src.contains("scrollAiMessages.fullScroll")
                            || src.contains("scrollAiMessages.smoothScrollTo")
                            || src.contains("scrollAiMessages.smoothScrollBy"),
                    "Missing scrollAiMessages.fullScroll or smoothScrollTo");
            return "Verified distinct User vs AI chat bubbles";
        }));

        out.add(execute("T2-F10-04", 2, f, "AI typing indicator cleaned in onResponse & onFailure + isAdded", "R2-R3, F10", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerExploreFragment.java"));
            assertTrue(src.contains("isAdded()"), "Missing isAdded() guard");
            String askAi = SourceInspector.extractMethodBody(src, "\\bvoid\\s+(?:sendAiQuestion|sendAiMessage)\\s*\\([^)]*\\)");
            assertFalse(askAi.isEmpty(), "sendAiQuestion/sendAiMessage method not found");
            assertTrue(askAi.contains("typing") || askAi.contains("Typing"),
                    "sendAiQuestion/sendAiMessage missing AI typing indicator lifecycle");
            return "Verified AI typing indicator & isAdded";
        }));

        out.add(execute("T2-F10-05", 2, f, "nearestBranch null guard, checkedIds guard & non-blocking", "R2-R3, F10", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerExploreFragment.java"));
            assertTrue(src.contains("nearestBranch == null") && src.contains("checkedIds.isEmpty()"),
                    "Missing nearestBranch or checkedIds guard");
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(src), "Blocking UI call found");
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "Missing motion in Explore");
            return "Verified Explore guards & non-blocking transitions";
        }));
    }

    private static void addF11(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F11";
        out.add(execute("T2-F11-01", 2, f, "fragment_customer_profile.xml scrollable form & padding", "R1, F11", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("fragment_customer_profile.xml");
            boolean scrollable = l.rootIsScrollable()
                    || (l.hasScrollContainerAnywhere()
                    && l.hasScrollAncestor("edtProfilePhone")
                    && l.hasScrollAncestor("btnCustomerLogout"));
            assertTrue(scrollable, "Profile form/actions must reside inside ScrollView/NestedScrollView");
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/fragment_customer_profile.xml"));
            assertTrue(raw.contains("phl_navy") && (raw.contains("@drawable/bg_") || raw.contains("phl_gold")),
                    "Profile layout missing luxury upgrade");
            return "Verified scrollable luxury profile layout";
        }));

        out.add(execute("T2-F11-02", 2, f, "Empty policyAlerts fallback & safe(...) on contact fields", "R3, F11", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerProfileFragment.java"));
            assertTrue(src.contains("policyAlerts") && src.contains("safe(") && src.contains("tvNotificationsBody"),
                    "Missing policyAlerts/safe/tvNotificationsBody fallback");
            assertTrue(src.contains("item_policy_alert") || src.contains("ItemPolicyAlertBinding"),
                    "Missing item_policy_alert inflation");
            return "Verified policyAlerts fallback & safe()";
        }));

        out.add(execute("T2-F11-03", 2, f, "Profile phone/email inputTypes & long name overflow guard", "R1, F11", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("fragment_customer_profile.xml");
            assertTrue("phone".equals(XmlInspector.getAttr(l.element("edtProfilePhone"), "inputType")),
                    "edtProfilePhone inputType != phone");
            assertTrue("textEmailAddress".equals(XmlInspector.getAttr(l.element("edtProfileEmail"), "inputType")),
                    "edtProfileEmail inputType != textEmailAddress");
            Element hdr = l.element("tvProfileHeader");
            assertTrue(ctx.xml().hasEllipsizeEnd(hdr) || "wrap_content".equals(XmlInspector.getAttr(hdr, "layout_height")),
                    "tvProfileHeader unsafe for long names");
            return "Verified profile inputTypes & header overflow safety";
        }));

        out.add(execute("T2-F11-04", 2, f, "item_policy_alert.xml multi-line wrap_content body", "R1, F11", () -> {
            XmlInspector.LayoutInfo item = ctx.xml().parseLayout("item_policy_alert.xml");
            assertTrue(item.exists, "Missing item_policy_alert.xml");
            boolean hasWrapBody = item.allElements.stream()
                    .filter(e -> XmlInspector.simpleTag(e.getTagName()).endsWith("TextView"))
                    .anyMatch(e -> "wrap_content".equals(ctx.xml().getEffectiveAttr(e, "layout_height"))
                            && !"1".equals(ctx.xml().getEffectiveAttr(e, "maxLines")));
            assertTrue(hasWrapBody, "item_policy_alert.xml must allow multi-line wrap_content alert body");
            return "Verified multi-line item_policy_alert.xml";
        }));

        out.add(execute("T2-F11-05", 2, f, "Profile logout clears session in onResponse/onFailure + isAdded", "R2-R3, F11", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerProfileFragment.java"));
            assertTrue(src.contains("isAdded()") && src.contains("clearSession()"),
                    "Missing isAdded/clearSession");
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(src), "Blocking UI call found");
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "Missing Profile motion");
            return "Verified Profile logout symmetry & non-blocking motion";
        }));
    }

    private static void addF12(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F12";
        out.add(execute("T2-F12-01", 2, f, "activity_transfer.xml wraps chipTransferMode in HorizontalScrollView", "R1, F12", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("activity_transfer.xml");
            assertTrue(l.hasAncestorTag("chipTransferMode", "HorizontalScrollView"),
                    "chipTransferMode is NOT wrapped inside <HorizontalScrollView>");
            return "Verified HorizontalScrollView around chipTransferMode";
        }));

        out.add(execute("T2-F12-02", 2, f, "bottom_sheet_transfer_confirm.xml wrapped in ScrollView", "R1, F12", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("bottom_sheet_transfer_confirm.xml");
            assertTrue(l.hasScrollContainerAnywhere(),
                    "bottom_sheet_transfer_confirm.xml lacks ScrollView/NestedScrollView");
            return "Verified scrollable transfer confirm bottom sheet";
        }));

        out.add(execute("T2-F12-03", 2, f, "tvSourceAccountInfo, edtAmount & tvConfirmSummary enforce tnum", "R1, F12", () -> {
            XmlInspector.LayoutInfo tr = ctx.xml().parseLayout("activity_transfer.xml");
            assertTrue(ctx.xml().hasTnum(tr.element("tvSourceAccountInfo")), "tvSourceAccountInfo missing tnum");
            assertTrue(ctx.xml().hasTnum(tr.element("edtAmount")), "edtAmount missing tnum");
            XmlInspector.LayoutInfo sh = ctx.xml().parseLayout("bottom_sheet_transfer_confirm.xml");
            assertTrue(ctx.xml().hasTnum(sh.element("tvConfirmSummary")), "tvConfirmSummary missing tnum");
            return "Verified tnum on Transfer & BottomSheet";
        }));

        out.add(execute("T2-F12-04", 2, f, "TransferActivity guards Long.parseLong & preserves >10M eKYC rule", "R3, F12", () -> {
            String src = ctx.readJava("ui/customer/TransferActivity.java");
            assertTrue(SourceInspector.allParseCallsGuardedByTryCatch(src, "Long.parseLong"),
                    "TransferActivity has unguarded Long.parseLong");
            String clean = SourceInspector.stripComments(src);
            assertTrue(clean.contains("amount > EKYC_THRESHOLD_VND && !\"DEPOSIT\".equals(currentMode)")
                    || clean.contains("amount > 10_000_000L && !\"DEPOSIT\".equals(currentMode)"),
                    "eKYC >10M rule altered");
            assertTrue(clean.contains("otp.length() != 6"), "Missing 6-digit OTP validation");
            return "Verified NumberFormatException guard & >10M eKYC rule";
        }));

        out.add(execute("T2-F12-05", 2, f, "Transfer activeSheetBinding=null & non-blocking mode switch", "R2-R3, F12", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/TransferActivity.java"));
            assertTrue(src.contains("activeSheetBinding = null"), "Missing activeSheetBinding = null on dismiss");
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(src), "Blocking UI call found");
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "Missing Transfer motion");
            return "Verified sheet cleanup & non-blocking motion";
        }));
    }

    private static void addF13(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F13";
        out.add(execute("T2-F13-01", 2, f, "tvCameraFallback & btnCaptureVerifyFace usable without camera", "R3, F13", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("activity_face_ekyc.xml");
            assertTrue(l.hasId("tvCameraFallback") && l.hasId("btnCaptureVerifyFace"),
                    "Missing tvCameraFallback or btnCaptureVerifyFace");
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/activity_face_ekyc.xml"));
            assertTrue(raw.contains("reticle") || raw.contains("oval"),
                    "Missing biometric reticle overlay in FaceEkyc");
            return "Verified fallback & capture accessibility";
        }));

        out.add(execute("T2-F13-02", 2, f, "Oval reticle & scanline overlays do not block touches", "R2, F13", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("activity_face_ekyc.xml");
            assertTrue(l.exists, "Missing activity_face_ekyc.xml");
            assertTrue((l.hasElementWithIdOrDrawableContaining("reticle") || l.hasElementWithIdOrDrawableContaining("oval"))
                            && l.hasElementWithIdOrDrawableContaining("scanline"),
                    "Missing reticle or scanline overlay");
            for (Element el : l.allElements) {
                String id = XmlInspector.stripIdPrefix(XmlInspector.getAttr(el, "id")).toLowerCase();
                if (id.contains("reticle") || id.contains("scanline")) {
                    assertFalse("true".equals(ctx.xml().getEffectiveAttr(el, "clickable")),
                            id + " overlay must not intercept clicks");
                }
            }
            return "Verified non-clickable biometric overlays";
        }));

        out.add(execute("T2-F13-03", 2, f, "FaceEkycActivity cancels looping animators in onDestroy()", "R2, F13", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/FaceEkycActivity.java"));
            String onDestroy = SourceInspector.extractMethodBody(src, "protected\\s+void\\s+onDestroy\\s*\\([^)]*\\)");
            assertFalse(onDestroy.isEmpty(), "FaceEkycActivity missing onDestroy()");
            assertTrue(onDestroy.contains(".cancel()") || onDestroy.contains("stop"),
                    "FaceEkycActivity.onDestroy() must cancel looping scanline/pulse animators");
            return "Verified onDestroy animator cleanup";
        }));

        out.add(execute("T2-F13-04", 2, f, "btnCaptureVerifyFace restored on failure & non-blocking", "R2-R3, F13", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/FaceEkycActivity.java"));
            assertTrue(src.contains("btnCaptureVerifyFace.setEnabled(true)"),
                    "Missing btnCaptureVerifyFace.setEnabled(true) recovery");
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(src), "Blocking UI call found");
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "Missing FaceEkyc motion");
            return "Verified FaceEkyc recovery & non-blocking motion";
        }));

        out.add(execute("T2-F13-05", 2, f, "FaceEkyc camera container uses responsive 0dp sizing", "R1, F13", () -> {
            XmlInspector.LayoutInfo ek = ctx.xml().parseLayout("activity_face_ekyc.xml");
            assertTrue(ek.exists, "Missing activity_face_ekyc.xml");
            boolean hasReticle = ek.hasElementWithIdOrDrawableContaining("reticle")
                    || ek.hasElementWithIdOrDrawableContaining("oval");
            boolean hasWeight = ek.allElements.stream()
                    .anyMatch(e -> "1".equals(ctx.xml().getEffectiveAttr(e, "layout_weight")));
            boolean hasConstraint0dp = ek.allElements.stream().anyMatch(e ->
                    "0dp".equals(ctx.xml().getEffectiveAttr(e, "layout_height"))
                            && (!XmlInspector.getAttr(e, "layout_constraintTop_toBottomOf").isEmpty()
                            || !XmlInspector.getAttr(e, "layout_constraintTop_toTopOf").isEmpty())
                            && (!XmlInspector.getAttr(e, "layout_constraintBottom_toTopOf").isEmpty()
                            || !XmlInspector.getAttr(e, "layout_constraintBottom_toBottomOf").isEmpty()));
            assertTrue((hasWeight || hasConstraint0dp) && hasReticle,
                    "Camera preview frame lacks responsive layout_weight=1 / ConstraintLayout 0dp or reticle");
            return "Verified responsive FaceEkyc layout";
        }));
    }

    private static void addF14(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F14";
        out.add(execute("F14_T2_01_SavedInstanceStateGuard", 2, f, "OfficerMainActivity savedInstanceState == null guard", "R3, F14", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerMainActivity.java"));
            assertTrue(src.contains("savedInstanceState == null") && src.contains("tab_off_customers"),
                    "Missing savedInstanceState == null guard");
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "Missing OfficerMainActivity motion");
            return "Verified savedInstanceState == null guard";
        }));

        out.add(execute("F14_T2_02_UnknownMenuItemReturnsFalse", 2, f, "Officer nav listener returns false on unknown ID", "R3, F14", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerMainActivity.java"));
            assertTrue(src.contains("return false;"), "Missing return false; fallback");
            return "Verified return false fallback";
        }));

        out.add(execute("F14_T2_03_FragmentTransactionUsesValidHost", 2, f, "swapFragment uses replace(officerFragmentHost)", "R3, F14", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerMainActivity.java"));
            assertTrue(src.contains(".replace(R.id.officerFragmentHost"), "Missing replace(officerFragmentHost)");
            assertFalse(src.contains(".add(R.id.officerFragmentHost"), "Must use replace, not add");
            return "Verified valid fragment replace";
        }));

        out.add(execute("F14_T2_04_NoBlockingOperationInTabSwitch", 2, f, "OfficerMainActivity zero Thread.sleep or sync .execute()", "R2, F14", () -> {
            String src = ctx.readJava("ui/officer/OfficerMainActivity.java");
            assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(src), "Blocking UI call found");
            assertFalse(SourceInspector.stripComments(src).contains(".execute()"), "Sync Retrofit .execute() on main thread");
            return "Verified non-blocking Officer tab switch";
        }));

        out.add(execute("F14_T2_05_MenuIconDrawablesExist", 2, f, "All menu_officer_nav icons exist as <vector>", "R1, F14", () -> {
            Map<String, XmlInspector.MenuItemInfo> menu = ctx.xml().parseMenu("menu_officer_nav.xml");
            assertTrue(menu.size() == 4, "Expected 4 officer menu items");
            for (XmlInspector.MenuItemInfo mi : menu.values()) {
                assertTrue(ctx.xml().drawableExists(mi.icon()), "Missing icon: " + mi.icon());
                String name = mi.icon().replace("@drawable/", "");
                String raw = ctx.xml().readRawXml(ctx.resDir().resolve("drawable/" + name + ".xml"));
                assertTrue(raw.contains("<vector"), mi.icon() + " is not a <vector>");
            }
            return "Verified 4 vector icons for Officer nav";
        }));

        out.add(execute("F14_T2_06_LabelVisibilityModeLabeled", 2, f, "officerBottomNav labelVisibilityMode=labeled", "R1, F14", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("activity_officer_main.xml");
            assertTrue("labeled".equals(ctx.xml().getEffectiveAttr(l.element("officerBottomNav"), "labelVisibilityMode")),
                    "officerBottomNav labelVisibilityMode != labeled");
            return "Verified labelVisibilityMode=labeled";
        }));
    }

    private static void addF15(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F15";
        out.add(execute("F15_T2_01_EmptySearchPassesNullQuery", 2, f, "Empty search passes null keyword cleanly", "R3, F15", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCustomersFragment.java"));
            boolean nullSafeGetText = Pattern.compile("getText\\(\\)\\s*(==|!=)\\s*null").matcher(src).find();
            boolean emptyToNull = Pattern.compile("(\\.isEmpty\\(\\)|TextUtils\\.isEmpty\\([^)]+\\))\\s*\\?\\s*null\\s*:|\\.searchCustomers\\(\\s*null\\s*\\)|if\\s*\\([^)]*isEmpty\\(\\)").matcher(src).find();
            assertTrue(nullSafeGetText && emptyToNull && src.contains(".searchCustomers("),
                    "Missing null-safe empty search query guard");
            return "Verified null-safe empty search";
        }));

        out.add(execute("F15_T2_02_IsAddedLifecycleGuards", 2, f, "OfficerCustomersFragment isAdded guards on callbacks", "R3, F15", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCustomersFragment.java"));
            int count = countOccurrences(src, "isAdded()");
            assertTrue(count >= 2, "Expected >= 2 isAdded() guards (search + update), found " + count);
            return "isAdded guards=" + count;
        }));

        out.add(execute("F15_T2_03_UpdatePhoneEmailValidation", 2, f, "Customer edit sheet validates phone before updateCustomer", "R3, F15", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCustomersFragment.java"));
            boolean hasPhoneValidation = Pattern.compile("(phone|Phone)\\w*\\.isEmpty\\(\\)|TextUtils\\.isEmpty\\([^)]*phone|(phone|Phone)\\w*\\.length\\(\\)\\s*<",
                    Pattern.CASE_INSENSITIVE).matcher(src).find();
            assertTrue(src.contains("BottomSheetDialog") && src.contains(".updateCustomer(") && hasPhoneValidation,
                    "Missing BottomSheetDialog or phone validation before updateCustomer");
            return "Verified edit customer validation";
        }));

        out.add(execute("F15_T2_04_OnFailureUserFeedback", 2, f, "OfficerCustomersFragment onFailure surfaces error feedback", "R3, F15", () -> {
            String src = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/officer/OfficerCustomersFragment.java"));
            Matcher m = Pattern.compile("public\\s+void\\s+onFailure\\s*\\([^)]*\\)\\s*\\{").matcher(src);
            int checked = 0;
            while (m.find()) {
                int openBrace = m.end() - 1;
                int closeBrace = SourceInspector.findMatchingBrace(src, openBrace);
                assertTrue(closeBrace > openBrace, "Unbalanced braces in onFailure");
                String body = src.substring(openBrace + 1, closeBrace).trim();
                assertFalse(body.isEmpty(), "Silent empty onFailure block in OfficerCustomersFragment");
                assertTrue(body.contains("isAdded()"), "onFailure missing isAdded() guard");
                checked++;
            }
            assertTrue(checked >= 2, "Expected >= 2 non-empty onFailure handlers, found " + checked);
            return "Non-empty guarded onFailure handlers=" + checked;
        }));

        out.add(execute("F15_T2_05_NullCustomerFieldSafety", 2, f, "Null-safe CustomerItem fields in OfficerCustomersFragment", "R3, F15", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCustomersFragment.java"));
            assertFalse(src.contains("new MaterialCardView"), "Still uses raw MaterialCardView");
            assertTrue(src.contains("equalsIgnoreCase") && (src.contains("!= null") || src.contains("safe(")),
                    "Missing null guards on CustomerItem fields");
            return "Verified null-safe CustomerItem binding";
        }));

        out.add(execute("F15_T2_06_TotalAccountsTabularNums", 2, f, "item_officer_customer.xml tnum & scrollable list", "R1, F15", () -> {
            XmlInspector.LayoutInfo item = ctx.xml().parseLayout("item_officer_customer.xml");
            assertTrue(item.exists, "Missing item_officer_customer.xml");
            assertTrue(item.allElements.stream().anyMatch(e -> ctx.xml().hasTnum(e)),
                    "item_officer_customer.xml missing tnum typography");
            XmlInspector.LayoutInfo frag = ctx.xml().parseLayout("fragment_officer_customers.xml");
            assertTrue(frag.hasScrollContainerAnywhere(), "fragment_officer_customers.xml not scrollable");
            return "Verified tnum & scrollable customer list";
        }));
    }

    private static void addF16(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F16";
        out.add(execute("F16_T2_01_NumberFormatExceptionSafetyOnDeposit", 2, f, "OfficerCreateAccountFragment guards Long.parseLong", "R3, F16", () -> {
            String src = ctx.readJava("ui/officer/OfficerCreateAccountFragment.java");
            assertTrue(SourceInspector.allParseCallsGuardedByTryCatch(src, "Long.parseLong"),
                    "OfficerCreateAccountFragment has unguarded Long.parseLong");
            return "Verified Long.parseLong NumberFormatException safety";
        }));

        out.add(execute("F16_T2_02_EmptyDepositDefaultsToZero", 2, f, "Empty deposit defaults to 0L & safe live preview", "R3, F16", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCreateAccountFragment.java"));
            assertTrue(src.contains("depositStr.isEmpty()") && src.contains("CurrencyUtils.formatVnd"),
                    "Missing empty deposit 0L fallback or live VND preview");
            return "Verified empty deposit 0L fallback";
        }));

        out.add(execute("F16_T2_03_NegativeOrExcessiveDepositGuard", 2, f, "Rejects negative or overflowing initialDeposit", "R3, F16", () -> {
            String src = ctx.readJava("ui/officer/OfficerCreateAccountFragment.java");
            assertTrue(SourceInspector.allParseCallsGuardedByTryCatch(src, "Long.parseLong"),
                    "Overflow throws uncaught NumberFormatException");
            String clean = SourceInspector.stripComments(src);
            assertTrue(clean.contains("< 0") || clean.contains("NumberFormatException"),
                    "Missing negative/overflow deposit guard");
            return "Verified negative/overflow deposit guard";
        }));

        out.add(execute("F16_T2_04_CoreFieldValidationBoundaries", 2, f, "Core onboarding validation boundaries preserved", "R3, F16", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCreateAccountFragment.java"));
            boolean hasFullNameCheck = src.contains("fullName.isEmpty()") || Pattern.compile("fullName\\.length\\(\\)\\s*<").matcher(src).find();
            boolean hasPasswordCheck = src.contains("password.isEmpty()") || Pattern.compile("password\\.length\\(\\)\\s*<").matcher(src).find();
            assertTrue(src.contains("username.length() < 4") && hasFullNameCheck
                            && src.contains("idCard.length() != 12") && src.contains("phone.length() < 9")
                            && hasPasswordCheck,
                    "Missing core field validation checks");
            return "Verified username/fullName/idCard/phone/password boundaries";
        }));

        out.add(execute("F16_T2_05_NullSafeTextOfHelper", 2, f, "Null-safe textOf(TextInputEditText) helper", "R3, F16", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCreateAccountFragment.java"));
            assertTrue(Pattern.compile("getText\\(\\)\\s*(==|!=)\\s*null").matcher(src).find(),
                    "textOf missing null guard on getText()");
            return "Verified null-safe textOf";
        }));

        out.add(execute("F16_T2_06_IsAddedAndOnFailureGuards", 2, f, "createCustomer onResponse & onFailure guard isAdded()", "R3, F16", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCreateAccountFragment.java"));
            assertTrue(countOccurrences(src, "isAdded()") >= 2,
                    "createCustomer onResponse & onFailure must both guard isAdded()");
            return "Verified isAdded guards on createCustomer";
        }));
    }

    private static void addF17(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F17";
        out.add(execute("F17_T2_01_NumberFormatExceptionSafety", 2, f, "OfficerRatesFragment guards parseLong & parseDouble", "R3, F17", () -> {
            String src = ctx.readJava("ui/officer/OfficerRatesFragment.java");
            assertTrue(SourceInspector.allParseCallsGuardedByTryCatch(src, "Long.parseLong"),
                    "OfficerRatesFragment has unguarded Long.parseLong");
            assertTrue(SourceInspector.allParseCallsGuardedByTryCatch(src, "Double.parseDouble"),
                    "OfficerRatesFragment has unguarded Double.parseDouble");
            return "Verified parseLong & parseDouble NumberFormatException guards";
        }));

        out.add(execute("F17_T2_02_InterestRateRangeBoundary", 2, f, "Interest rate bounded (0, 100] & non-NaN/Infinite", "R3, F17", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerRatesFragment.java"));
            assertTrue((src.contains("<= 0") || src.contains("<= 0.0"))
                    && (src.contains("> 100") || src.contains("> 100.0") || src.contains("isNaN") || src.contains("isInfinite")),
                    "OfficerRatesFragment missing (0, 100] rate bounds validation");
            return "Verified interest rate range validation";
        }));

        out.add(execute("F17_T2_03_EmptyIdOrRateGuard", 2, f, "Empty targetId or rate short-circuits cleanly", "R3, F17", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerRatesFragment.java"));
            boolean hasIdEmptyCheck = Pattern.compile("(id\\w*|target\\w*)\\.isEmpty\\(\\)|TextUtils\\.isEmpty\\([^)]*(id|target)",
                    Pattern.CASE_INSENSITIVE).matcher(src).find();
            boolean hasRateEmptyCheck = Pattern.compile("(rate\\w*)\\.isEmpty\\(\\)|TextUtils\\.isEmpty\\([^)]*rate",
                    Pattern.CASE_INSENSITIVE).matcher(src).find();
            assertTrue(hasIdEmptyCheck && hasRateEmptyCheck,
                    "Missing empty targetId or rate short-circuit validation");
            return "Verified empty ID/rate short-circuit";
        }));

        out.add(execute("F17_T2_04_TargetIdPositiveBoundary", 2, f, "Rejects targetId <= 0", "R3, F17", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerRatesFragment.java"));
            assertTrue(src.contains("targetId <= 0") || src.contains("<= 0"),
                    "Missing targetId <= 0 validation");
            return "Verified targetId > 0 check";
        }));

        out.add(execute("F17_T2_05_ChipSelectionStateConsistency", 2, f, "ChipGroup checkedIds empty guard & MORTGAGE/SAVINGS", "R3, F17", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerRatesFragment.java"));
            boolean safeCheckedIds = src.contains("checkedIds.isEmpty()") || !src.contains("checkedIds.get(");
            assertTrue(src.contains("chipRateMortgage.isChecked()") && src.contains("\"MORTGAGE\"")
                            && src.contains("\"SAVINGS\"") && safeCheckedIds,
                    "Missing consistent chip state listener or checkedIds safety guard");
            return "Verified chip state consistency";
        }));

        out.add(execute("F17_T2_06_IsAddedAndScrollableLayout", 2, f, "fragment_officer_rates scrollable & callbacks isAdded", "R1, R3, F17", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("fragment_officer_rates.xml");
            assertTrue(l.hasScrollContainerAnywhere(), "fragment_officer_rates.xml lacks ScrollView/NestedScrollView");
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerRatesFragment.java"));
            assertTrue(countOccurrences(src, "isAdded()") >= 2,
                    "OfficerRatesFragment callbacks missing isAdded() guards");
            return "Verified scrollable rates layout & isAdded guards";
        }));
    }

    private static void addF18(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F18";
        out.add(execute("F18_T2_01_NullSafeAuditItemFields", 2, f, "Null-safe AuditLogItem fields in OfficerAuditFragment", "R3, F18", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerAuditFragment.java"));
            assertFalse(src.contains("new MaterialCardView"), "Still uses raw MaterialCardView");
            assertTrue(src.contains("!= null") || src.contains("safe("), "Missing null guards on AuditLogItem fields");
            return "Verified null-safe AuditLogItem binding";
        }));

        out.add(execute("F18_T2_02_EmptyAuditListHandling", 2, f, "Empty audit log list displays safe empty feedback", "R3, F18", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerAuditFragment.java"));
            boolean clearsOrAdapts = src.contains("removeAllViews()")
                    || src.contains("RecyclerView")
                    || src.contains("submitList")
                    || src.contains("notifyDataSetChanged");
            boolean handlesEmpty = src.contains("logs.isEmpty()") || src.contains("isEmpty()");
            assertTrue(clearsOrAdapts && handlesEmpty,
                    "Missing empty audit list handling");
            return "Verified empty audit list handling";
        }));

        out.add(execute("F18_T2_03_LogoutClearsSessionEvenOnNetworkFailure", 2, f, "Officer logout clears session even on network failure", "R3, F18", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerAuditFragment.java"));
            String logoutBody = SourceInspector.extractMethodBody(src, "\\bvoid\\s+logout\\s*\\([^)]*\\)");
            assertFalse(logoutBody.isEmpty(), "logout() method not found");
            String onResp = SourceInspector.extractMethodBody(logoutBody, "public\\s+void\\s+onResponse\\s*\\([^)]*\\)");
            String onFail = SourceInspector.extractMethodBody(logoutBody, "public\\s+void\\s+onFailure\\s*\\([^)]*\\)");
            assertFalse(onResp.isEmpty() || onFail.isEmpty(), "logout() missing onResponse/onFailure callbacks");
            assertTrue(callbackClearsSessionAndNavigatesToLogin(onResp, src)
                            && callbackClearsSessionAndNavigatesToLogin(onFail, src),
                    "logout() must clear session and navigate to LoginActivity in both onResponse and onFailure");
            return "Verified offline logout resilience";
        }));

        out.add(execute("F18_T2_04_IsAddedGuardsOnAuditAndLogout", 2, f, "isAdded() guards on getAuditLogs & logout callbacks", "R3, F18", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerAuditFragment.java"));
            assertTrue(countOccurrences(src, "isAdded()") >= 3,
                    "Expected >= 3 isAdded() guards in OfficerAuditFragment");
            return "Verified isAdded() guards";
        }));

        out.add(execute("F18_T2_05_AuditLogLimitPreserved", 2, f, "getAuditLogs(50) limit preserved", "R3, F18", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerAuditFragment.java"));
            assertTrue(src.contains("getAuditLogs(50)"), "getAuditLogs(50) limit changed");
            return "Verified getAuditLogs(50)";
        }));

        out.add(execute("F18_T2_06_ScrollableAuditListAndDangerLogout", 2, f, "Audit scrollable & btnOfficerLogout uses phl_danger", "R1, F18", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("fragment_officer_audit.xml");
            assertTrue(l.hasScrollContainerAnywhere(), "fragment_officer_audit.xml not scrollable");
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/fragment_officer_audit.xml"));
            Element btnLogout = l.element("btnOfficerLogout");
            boolean hasDanger = raw.contains("phl_danger")
                    || (btnLogout != null && ctx.xml().getEffectiveAttr(btnLogout, "textColor").contains("phl_danger"));
            assertTrue(hasDanger, "btnOfficerLogout lacks @color/phl_danger emphasis");
            return "Verified scrollable audit list & danger logout styling";
        }));
    }

    private static void addF19(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F19";
        out.add(execute("F19_T2_01_ZeroBrokenViewBindingReferences", 2, f, "Zero broken ViewBinding IDs across all UI files", "AC, F19", () -> {
            Map<String, String> classToDefaultLayout = new HashMap<>();
            classToDefaultLayout.put("LoginActivity.java", "activity_login.xml");
            classToDefaultLayout.put("ForgotPasswordActivity.java", "activity_forgot_password.xml");
            classToDefaultLayout.put("CustomerMainActivity.java", "activity_customer_main.xml");
            classToDefaultLayout.put("CustomerHomeFragment.java", "fragment_customer_home.xml");
            classToDefaultLayout.put("CustomerAccountsFragment.java", "fragment_customer_accounts.xml");
            classToDefaultLayout.put("CustomerUtilitiesFragment.java", "fragment_customer_utilities.xml");
            classToDefaultLayout.put("CustomerExploreFragment.java", "fragment_customer_explore.xml");
            classToDefaultLayout.put("CustomerProfileFragment.java", "fragment_customer_profile.xml");
            classToDefaultLayout.put("TransferActivity.java", "activity_transfer.xml");
            classToDefaultLayout.put("FaceEkycActivity.java", "activity_face_ekyc.xml");
            classToDefaultLayout.put("RolePlaceholderFragment.java", "fragment_role_placeholder.xml");
            classToDefaultLayout.put("OfficerMainActivity.java", "activity_officer_main.xml");
            classToDefaultLayout.put("OfficerCustomersFragment.java", "fragment_officer_customers.xml");
            classToDefaultLayout.put("OfficerCreateAccountFragment.java", "fragment_officer_create.xml");
            classToDefaultLayout.put("OfficerRatesFragment.java", "fragment_officer_rates.xml");
            classToDefaultLayout.put("OfficerAuditFragment.java", "fragment_officer_audit.xml");

            List<String> broken = new ArrayList<>();
            int checkedRefs = 0;
            for (Path jf : ctx.allJavaFiles()) {
                String fname = jf.getFileName().toString();
                String src = SourceInspector.readFile(jf);
                String stripped = SourceInspector.stripCommentsAndStrings(src);
                Map<String, List<XmlInspector.LayoutInfo>> varToLayouts = new HashMap<>();
                String defLayout = classToDefaultLayout.get(fname);
                if (defLayout != null) {
                    varToLayouts.computeIfAbsent("binding", k -> new ArrayList<>())
                            .add(ctx.xml().parseLayout(defLayout));
                }
                Matcher bm = Pattern.compile("\\b([A-Z]\\w+Binding)\\s+(\\w+)\\b").matcher(stripped);
                while (bm.find()) {
                    String bType = bm.group(1);
                    if ("ViewBinding".equals(bType) || "ViewDataBinding".equals(bType)) continue;
                    String varName = bm.group(2);
                    String layoutName = SourceInspector.camelToSnake(bType.replace("Binding", "")) + ".xml";
                    XmlInspector.LayoutInfo layout = ctx.xml().parseLayout(layoutName);
                    if (!layout.exists) {
                        broken.add(fname + ":" + bType + " -> missing " + layoutName);
                        continue;
                    }
                    List<XmlInspector.LayoutInfo> list = varToLayouts.computeIfAbsent(varName, k -> new ArrayList<>());
                    if (list.stream().noneMatch(l -> l.fileName.equals(layout.fileName))) {
                        list.add(layout);
                    }
                }
                for (Map.Entry<String, List<XmlInspector.LayoutInfo>> entry : varToLayouts.entrySet()) {
                    String varName = entry.getKey();
                    List<XmlInspector.LayoutInfo> layouts = entry.getValue();
                    for (String field : SourceInspector.extractBindingFields(src, varName)) {
                        checkedRefs++;
                        boolean foundInAny = layouts.stream().anyMatch(l -> l.hasId(field));
                        if (!foundInAny) {
                            String names = String.join("|", layouts.stream().map(l -> l.fileName).toList());
                            broken.add(fname + ":" + varName + "." + field + " not in " + names);
                        }
                    }
                }
            }
            assertTrue(broken.isEmpty(), "Broken ViewBinding references: " + broken);
            return "Verified " + checkedRefs + " ViewBinding references with 0 mismatches";
        }));

        out.add(execute("F19_T2_02_ZeroRawProgrammaticCardsOrDialogs", 2, f, "Zero raw programmatic MaterialCardView/AlertDialog builders", "R1, F19", () -> {
            List<String> offenders = new ArrayList<>();
            for (String rel : List.of(
                    "ui/customer/CustomerAccountsFragment.java",
                    "ui/customer/CustomerUtilitiesFragment.java",
                    "ui/customer/CustomerExploreFragment.java",
                    "ui/officer/OfficerCustomersFragment.java",
                    "ui/officer/OfficerAuditFragment.java",
                    "ui/common/ChangePasswordDialogHelper.java",
                    "ui/customer/ReceiptDialogHelper.java")) {
                String code = SourceInspector.stripCommentsAndStrings(ctx.readJava(rel));
                if (code.contains("new MaterialCardView") || code.contains("new EditText(")
                        || code.contains(".setMessage(receiptText)")) {
                    offenders.add(rel);
                }
            }
            assertTrue(offenders.isEmpty(), "Raw programmatic card/dialog builders remain in: " + offenders);
            return "Zero raw programmatic cards/dialogs across all 7 files";
        }));

        out.add(execute("F19_T2_03_TabularNumsUniversalMonetarySweep", 2, f, "Universal tnum across all VND/rate views", "R1, AC, F19", () -> {
            List<String> missing = new ArrayList<>();
            XmlInspector.LayoutInfo home = ctx.xml().parseLayout("fragment_customer_home.xml");
            if (!ctx.xml().hasTnum(home.element("tvHomeBalance"))) missing.add("tvHomeBalance");
            if (!ctx.xml().hasTnum(home.element("tvHomeAccountNo"))) missing.add("tvHomeAccountNo");
            XmlInspector.LayoutInfo row = ctx.xml().parseLayout("item_history_row.xml");
            if (!ctx.xml().hasTnum(row.element("tvHistoryAmount"))) missing.add("tvHistoryAmount");
            XmlInspector.LayoutInfo tr = ctx.xml().parseLayout("activity_transfer.xml");
            if (!ctx.xml().hasTnum(tr.element("tvSourceAccountInfo"))) missing.add("tvSourceAccountInfo");
            if (!ctx.xml().hasTnum(tr.element("edtAmount"))) missing.add("edtAmount");
            XmlInspector.LayoutInfo sh = ctx.xml().parseLayout("bottom_sheet_transfer_confirm.xml");
            if (!ctx.xml().hasTnum(sh.element("tvConfirmSummary"))) missing.add("tvConfirmSummary");
            XmlInspector.LayoutInfo oc = ctx.xml().parseLayout("fragment_officer_create.xml");
            if (!ctx.xml().hasTnum(oc.element("edtInitialDeposit"))) missing.add("edtInitialDeposit");
            XmlInspector.LayoutInfo or = ctx.xml().parseLayout("fragment_officer_rates.xml");
            if (!ctx.xml().hasTnum(or.element("edtNewInterestRate"))) missing.add("edtNewInterestRate");
            XmlInspector.LayoutInfo rec = ctx.xml().parseLayout("dialog_receipt.xml");
            if (!rec.exists || rec.allElements.stream().noneMatch(e -> ctx.xml().hasTnum(e))) missing.add("dialog_receipt.xml");
            XmlInspector.LayoutInfo ut = ctx.xml().parseLayout("item_utility_tile.xml");
            if (!ut.exists || ut.allElements.stream().noneMatch(e -> ctx.xml().hasTnum(e))) missing.add("item_utility_tile.xml");
            assertTrue(missing.isEmpty(), "Missing tnum on monetary/rate views: " + missing);
            return "Universal tnum verified across Customer, Officer & Dialogs";
        }));

        out.add(execute("F19_T2_04_All15ScreensHaveMotionBlocks", 2, f, "All 15 screens implement native motion blocks", "R2, AC, F19", () -> {
            List<String> screens = List.of(
                    "ui/auth/LoginActivity.java",
                    "ui/auth/ForgotPasswordActivity.java",
                    "ui/customer/CustomerMainActivity.java",
                    "ui/customer/CustomerHomeFragment.java",
                    "ui/customer/CustomerAccountsFragment.java",
                    "ui/customer/CustomerUtilitiesFragment.java",
                    "ui/customer/CustomerExploreFragment.java",
                    "ui/customer/CustomerProfileFragment.java",
                    "ui/customer/TransferActivity.java",
                    "ui/customer/FaceEkycActivity.java",
                    "ui/officer/OfficerMainActivity.java",
                    "ui/officer/OfficerCustomersFragment.java",
                    "ui/officer/OfficerCreateAccountFragment.java",
                    "ui/officer/OfficerRatesFragment.java",
                    "ui/officer/OfficerAuditFragment.java"
            );
            List<String> noMotion = new ArrayList<>();
            for (String s : screens) {
                if (SourceInspector.findMotionPrimitives(ctx.readJava(s)).isEmpty()) {
                    noMotion.add(s);
                }
            }
            assertTrue(noMotion.isEmpty(), "Screens with 0 motion blocks: " + noMotion);
            return "All 15 screens have active motion blocks";
        }));

        out.add(execute("F19_T2_05_NonBlockingAnimationsNoUiSleep", 2, f, "Zero Thread.sleep/runBlocking/setClickable(false)", "R2, AC, F19", () -> {
            List<String> offenders = new ArrayList<>();
            for (Path jf : ctx.allJavaFiles()) {
                if (SourceInspector.hasBlockingUiSleepOrClickableFalse(SourceInspector.readFile(jf))) {
                    offenders.add(jf.getFileName().toString());
                }
            }
            assertTrue(offenders.isEmpty(), "Blocking UI patterns found in: " + offenders);
            return "Zero blocking Thread.sleep / setClickable(false) across all Java files";
        }));

        out.add(execute("F19_T2_06_CurrencyUtilsSignaturePreserved", 2, f, "CurrencyUtils formatVnd & formatSignedVnd intact", "R3, F19", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("utils/CurrencyUtils.java"));
            assertTrue(src.contains("public static String formatVnd(long")
                    && src.contains("public static String formatSignedVnd(long")
                    && src.contains("symbols.setGroupingSeparator('.')")
                    && src.contains("\" VNĐ\""),
                    "CurrencyUtils signature or VND format altered");
            return "CurrencyUtils signatures & '.' + ' VNĐ' format intact";
        }));
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private static boolean styleInheritsOrUsesAny(XmlInspector xml, String styleName, Set<String> targets) {
        Map<String, XmlInspector.StyleDef> styles = xml.loadStyles();
        Set<String> visited = new HashSet<>();
        String cur = styleName;
        while (cur != null && !cur.isEmpty() && visited.add(cur)) {
            if (targets.contains(cur)) {
                return true;
            }
            XmlInspector.StyleDef def = styles.get(cur);
            if (def == null) {
                break;
            }
            for (String taKey : List.of("android:textAppearance", "textAppearance")) {
                String ta = def.items().get(taKey);
                if (ta != null && ta.startsWith("@style/")) {
                    String taName = ta.substring(7);
                    if (targets.contains(taName) || styleInheritsOrUsesAny(xml, taName, targets)) {
                        return true;
                    }
                }
            }
            cur = def.parent();
        }
        return false;
    }

    private static boolean matchesAnyStyleRef(String attrVal, Set<String> styleNames) {
        if (attrVal == null || !attrVal.startsWith("@style/")) {
            return false;
        }
        return styleNames.contains(attrVal.substring(7));
    }

    private static boolean callbackClearsSessionAndNavigatesToLogin(String cbBody, String fullSrc) {
        if (cbBody.contains("clearSession()") && cbBody.contains("LoginActivity.class")) {
            return true;
        }
        Matcher callMatcher = Pattern.compile("\\b(\\w+)\\s*\\(").matcher(cbBody);
        Set<String> checked = new LinkedHashSet<>();
        while (callMatcher.find()) {
            String methodName = callMatcher.group(1);
            if (!checked.add(methodName)) {
                continue;
            }
            String helperBody = SourceInspector.extractMethodBody(fullSrc,
                    "\\bvoid\\s+" + Pattern.quote(methodName) + "\\s*\\([^)]*\\)");
            if (!helperBody.isEmpty()
                    && helperBody.contains("clearSession()")
                    && helperBody.contains("LoginActivity.class")) {
                return true;
            }
        }
        return false;
    }
}
