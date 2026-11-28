package vn.edu.tdtu.phlbanking.e2e;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Pattern;

import static vn.edu.tdtu.phlbanking.e2e.TestResult.assertFalse;
import static vn.edu.tdtu.phlbanking.e2e.TestResult.assertTrue;
import static vn.edu.tdtu.phlbanking.e2e.TestResult.execute;

public final class Tier1FeatureTests {

    private Tier1FeatureTests() {
    }

    public static List<TestResult> runAll(E2ERunner.RunContext ctx) {
        List<TestResult> out = new ArrayList<>(112);
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
        out.add(execute("T1-F01-01", 1, f, "dimens.xml luxury dimension tokens", "R1, F1", () -> {
            Path p = ctx.resDir().resolve("values/dimens.xml");
            assertTrue(Files.isRegularFile(p), "Missing res/values/dimens.xml");
            Map<String, String> dimens = ctx.xml().loadDimens();
            assertTrue(dimens.size() >= 8, "Expected >= 8 <dimen> tokens, found " + dimens.size());
            String joined = String.join(",", dimens.keySet()).toLowerCase();
            assertTrue(joined.contains("radius") || joined.contains("corner"), "dimens.xml missing corner/radius token");
            assertTrue(joined.contains("elevation"), "dimens.xml missing elevation token");
            assertTrue(joined.contains("padding") || joined.contains("spacing") || joined.contains("space_"),
                    "dimens.xml missing padding/spacing token");
            assertTrue(joined.contains("stroke") || joined.contains("hairline") || joined.contains("divider"),
                    "dimens.xml missing stroke/hairline token");
            return "dimens count=" + dimens.size();
        }));

        out.add(execute("T1-F01-02", 1, f, "styles.xml TextAppearance & Widget.PHL styles", "R1, F1", () -> {
            assertTrue(Files.isRegularFile(ctx.resDir().resolve("values/styles.xml")), "Missing res/values/styles.xml");
            Map<String, XmlInspector.StyleDef> styles = ctx.xml().loadStyles();
            long textApps = styles.keySet().stream().filter(k -> k.startsWith("TextAppearance.PHL.")).count();
            long widgetStyles = styles.keySet().stream().filter(k -> k.startsWith("Widget.PHL.")).count();
            assertTrue(textApps >= 4, "Expected >= 4 TextAppearance.PHL.* styles, found " + textApps);
            assertTrue(widgetStyles >= 4, "Expected >= 4 Widget.PHL.* styles (baseline had 2), found " + widgetStyles);
            return "TextAppearance=" + textApps + ", Widget=" + widgetStyles;
        }));

        out.add(execute("T1-F01-03", 1, f, "res/color/ stateful ColorStateList selectors", "R1, F1", () -> {
            List<Path> colorFiles = ctx.allColorSelectorFiles();
            assertTrue(colorFiles.size() >= 2, "Expected >= 2 ColorStateList XMLs in res/color/, found " + colorFiles.size());
            int validSelectors = 0;
            for (Path cf : colorFiles) {
                Optional<Document> doc = ctx.xml().parseXml(cf);
                if (doc.isPresent() && "selector".equals(doc.get().getDocumentElement().getTagName())) {
                    validSelectors++;
                }
            }
            assertTrue(validSelectors >= 2, "Expected >= 2 valid <selector> files in res/color/");
            return "selectors=" + validSelectors;
        }));

        out.add(execute("T1-F01-04", 1, f, "colors.xml luxury palette & expansion", "R1, F1", () -> {
            Map<String, String> colors = ctx.xml().loadColors();
            assertTrue("#0F1E36".equalsIgnoreCase(colors.get("phl_navy")), "phl_navy != #0F1E36");
            assertTrue("#0E9F6E".equalsIgnoreCase(colors.get("phl_emerald")), "phl_emerald != #0E9F6E");
            assertTrue("#B8860B".equalsIgnoreCase(colors.get("phl_gold")), "phl_gold != #B8860B");
            assertTrue("#C59B27".equalsIgnoreCase(colors.get("phl_gold_soft")), "phl_gold_soft != #C59B27");
            assertTrue("#F5F7FA".equalsIgnoreCase(colors.get("phl_bg")), "phl_bg != #F5F7FA");
            assertTrue(colors.size() >= 20, "Expected >= 20 <color> tokens, found " + colors.size());
            return "colors count=" + colors.size();
        }));

        out.add(execute("T1-F01-05", 1, f, "Card elevation >0dp <=8dp & luxury card styles", "R1, F1", () -> {
            Map<String, XmlInspector.StyleDef> styles = ctx.xml().loadStyles();
            boolean hasSubtleElevation = false;
            for (String styleName : styles.keySet()) {
                if (styleName.startsWith("Widget.PHL.Card")) {
                    String elev = ctx.xml().resolveStyleItem(styleName, "cardElevation");
                    OptionalDouble dp = ctx.xml().parseDp(elev);
                    if (dp.isPresent() && dp.getAsDouble() > 0.0 && dp.getAsDouble() <= 8.0) {
                        hasSubtleElevation = true;
                        break;
                    }
                }
            }
            assertTrue(hasSubtleElevation, "No Widget.PHL.Card* style sets subtle cardElevation (>0dp, <=8dp)");
            return "Verified subtle Widget.PHL.Card* elevation";
        }));

        out.add(execute("T1-F01-06", 1, f, "Theme.PHLBanking Material3 & core attributes preserved", "R1, F1", () -> {
            Map<String, XmlInspector.StyleDef> styles = ctx.xml().loadStyles();
            XmlInspector.StyleDef theme = styles.get("Theme.PHLBanking");
            assertTrue(theme != null, "Theme.PHLBanking missing");
            assertTrue(theme.parent().contains("Theme.Material3.Light.NoActionBar"), "Parent theme changed: " + theme.parent());
            assertTrue("@color/phl_navy".equals(theme.items().get("colorPrimary")), "colorPrimary changed");
            assertTrue("@color/phl_emerald".equals(theme.items().get("colorSecondary")), "colorSecondary changed");
            assertTrue("@color/phl_bg".equals(theme.items().get("android:colorBackground")), "colorBackground changed");
            return "Theme.PHLBanking preserved";
        }));
    }

