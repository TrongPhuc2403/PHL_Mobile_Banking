package vn.edu.tdtu.phlbanking.e2e;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static vn.edu.tdtu.phlbanking.e2e.TestResult.assertFalse;
import static vn.edu.tdtu.phlbanking.e2e.TestResult.assertTrue;
import static vn.edu.tdtu.phlbanking.e2e.TestResult.execute;

public final class Tier4ScenarioTests {

    private Tier4ScenarioTests() {
    }

    public static List<TestResult> runAll(E2ERunner.RunContext ctx) {
        List<TestResult> out = new ArrayList<>(10);
        String f = "E2EScenarios";

        out.add(execute("T4_01_CleanDebugBuild", 4, f, "Clean Debug APK Build (assembleDebug)", "AC, F19", () -> {
            ApkInspector.ApkSnapshot apk = ApkInspector.inspect(ctx.apkPath());
            if (!ctx.skipGradle()) {
                assertTrue(ctx.buildExitCode() == 0,
                        "gradlew assembleDebug failed with exit code " + ctx.buildExitCode());
            } else {
                long latestSrc = ctx.latestMainSourceModifiedMillis();
                assertTrue(apk.exists() && apk.lastModifiedMillis() >= latestSrc,
                        "-SkipGradle set but APK is missing or stale compared to app/src/main");
            }
            assertTrue(apk.exists() && apk.sizeBytes() > 100_000L,
                    "Built app-debug.apk missing or empty (" + apk.sizeBytes() + " bytes)");
            return "assembleDebug verified: APK size=" + apk.sizeBytes() + " bytes";
        }));

        out.add(execute("T4_02_AndroidLintQualityGate", 4, f, "Android Lint Static Quality Gate (0 Fatal/Error)", "AC, F19", () -> {
            if (!ctx.skipGradle()) {
                assertTrue(ctx.lintExitCode() == 0,
                        "gradlew lintDebug failed with exit code " + ctx.lintExitCode());
            }
            LintInspector.LintSummary lint = LintInspector.inspect(ctx.lintXmlPath());
            assertTrue(lint.reportExists(), "Missing lint-results-debug.xml");
            int critical = lint.fatalCount() + lint.errorCount();
            assertTrue(critical == 0,
                    "Lint reported " + critical + " Fatal/Error issues: " + lint.fatalAndErrorDetails());
            return "Lint passed: Fatal=0, Error=0, Warning=" + lint.warningCount();
        }));

        out.add(execute("T4_03_ApkPackagingAndDexVerification", 4, f, "APK Resource, Anim, Color, Drawable & DEX Verification", "AC, F1-F19", () -> {
            ApkInspector.ApkSnapshot apk = ApkInspector.inspect(ctx.apkPath());
            assertTrue(apk.exists(), apk.error());
            assertTrue(apk.hasEntry("AndroidManifest.xml") && apk.hasEntry("resources.arsc"),
                    "APK missing AndroidManifest.xml or resources.arsc");
            for (String layout : List.of(
                    "activity_login.xml", "activity_forgot_password.xml", "activity_customer_main.xml",
                    "activity_officer_main.xml", "activity_transfer.xml", "activity_face_ekyc.xml",
                    "fragment_customer_home.xml", "fragment_customer_accounts.xml",
                    "fragment_customer_utilities.xml", "fragment_customer_explore.xml",
                    "fragment_customer_profile.xml", "fragment_officer_customers.xml",
                    "fragment_officer_create.xml", "fragment_officer_rates.xml",
                    "fragment_officer_audit.xml", "bottom_sheet_transfer_confirm.xml",
                    "dialog_receipt.xml", "dialog_change_password.xml",
                    "item_utility_tile.xml", "item_branch_card.xml", "item_ai_message.xml",
                    "item_policy_alert.xml", "item_officer_customer.xml",
                    "bottom_sheet_officer_customer_edit.xml", "item_officer_audit.xml")) {
                assertTrue(apk.hasResource("layout", layout), "APK missing layout: " + layout);
            }
            assertTrue(apk.hasResource("menu", "menu_customer_nav.xml")
                    && apk.hasResource("menu", "menu_officer_nav.xml"), "APK missing menus");
            assertTrue(apk.countResourcesInType("anim") >= 4,
                    "APK res/anim count < 4: " + apk.countResourcesInType("anim"));
            assertTrue(apk.countResourcesInType("color") >= 2,
                    "APK res/color count < 2: " + apk.countResourcesInType("color"));
            assertTrue(apk.countResourcesInType("drawable") >= 15,
                    "APK res/drawable count < 15: " + apk.countResourcesInType("drawable"));
            for (String fqcn : List.of(
                    "vn.edu.tdtu.phlbanking.ui.auth.SplashRouterActivity",
                    "vn.edu.tdtu.phlbanking.ui.auth.LoginActivity",
                    "vn.edu.tdtu.phlbanking.ui.auth.ForgotPasswordActivity",
                    "vn.edu.tdtu.phlbanking.ui.customer.CustomerMainActivity",
                    "vn.edu.tdtu.phlbanking.ui.customer.TransferActivity",
                    "vn.edu.tdtu.phlbanking.ui.customer.FaceEkycActivity",
                    "vn.edu.tdtu.phlbanking.ui.officer.OfficerMainActivity",
                    "vn.edu.tdtu.phlbanking.ui.common.MotionHelper",
                    "vn.edu.tdtu.phlbanking.ui.customer.ReceiptDialogHelper",
                    "vn.edu.tdtu.phlbanking.ui.common.ChangePasswordDialogHelper",
                    "vn.edu.tdtu.phlbanking.data.remote.AuthApiService",
                    "vn.edu.tdtu.phlbanking.data.remote.CustomerApiService",
                    "vn.edu.tdtu.phlbanking.data.remote.OfficerApiService")) {
                assertTrue(apk.hasDexClassDescriptor(fqcn), "DEX missing class: " + fqcn);
            }
            return "APK verified: entries=" + apk.entryNames().size()
                    + ", anims=" + apk.countResourcesInType("anim")
                    + ", colors=" + apk.countResourcesInType("color")
                    + ", drawables=" + apk.countResourcesInType("drawable");
        }));

        out.add(execute("T4_04_Complete33UseCaseContractMatrix", 4, f, "Complete 33-Use-Case Contract & Endpoint Matrix", "R3, AC, F4-F19", () -> {
            List<SourceInspector.RetrofitEndpoint> auth = SourceInspector.parseRetrofitEndpoints(ctx.readJava("data/remote/AuthApiService.java"));
            List<SourceInspector.RetrofitEndpoint> cust = SourceInspector.parseRetrofitEndpoints(ctx.readJava("data/remote/CustomerApiService.java"));
            List<SourceInspector.RetrofitEndpoint> off = SourceInspector.parseRetrofitEndpoints(ctx.readJava("data/remote/OfficerApiService.java"));
            assertTrue(auth.size() == 4 && cust.size() == 19 && off.size() == 5,
                    "Retrofit interface endpoint count changed");
            StringBuilder ui = new StringBuilder();
            for (Path jf : ctx.allJavaFiles()) {
                if (jf.toString().replace('\\', '/').contains("/ui/")) {
                    ui.append(SourceInspector.stripComments(SourceInspector.readFile(jf))).append('\n');
                }
            }
            String uiSrc = ui.toString();
            String[] ucsAndCalls = {
                    ".login(", ".logout(", ".changePassword(", ".requestForgotOtp(", ".confirmForgotPassword(",
                    ".getMyProfile(", ".updateContact(", ".verifyEkyc(", ".requestOtp(",
                    ".getAccounts(", ".getMortgages(", ".adjustInterestRate(", ".getHistory(",
                    ".internalTransfer(", ".interbankTransfer(", ".gatewayPay(", ".deposit(", ".withdraw(",
                    ".payUtility(", ".getNotifications(", ".getBranches(", ".askAi(",
                    ".searchCustomers(", ".createCustomer(", ".updateCustomer(", ".getAuditLogs("
            };
            for (String call : ucsAndCalls) {
                assertTrue(uiSrc.contains(call), "33-UC matrix missing UI invocation: " + call);
            }
            for (String util : List.of("ELECTRIC", "WATER", "TOPUP", "FLIGHT", "CINEMA", "HOTEL", "ECOMMERCE")) {
                assertTrue(uiSrc.contains(util), "33-UC matrix missing utility UC: " + util);
            }
            assertTrue(uiSrc.contains("ReceiptDialogHelper.showReceipt"), "Missing UC-TXN-04 ReceiptDialogHelper");
            return "33/33 Use Cases wired across 28 Retrofit endpoints";
        }));

        out.add(execute("T4_05_PerScreenDesignAndMotionRubric", 4, f, "Per-Screen Visual Design & Motion Rubric (All 15+ Screens)", "R1-R2, AC, F1-F18", () -> {
            Map<String, String> colors = ctx.xml().loadColors();
            assertTrue("#0F1E36".equalsIgnoreCase(colors.get("phl_navy"))
                    && "#0E9F6E".equalsIgnoreCase(colors.get("phl_emerald"))
                    && "#B8860B".equalsIgnoreCase(colors.get("phl_gold"))
                    && "#F5F7FA".equalsIgnoreCase(colors.get("phl_bg")),
                    "Luxury palette tokens mismatch");
            String[] multiMotionScreens = {
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
            };
            List<String> rubricFailures = new ArrayList<>();
            for (String s : multiMotionScreens) {
                Set<String> m = SourceInspector.findMotionPrimitives(ctx.readJava(s));
                if (m.size() < 2) {
                    rubricFailures.add(s + "(motions=" + m.size() + ")");
                }
            }
            assertTrue(rubricFailures.isEmpty(),
                    "Screens failing >= 2 motion blocks rubric: " + rubricFailures);
            assertTrue(ctx.xml().parseLayout("dialog_receipt.xml").exists
                    && ctx.xml().parseLayout("dialog_change_password.xml").exists,
                    "Missing luxury dialog layouts");
            return "All 15 screens + dialogs pass Luxury Design & >=2 Motion Rubric";
        }));

        out.add(execute("T4_06_CustomerWealthManagementE2E", 4, f, "Customer Wealth E2E (Auth -> Home -> Accounts -> Transfer >10M -> eKYC -> Receipt)", "R1-R3, F1-F8, F12-F13", () -> {
            assertTrue(SourceInspector.stripComments(ctx.readJava("ui/auth/LoginActivity.java")).contains("CustomerMainActivity"),
                    "Login -> CustomerMain broken");
            String home = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerHomeFragment.java"));
            assertTrue(home.contains("animateVndCounter") && home.contains("balanceHidden")
                    && home.contains("ReceiptDialogHelper.showReceipt"), "Home counter/mask/receipt broken");
            String accts = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerAccountsFragment.java"));
            assertTrue(accts.contains("flipCard3D") && !accts.contains("new MaterialCardView"),
                    "Accounts 3D flip / XML card upgrade missing");
            String tr = SourceInspector.stripComments(ctx.readJava("ui/customer/TransferActivity.java"));
            String ek = SourceInspector.stripComments(ctx.readJava("ui/customer/FaceEkycActivity.java"));
            assertTrue(tr.contains("10_000_000L") && tr.contains("ekycLauncher")
                    && ek.contains("RESULT_EKYC_TOKEN") && tr.contains("ReceiptDialogHelper.showReceipt"),
                    "Transfer >10M -> FaceEkyc -> Receipt flow broken");
            assertTrue(ctx.xml().parseLayout("bottom_sheet_transfer_confirm.xml").hasScrollContainerAnywhere(),
                    "Transfer confirm bottom sheet not scrollable");
            return "Verified end-to-end Customer Wealth Management journey";
        }));

        out.add(execute("T4_07_CustomerLifestyleAndAiConciergeE2E", 4, f, "Customer Lifestyle & AI Concierge E2E (Utilities 7 + Explore + Profile)", "R1-R3, F9-F11", () -> {
            String ut = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerUtilitiesFragment.java"));
            assertTrue(ctx.xml().parseLayout("item_utility_tile.xml").exists && !ut.contains("new MaterialCardView")
                    && ut.contains("BottomSheetTransferConfirmBinding") && ut.contains("ReceiptDialogHelper.showReceipt"),
                    "Utilities XML tile / shared sheet / receipt broken");
            String ex = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerExploreFragment.java"));
            assertTrue(ctx.xml().parseLayout("item_branch_card.xml").exists
                    && ctx.xml().parseLayout("item_ai_message.xml").exists
                    && ex.contains("google.navigation:q=") && ex.contains("phl://transfer"),
                    "Explore XML cards/bubbles/deep-links broken");
            String pr = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerProfileFragment.java"));
            assertTrue(ctx.xml().parseLayout("item_policy_alert.xml").exists
                    && pr.contains("ChangePasswordDialogHelper.show") && pr.contains(".logout()"),
                    "Profile XML alerts / password / logout broken");
            return "Verified Customer Lifestyle & AI Concierge journey";
        }));

        out.add(execute("T4_08_OfficerExecutiveOperationsE2E", 4, f, "Officer Executive E2E (Shell -> Customers -> Create -> Rates -> Audit)", "R1-R3, F14-F18", () -> {
            String shell = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerMainActivity.java"));
            assertFalse(SourceInspector.findMotionPrimitives(shell).isEmpty(), "Officer shell lacks motion");
            String cust = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCustomersFragment.java"));
            assertTrue(ctx.xml().parseLayout("item_officer_customer.xml").exists
                    && ctx.xml().parseLayout("bottom_sheet_officer_customer_edit.xml").exists
                    && cust.contains("BottomSheetDialog"), "OfficerCustomers XML/BottomSheet incomplete");
            String create = ctx.readJava("ui/officer/OfficerCreateAccountFragment.java");
            assertTrue(SourceInspector.allParseCallsGuardedByTryCatch(create, "Long.parseLong")
                    && create.contains("CurrencyUtils.formatVnd"), "OfficerCreateAccount unsafe parseLong or missing live VND");
            String rates = ctx.readJava("ui/officer/OfficerRatesFragment.java");
            assertTrue(SourceInspector.allParseCallsGuardedByTryCatch(rates, "Long.parseLong")
                    && SourceInspector.allParseCallsGuardedByTryCatch(rates, "Double.parseDouble"),
                    "OfficerRates unsafe numeric parsing");
            String audit = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerAuditFragment.java"));
            assertTrue(ctx.xml().parseLayout("item_officer_audit.xml").exists
                    && audit.contains("ChangePasswordDialogHelper.show") && audit.contains(".logout()"),
                    "OfficerAudit XML timeline / auth incomplete");
            return "Verified end-to-end Officer Executive Operations journey";
        }));

        out.add(execute("T4_09_NativeUiAndScopeIsolationIntegrity", 4, f, "Pure Native UI & Backend/Database/Docs Scope Isolation", "R1, R3, AC, F19", () -> {
            for (Path jf : ctx.allJavaFiles()) {
                assertFalse(SourceInspector.hasWebViewReference(SourceInspector.readFile(jf)),
                        "WebView found in " + jf.getFileName());
            }
            for (Path lf : ctx.allLayoutFiles()) {
                XmlInspector.LayoutInfo li = ctx.xml().parseLayout(lf.getFileName().toString());
                assertTrue(li.exists, "Failed to parse layout XML: " + lf.getFileName());
                assertFalse(li.hasWebView(), "WebView in " + lf.getFileName());
            }
            ApkInspector.ApkSnapshot apk = ApkInspector.inspect(ctx.apkPath());
            if (apk.exists()) {
                assertFalse(apk.hasAnyWebAsset(), "Built APK contains WebView or HTML/CSS/JS assets");
            }
            String scopeViolations = SourceInspector.readFile(ctx.scopeViolationsFile()).trim();
            assertTrue(scopeViolations.isEmpty(), "Files modified outside android/: " + scopeViolations);
            String gitStatus = SourceInspector.readFile(ctx.gitStatusFile());
            if (!gitStatus.isBlank()) {
                for (String line : gitStatus.split("\\R")) {
                    if (line.length() < 4) continue;
                    String code = line.substring(0, 2);
                    String path = line.substring(3).trim();
                    if (path.startsWith("backend/") || path.startsWith("database/")
                            || path.startsWith("docs/") || path.startsWith("frontend/")) {
                        assertTrue("??".equals(code), "Modified tracked file outside android/: " + line);
                    }
                }
            }
            return "Verified 0 WebView/HTML/CSS/JS & strict android/ scope isolation";
        }));

        out.add(execute("T4_10_LiveMotionAndTypographyDisciplineAudit", 4, f, "MotionHelper Completeness, Non-Blocking UI & 100% tnum Sweep", "R1-R2, AC, F1-F18", () -> {
            String mh = SourceInspector.stripComments(ctx.readJava("ui/common/MotionHelper.java"));
            assertFalse(mh.isEmpty(), "MotionHelper.java missing");
            for (String m : List.of("staggerEntrance", "staggerChildren", "animateVndCounter", "crossfadeText",
                    "applyTactilePress", "startShimmerSweep", "stopShimmer", "flipCard3D",
                    "shakeError", "pulseView", "startScanlineLoop", "animateTabSelection",
                    "applyFragmentTransition", "applyActivityTransition")) {
                assertTrue(mh.contains(m + "("), "MotionHelper missing " + m);
            }
            for (Path jf : ctx.allJavaFiles()) {
                assertFalse(SourceInspector.hasBlockingUiSleepOrClickableFalse(SourceInspector.readFile(jf)),
                        "Blocking Thread.sleep/setClickable(false) in " + jf.getFileName());
            }
            assertTrue(ctx.xml().hasTnum(ctx.xml().parseLayout("fragment_customer_home.xml").element("tvHomeBalance"))
                    && ctx.xml().hasTnum(ctx.xml().parseLayout("item_history_row.xml").element("tvHistoryAmount"))
                    && ctx.xml().hasTnum(ctx.xml().parseLayout("activity_transfer.xml").element("edtAmount"))
                    && ctx.xml().hasTnum(ctx.xml().parseLayout("bottom_sheet_transfer_confirm.xml").element("tvConfirmSummary"))
                    && ctx.xml().hasTnum(ctx.xml().parseLayout("fragment_officer_create.xml").element("edtInitialDeposit"))
                    && ctx.xml().hasTnum(ctx.xml().parseLayout("fragment_officer_rates.xml").element("edtNewInterestRate")),
                    "Missing tnum on core monetary/rate fields");
            return "Verified complete MotionHelper, non-blocking UI, and 100% tnum discipline";
        }));

        return out;
    }
}
