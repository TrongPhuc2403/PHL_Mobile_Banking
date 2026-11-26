package vn.edu.tdtu.phlbanking.e2e;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static vn.edu.tdtu.phlbanking.e2e.TestResult.assertFalse;
import static vn.edu.tdtu.phlbanking.e2e.TestResult.assertTrue;
import static vn.edu.tdtu.phlbanking.e2e.TestResult.execute;

public final class Tier3CrossFeatureTests {

    private Tier3CrossFeatureTests() {
    }

    public static List<TestResult> runAll(E2ERunner.RunContext ctx) {
        List<TestResult> out = new ArrayList<>(22);
        String f = "CrossFeature";

        out.add(execute("T3_01", 3, f, "Customer 5-Tab Shell x menu x 5 Fragments", "R1-R3, F1, F3, F6", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerMainActivity.java"));
            Map<String, XmlInspector.MenuItemInfo> menu = ctx.xml().parseMenu("menu_customer_nav.xml");
            assertTrue(menu.size() == 5, "menu_customer_nav must have 5 tabs");
            for (String tab : List.of("tab_cust_home", "tab_cust_accounts", "tab_cust_utilities",
                    "tab_cust_explore", "tab_cust_profile")) {
                assertTrue(menu.containsKey(tab) && src.contains(tab), "Missing customer tab wiring: " + tab);
            }
            for (String frag : List.of("CustomerHomeFragment", "CustomerAccountsFragment",
                    "CustomerUtilitiesFragment", "CustomerExploreFragment", "CustomerProfileFragment")) {
                assertTrue(src.contains(frag), "Missing fragment: " + frag);
            }
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "CustomerMainActivity lacks motion");
            Element nav = ctx.xml().parseLayout("activity_customer_main.xml").element("customerBottomNav");
            assertFalse(ctx.xml().parseColorSelector(ctx.xml().getEffectiveAttr(nav, "itemIconTint")).isEmpty(),
                    "customerBottomNav lacks stateful res/color/ selector");
            return "Verified 5-tab Customer shell x fragments x motion x selector";
        }));

        out.add(execute("T3_02", 3, f, "Officer 4-Tab Shell x menu x 4 Fragments", "R1-R3, F1, F3, F14", () -> {
            String src = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerMainActivity.java"));
            Map<String, XmlInspector.MenuItemInfo> menu = ctx.xml().parseMenu("menu_officer_nav.xml");
            assertTrue(menu.size() == 4, "menu_officer_nav must have 4 tabs");
            for (String tab : List.of("tab_off_customers", "tab_off_create", "tab_off_rates", "tab_off_audit")) {
                assertTrue(menu.containsKey(tab) && src.contains(tab), "Missing officer tab wiring: " + tab);
            }
            for (String frag : List.of("OfficerCustomersFragment", "OfficerCreateAccountFragment",
                    "OfficerRatesFragment", "OfficerAuditFragment")) {
                assertTrue(src.contains(frag), "Missing officer fragment: " + frag);
            }
            assertFalse(SourceInspector.findMotionPrimitives(src).isEmpty(), "OfficerMainActivity lacks motion");
            Element nav = ctx.xml().parseLayout("activity_officer_main.xml").element("officerBottomNav");
            assertFalse(ctx.xml().parseColorSelector(ctx.xml().getEffectiveAttr(nav, "itemIconTint")).isEmpty(),
                    "officerBottomNav lacks stateful res/color/ selector");
            return "Verified 4-tab Officer shell x fragments x motion x selector";
        }));

        out.add(execute("T3_03", 3, f, "Home Quick Actions x TransferActivity EXTRA_* modes", "R3, F7, F12", () -> {
            String home = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerHomeFragment.java"));
            String tr = SourceInspector.stripComments(ctx.readJava("ui/customer/TransferActivity.java"));
            assertTrue(home.contains("openTransfer(\"INTERNAL\")") && home.contains("openTransfer(\"DEPOSIT\")")
                    && home.contains("openTransfer(\"GATEWAY\")"), "Home quick-action modes broken");
            for (String ex : List.of("EXTRA_MODE", "EXTRA_SOURCE_ACC", "EXTRA_AVAILABLE_BAL")) {
                assertTrue(home.contains(ex) && tr.contains(ex), "Intent extra mismatch: " + ex);
            }
            assertTrue(tr.contains("chipDeposit.setChecked(true)") && tr.contains("chipGateway.setChecked(true)"),
                    "TransferActivity missing chip pre-selection from EXTRA_MODE");
            return "Verified Home quick actions -> TransferActivity intent contract";
        }));

        out.add(execute("T3_04", 3, f, "High-Value Transfer (>10M) x FaceEkyc x BottomSheet x Receipt", "R3, F4, F12, F13", () -> {
            String tr = SourceInspector.stripComments(ctx.readJava("ui/customer/TransferActivity.java"));
            String ek = SourceInspector.stripComments(ctx.readJava("ui/customer/FaceEkycActivity.java"));
            assertTrue(tr.contains("10_000_000L") && tr.contains("boxEkycStep")
                            && tr.contains("ekycLauncher")
                            && Pattern.compile("verifiedEkycToken\\s*(==|!=)\\s*null|TextUtils\\.isEmpty\\(\\s*verifiedEkycToken\\s*\\)").matcher(tr).find()
                            && tr.contains("ReceiptDialogHelper.showReceipt"),
                    "Missing high-value eKYC gate or receipt handoff");
            assertTrue(ek.contains("RESULT_EKYC_TOKEN") && ek.contains("RESULT_OK"),
                    "FaceEkycActivity missing RESULT_EKYC_TOKEN result contract");
            return "Verified >10M Transfer x FaceEkyc x Sheet x Receipt";
        }));

        out.add(execute("T3_05", 3, f, "5 Transfer Modes x CustomerApiService + TransitionManager", "R2-R3, F12", () -> {
            String tr = SourceInspector.stripComments(ctx.readJava("ui/customer/TransferActivity.java"));
            for (String api : List.of("internalTransfer", "interbankTransfer", "gatewayPay", "deposit", "withdraw")) {
                assertTrue(tr.contains("." + api + "("), "TransferActivity missing API call: " + api);
            }
            assertTrue(tr.contains("idempotencyKey") && tr.contains("otpCode") && tr.contains("ekycToken"),
                    "Missing idempotencyKey/otpCode/ekycToken");
            assertTrue(tr.contains("TransitionManager") || tr.contains("MotionHelper"),
                    "TransferActivity mode switching lacks TransitionManager/MotionHelper");
            return "Verified all 5 transfer modes & animated mode switch";
        }));

        out.add(execute("T3_06", 3, f, "Home HistoryAdapter x item_history_row x ReceiptDialogHelper", "R1, R3, F4, F7", () -> {
            String home = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerHomeFragment.java"));
            assertTrue(home.contains("ItemHistoryRowBinding") && home.contains("ReceiptDialogHelper.showReceipt("),
                    "HistoryAdapter missing ItemHistoryRowBinding or ReceiptDialogHelper");
            XmlInspector.LayoutInfo rec = ctx.xml().parseLayout("dialog_receipt.xml");
            assertTrue(rec.exists && rec.allElements.stream().anyMatch(e -> ctx.xml().hasTnum(e)),
                    "ReceiptDialogHelper lacks luxury dialog_receipt.xml with tnum");
            return "Verified HistoryAdapter -> luxury dialog_receipt.xml";
        }));

        out.add(execute("T3_07", 3, f, "7 Utilities x item_utility_tile x Shared BottomSheet x Receipt", "R1, R3, F4, F9", () -> {
            String ut = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerUtilitiesFragment.java"));
            for (String code : List.of("ELECTRIC", "WATER", "TOPUP", "FLIGHT", "CINEMA", "HOTEL", "ECOMMERCE")) {
                assertTrue(ut.contains(code), "Missing utility: " + code);
            }
            assertTrue(ctx.xml().parseLayout("item_utility_tile.xml").exists, "Missing item_utility_tile.xml");
            assertTrue(ut.contains("BottomSheetTransferConfirmBinding") && ut.contains(".requestOtp(")
                    && ut.contains(".payUtility(") && ut.contains("ReceiptDialogHelper.showReceipt"),
                    "Utilities missing shared bottom sheet / OTP / payUtility / receipt");
            return "Verified 7 utilities -> tile -> shared sheet -> OTP -> receipt";
        }));

        out.add(execute("T3_08", 3, f, "Explore AI Deep-Links (phl://) x CustomerMainActivity x Transfer", "R3, F6, F10, F12", () -> {
            String ex = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerExploreFragment.java"));
            assertTrue(ex.contains("phl://transfer") && ex.contains("TransferActivity.class"), "Missing phl://transfer");
            assertTrue(ex.contains("phl://map") && ex.contains("chipMapBranch.setChecked(true)"), "Missing phl://map");
            assertTrue(ex.contains("phl://savings") && ex.contains("phl://mortgage") && ex.contains("tab_cust_accounts"),
                    "Missing phl://savings|mortgage -> accounts tab");
            assertTrue(ex.contains("phl://utilities") && ex.contains("tab_cust_utilities"),
                    "Missing phl://utilities -> utilities tab");
            return "Verified all 5 phl:// deep-links";
        }));

        out.add(execute("T3_09", 3, f, "Explore Map Branches x item_branch_card x Walking Intent", "R1, R3, F10", () -> {
            String ex = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerExploreFragment.java"));
            assertTrue(ctx.xml().parseLayout("item_branch_card.xml").exists, "Missing item_branch_card.xml");
            assertTrue(ex.contains("getBranches(10.7315, 106.6980)")
                    && ex.contains("Intent.ACTION_VIEW") && ex.contains("google.navigation:q="),
                    "Missing getBranches or google.navigation walking intent");
            return "Verified Map branches -> item_branch_card -> walking intent";
        }));

        out.add(execute("T3_10", 3, f, "Shared ChangePasswordDialogHelper across Customer & Officer", "R1, R3, F4, F11, F18", () -> {
            String prof = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerProfileFragment.java"));
            String audit = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerAuditFragment.java"));
            String helper = SourceInspector.stripComments(ctx.readJava("ui/common/ChangePasswordDialogHelper.java"));
            assertTrue(prof.contains("ChangePasswordDialogHelper.show"), "CustomerProfile missing ChangePasswordDialogHelper");
            assertTrue(audit.contains("ChangePasswordDialogHelper.show"), "OfficerAudit missing ChangePasswordDialogHelper");
            assertTrue(ctx.xml().parseLayout("dialog_change_password.xml").exists
                    && helper.contains("changePassword("), "Helper missing dialog_change_password.xml or changePassword API");
            return "Verified shared ChangePasswordDialogHelper across both roles";
        }));

        out.add(execute("T3_11", 3, f, "Shared Theme, Palette, Styles & MotionHelper across Roles", "R1-R2, F1, F3, F6, F14", () -> {
            XmlInspector.ManifestInfo m = ctx.xml().parseManifest();
            assertTrue("@style/Theme.PHLBanking".equals(m.applicationTheme()), "Manifest theme != @style/Theme.PHLBanking");
            assertTrue(ctx.xml().loadStyles().keySet().stream().anyMatch(k -> k.startsWith("TextAppearance.PHL.")),
                    "Missing TextAppearance.PHL.* styles");
            assertFalse(ctx.readJava("ui/common/MotionHelper.java").isEmpty(), "Missing MotionHelper.java");
            assertFalse(SourceInspector.findMotionPrimitives(ctx.readJava("ui/customer/CustomerMainActivity.java")).isEmpty(),
                    "Customer shell lacks MotionHelper/motion");
            assertFalse(SourceInspector.findMotionPrimitives(ctx.readJava("ui/officer/OfficerMainActivity.java")).isEmpty(),
                    "Officer shell lacks MotionHelper/motion");
            return "Verified shared luxury theme, styles, and MotionHelper across roles";
        }));

        out.add(execute("T3_12", 3, f, "Auth Splash -> Login <-> ForgotPassword -> Role Routing", "R2-R3, F4, F5", () -> {
            String splash = SourceInspector.stripComments(ctx.readJava("ui/auth/SplashRouterActivity.java"));
            String login = SourceInspector.stripComments(ctx.readJava("ui/auth/LoginActivity.java"));
            String forgot = SourceInspector.stripComments(ctx.readJava("ui/auth/ForgotPasswordActivity.java"));
            assertTrue(splash.contains("isLoggedIn()") && splash.contains("OFFICER")
                    && splash.contains("OfficerMainActivity") && splash.contains("CustomerMainActivity"),
                    "SplashRouter role routing broken");
            assertTrue(login.contains("ForgotPasswordActivity.class") && login.contains("FLAG_ACTIVITY_CLEAR_TASK")
                    && forgot.contains("confirmForgotPassword("),
                    "Login <-> ForgotPassword flow broken");
            assertFalse(SourceInspector.findMotionPrimitives(splash).isEmpty()
                    || SourceInspector.findMotionPrimitives(login).isEmpty()
                    || SourceInspector.findMotionPrimitives(forgot).isEmpty(),
                    "Auth flow missing motion upgrades");
            return "Verified Auth Splash <-> Login <-> ForgotPassword + motion";
        }));

        out.add(execute("T3_13", 3, f, "Dual-Role Logout Symmetry (Customer, Officer, Placeholder)", "R3, F4, F11, F18", () -> {
            for (String rel : List.of("ui/customer/CustomerProfileFragment.java",
                    "ui/officer/OfficerAuditFragment.java", "ui/common/RolePlaceholderFragment.java")) {
                String src = SourceInspector.stripComments(ctx.readJava(rel));
                assertTrue(src.contains(".logout()") && src.contains("clearSession()")
                        && src.contains("LoginActivity.class") && src.contains("FLAG_ACTIVITY_CLEAR_TASK"),
                        "Logout symmetry broken in " + rel);
            }
            return "Verified logout symmetry across all 3 logout entry points";
        }));

        out.add(execute("T3_14", 3, f, "SessionManager x ApiClient Bearer Token x 3 Retrofit Services", "R3, F19", () -> {
            String apiClient = SourceInspector.stripComments(ctx.readJava("data/remote/ApiClient.java"));
            String session = SourceInspector.stripComments(ctx.readJava("data/local/SessionManager.java"));
            assertTrue(session.contains("getAccessToken") && apiClient.contains("getAccessToken")
                    && apiClient.contains("Bearer ") && apiClient.contains("BuildConfig.BASE_URL"),
                    "ApiClient Bearer token or BASE_URL wiring broken");
            assertTrue(apiClient.contains("AuthApiService") && apiClient.contains("CustomerApiService")
                    && apiClient.contains("OfficerApiService"),
                    "ApiClient missing one of the 3 Retrofit services");
            return "Verified Bearer token injection across all 3 Retrofit services";
        }));

        out.add(execute("T3_15", 3, f, "CustomerAccounts x OfficerRates Savings/Mortgage Consistency", "R1, R3, F8, F17", () -> {
            String acct = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerAccountsFragment.java"));
            String rates = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerRatesFragment.java"));
            assertTrue(acct.contains("SAVINGS") && acct.contains("savingsInterestRate")
                    && rates.contains("\"SAVINGS\"") && rates.contains("\"MORTGAGE\""),
                    "SAVINGS/MORTGAGE domain mismatch");
            assertTrue(ctx.allLayoutFiles().stream().anyMatch(p -> p.getFileName().toString().startsWith("item_account_")),
                    "CustomerAccounts missing XML item_account_*.xml");
            assertTrue(ctx.xml().hasTnum(ctx.xml().parseLayout("fragment_officer_rates.xml").element("edtNewInterestRate")),
                    "OfficerRates edtNewInterestRate missing tnum");
            return "Verified CustomerAccounts x OfficerRates consistency";
        }));

        out.add(execute("T3_16", 3, f, "CustomerProfile Contact x OfficerCustomers Management", "R1, R3, F11, F15", () -> {
            String prof = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerProfileFragment.java"));
            String off = SourceInspector.stripComments(ctx.readJava("ui/officer/OfficerCustomersFragment.java"));
            assertTrue(prof.contains("updateContact(") && off.contains("updateCustomer(")
                    && off.contains("\"phone\"") && off.contains("\"email\"") && off.contains("\"status\""),
                    "Customer/Officer contact management mismatch");
            assertTrue(ctx.xml().parseLayout("bottom_sheet_officer_customer_edit.xml").exists,
                    "OfficerCustomers missing bottom_sheet_officer_customer_edit.xml");
            return "Verified CustomerProfile x OfficerCustomers management";
        }));

        out.add(execute("T3_17", 3, f, "CustomerHome onResume Refresh x Transfer/Utilities", "R2-R3, F7, F9, F12", () -> {
            String home = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerHomeFragment.java"));
            String onResume = SourceInspector.extractMethodBody(home, "public\\s+void\\s+onResume\\s*\\([^)]*\\)");
            assertFalse(onResume.isEmpty(), "CustomerHomeFragment.onResume missing");
            assertTrue(reachesApiCall(onResume, home, ".getAccounts(")
                            && reachesApiCall(onResume, home, ".getHistory("),
                    "CustomerHomeFragment.onResume must reload accounts and history");
            assertTrue(home.contains("animateVndCounter"),
                    "CustomerHomeFragment must animate VND balance updates");
            return "Verified Home onResume refresh + animated counter";
        }));

        out.add(execute("T3_18", 3, f, "CurrencyUtils x tnum Consistency Across All Monetary Screens", "R1, R3, F1-F18", () -> {
            for (String rel : List.of("ui/customer/CustomerHomeFragment.java",
                    "ui/customer/CustomerAccountsFragment.java",
                    "ui/customer/TransferActivity.java",
                    "ui/customer/CustomerUtilitiesFragment.java",
                    "ui/customer/ReceiptDialogHelper.java",
                    "ui/officer/OfficerCreateAccountFragment.java")) {
                assertTrue(SourceInspector.stripComments(ctx.readJava(rel)).contains("CurrencyUtils.format"),
                        rel + " missing CurrencyUtils formatting");
            }
            assertTrue(ctx.xml().hasTnum(ctx.xml().parseLayout("fragment_customer_home.xml").element("tvHomeBalance"))
                    && ctx.xml().hasTnum(ctx.xml().parseLayout("activity_transfer.xml").element("edtAmount"))
                    && ctx.xml().hasTnum(ctx.xml().parseLayout("fragment_officer_create.xml").element("edtInitialDeposit")),
                    "Missing tnum on paired XML monetary views");
            return "Verified CurrencyUtils + tnum across Customer & Officer";
        }));

        out.add(execute("T3_19", 3, f, "Shared bottom_sheet_transfer_confirm Dual-Context Contract", "R1, R3, F9, F12", () -> {
            XmlInspector.LayoutInfo sh = ctx.xml().parseLayout("bottom_sheet_transfer_confirm.xml");
            assertTrue(sh.hasScrollContainerAnywhere(), "Shared bottom sheet lacks ScrollView");
            assertTrue("gone".equalsIgnoreCase(ctx.xml().getEffectiveAttr(sh.element("boxEkycStep"), "visibility")),
                    "boxEkycStep must default to gone for Utilities reuse");
            String tr = SourceInspector.stripComments(ctx.readJava("ui/customer/TransferActivity.java"));
            String ut = SourceInspector.stripComments(ctx.readJava("ui/customer/CustomerUtilitiesFragment.java"));
            assertTrue(tr.contains("BottomSheetTransferConfirmBinding") && ut.contains("BottomSheetTransferConfirmBinding"),
                    "Shared BottomSheetTransferConfirmBinding not used by both Transfer & Utilities");
            return "Verified dual-context bottom_sheet_transfer_confirm.xml contract";
        }));

        out.add(execute("T3_20", 3, f, "ReceiptDialogHelper Share Intent Contract across 3 Callers", "R1, R3, F4, F7, F9, F12", () -> {
            String rec = SourceInspector.stripComments(ctx.readJava("ui/customer/ReceiptDialogHelper.java"));
            assertTrue(rec.contains("Intent.ACTION_SEND") && rec.contains("\"text/plain\"")
                    && rec.contains("Intent.createChooser"), "Receipt share intent broken");
            assertTrue(ctx.xml().parseLayout("dialog_receipt.xml").exists, "Missing dialog_receipt.xml");
            for (String caller : List.of("ui/customer/CustomerHomeFragment.java",
                    "ui/customer/TransferActivity.java", "ui/customer/CustomerUtilitiesFragment.java")) {
                assertTrue(SourceInspector.stripComments(ctx.readJava(caller)).contains("ReceiptDialogHelper.showReceipt"),
                        caller + " missing ReceiptDialogHelper.showReceipt call");
            }
            return "Verified Receipt share contract across all 3 callers";
        }));

        out.add(execute("T3_21", 3, f, "LoginActivity Demo Quick-Fill -> Role Shells & ForgotPassword", "R1-R3, F5, F6, F14", () -> {
            String login = SourceInspector.stripComments(ctx.readJava("ui/auth/LoginActivity.java"));
            assertTrue(login.contains("long.customer") && login.contains("phuc.officer")
                    && login.contains("Phl@2026"), "Demo credentials missing");
            assertTrue(login.contains("OfficerMainActivity.class") && login.contains("CustomerMainActivity.class"),
                    "Role navigation missing");
            assertFalse(SourceInspector.findMotionPrimitives(login).isEmpty(), "LoginActivity lacks motion");
            return "Verified Demo Quick-Fill -> Role Shells";
        }));

        out.add(execute("T3_22", 3, f, "FaceEkyc CameraX + Fallback + TransferActivity Result Contract", "R1-R3, F12, F13", () -> {
            String ek = SourceInspector.stripComments(ctx.readJava("ui/customer/FaceEkycActivity.java"));
            String tr = SourceInspector.stripComments(ctx.readJava("ui/customer/TransferActivity.java"));
            assertTrue(ek.contains("Manifest.permission.CAMERA") && ek.contains("tvCameraFallback")
                    && ek.contains("RESULT_EKYC_TOKEN") && tr.contains("FaceEkycActivity.RESULT_EKYC_TOKEN"),
                    "FaceEkyc <-> TransferActivity result contract broken");
            XmlInspector.LayoutInfo ekXml = ctx.xml().parseLayout("activity_face_ekyc.xml");
            assertTrue((ekXml.hasElementWithIdOrDrawableContaining("reticle")
                            || ekXml.hasElementWithIdOrDrawableContaining("oval"))
                            && ekXml.hasElementWithIdOrDrawableContaining("scanline"),
                    "FaceEkyc missing biometric reticle/scanline overlays");
            return "Verified FaceEkyc CameraX + Fallback + Transfer result contract";
        }));

        return out;
    }

    private static boolean reachesApiCall(String entryBody, String fullSrc, String apiToken) {
        if (entryBody.contains(apiToken)) {
            return true;
        }
        Matcher m = Pattern.compile("\\b(\\w+)\\s*\\(").matcher(entryBody);
        Set<String> seen = new LinkedHashSet<>();
        while (m.find()) {
            String helper = m.group(1);
            if (!seen.add(helper)) {
                continue;
            }
            String body = SourceInspector.extractMethodBody(fullSrc,
                    "\\bvoid\\s+" + Pattern.quote(helper) + "\\s*\\([^)]*\\)");
            if (!body.isEmpty() && body.contains(apiToken)) {
                return true;
            }
        }
        return false;
    }
}