    private static void addF2(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F2";
        out.add(execute("T1-F02-01", 1, f, "Midnight Navy gradient/layer-list drawables", "R1, F2", () -> {
            int count = 0;
            for (Path d : ctx.allDrawableFiles()) {
                String raw = ctx.xml().readRawXml(d);
                boolean hasGrad = raw.contains("<gradient") || raw.contains("<layer-list");
                boolean hasNavy = raw.contains("phl_navy") || raw.toUpperCase().contains("#0F1E36")
                        || raw.toUpperCase().contains("#091324") || raw.toUpperCase().contains("#1B2F52");
                if (hasGrad && hasNavy) {
                    count++;
                }
            }
            assertTrue(count >= 2, "Expected >= 2 Navy gradient/layer-list drawables, found " + count);
            return "navy gradient drawables=" + count;
        }));

        out.add(execute("T1-F02-02", 1, f, "Champagne Gold accent drawables", "R1, F2", () -> {
            int count = 0;
            for (Path d : ctx.allDrawableFiles()) {
                String raw = ctx.xml().readRawXml(d);
                boolean isShapeOrLayer = raw.contains("<shape") || raw.contains("<gradient") || raw.contains("<layer-list");
                boolean hasGold = raw.contains("phl_gold") || raw.toUpperCase().contains("#B8860B")
                        || raw.toUpperCase().contains("#C59B27");
                if (isShapeOrLayer && hasGold) {
                    count++;
                }
            }
            assertTrue(count >= 2, "Expected >= 2 Champagne Gold drawables, found " + count);
            return "gold drawables=" + count;
        }));

        out.add(execute("T1-F02-03", 1, f, "Emerald pill, surface card & shimmer drawables", "R1, F2", () -> {
            boolean hasEmeraldPill = false;
            boolean hasSurfaceCard = false;
            boolean hasShimmer = false;
            for (Path d : ctx.allDrawableFiles()) {
                String name = d.getFileName().toString().toLowerCase();
                String raw = ctx.xml().readRawXml(d);
                if (raw.contains("phl_emerald") && (raw.contains("<shape") || raw.contains("<layer-list"))) {
                    hasEmeraldPill = true;
                }
                if ((raw.contains("phl_surface") || raw.contains("phl_border")) && raw.contains("<shape")) {
                    hasSurfaceCard = true;
                }
                if (name.contains("shimmer") || (raw.contains("<gradient") && raw.contains("shimmer"))) {
                    hasShimmer = true;
                }
            }
            assertTrue(hasEmeraldPill, "Missing emerald pill/badge drawable");
            assertTrue(hasSurfaceCard, "Missing surface/border card drawable");
            assertTrue(hasShimmer, "Missing shimmer gradient drawable");
            return "Verified emerald, surface, and shimmer drawables";
        }));

        out.add(execute("T1-F02-04", 1, f, ">= 15 XML drawables including vectors & chat bubbles", "R1, F2", () -> {
            List<Path> drawables = ctx.allDrawableFiles();
            assertTrue(drawables.size() >= 15, "Expected >= 15 XML drawables, found " + drawables.size());
            long vectors = drawables.stream().filter(d -> ctx.xml().readRawXml(d).contains("<vector")).count();
            long chatBubbles = drawables.stream().filter(d -> d.getFileName().toString().contains("chat")).count();
            assertTrue(vectors >= 10, "Expected >= 10 vector icons, found " + vectors);
            assertTrue(chatBubbles >= 2, "Expected >= 2 chat bubble drawables, found " + chatBubbles);
            return "drawables=" + drawables.size() + ", vectors=" + vectors;
        }));

        out.add(execute("T1-F02-05", 1, f, "Biometric eKYC oval reticle drawable", "R1, F2", () -> {
            boolean found = false;
            for (Path d : ctx.allDrawableFiles()) {
                String name = d.getFileName().toString().toLowerCase();
                String raw = ctx.xml().readRawXml(d);
                if ((name.contains("reticle") || name.contains("oval") || name.contains("ekyc"))
                        && (raw.contains("oval") || raw.contains("<vector") || raw.contains("<layer-list"))
                        && (raw.contains("phl_gold") || raw.contains("phl_emerald") || raw.contains("phl_navy"))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Missing biometric eKYC oval reticle drawable");
            return "Found biometric oval reticle drawable";
        }));
    }

    private static void addF3(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F3";
        out.add(execute("T1-F03-01", 1, f, "MotionHelper.java exists in ui/common/", "R2, F3", () -> {
            Path p = ctx.javaFile("ui/common/MotionHelper.java");
            assertTrue(Files.isRegularFile(p), "Missing ui/common/MotionHelper.java");
            return p.toString();
        }));

        out.add(execute("T1-F03-02", 1, f, "res/anim/ contains >= 4 valid XML animation files", "R2, F3", () -> {
            List<Path> anims = ctx.allAnimFiles();
            assertTrue(anims.size() >= 4, "Expected >= 4 anim XML files, found " + anims.size());
            Set<String> validRoots = Set.of("set", "translate", "alpha", "scale", "rotate");
            int valid = 0;
            for (Path a : anims) {
                Optional<Document> doc = ctx.xml().parseXml(a);
                if (doc.isPresent() && validRoots.contains(doc.get().getDocumentElement().getTagName())) {
                    valid++;
                }
            }
            assertTrue(valid >= 4, "Expected >= 4 valid anim XML documents, found " + valid);
            return "valid anim XMLs=" + valid;
        }));

        out.add(execute("T1-F03-03", 1, f, "MotionHelper exposes required native motion methods", "R2, F3", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/common/MotionHelper.java"));
            assertFalse(src.isEmpty(), "MotionHelper.java missing or empty");
            String[] requiredMethods = {
                    "staggerEntrance", "staggerChildren", "animateVndCounter", "crossfadeText",
                    "applyTactilePress", "startShimmerSweep", "stopShimmer", "flipCard3D",
                    "shakeError", "pulseView", "startScanlineLoop", "animateTabSelection",
                    "applyFragmentTransition", "applyActivityTransition"
            };
            List<String> missing = new ArrayList<>();
            for (String m : requiredMethods) {
                if (!Pattern.compile("public\\s+static\\s+\\w+\\s+" + m + "\\s*\\(").matcher(src).find()) {
                    missing.add(m);
                }
            }
            assertTrue(missing.isEmpty(), "MotionHelper missing public static methods: " + missing);
            assertTrue(src.contains("ObjectAnimator") && src.contains("ValueAnimator"),
                    "MotionHelper must use ObjectAnimator & ValueAnimator");
            return "All 14 MotionHelper APIs verified";
        }));

        out.add(execute("T1-F03-04", 1, f, "MotionHelper.animateVndCounter uses ValueAnimator & CurrencyUtils", "R2, F3", () -> {
            String src = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/common/MotionHelper.java"));
            assertFalse(src.isEmpty(), "MotionHelper.java missing");
            String body = SourceInspector.extractMethodBody(src, "public\\s+static\\s+[^\\n]*\\banimateVndCounter\\s*\\([^)]*\\)");
            assertFalse(body.isEmpty(), "animateVndCounter method not found");
            assertTrue(body.contains("ValueAnimator"), "animateVndCounter must use ValueAnimator");
            assertTrue(body.contains("CurrencyUtils.formatVnd"), "animateVndCounter must format ticks via CurrencyUtils.formatVnd");
            assertTrue(body.contains(".start()"), "animateVndCounter must invoke .start() on animator");
            return "Verified animateVndCounter implementation";
        }));

        out.add(execute("T1-F03-05", 1, f, "MotionHelper.flipCard3D sets cameraDistance & rotationY", "R2, F3", () -> {
            String src = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/common/MotionHelper.java"));
            assertFalse(src.isEmpty(), "MotionHelper.java missing");
            String body = SourceInspector.extractMethodBody(src, "public\\s+static\\s+[^\\n]*\\bflipCard3D\\s*\\([^)]*\\)");
            assertFalse(body.isEmpty(), "flipCard3D method not found");
            assertTrue(body.contains("setCameraDistance"), "flipCard3D must configure setCameraDistance");
            assertTrue(body.contains("rotationY") || body.contains("ROTATION_Y"),
                    "flipCard3D must animate rotationY / View.ROTATION_Y");
            assertTrue(body.contains(".start()"), "flipCard3D must invoke .start() on animators");
            return "Verified flipCard3D implementation";
        }));
    }

    private static void addF4(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F4";
        out.add(execute("T1-F04-01", 1, f, "dialog_receipt.xml exists and inflated in ReceiptDialogHelper", "R1, F4", () -> {
            XmlInspector.LayoutInfo layout = ctx.xml().parseLayout("dialog_receipt.xml");
            assertTrue(layout.exists, "Missing res/layout/dialog_receipt.xml");
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/ReceiptDialogHelper.java"));
            assertTrue(src.contains("DialogReceiptBinding") || src.contains("R.layout.dialog_receipt"),
                    "ReceiptDialogHelper does not inflate dialog_receipt.xml");
            assertFalse(src.contains(".setMessage(receiptText)"), "ReceiptDialogHelper still uses plaintext setMessage(receiptText)");
            return "Verified dialog_receipt.xml inflation";
        }));

        out.add(execute("T1-F04-02", 1, f, "ReceiptDialogHelper luxury styling, share Intent & motion", "R1-R3, F4", () -> {
            String xmlRaw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/dialog_receipt.xml"));
            assertFalse(xmlRaw.isEmpty(), "Missing dialog_receipt.xml");
            assertTrue(xmlRaw.contains("phl_emerald") || xmlRaw.contains("phl_navy"), "dialog_receipt.xml lacks PHL luxury colors");
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/ReceiptDialogHelper.java"));
            assertTrue(src.contains("Intent.ACTION_SEND"), "ReceiptDialogHelper missing share Intent.ACTION_SEND");
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "ReceiptDialogHelper has zero motion blocks");
            return "Verified receipt luxury layout, share, and motion";
        }));

        out.add(execute("T1-F04-03", 1, f, "dialog_change_password.xml 3 TextInputLayouts & inflation", "R1, F4", () -> {
            XmlInspector.LayoutInfo layout = ctx.xml().parseLayout("dialog_change_password.xml");
            assertTrue(layout.exists, "Missing res/layout/dialog_change_password.xml");
            String xmlRaw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/dialog_change_password.xml"));
            int tilCount = countOccurrences(xmlRaw, "TextInputLayout");
            int toggleCount = countOccurrences(xmlRaw, "password_toggle");
            assertTrue(tilCount >= 3 && toggleCount >= 3,
                    "Expected >= 3 TextInputLayout with password_toggle (til=" + tilCount + ", toggle=" + toggleCount + ")");
            String code = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/common/ChangePasswordDialogHelper.java"));
            assertFalse(code.contains("new EditText("), "ChangePasswordDialogHelper still uses raw new EditText(");
            assertTrue(code.contains("DialogChangePasswordBinding") || code.contains("R.layout.dialog_change_password"),
                    "ChangePasswordDialogHelper does not inflate dialog_change_password.xml");
            return "til=" + tilCount + ", toggles=" + toggleCount;
        }));

        out.add(execute("T1-F04-04", 1, f, "ChangePasswordDialogHelper motion & changePassword API", "R2-R3, F4", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/common/ChangePasswordDialogHelper.java"));
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "ChangePasswordDialogHelper has 0 motion blocks");
            assertTrue(src.contains("changePassword("), "Missing changePassword API call");
            return "motion=" + SourceInspector.findMotionPrimitives(src);
        }));

        out.add(execute("T1-F04-05", 1, f, "SplashRouterActivity smooth transition & role routing", "R2-R3, F4", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/auth/SplashRouterActivity.java"));
            assertTrue(src.contains("MotionHelper.applyActivityTransition") || src.contains("overridePendingTransition"),
                    "SplashRouterActivity missing activity transition motion");
            assertTrue(src.contains("OFFICER") && src.contains("OfficerMainActivity")
                    && src.contains("CustomerMainActivity") && src.contains("LoginActivity"),
                    "SplashRouterActivity missing role routing");
            return "Verified SplashRouterActivity transition & routing";
        }));

        out.add(execute("T1-F04-06", 1, f, "Shared dialog signatures & RolePlaceholderFragment IDs preserved", "R3, F4", () -> {
            XmlInspector.LayoutInfo rp = ctx.xml().parseLayout("fragment_role_placeholder.xml");
            assertTrue(rp.exists, "Missing fragment_role_placeholder.xml");
            for (String id : List.of("tvTabRoleBadge", "tvTabTitle", "tvTabSummary", "btnTabLogout")) {
                assertTrue(rp.hasId(id), "fragment_role_placeholder.xml missing ID: " + id);
            }
            String receiptSrc = SourceInspector.stripComments(ctx.readJava("ui/customer/ReceiptDialogHelper.java"));
            assertTrue(Pattern.compile("public\\s+static\\s+void\\s+showReceipt\\s*\\(\\s*Context\\s+\\w+\\s*,\\s*String\\s+\\w+\\s*,\\s*String\\s+\\w+\\s*,\\s*long\\s+\\w+\\s*,\\s*long\\s+\\w+\\s*,\\s*String\\s+\\w+\\s*,\\s*String\\s+\\w+\\s*,\\s*String\\s+\\w+\\s*\\)")
                    .matcher(receiptSrc).find(), "ReceiptDialogHelper.showReceipt 8-arg signature changed");
            String cpSrc = SourceInspector.stripComments(ctx.readJava("ui/common/ChangePasswordDialogHelper.java"));
            assertTrue(Pattern.compile("public\\s+static\\s+void\\s+show\\s*\\(\\s*Context\\s+\\w+\\s*\\)").matcher(cpSrc).find(),
                    "ChangePasswordDialogHelper.show(Context) signature changed");
            return "Shared helper signatures & RolePlaceholder IDs intact";
        }));
    }

    private static void addF5(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F5";
        out.add(execute("T1-F05-01", 1, f, "activity_login.xml luxury header & rounded error banner", "R1, F5", () -> {
            XmlInspector.LayoutInfo layout = ctx.xml().parseLayout("activity_login.xml");
            assertTrue(layout.exists, "Missing activity_login.xml");
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/activity_login.xml"));
            assertTrue(raw.contains("@drawable/bg_"), "activity_login.xml lacks luxury @drawable/bg_* background");
            assertTrue(raw.contains("phl_gold"), "activity_login.xml lacks Champagne Gold accent");
            Element err = layout.element("tvErrorBanner");
            assertTrue(err != null && ctx.xml().getEffectiveAttr(err, "background").contains("@drawable/"),
                    "tvErrorBanner lacks rounded @drawable/ background");
            return "Verified activity_login.xml luxury styling";
        }));

        out.add(execute("T1-F05-02", 1, f, "LoginActivity >= 3 motion primitives", "R2, F5", () -> {
            String src = ctx.readJava("ui/auth/LoginActivity.java");
            Set<String> motions = SourceInspector.findMotionPrimitives(src);
            assertTrue(motions.size() >= 3, "Expected >= 3 motion primitives in LoginActivity, found " + motions);
            return "motions=" + motions;
        }));

        out.add(execute("T1-F05-03", 1, f, "activity_forgot_password.xml luxury header & step structure", "R1, F5", () -> {
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/activity_forgot_password.xml"));
            assertFalse(raw.isEmpty(), "Missing activity_forgot_password.xml");
            assertTrue(raw.contains("phl_navy") && (raw.contains("phl_gold") || raw.contains("@drawable/bg_")),
                    "activity_forgot_password.xml lacks luxury navy/gold structure");
            assertTrue(raw.contains("Step") || raw.contains("Bước") || countOccurrences(raw, "MaterialCardView") >= 2,
                    "activity_forgot_password.xml lacks step indicator or multi-card luxury structure");
            return "Verified activity_forgot_password.xml luxury structure";
        }));

        out.add(execute("T1-F05-04", 1, f, "ForgotPasswordActivity >= 2 motion primitives", "R2, F5", () -> {
            String src = ctx.readJava("ui/auth/ForgotPasswordActivity.java");
            Set<String> motions = SourceInspector.findMotionPrimitives(src);
            assertTrue(motions.size() >= 2, "Expected >= 2 motion primitives in ForgotPasswordActivity, found " + motions);
            return "motions=" + motions;
        }));

        out.add(execute("T1-F05-05", 1, f, "Auth flow native UI & API preservation", "R1-R3, F5", () -> {
            String loginSrc = SourceInspector.stripComments(ctx.readJava("ui/auth/LoginActivity.java"));
            String forgotSrc = SourceInspector.stripComments(ctx.readJava("ui/auth/ForgotPasswordActivity.java"));
            assertFalse(SourceInspector.hasWebViewReference(loginSrc) || SourceInspector.hasWebViewReference(forgotSrc),
                    "WebView detected in Auth flow");
            assertTrue(loginSrc.contains(".login("), "LoginActivity missing .login(...) call");
            assertTrue(forgotSrc.contains(".requestForgotOtp(") && forgotSrc.contains(".confirmForgotPassword("),
                    "ForgotPasswordActivity missing forgot-password API calls");
            assertFalse(SourceInspector.findMotionPrimitives(loginSrc).isEmpty()
                    || SourceInspector.findMotionPrimitives(forgotSrc).isEmpty(), "Missing Auth motion upgrades");
            return "Verified Auth APIs, 0 WebView, and motion";
        }));

        out.add(execute("T1-F05-06", 1, f, "Auth ViewBinding IDs & TextInputEditText contract preserved", "R3, F5", () -> {
            XmlInspector.LayoutInfo login = ctx.xml().parseLayout("activity_login.xml");
            for (String id : List.of("edtUsername", "edtPassword", "tvErrorBanner", "btnLogin",
                    "progressLogin", "tvForgotPassword", "btnDemoCustomer", "btnDemoOfficer")) {
                assertTrue(login.hasId(id), "activity_login.xml missing ID: " + id);
            }
            XmlInspector.LayoutInfo forgot = ctx.xml().parseLayout("activity_forgot_password.xml");
            for (String id : List.of("toolbarForgot", "edtForgotUser", "btnRequestOtp",
                    "edtForgotOtp", "edtForgotNewPass", "btnConfirmReset")) {
                assertTrue(forgot.hasId(id), "activity_forgot_password.xml missing ID: " + id);
            }
            for (String edt : List.of("edtForgotUser", "edtForgotOtp", "edtForgotNewPass")) {
                assertTrue("TextInputEditText".equals(forgot.tagOf(edt)),
                        edt + " must remain TextInputEditText, found " + forgot.tagOf(edt));
            }
            return "All 14 Auth IDs preserved with TextInputEditText contract";
        }));
    }

    private static void addF6(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F6";
        out.add(execute("T1-F06-01", 1, f, "customerBottomNav uses stateful ColorStateList selectors", "R1, F6", () -> {
            XmlInspector.LayoutInfo layout = ctx.xml().parseLayout("activity_customer_main.xml");
            assertTrue(layout.exists, "Missing activity_customer_main.xml");
            Element nav = layout.element("customerBottomNav");
            assertTrue(nav != null, "Missing customerBottomNav");
            String iconTint = ctx.xml().getEffectiveAttr(nav, "itemIconTint");
            String textColor = ctx.xml().getEffectiveAttr(nav, "itemTextColor");
            assertFalse("@color/phl_navy".equals(iconTint), "itemIconTint still flat @color/phl_navy");
            assertFalse("@color/phl_navy".equals(textColor), "itemTextColor still flat @color/phl_navy");
            assertFalse(ctx.xml().parseColorSelector(iconTint).isEmpty(), "Missing selector XML for " + iconTint);
            assertFalse(ctx.xml().parseColorSelector(textColor).isEmpty(), "Missing selector XML for " + textColor);
            return "iconTint=" + iconTint + ", textColor=" + textColor;
        }));

        out.add(execute("T1-F06-02", 1, f, "customerBottomNav top hairline border / elevation", "R1, F6", () -> {
            XmlInspector.LayoutInfo layout = ctx.xml().parseLayout("activity_customer_main.xml");
            Element nav = layout.element("customerBottomNav");
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/activity_customer_main.xml"));
            OptionalDouble elev = ctx.xml().parseDp(ctx.xml().getEffectiveAttr(nav, "elevation"));
            boolean hasElevation = elev.isPresent() && elev.getAsDouble() > 0;
            boolean hasHairline = raw.contains("phl_border") || raw.contains("phl_gold") || raw.contains("@drawable/");
            assertTrue(hasElevation || hasHairline, "customerBottomNav lacks hairline divider or elevation > 0dp");
            return "Verified customerBottomNav border/elevation";
        }));

        out.add(execute("T1-F06-03", 1, f, "CustomerMainActivity swapFragment smooth transition", "R2, F6", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerMainActivity.java"));
            assertTrue(src.contains("setCustomAnimations") || src.contains("MotionHelper.applyFragmentTransition"),
                    "CustomerMainActivity.swapFragment missing fragment transition animation");
            return "Verified CustomerMainActivity fragment transition";
        }));

        out.add(execute("T1-F06-04", 1, f, "CustomerMainActivity tab selection micro-bounce", "R2, F6", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerMainActivity.java"));
            assertTrue(src.contains("MotionHelper.animateTabSelection")
                    || src.contains("ObjectAnimator") || src.contains(".animate()"),
                    "CustomerMainActivity missing tab micro-bounce animation");
            return "Verified tab micro-bounce animation";
        }));

        out.add(execute("T1-F06-05", 1, f, "CustomerMainActivity selectTab, 5 fragments & 0 WebView", "R1-R3, F6", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerMainActivity.java"));
            assertFalse(SourceInspector.hasWebViewReference(src), "WebView found");
            assertTrue(src.contains("public void selectTab(int"), "Missing public void selectTab(int)");
            for (String frag : List.of("CustomerHomeFragment", "CustomerAccountsFragment",
                    "CustomerUtilitiesFragment", "CustomerExploreFragment", "CustomerProfileFragment")) {
                assertTrue(src.contains(frag), "Missing fragment wiring: " + frag);
            }
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "Missing motion primitives");
            return "Verified selectTab + 5 fragments + motion";
        }));

        out.add(execute("T1-F06-06", 1, f, "Customer shell ViewBinding & 5 menu IDs preserved", "R3, F6", () -> {
            XmlInspector.LayoutInfo layout = ctx.xml().parseLayout("activity_customer_main.xml");
            assertTrue(layout.hasId("customerFragmentHost") && layout.hasId("customerBottomNav"),
                    "Missing customerFragmentHost or customerBottomNav");
            Map<String, XmlInspector.MenuItemInfo> menu = ctx.xml().parseMenu("menu_customer_nav.xml");
            for (String id : List.of("tab_cust_home", "tab_cust_accounts", "tab_cust_utilities",
                    "tab_cust_explore", "tab_cust_profile")) {
                assertTrue(menu.containsKey(id), "menu_customer_nav.xml missing " + id);
            }
            return "Verified 2 shell IDs + 5 menu IDs";
        }));
    }

    private static void addF7(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F7";
        out.add(execute("T1-F07-01", 1, f, "fragment_customer_home.xml luxury hero & quick action icons", "R1, F7", () -> {
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/fragment_customer_home.xml"));
            assertTrue(raw.contains("@drawable/bg_"), "fragment_customer_home.xml lacks @drawable/bg_* luxury hero");
            assertTrue(raw.contains("phl_gold"), "fragment_customer_home.xml lacks gold accent");
            XmlInspector.LayoutInfo layout = ctx.xml().parseLayout("fragment_customer_home.xml");
            int iconBtns = 0;
            for (String id : List.of("btnQuickTransfer", "btnQuickDepositWithdraw", "btnQuickGateway")) {
                Element el = layout.element(id);
                if (el != null && (!ctx.xml().getEffectiveAttr(el, "icon").isEmpty()
                        || !ctx.xml().getEffectiveAttr(el, "drawableTop").isEmpty())) {
                    iconBtns++;
                }
            }
            assertTrue(iconBtns >= 3 || countOccurrences(raw, "@drawable/ic_") >= 3,
                    "Quick action buttons lack vector icons");
            return "Verified home hero gradient, gold accent & quick action icons";
        }));

        out.add(execute("T1-F07-02", 1, f, "item_history_row.xml leading direction badge & card polish", "R1, F7", () -> {
            XmlInspector.LayoutInfo row = ctx.xml().parseLayout("item_history_row.xml");
            assertTrue(row.exists, "Missing item_history_row.xml");
            boolean hasImageView = row.allElements.stream()
                    .anyMatch(e -> "ImageView".equals(XmlInspector.simpleTag(e.getTagName())));
            assertTrue(hasImageView, "item_history_row.xml missing leading direction ImageView badge");
            return "Verified direction icon badge in item_history_row.xml";
        }));

        out.add(execute("T1-F07-03", 1, f, "CustomerHomeFragment animated VND balance counter", "R2, F7", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerHomeFragment.java"));
            assertTrue(src.contains("animateVndCounter") || src.contains("ValueAnimator"),
                    "CustomerHomeFragment missing animated VND balance counter");
            return "Verified animated VND counter in CustomerHomeFragment";
        }));

        out.add(execute("T1-F07-04", 1, f, "CustomerHomeFragment >= 2 additional motion blocks", "R2, F7", () -> {
            String src = ctx.readJava("ui/customer/CustomerHomeFragment.java");
            Set<String> motions = SourceInspector.findMotionPrimitives(src);
            assertTrue(motions.size() >= 3,
                    "Expected >= 3 total motion blocks (counter + 2 more), found " + motions);
            return "motions=" + motions;
        }));

        out.add(execute("T1-F07-05", 1, f, "HistoryAdapter badge tint, receipt & APIs intact", "R1-R3, F7", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerHomeFragment.java"));
            assertFalse(SourceInspector.hasWebViewReference(src), "WebView found");
            assertTrue(src.contains("phl_emerald") && src.contains("phl_danger"),
                    "Missing semantic emerald/danger direction tint");
            assertTrue(src.contains("ReceiptDialogHelper.showReceipt"), "Missing ReceiptDialogHelper.showReceipt");
            assertTrue(src.contains(".getAccounts()") && src.contains(".getHistory()"),
                    "Missing getAccounts/getHistory calls");
            return "Verified HistoryAdapter semantic tint, receipt & APIs";
        }));

        out.add(execute("T1-F07-06", 1, f, "Home & History ViewBinding IDs & btnToggleBalance type preserved", "R3, F7", () -> {
            XmlInspector.LayoutInfo home = ctx.xml().parseLayout("fragment_customer_home.xml");
            for (String id : List.of("tvHomeGreeting", "tvHomeAccountNo", "tvHomeBalance",
                    "btnToggleBalance", "btnQuickTransfer", "btnQuickDepositWithdraw",
                    "btnQuickGateway", "tvHistoryEmpty", "rvRecentHistory")) {
                assertTrue(home.hasId(id), "fragment_customer_home.xml missing " + id);
            }
            String toggleTag = home.tagOf("btnToggleBalance");
            assertFalse("ImageButton".equals(toggleTag), "btnToggleBalance must NOT be ImageButton");
            XmlInspector.LayoutInfo row = ctx.xml().parseLayout("item_history_row.xml");
            for (String id : List.of("tvHistoryTitle", "tvHistoryMeta", "tvHistoryAmount")) {
                assertTrue(row.hasId(id), "item_history_row.xml missing " + id);
            }
            return "All 12 Home/History IDs preserved";
        }));
    }

    private static void addF8(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F8";
        out.add(execute("T1-F08-01", 1, f, "fragment_customer_accounts.xml luxury Navy/Gold header", "R1, F8", () -> {
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/fragment_customer_accounts.xml"));
            assertTrue(raw.contains("phl_navy") && (raw.contains("phl_gold") || raw.contains("@drawable/bg_")),
                    "fragment_customer_accounts.xml lacks luxury Navy/Gold header");
            return "Verified fragment_customer_accounts.xml luxury header";
        }));

        out.add(execute("T1-F08-02", 1, f, "Accounts uses XML item layouts & zero raw MaterialCardView", "R1, F8", () -> {
            long accountLayouts = ctx.allLayoutFiles().stream()
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("item_account_") || n.equals("item_repayment_row.xml");
                    }).count();
            assertTrue(accountLayouts >= 2, "Expected >= 2 item_account_*/item_repayment_row XML layouts, found " + accountLayouts);
            String code = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/customer/CustomerAccountsFragment.java"));
            assertFalse(code.contains("new MaterialCardView"), "CustomerAccountsFragment still constructs raw new MaterialCardView");
            return "account XML layouts=" + accountLayouts;
        }));

        out.add(execute("T1-F08-03", 1, f, "Distinct Checking/Savings/Mortgage XML cards & status pills", "R1, F8", () -> {
            boolean hasAccountCardLayout = false, hasRepayment = false;
            StringBuilder accountXmls = new StringBuilder();
            for (Path p : ctx.allLayoutFiles()) {
                String n = p.getFileName().toString();
                if (n.startsWith("item_account_")) {
                    hasAccountCardLayout = true;
                    accountXmls.append(ctx.xml().readRawXml(p)).append('\n');
                }
                if (n.contains("repayment")) {
                    hasRepayment = true;
                    accountXmls.append(ctx.xml().readRawXml(p)).append('\n');
                }
            }
            String javaSrc = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerAccountsFragment.java"));
            assertTrue(hasAccountCardLayout, "Missing item_account_*.xml card layout");
            assertTrue(hasRepayment, "Missing item_repayment_row.xml for mortgage schedules");
            String combined = javaSrc + "\n" + accountXmls;
            assertTrue(combined.contains("CHECKING") && combined.contains("SAVINGS")
                    && (combined.contains("phl_navy") && combined.contains("phl_emerald")),
                    "Account cards must distinguish Checking (Navy) & Savings (Emerald) + Mortgage");
            return "Verified Checking/Savings/Mortgage & repayment XML layouts";
        }));

        out.add(execute("T1-F08-04", 1, f, "CustomerAccountsFragment 3D card flip & shimmer sheen", "R2, F8", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerAccountsFragment.java"));
            assertTrue(src.contains("flipCard3D") || src.contains("rotationY"),
                    "CustomerAccountsFragment missing 3D card flip motion");
            assertTrue(src.contains("startShimmerSweep") || src.contains("shimmer"),
                    "CustomerAccountsFragment missing shimmer sheen motion");
            return "Verified 3D flip + shimmer in CustomerAccountsFragment";
        }));

        out.add(execute("T1-F08-05", 1, f, "CustomerAccountsFragment stagger entrance & expand transition", "R2, F8", () -> {
            String src = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/customer/CustomerAccountsFragment.java"));
            assertTrue(src.contains("stagger") || src.contains(".animate()"),
                    "CustomerAccountsFragment missing staggered entrance");
            boolean hasExpandAnim = src.contains("TransitionManager")
                    || src.contains("ObjectAnimator")
                    || src.contains("ValueAnimator")
                    || src.contains(".animate()")
                    || src.contains("MotionHelper.");
            assertTrue(hasExpandAnim && src.contains("setVisibility("),
                    "CustomerAccountsFragment missing animated schedule expand/collapse (TransitionManager/animator + setVisibility)");
            return "Verified stagger entrance & schedule expand motion";
        }));

        out.add(execute("T1-F08-06", 1, f, "containerAccounts ID & getAccounts/getMortgages APIs preserved", "R3, F8", () -> {
            XmlInspector.LayoutInfo layout = ctx.xml().parseLayout("fragment_customer_accounts.xml");
            assertTrue(layout.hasId("containerAccounts"), "Missing containerAccounts");
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerAccountsFragment.java"));
            assertTrue(src.contains(".getAccounts()") && src.contains(".getMortgages()"),
                    "Missing getAccounts/getMortgages API calls");
            return "Verified containerAccounts & APIs";
        }));
    }

    private static void addF9(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F9";
        out.add(execute("T1-F09-01", 1, f, "fragment_customer_utilities.xml luxury Navy/Gold header", "R1, F9", () -> {
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/fragment_customer_utilities.xml"));
            assertTrue(raw.contains("phl_navy") && (raw.contains("phl_gold") || raw.contains("@drawable/bg_")),
                    "fragment_customer_utilities.xml lacks luxury Navy/Gold banner");
            return "Verified utilities luxury banner";
        }));

        out.add(execute("T1-F09-02", 1, f, "item_utility_tile.xml inflated with zero raw MaterialCardView", "R1, F9", () -> {
            XmlInspector.LayoutInfo tile = ctx.xml().parseLayout("item_utility_tile.xml");
            assertTrue(tile.exists, "Missing res/layout/item_utility_tile.xml");
            String code = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/customer/CustomerUtilitiesFragment.java"));
            assertFalse(code.contains("new MaterialCardView"), "CustomerUtilitiesFragment still uses raw new MaterialCardView");
            assertTrue(code.contains("ItemUtilityTileBinding") || code.contains("R.layout.item_utility_tile"),
                    "CustomerUtilitiesFragment does not inflate item_utility_tile.xml");
            return "Verified item_utility_tile.xml inflation";
        }));

        out.add(execute("T1-F09-03", 1, f, "item_utility_tile.xml vector icon, supplier & VND price pill", "R1, F9", () -> {
            XmlInspector.LayoutInfo tile = ctx.xml().parseLayout("item_utility_tile.xml");
            assertTrue(tile.exists, "Missing item_utility_tile.xml");
            boolean hasImg = tile.allElements.stream()
                    .anyMatch(e -> "ImageView".equals(XmlInspector.simpleTag(e.getTagName())));
            assertTrue(hasImg, "item_utility_tile.xml missing ImageView icon");
            String src = ctx.readJava("ui/customer/CustomerUtilitiesFragment.java");
            for (String code : List.of("ELECTRIC", "WATER", "TOPUP", "FLIGHT", "CINEMA", "HOTEL", "ECOMMERCE")) {
                assertTrue(src.contains(code), "Missing service code " + code);
            }
            return "Verified tile icon & all 7 service codes";
        }));

        out.add(execute("T1-F09-04", 1, f, "CustomerUtilitiesFragment >= 2 motion blocks", "R2, F9", () -> {
            String src = ctx.readJava("ui/customer/CustomerUtilitiesFragment.java");
            Set<String> motions = SourceInspector.findMotionPrimitives(src);
            assertTrue(motions.size() >= 2, "Expected >= 2 motion primitives in CustomerUtilitiesFragment, found " + motions);
            return "motions=" + motions;
        }));

        out.add(execute("T1-F09-05", 1, f, "Utilities 7 UCs, OTP, payUtility, Receipt & 0 WebView", "R1-R3, F9", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerUtilitiesFragment.java"));
            assertFalse(SourceInspector.hasWebViewReference(src), "WebView detected");
            assertTrue(src.contains(".requestOtp(") && src.contains(".payUtility(")
                    && src.contains("ReceiptDialogHelper.showReceipt"),
                    "Missing requestOtp/payUtility/showReceipt wiring");
            return "Verified 7 UCs, OTP, payUtility, receipt & 0 WebView";
        }));

        out.add(execute("T1-F09-06", 1, f, "gridUtilities & BottomSheetTransferConfirmBinding preserved", "R3, F9", () -> {
            XmlInspector.LayoutInfo util = ctx.xml().parseLayout("fragment_customer_utilities.xml");
            assertTrue(util.hasId("gridUtilities") && "GridLayout".equals(util.tagOf("gridUtilities")),
                    "gridUtilities missing or not GridLayout");
            XmlInspector.LayoutInfo sheet = ctx.xml().parseLayout("bottom_sheet_transfer_confirm.xml");
            for (String id : List.of("tvConfirmSummary", "edtConfirmOtp", "btnSendOtp", "btnExecuteTransaction")) {
                assertTrue(sheet.hasId(id), "bottom_sheet_transfer_confirm.xml missing " + id);
            }
            return "Verified gridUtilities & shared sheet IDs";
        }));
    }

    private static void addF10(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F10";
        out.add(execute("T1-F10-01", 1, f, "item_branch_card.xml inflated replacing raw addBranchCard", "R1, F10", () -> {
            XmlInspector.LayoutInfo card = ctx.xml().parseLayout("item_branch_card.xml");
            assertTrue(card.exists, "Missing res/layout/item_branch_card.xml");
            String code = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/customer/CustomerExploreFragment.java"));
            assertFalse(code.contains("new MaterialCardView"), "CustomerExploreFragment still builds raw MaterialCardView");
            assertTrue(code.contains("ItemBranchCardBinding") || code.contains("R.layout.item_branch_card"),
                    "CustomerExploreFragment does not inflate item_branch_card.xml");
            return "Verified item_branch_card.xml inflation";
        }));

        out.add(execute("T1-F10-02", 1, f, "item_ai_message.xml rounded bubbles replacing setBackgroundColor", "R1, F10", () -> {
            XmlInspector.LayoutInfo msg = ctx.xml().parseLayout("item_ai_message.xml");
            assertTrue(msg.exists, "Missing res/layout/item_ai_message.xml");
            String code = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/customer/CustomerExploreFragment.java"));
            assertFalse(code.contains(".setBackgroundColor("), "CustomerExploreFragment still uses flat setBackgroundColor");
            assertTrue(code.contains("ItemAiMessageBinding") || code.contains("R.layout.item_ai_message"),
                    "CustomerExploreFragment does not inflate item_ai_message.xml");
            return "Verified item_ai_message.xml & zero setBackgroundColor";
        }));

        out.add(execute("T1-F10-03", 1, f, "fragment_customer_explore.xml luxury cards & AI polish", "R1, F10", () -> {
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/fragment_customer_explore.xml"));
            assertTrue(raw.contains("phl_navy") && (raw.contains("@drawable/bg_") || raw.contains("phl_gold")),
                    "fragment_customer_explore.xml lacks luxury styling");
            assertTrue(raw.contains("phl_emerald"), "fragment_customer_explore.xml lacks emerald nearest-branch badge");
            return "Verified explore luxury styling";
        }));

        out.add(execute("T1-F10-04", 1, f, "CustomerExploreFragment >= 3 motion blocks", "R2, F10", () -> {
            String src = ctx.readJava("ui/customer/CustomerExploreFragment.java");
            Set<String> motions = SourceInspector.findMotionPrimitives(src);
            assertTrue(motions.size() >= 3, "Expected >= 3 motion blocks in CustomerExploreFragment, found " + motions);
            return "motions=" + motions;
        }));

        out.add(execute("T1-F10-05", 1, f, "Explore Map/AI APIs preserved", "R1-R3, F10", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerExploreFragment.java"));
            assertFalse(SourceInspector.hasWebViewReference(src), "WebView found");
            assertTrue(src.contains("LocationServices.getFusedLocationProviderClient(")
                    && src.contains("fusedLocationClient.getCurrentLocation(")
                    && src.contains("loadBranches(location.getLatitude(), location.getLongitude())")
                    && src.contains(".getBranches(lat, lng)"),
                    "Branch lookup must use the current fused location");
            assertTrue(src.contains("loadBranches(null, null)")
                    && src.contains("nearestBranch = nearby ? data.nearest : null")
                    && src.contains("branchAdapter.submitList(branches, nearby)"),
                    "Location fallback must list branches without coordinates or a nearest branch");
            assertTrue(src.contains("new Intent(Intent.ACTION_VIEW, webUrl)"),
                    "Missing ACTION_VIEW for the walking directions URL");
            assertTrue(src.contains(".askAi("), "Missing askAi");
            return "Verified Explore Map/AI APIs";
        }));

        out.add(execute("T1-F10-06", 1, f, "Explore ViewBinding IDs & ScrollView preserved", "R3, F10", () -> {
            XmlInspector.LayoutInfo ex = ctx.xml().parseLayout("fragment_customer_explore.xml");
            for (String id : List.of("chipExploreMode", "chipMapBranch", "chipAiAssistant",
                    "panelMapBranches", "tvNearestBranch", "btnOpenWalkingMap",
                    "containerBranchList", "panelAiChat", "scrollAiMessages",
                    "containerAiMessages", "layoutAiTyping", "edtAiPrompt", "btnSendAi")) {
                assertTrue(ex.hasId(id), "fragment_customer_explore.xml missing " + id);
            }
            String scrollTag = ex.tagOf("scrollAiMessages");
            assertTrue("ScrollView".equals(scrollTag) || "NestedScrollView".equals(scrollTag),
                    "scrollAiMessages must be ScrollView/NestedScrollView, found " + scrollTag);
            return "Verified Explore IDs and message scroll container";
        }));
    }

    private static void addF11(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F11";
        out.add(execute("T1-F11-01", 1, f, "fragment_customer_profile.xml Midnight Navy profile banner & crest", "R1, F11", () -> {
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/fragment_customer_profile.xml"));
            assertTrue(raw.contains("phl_navy") && (raw.contains("@drawable/bg_") || raw.contains("phl_gold")),
                    "fragment_customer_profile.xml lacks Midnight Navy profile banner & gold accent");
            return "Verified profile navy banner & accent";
        }));

        out.add(execute("T1-F11-02", 1, f, "item_policy_alert.xml exists & inflated in CustomerProfileFragment", "R1, F11", () -> {
            XmlInspector.LayoutInfo item = ctx.xml().parseLayout("item_policy_alert.xml");
            assertTrue(item.exists, "Missing res/layout/item_policy_alert.xml");
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerProfileFragment.java"));
            assertTrue(src.contains("ItemPolicyAlertBinding") || src.contains("R.layout.item_policy_alert"),
                    "CustomerProfileFragment does not inflate item_policy_alert.xml");
            return "Verified item_policy_alert.xml inflation";
        }));

        out.add(execute("T1-F11-03", 1, f, "CustomerProfileFragment >= 2 motion blocks", "R2, F11", () -> {
            String src = ctx.readJava("ui/customer/CustomerProfileFragment.java");
            Set<String> motions = SourceInspector.findMotionPrimitives(src);
            assertTrue(motions.size() >= 2, "Expected >= 2 motion blocks in CustomerProfileFragment, found " + motions);
            return "motions=" + motions;
        }));

        out.add(execute("T1-F11-04", 1, f, "Profile security actions luxury styling & card hierarchy", "R1, F11", () -> {
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/fragment_customer_profile.xml"));
            assertTrue(raw.contains("phl_danger") && (raw.contains("icon") || raw.contains("@drawable/")),
                    "Security action buttons lack semantic danger/icon polish");
            return "Verified profile security card hierarchy";
        }));

        out.add(execute("T1-F11-05", 1, f, "Profile APIs, ChangePassword & logout preserved", "R1-R3, F11", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerProfileFragment.java"));
            assertFalse(SourceInspector.hasWebViewReference(src), "WebView found");
            assertTrue(src.contains(".getMyProfile()") && src.contains(".updateContact(")
                    && src.contains(".getNotifications()") && src.contains("ChangePasswordDialogHelper.show")
                    && src.contains(".logout()"), "Missing Profile API/auth calls");
            return "Verified Profile APIs & 0 WebView";
        }));

        out.add(execute("T1-F11-06", 1, f, "All 8 Profile ViewBinding IDs preserved", "R3, F11", () -> {
            XmlInspector.LayoutInfo layout = ctx.xml().parseLayout("fragment_customer_profile.xml");
            for (String id : List.of("tvProfileHeader", "edtProfilePhone", "edtProfileEmail",
                    "edtProfileAddress", "btnSaveContact", "tvNotificationsBody",
                    "btnCustomerChangePassword", "btnCustomerLogout")) {
                assertTrue(layout.hasId(id), "fragment_customer_profile.xml missing " + id);
            }
            return "All 8 Profile IDs preserved";
        }));
    }

    private static void addF12(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F12";
        out.add(execute("T1-F12-01", 1, f, "activity_transfer.xml Navy/Gold source banner & quick-amount chips", "R1, F12", () -> {
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/activity_transfer.xml"));
            assertTrue(raw.contains("@drawable/bg_")
                            && (raw.contains("phl_navy") || raw.contains("bg_navy") || raw.contains("Widget.PHL.Card.Hero")),
                    "activity_transfer.xml lacks Navy luxury banner drawable");
            boolean hasQuickAmounts = raw.contains("1.000.000") || raw.contains("500.000") || raw.contains("10.000.000")
                    || raw.contains("quickAmount") || raw.contains("chipAmount") || raw.contains("chipPreset");
            assertTrue(hasQuickAmounts, "activity_transfer.xml missing quick-amount chips/buttons");
            return "Verified activity_transfer.xml luxury banner & quick-amount controls";
        }));

        out.add(execute("T1-F12-02", 1, f, "bottom_sheet_transfer_confirm.xml drag handle & luxury containers", "R1, F12", () -> {
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/bottom_sheet_transfer_confirm.xml"));
            assertTrue(raw.contains("handle") || raw.contains("bg_sheet_handle"),
                    "bottom_sheet_transfer_confirm.xml missing drag-handle pill");
            assertTrue(raw.contains("@drawable/bg_"), "bottom_sheet_transfer_confirm.xml lacks rounded luxury containers");
            return "Verified bottom sheet drag handle & luxury surfaces";
        }));

        out.add(execute("T1-F12-03", 1, f, "TransferActivity applyMode uses TransitionManager/MotionHelper", "R2, F12", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/TransferActivity.java"));
            String applyMode = SourceInspector.extractMethodBody(src, "private\\s+void\\s+applyMode\\s*\\([^)]*\\)");
            assertTrue(applyMode.contains("TransitionManager") || applyMode.contains("MotionHelper"),
                    "TransferActivity.applyMode missing TransitionManager/MotionHelper animation");
            return "Verified applyMode animated transition";
        }));

        out.add(execute("T1-F12-04", 1, f, "TransferActivity >= 2 additional motion blocks", "R2, F12", () -> {
            String src = ctx.readJava("ui/customer/TransferActivity.java");
            Set<String> motions = SourceInspector.findMotionPrimitives(src);
            assertTrue(motions.size() >= 3, "Expected >= 3 total motion blocks in TransferActivity, found " + motions);
            return "motions=" + motions;
        }));

        out.add(execute("T1-F12-05", 1, f, "Transfer 5 modes, >10M eKYC gate, OTP & Receipt preserved", "R1-R3, F12", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/TransferActivity.java"));
            assertFalse(SourceInspector.hasWebViewReference(src), "WebView found");
            for (String m : List.of("INTERNAL", "INTERBANK", "GATEWAY", "DEPOSIT", "WITHDRAW",
                    "10_000_000L", "ekycLauncher", ".requestOtp(", "ReceiptDialogHelper.showReceipt")) {
                assertTrue(src.contains(m), "TransferActivity missing " + m);
            }
            return "Verified 5 transfer modes, >10M eKYC gate & APIs";
        }));

        out.add(execute("T1-F12-06", 1, f, "All 17 Transfer IDs, 6 Sheet IDs & EXTRA_* constants preserved", "R3, F12", () -> {
            XmlInspector.LayoutInfo tr = ctx.xml().parseLayout("activity_transfer.xml");
            for (String id : List.of("toolbarTransfer", "chipTransferMode", "chipInternal", "chipInterbank",
                    "chipGateway", "chipDeposit", "chipWithdraw", "tvSourceAccountInfo", "tilBankCode",
                    "edtBankCode", "tilDestAccount", "edtDestAccount", "tilDestName", "edtDestName",
                    "edtAmount", "edtContent", "btnContinueTransfer")) {
                assertTrue(tr.hasId(id), "activity_transfer.xml missing " + id);
            }
            XmlInspector.LayoutInfo sh = ctx.xml().parseLayout("bottom_sheet_transfer_confirm.xml");
            for (String id : List.of("tvConfirmSummary", "boxEkycStep", "btnVerifyEkyc",
                    "edtConfirmOtp", "btnSendOtp", "btnExecuteTransaction")) {
                assertTrue(sh.hasId(id), "bottom_sheet_transfer_confirm.xml missing " + id);
            }
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/TransferActivity.java"));
            for (String c : List.of("EXTRA_MODE", "EXTRA_SOURCE_ACC", "EXTRA_AVAILABLE_BAL")) {
                assertTrue(src.contains(c), "Missing constant " + c);
            }
            return "All 23 Transfer+Sheet IDs & EXTRA_* preserved";
        }));
    }

    private static void addF13(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F13";
        out.add(execute("T1-F13-01", 1, f, "activity_face_ekyc.xml overlays biometric oval reticle", "R1, F13", () -> {
            XmlInspector.LayoutInfo ek = ctx.xml().parseLayout("activity_face_ekyc.xml");
            assertTrue(ek.exists, "Missing activity_face_ekyc.xml");
            assertTrue(ek.hasElementWithIdOrDrawableContaining("reticle")
                            || ek.hasElementWithIdOrDrawableContaining("oval"),
                    "activity_face_ekyc.xml missing biometric oval reticle overlay");
            return "Verified biometric oval reticle in activity_face_ekyc.xml";
        }));

        out.add(execute("T1-F13-02", 1, f, "activity_face_ekyc.xml scanline & security pill overlay", "R1, F13", () -> {
            XmlInspector.LayoutInfo ek = ctx.xml().parseLayout("activity_face_ekyc.xml");
            assertTrue(ek.exists, "Missing activity_face_ekyc.xml");
            assertTrue(ek.hasElementWithIdOrDrawableContaining("scanline"),
                    "activity_face_ekyc.xml missing scanline view");
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/activity_face_ekyc.xml"));
            assertTrue(raw.contains("bg_pill_") || raw.contains("phl_gold") || raw.contains("phl_emerald"),
                    "activity_face_ekyc.xml missing luxury security status pill");
            return "Verified scanline & status pill overlay";
        }));

        out.add(execute("T1-F13-03", 1, f, "FaceEkycActivity pulsing animation on oval reticle", "R2, F13", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/FaceEkycActivity.java"));
            assertTrue(src.contains("pulseView") || src.contains("ObjectAnimator"),
                    "FaceEkycActivity missing reticle pulse animation");
            return "Verified FaceEkycActivity pulse animation";
        }));

        out.add(execute("T1-F13-04", 1, f, "FaceEkycActivity scanline sweep & verification transition", "R2, F13", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/FaceEkycActivity.java"));
            assertTrue(src.contains("startScanlineLoop") || src.contains("translationY"),
                    "FaceEkycActivity missing scanline sweep loop");
            assertTrue(SourceInspector.findMotionPrimitives(src).size() >= 2,
                    "FaceEkycActivity needs >= 2 motion primitives");
            return "Verified scanline loop & verification motion";
        }));

        out.add(execute("T1-F13-05", 1, f, "FaceEkyc CameraX, permission, verifyEkyc & 0 WebView preserved", "R1-R3, F13", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/FaceEkycActivity.java"));
            assertFalse(SourceInspector.hasWebViewReference(src), "WebView found");
            assertTrue(src.contains("ProcessCameraProvider") && src.contains("Manifest.permission.CAMERA")
                    && src.contains(".verifyEkyc(") && src.contains("RESULT_EKYC_TOKEN"),
                    "Missing CameraX/permission/verifyEkyc/RESULT_EKYC_TOKEN");
            return "Verified CameraX & eKYC contract";
        }));

        out.add(execute("T1-F13-06", 1, f, "All 4 FaceEkyc ViewBinding IDs & RESULT_EKYC_TOKEN preserved", "R3, F13", () -> {
            XmlInspector.LayoutInfo ek = ctx.xml().parseLayout("activity_face_ekyc.xml");
            for (String id : List.of("toolbarEkyc", "previewCameraEkyc", "tvCameraFallback", "btnCaptureVerifyFace")) {
                assertTrue(ek.hasId(id), "activity_face_ekyc.xml missing " + id);
            }
            assertTrue(ek.tagOf("previewCameraEkyc").contains("PreviewView"),
                    "previewCameraEkyc must remain PreviewView");
            return "All 4 FaceEkyc IDs preserved";
        }));
    }

    private static void addF14(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F14";
        out.add(execute("F14_T1_01_LayoutAndIdsPreserved", 1, f, "Officer main layout & IDs preserved", "R3, F14", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("activity_officer_main.xml");
            assertTrue(l.exists, "Missing activity_officer_main.xml");
            assertTrue(l.hasId("officerFragmentHost") && l.hasId("officerBottomNav"),
                    "Missing officerFragmentHost or officerBottomNav");
            Element nav = l.element("officerBottomNav");
            assertTrue("@menu/menu_officer_nav".equals(XmlInspector.getAttr(nav, "menu")),
                    "officerBottomNav menu != @menu/menu_officer_nav");
            return "Verified officerFragmentHost & officerBottomNav";
        }));

        out.add(execute("F14_T1_02_FourOfficerMenuItemsPreserved", 1, f, "4 Officer menu items preserved", "R3, F14", () -> {
            Map<String, XmlInspector.MenuItemInfo> menu = ctx.xml().parseMenu("menu_officer_nav.xml");
            for (String id : List.of("tab_off_customers", "tab_off_create", "tab_off_rates", "tab_off_audit")) {
                assertTrue(menu.containsKey(id), "menu_officer_nav.xml missing " + id);
            }
            return "Verified 4 officer menu items";
        }));

        out.add(execute("F14_T1_03_StatefulBottomNavColorSelectors", 1, f, "officerBottomNav uses stateful ColorStateList", "R1, F14", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("activity_officer_main.xml");
            Element nav = l.element("officerBottomNav");
            String iconTint = ctx.xml().getEffectiveAttr(nav, "itemIconTint");
            String textColor = ctx.xml().getEffectiveAttr(nav, "itemTextColor");
            assertFalse("@color/phl_navy".equals(iconTint), "officerBottomNav itemIconTint still flat @color/phl_navy");
            assertFalse("@color/phl_navy".equals(textColor), "officerBottomNav itemTextColor still flat @color/phl_navy");
            List<XmlInspector.SelectorItem> items = ctx.xml().parseColorSelector(iconTint);
            assertTrue(items.stream().anyMatch(i -> "true".equals(i.stateChecked()))
                    && items.stream().anyMatch(i -> i.stateChecked().isEmpty()),
                    "Selector " + iconTint + " lacks state_checked=true and default item");
            return "Verified officerBottomNav selectors: " + iconTint + ", " + textColor;
        }));

        out.add(execute("F14_T1_04_DefaultTabAndFragmentWiring", 1, f, "OfficerMainActivity maps 4 fragments & default tab", "R3, F14", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerMainActivity.java"));
            for (String frag : List.of("OfficerCustomersFragment", "OfficerCreateAccountFragment",
                    "OfficerRatesFragment", "OfficerAuditFragment", "tab_off_customers")) {
                assertTrue(src.contains(frag), "OfficerMainActivity missing " + frag);
            }
            return "Verified 4 Officer fragment wirings";
        }));

        out.add(execute("F14_T1_05_SmoothFragmentTransitionAndTabMotion", 1, f, "OfficerMainActivity transitions & tab motion", "R2, F14", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerMainActivity.java"));
            assertTrue(src.contains("applyFragmentTransition") || src.contains("setCustomAnimations"),
                    "OfficerMainActivity missing fragment transition animation");
            assertTrue(src.contains("animateTabSelection") || src.contains("ObjectAnimator") || src.contains(".animate()"),
                    "OfficerMainActivity missing tab selection animation");
            return "Verified OfficerMainActivity transitions & tab motion";
        }));

        out.add(execute("F14_T1_06_LuxuryShellSurfaceAndElevation", 1, f, "activity_officer_main.xml luxury surface & elevation", "R1, F14", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("activity_officer_main.xml");
            assertTrue("@color/phl_bg".equals(ctx.xml().getEffectiveAttr(l.root, "background")),
                    "Root background != @color/phl_bg");
            Element nav = l.element("officerBottomNav");
            OptionalDouble elev = ctx.xml().parseDp(ctx.xml().getEffectiveAttr(nav, "elevation"));
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/activity_officer_main.xml"));
            assertTrue((elev.isPresent() && elev.getAsDouble() >= 2.0) || raw.contains("phl_border"),
                    "officerBottomNav lacks elevation >= 2dp or border divider");
            return "Verified officer shell surface & elevation";
        }));
    }

    private static void addF15(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F15";
        out.add(execute("F15_T1_01_PreservedIdsAndLuxuryNavyHeader", 1, f, "OfficerCustomers IDs & Luxury Navy header", "R1, R3, F15", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("fragment_officer_customers.xml");
            for (String id : List.of("edtSearchCustomer", "btnSearchCustomer", "containerCustomers")) {
                assertTrue(l.hasId(id), "fragment_officer_customers.xml missing " + id);
            }
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/fragment_officer_customers.xml"));
            assertTrue(raw.contains("@drawable/bg_") && (raw.contains("phl_gold") || raw.contains("phl_emerald")),
                    "fragment_officer_customers.xml lacks luxury Navy header & gold/emerald accent");
            return "Verified OfficerCustomers IDs & Navy header";
        }));

        out.add(execute("F15_T1_02_XmlCustomerCardLayoutNoRawCards", 1, f, "item_officer_customer.xml & zero raw MaterialCardView", "R1, F15", () -> {
            XmlInspector.LayoutInfo item = ctx.xml().parseLayout("item_officer_customer.xml");
            assertTrue(item.exists, "Missing res/layout/item_officer_customer.xml");
            String code = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/officer/OfficerCustomersFragment.java"));
            assertFalse(code.contains("new MaterialCardView"), "OfficerCustomersFragment still constructs raw MaterialCardView");
            assertFalse(code.contains("new TextView("), "OfficerCustomersFragment still constructs raw TextView");
            assertTrue(code.contains("ItemOfficerCustomerBinding") || code.contains("R.layout.item_officer_customer"),
                    "OfficerCustomersFragment does not inflate item_officer_customer.xml");
            return "Verified item_officer_customer.xml & zero raw cards";
        }));

        out.add(execute("F15_T1_03_XmlBottomSheetEditDialog", 1, f, "bottom_sheet_officer_customer_edit.xml & BottomSheetDialog", "R1, F15", () -> {
            XmlInspector.LayoutInfo sheet = ctx.xml().parseLayout("bottom_sheet_officer_customer_edit.xml");
            assertTrue(sheet.exists, "Missing res/layout/bottom_sheet_officer_customer_edit.xml");
            String code = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/officer/OfficerCustomersFragment.java"));
            assertTrue(code.contains("BottomSheetDialog"), "OfficerCustomersFragment does not use BottomSheetDialog");
            assertFalse(code.contains("new AlertDialog.Builder") || code.contains("new EditText("),
                    "OfficerCustomersFragment still uses raw AlertDialog/EditText");
            return "Verified M3 BottomSheetDialog edit customer UI";
        }));

        out.add(execute("F15_T1_04_StaggeredEntranceMotion", 1, f, "OfficerCustomersFragment stagger & tactile motion", "R2, F15", () -> {
            String src = ctx.readJava("ui/officer/OfficerCustomersFragment.java");
            Set<String> motions = SourceInspector.findMotionPrimitives(src);
            assertTrue(motions.size() >= 2, "Expected >= 2 motion blocks in OfficerCustomersFragment, found " + motions);
            return "motions=" + motions;
        }));

        out.add(execute("F15_T1_05_RetrofitSearchAndUpdateWiring", 1, f, "searchCustomers & updateCustomer Retrofit wiring", "R3, F15", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCustomersFragment.java"));
            assertTrue(src.contains(".searchCustomers(") && src.contains(".updateCustomer("),
                    "Missing searchCustomers/updateCustomer API calls");
            assertTrue(src.contains("\"phone\"") && src.contains("\"email\"") && src.contains("\"status\""),
                    "Missing phone/email/status payload keys");
            return "Verified UC-OFF-01 & UC-OFF-03 wiring";
        }));

        out.add(execute("F15_T1_06_SemanticStatusPillColors", 1, f, "ACTIVE/LOCKED/eKYC semantic pill colors", "R1, F15", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCustomersFragment.java"));
            String itemRaw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/item_officer_customer.xml"));
            assertTrue((src.contains("phl_emerald") || itemRaw.contains("phl_emerald"))
                    && (src.contains("phl_danger") || itemRaw.contains("phl_danger")),
                    "Missing ACTIVE (emerald) / LOCKED (danger) semantic pill colors");
            return "Verified ACTIVE/LOCKED/eKYC pill colors";
        }));
    }

    private static void addF16(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F16";
        out.add(execute("F16_T1_01_PreservedIdsAndScrollContainer", 1, f, "OfficerCreateAccount IDs & ScrollView container", "R1, R3, F16", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("fragment_officer_create.xml");
            for (String id : List.of("edtNewUsername", "edtNewPassword", "edtNewFullName",
                    "edtNewIdCard", "edtNewDob", "edtNewPhone", "edtInitialDeposit", "btnCreateCustomer")) {
                assertTrue(l.hasId(id), "fragment_officer_create.xml missing " + id);
            }
            boolean formScrollable = l.rootIsScrollable()
                    || (l.hasScrollContainerAnywhere()
                    && l.hasScrollAncestor("edtNewUsername")
                    && l.hasScrollAncestor("btnCreateCustomer"));
            assertTrue(formScrollable,
                    "fragment_officer_create.xml form inputs/actions must reside inside ScrollView/NestedScrollView");
            return "All 8 OfficerCreateAccount IDs preserved in ScrollView";
        }));

        out.add(execute("F16_T1_02_LuxuryNavyHeaderAndGroupedSections", 1, f, "OfficerCreateAccount Navy header & grouped cards", "R1, F16", () -> {
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/fragment_officer_create.xml"));
            assertTrue(raw.contains("@drawable/bg_") && raw.contains("phl_navy"),
                    "fragment_officer_create.xml lacks Navy header drawable");
            assertTrue(countOccurrences(raw, "MaterialCardView") >= 2,
                    "fragment_officer_create.xml lacks grouped onboarding card sections");
            return "Verified Navy header & grouped onboarding cards";
        }));

        out.add(execute("F16_T1_03_TabularNumsAndLiveVndPreview", 1, f, "edtInitialDeposit tnum & live VND preview", "R1, F16", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("fragment_officer_create.xml");
            assertTrue(ctx.xml().hasTnum(l.element("edtInitialDeposit")),
                    "edtInitialDeposit lacks fontFeatureSettings=tnum");
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCreateAccountFragment.java"));
            assertTrue(src.contains("CurrencyUtils.formatVnd") && (src.contains("TextWatcher") || src.contains("addTextChangedListener")),
                    "OfficerCreateAccountFragment missing live VND formatted preview via TextWatcher");
            return "Verified tnum & live CurrencyUtils.formatVnd preview";
        }));

        out.add(execute("F16_T1_04_AnimatedOnboardingConfirmation", 1, f, "OfficerCreateAccount MotionHelper & animated confirmation", "R2, F16", () -> {
            String src = ctx.readJava("ui/officer/OfficerCreateAccountFragment.java");
            Set<String> motions = SourceInspector.findMotionPrimitives(src);
            assertTrue(motions.size() >= 2, "Expected >= 2 motion primitives in OfficerCreateAccountFragment, found " + motions);
            assertTrue(src.contains("cifNumber") && src.contains("accountNumber"),
                    "Missing cifNumber/accountNumber onboarding confirmation");
            return "motions=" + motions;
        }));

        out.add(execute("F16_T1_05_RetrofitCreateCustomerPayload", 1, f, "createCustomer Retrofit payload keys intact", "R3, F16", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCreateAccountFragment.java"));
            assertTrue(src.contains(".createCustomer("), "Missing createCustomer call");
            for (String key : List.of("\"username\"", "\"password\"", "\"fullName\"",
                    "\"idCardNumber\"", "\"dob\"", "\"phone\"", "\"initialDeposit\"")) {
                assertTrue(src.contains(key), "Missing createCustomer payload key: " + key);
            }
            return "Verified UC-OFF-02 createCustomer payload";
        }));

        out.add(execute("F16_T1_06_InputTypesAndMaxLengths", 1, f, "edtNewIdCard/Phone/Deposit inputType & maxLength", "R3, F16", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("fragment_officer_create.xml");
            Element idCard = l.element("edtNewIdCard");
            assertTrue("number".equals(XmlInspector.getAttr(idCard, "inputType"))
                    && "12".equals(XmlInspector.getAttr(idCard, "maxLength")),
                    "edtNewIdCard must have inputType=number & maxLength=12");
            assertTrue("phone".equals(XmlInspector.getAttr(l.element("edtNewPhone"), "inputType")),
                    "edtNewPhone must have inputType=phone");
            assertTrue("number".equals(XmlInspector.getAttr(l.element("edtInitialDeposit"), "inputType")),
                    "edtInitialDeposit must have inputType=number");
            return "Verified inputTypes & maxLength=12";
        }));
    }

    private static void addF17(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F17";
        out.add(execute("F17_T1_01_PreservedIdsAndChipGroup", 1, f, "OfficerRates IDs & singleSelection ChipGroup", "R3, F17", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("fragment_officer_rates.xml");
            for (String id : List.of("chipRateTarget", "chipRateSavings", "chipRateMortgage",
                    "edtRateTargetId", "edtNewInterestRate", "btnUpdateRate")) {
                assertTrue(l.hasId(id), "fragment_officer_rates.xml missing " + id);
            }
            Element cg = l.element("chipRateTarget");
            assertTrue("true".equals(XmlInspector.getAttr(cg, "singleSelection"))
                    && "true".equals(XmlInspector.getAttr(cg, "selectionRequired")),
                    "chipRateTarget must enforce singleSelection & selectionRequired");
            return "All 6 OfficerRates IDs & ChipGroup flags preserved";
        }));

        out.add(execute("F17_T1_02_LuxuryNavyHeader", 1, f, "fragment_officer_rates.xml luxury Navy header", "R1, F17", () -> {
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/fragment_officer_rates.xml"));
            assertTrue(raw.contains("@drawable/bg_") && raw.contains("phl_navy")
                    && (raw.contains("phl_gold") || raw.contains("phl_emerald")),
                    "fragment_officer_rates.xml lacks luxury Navy header & gold/emerald badge");
            return "Verified OfficerRates Navy header";
        }));

        out.add(execute("F17_T1_03_AnimatedModeCardTransition", 1, f, "OfficerRates animated Savings vs Mortgage transition", "R2, F17", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerRatesFragment.java"));
            assertTrue(src.contains("setOnCheckedStateChangeListener"),
                    "OfficerRatesFragment missing chipRateTarget state listener");
            assertTrue(src.contains("TransitionManager") || src.contains("MotionHelper"),
                    "OfficerRatesFragment missing animated mode transition");
            return "Verified Savings/Mortgage animated mode transition";
        }));

        out.add(execute("F17_T1_04_TabularNumsOnRateAndId", 1, f, "tnum & numeric inputTypes on OfficerRates", "R1, F17", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("fragment_officer_rates.xml");
            assertTrue(ctx.xml().hasTnum(l.element("edtNewInterestRate")),
                    "edtNewInterestRate lacks fontFeatureSettings=tnum");
            assertTrue("numberDecimal".equals(XmlInspector.getAttr(l.element("edtNewInterestRate"), "inputType")),
                    "edtNewInterestRate inputType != numberDecimal");
            assertTrue("number".equals(XmlInspector.getAttr(l.element("edtRateTargetId"), "inputType")),
                    "edtRateTargetId inputType != number");
            return "Verified tnum & numeric inputTypes";
        }));

        out.add(execute("F17_T1_05_RateConfirmationMicroAnimation", 1, f, "OfficerRates MotionHelper micro-animations", "R2, F17", () -> {
            String src = ctx.readJava("ui/officer/OfficerRatesFragment.java");
            Set<String> motions = SourceInspector.findMotionPrimitives(src);
            assertTrue(motions.size() >= 2, "Expected >= 2 motion blocks in OfficerRatesFragment, found " + motions);
            return "motions=" + motions;
        }));

        out.add(execute("F17_T1_06_RetrofitAdjustRatePayload", 1, f, "adjustInterestRate Retrofit payload preserved", "R3, F17", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerRatesFragment.java"));
            assertTrue(src.contains(".adjustInterestRate(")
                    && src.contains("\"targetType\"") && src.contains("\"targetId\"")
                    && src.contains("\"newInterestRate\"") && src.contains("\"MORTGAGE\"") && src.contains("\"SAVINGS\""),
                    "Missing adjustInterestRate payload contract");
            return "Verified UC-ACC-03 adjustInterestRate contract";
        }));
    }

    private static void addF18(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F18";
        out.add(execute("F18_T1_01_PreservedIdsAndExecutiveBanner", 1, f, "OfficerAudit IDs & executive identity banner", "R1, R3, F18", () -> {
            XmlInspector.LayoutInfo l = ctx.xml().parseLayout("fragment_officer_audit.xml");
            for (String id : List.of("btnOfficerChangePassword", "btnOfficerLogout", "containerAuditLogs")) {
                assertTrue(l.hasId(id), "fragment_officer_audit.xml missing " + id);
            }
            String raw = ctx.xml().readRawXml(ctx.resDir().resolve("layout/fragment_officer_audit.xml"));
            assertTrue(raw.contains("@drawable/bg_") && raw.contains("phl_navy"),
                    "fragment_officer_audit.xml lacks Navy executive banner");
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerAuditFragment.java"));
            assertTrue(src.contains("getFullName()") && src.contains("getIdentityCode()"),
                    "OfficerAuditFragment missing SessionManager executive identity binding");
            return "Verified OfficerAudit IDs & executive banner";
        }));

        out.add(execute("F18_T1_02_XmlAuditTimelineItemNoRawCards", 1, f, "item_officer_audit.xml & zero raw MaterialCardView", "R1, F18", () -> {
            XmlInspector.LayoutInfo item = ctx.xml().parseLayout("item_officer_audit.xml");
            assertTrue(item.exists, "Missing res/layout/item_officer_audit.xml");
            String code = SourceInspector.stripCommentsAndStrings(ctx.readJava("ui/officer/OfficerAuditFragment.java"));
            assertFalse(code.contains("new MaterialCardView") || code.contains("new TextView("),
                    "OfficerAuditFragment still builds raw MaterialCardView/TextView");
            assertTrue(code.contains("ItemOfficerAuditBinding") || code.contains("R.layout.item_officer_audit"),
                    "OfficerAuditFragment does not inflate item_officer_audit.xml");
            return "Verified item_officer_audit.xml & zero raw cards";
        }));

        out.add(execute("F18_T1_03_ExpandableDiffDetailsAndVndTnum", 1, f, "Expandable audit diff details & VND tnum", "R1-R2, F18", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerAuditFragment.java"));
            assertTrue(src.contains("oldValue") && src.contains("newValue"),
                    "OfficerAuditFragment missing oldValue/newValue binding");
            assertTrue(src.contains("TransitionManager") || src.contains("setOnClickListener"),
                    "OfficerAuditFragment missing expandable diff interaction");
            XmlInspector.LayoutInfo item = ctx.xml().parseLayout("item_officer_audit.xml");
            assertTrue(item.exists && item.allElements.stream().anyMatch(e -> ctx.xml().hasTnum(e)),
                    "item_officer_audit.xml lacks tnum typography");
            return "Verified expandable diff & tnum typography";
        }));

        out.add(execute("F18_T1_04_StaggeredAuditEntranceMotion", 1, f, "OfficerAuditFragment stagger & tactile motion", "R2, F18", () -> {
            String src = ctx.readJava("ui/officer/OfficerAuditFragment.java");
            Set<String> motions = SourceInspector.findMotionPrimitives(src);
            assertTrue(motions.size() >= 2, "Expected >= 2 motion primitives in OfficerAuditFragment, found " + motions);
            return "motions=" + motions;
        }));

        out.add(execute("F18_T1_05_RetrofitAuditAndAuthActions", 1, f, "getAuditLogs, ChangePassword & logout preserved", "R3, F18", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerAuditFragment.java"));
            assertTrue(src.contains(".getAuditLogs(50)") && src.contains("ChangePasswordDialogHelper.show")
                    && src.contains(".logout()"), "Missing UC-OFF-04 / UC-AUTH-02 / UC-AUTH-03 calls");
            return "Verified audit & auth actions";
        }));

        out.add(execute("F18_T1_06_LogoutClearsSessionAndTaskStack", 1, f, "Officer logout clears SessionManager & task stack", "R3, F18", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerAuditFragment.java"));
            assertTrue(src.contains("clearSession()") && src.contains("LoginActivity.class")
                    && src.contains("FLAG_ACTIVITY_NEW_TASK") && src.contains("FLAG_ACTIVITY_CLEAR_TASK"),
                    "Officer logout missing clearSession or task stack flags");
            return "Verified Officer logout session/stack clearing";
        }));
    }

    private static void addF19(E2ERunner.RunContext ctx, List<TestResult> out) {
        String f = "F19";
        out.add(execute("F19_T1_01_GradleConfigAndSdkContract", 1, f, "app/build.gradle SDK & BASE_URL contract", "AC, F19", () -> {
            String gradle = SourceInspector.readFile(ctx.appBuildGradle());
            assertTrue(gradle.contains("vn.edu.tdtu.phlbanking"), "namespace changed");
            assertTrue(gradle.contains("compileSdk 34") && gradle.contains("minSdk 24") && gradle.contains("targetSdk 34"),
                    "SDK levels changed");
            assertTrue(gradle.contains("viewBinding true") && gradle.contains("VERSION_11"),
                    "viewBinding or VERSION_11 changed");
            assertTrue(gradle.contains("http://10.0.2.2:8080/"), "BASE_URL changed");
            return "Verified app/build.gradle contract";
        }));

        out.add(execute("F19_T1_02_ManifestPermissionsAnd7Activities", 1, f, "Manifest 5 permissions & 7 activities preserved", "AC, F19", () -> {
            XmlInspector.ManifestInfo m = ctx.xml().parseManifest();
            assertTrue(m.exists(), "Missing AndroidManifest.xml");
            for (String p : List.of("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
                    "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.CAMERA")) {
                assertTrue(m.permissions().contains(p), "Missing permission: " + p);
            }
            assertTrue(m.isLauncher("SplashRouterActivity"), "SplashRouterActivity must be MAIN/LAUNCHER");
            for (String a : List.of("SplashRouterActivity", "LoginActivity", "ForgotPasswordActivity",
                    "CustomerMainActivity", "TransferActivity", "FaceEkycActivity", "OfficerMainActivity")) {
                assertTrue(m.hasActivity(a), "Missing activity in Manifest: " + a);
            }
            return "Verified 5 permissions & 7 activities";
        }));

        out.add(execute("F19_T1_03_All33UseCasesWiredToRetrofit", 1, f, "All 28 Retrofit endpoints & 33 UCs wired", "R3, AC, F19", () -> {
            List<SourceInspector.RetrofitEndpoint> auth = SourceInspector.parseRetrofitEndpoints(ctx.readJava("data/remote/AuthApiService.java"));
            List<SourceInspector.RetrofitEndpoint> cust = SourceInspector.parseRetrofitEndpoints(ctx.readJava("data/remote/CustomerApiService.java"));
            List<SourceInspector.RetrofitEndpoint> off = SourceInspector.parseRetrofitEndpoints(ctx.readJava("data/remote/OfficerApiService.java"));
            assertTrue(auth.size() == 4, "AuthApiService endpoints != 4: " + auth.size());
            assertTrue(cust.size() == 19, "CustomerApiService endpoints != 19: " + cust.size());
            assertTrue(off.size() == 5, "OfficerApiService endpoints != 5: " + off.size());

            StringBuilder allUi = new StringBuilder();
            for (Path jf : ctx.allJavaFiles()) {
                if (jf.toString().replace('\\', '/').contains("/ui/")) {
                    allUi.append(SourceInspector.stripComments(SourceInspector.readFile(jf))).append('\n');
                }
            }
            String uiCode = allUi.toString();
            for (String method : List.of("login", "logout", "requestForgotOtp", "confirmForgotPassword",
                    "getAccounts", "getMortgages", "getHistory", "requestOtp", "verifyEkyc",
                    "internalTransfer", "interbankTransfer", "gatewayPay", "deposit", "withdraw",
                    "payUtility", "getNotifications", "getBranches", "askAi", "getMyProfile",
                    "updateContact", "changePassword", "searchCustomers", "createCustomer",
                    "updateCustomer", "adjustInterestRate", "getAuditLogs")) {
                assertTrue(uiCode.contains("." + method + "("), "UI never invokes Retrofit method: " + method);
            }
            return "Auth=4, Cust=19, Off=5 endpoints & all UI call sites verified";
        }));

        out.add(execute("F19_T1_04_ZeroWebViewAndZeroHtmlCss", 1, f, "Zero WebView, HTML, CSS, or JS anywhere", "R1, AC, F19", () -> {
            for (Path jf : ctx.allJavaFiles()) {
                assertFalse(SourceInspector.hasWebViewReference(SourceInspector.readFile(jf)),
                        "WebView reference in " + jf.getFileName());
            }
            for (Path lf : ctx.allLayoutFiles()) {
                XmlInspector.LayoutInfo li = ctx.xml().parseLayout(lf.getFileName().toString());
                assertTrue(li.exists, "Failed to parse layout XML: " + lf.getFileName());
                assertFalse(li.hasWebView(), "WebView in " + lf.getFileName());
            }
            assertTrue(SourceInspector.listFilesWithExtension(ctx.mainDir(), ".html").isEmpty()
                    && SourceInspector.listFilesWithExtension(ctx.mainDir(), ".css").isEmpty()
                    && SourceInspector.listFilesWithExtension(ctx.mainDir(), ".js").isEmpty(),
                    "HTML/CSS/JS asset found in app/src/main");
            return "Zero WebView/HTML/CSS/JS verified";
        }));

        out.add(execute("F19_T1_05_BackendDatabaseDocsScopeIsolation", 1, f, "Backend/database/docs untouched (scope isolation)", "R3, AC, F19", () -> {
            String gitStatus = SourceInspector.readFile(ctx.gitStatusFile());
            String scopeViolations = SourceInspector.readFile(ctx.scopeViolationsFile()).trim();
            assertTrue(scopeViolations.isEmpty(), "Files modified outside android/: " + scopeViolations);
            if (!gitStatus.isBlank()) {
                for (String line : gitStatus.split("\\R")) {
                    if (line.length() < 4) {
                        continue;
                    }
                    String code = line.substring(0, 2);
                    String path = line.substring(3).trim();
                    if (path.startsWith("backend/") || path.startsWith("database/")
                            || path.startsWith("docs/") || path.startsWith("frontend/")) {
                        assertTrue("??".equals(code),
                                "Tracked/staged modification outside android/: " + line);
                    }
                }
            }
            return "Scope isolation verified (0 edits outside android/)";
        }));

        out.add(execute("F19_T1_06_NoKotlinOrExternalUnauthorizedDeps", 1, f, "Zero Kotlin files & preserved dependencies", "AC, F19", () -> {
            List<Path> kt = SourceInspector.listFilesWithExtension(ctx.androidDir().resolve("app/src"), ".kt");
            assertTrue(kt.isEmpty(), "Kotlin files found: " + kt);
            String gradle = SourceInspector.readFile(ctx.appBuildGradle());
            for (String dep : List.of("com.google.android.material:material", "com.squareup.retrofit2:retrofit",
                    "com.squareup.okhttp3:logging-interceptor", "androidx.camera:camera-core")) {
                assertTrue(gradle.contains(dep), "Missing core dependency: " + dep);
            }
            return "Zero Kotlin files & all core dependencies intact";
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
}
