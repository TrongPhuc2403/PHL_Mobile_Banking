package vn.edu.tdtu.phlbanking;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Smoke test uses normal launcher and visible controls; it never grants camera permission. */
@RunWith(AndroidJUnit4.class)
public class EkycUiSmokeTest {

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String id) {
        if (node == null) return null;
        if (id.equals(node.getViewIdResourceName())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), id);
            if (found != null) return found;
        }
        return null;
    }

    private void dump(AccessibilityNodeInfo node, StringBuilder out, int depth) {
        if (node == null || depth > 25) return;
        out.append(node.getViewIdResourceName())
            .append(" | ")
            .append(node.getText())
            .append(" | enabled=")
            .append(node.isEnabled())
            .append(" | ")
            .append(node.getContentDescription())
            .append("\n");
        for (int i = 0; i < node.getChildCount(); i++) dump(node.getChild(i), out, depth + 1);
    }

    private AccessibilityNodeInfo waitFor(String id) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo node = find(
                instrumentation.getUiAutomation().getRootInActiveWindow(),
                id.contains(":id/") ? id : "vn.edu.tdtu.phlbanking:id/" + id
            );
            if (node != null) return node;
            SystemClock.sleep(100);
        }
        fail("Visible control not found: " + id);
        return null;
    }

    private void click(String id) {
        assertTrue(waitFor(id).performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }

    private void setText(String id, String value) {
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        assertTrue(waitFor(id).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));
    }

    private void ensureCustomerHome() {
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo login = find(root, "vn.edu.tdtu.phlbanking:id/btnLogin");
            AccessibilityNodeInfo home = find(root, "vn.edu.tdtu.phlbanking:id/tab_cust_home");
            if (login != null && login.isEnabled()) {
                click("btnDemoCustomer");
                click("btnLogin");
                waitFor("tab_cust_home");
                click("tab_cust_home");
                return;
            }
            if (home != null) {
                home.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                // An expired session can briefly show Home before the API redirects to Login.
                long settled = SystemClock.uptimeMillis() + 3000;
                while (SystemClock.uptimeMillis() < settled) {
                    AccessibilityNodeInfo visible = instrumentation
                        .getUiAutomation()
                        .getRootInActiveWindow();
                    if (find(visible, "vn.edu.tdtu.phlbanking:id/btnLogin") != null) break;
                    SystemClock.sleep(100);
                }
                AccessibilityNodeInfo visible = instrumentation
                    .getUiAutomation()
                    .getRootInActiveWindow();
                if (
                    find(visible, "vn.edu.tdtu.phlbanking:id/btnLogin") == null &&
                    find(visible, "vn.edu.tdtu.phlbanking:id/btnHomeEkyc") != null
                ) return;
            }
            SystemClock.sleep(100);
        }
        fail("Neither login form nor customer navigation became visible");
    }

    private void checkTransactionGate() {
        ensureCustomerHome();
        waitFor("btnHomeEkyc");
        click("btnQuickTransfer");
        setText("edtDestAccount", "52400114");
        setText("edtAmount", "15000000");
        click("btnContinueTransfer");
        assertFalse("High-value OTP must remain blocked", waitFor("btnSendOtp").isEnabled());
        assertFalse(
            "High-value execute must remain blocked",
            waitFor("btnExecuteTransaction").isEnabled()
        );
        assertFalse("OTP input must remain blocked", waitFor("edtConfirmOtp").isEnabled());
        assertTrue(waitFor("tvSecurityProgress").getText().toString().contains("OTP"));
        click("btnVerifyEkyc");
        waitFor("android:id/button2").performAction(AccessibilityNodeInfo.ACTION_CLICK); // Decline consent, no permission bypass.
        assertFalse("Cancel eKYC must not unlock OTP", waitFor("btnSendOtp").isEnabled());
        assertFalse(
            "Cancel eKYC must not unlock execute",
            waitFor("btnExecuteTransaction").isEnabled()
        );
        Bundle status = new Bundle();
        status.putString(
            "stream",
            "\nPASS: 15M → eKYC mandatory; OTP and execute disabled; decline returns blocked. No money moved.\n"
        );
        instrumentation.sendStatus(0, status);
    }

    private AccessibilityNodeInfo waitEnabled(String id) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo node = waitFor(id);
            if (node.isEnabled()) return node;
            SystemClock.sleep(100);
        }
        fail("Control remains disabled: " + id);
        return null;
    }

    private void checkSelfEnrollment(boolean inspectFirstFrame) {
        ensureCustomerHome();
        waitFor("btnHomeEkyc");
        click("btnHomeEkyc");
        AccessibilityNodeInfo action = waitEnabled("btnProfileEkyc");
        assertTrue(
            "Must show first-time self registration",
            action.getText().toString().contains("Đăng ký")
        );
        click("btnProfileEkyc");
        assertFalse(waitFor("btnConfirmEnrollmentOtp").isEnabled());
        setText("edtEnrollmentPassword", "Phl@2026");
        click("btnSendEnrollmentOtp");
        long deadline = SystemClock.uptimeMillis() + 15000;
        String code = null;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo node = find(
                instrumentation.getUiAutomation().getRootInActiveWindow(),
                "vn.edu.tdtu.phlbanking:id/tvEnrollmentOtpNote"
            );
            if (node != null && node.getText() != null) {
                Matcher match = Pattern.compile("OTP đăng ký: ([0-9]{6})").matcher(node.getText());
                if (match.find()) {
                    code = match.group(1);
                    break;
                }
            }
            SystemClock.sleep(100);
        }
        assertNotNull("No DEV OTP shown in normal UI", code);
        AccessibilityNodeInfo input = waitFor("edtEnrollmentOtp");
        assertFalse(
            "OTP must not auto-fill",
            code.contentEquals(input.getText() == null ? "" : input.getText())
        );
        setText("edtEnrollmentOtp", code);
        click("btnConfirmEnrollmentOtp");
        waitFor("android:id/button1");
        assertTrue(
            "Must show registration consent before capturing",
            waitFor("android:id/message")
                .getText()
                .toString()
                .contains("đăng ký ảnh gốc cho chính tài khoản")
        );
        if (inspectFirstFrame) {
            click("android:id/button1"); // Normal informed consent; test never grants a system camera permission.
            AccessibilityNodeInfo capture = waitEnabled("btnCaptureVerifyFace");
            long readyDeadline = SystemClock.uptimeMillis() + 15000;
            while (
                !capture.getText().toString().contains("1 ·") &&
                SystemClock.uptimeMillis() < readyDeadline
            ) {
                SystemClock.sleep(100);
                capture = waitEnabled("btnCaptureVerifyFace");
            }
            assertTrue(
                "Capture challenge must be ready",
                capture.getText().toString().contains("1 ·")
            );
            click("btnCaptureVerifyFace");
            long checkedDeadline = SystemClock.uptimeMillis() + 20000;
            String feedback = "";
            while (SystemClock.uptimeMillis() < checkedDeadline) {
                AccessibilityNodeInfo note = find(
                    instrumentation.getUiAutomation().getRootInActiveWindow(),
                    "vn.edu.tdtu.phlbanking:id/tvCapturedFrameStatus"
                );
                if (note != null && note.getText() != null) {
                    feedback = note.getText().toString();
                    if (feedback.contains("Đã thấy") || feedback.contains("chưa đạt")) break;
                }
                SystemClock.sleep(100);
            }
            AccessibilityNodeInfo instruction = find(
                instrumentation.getUiAutomation().getRootInActiveWindow(),
                "vn.edu.tdtu.phlbanking:id/tvEkycInstruction"
            );
            String detail =
                instruction == null || instruction.getText() == null
                    ? ""
                    : instruction.getText().toString();
            Bundle checked = new Bundle();
            checked.putString(
                "stream",
                "\nFIRST_CAPTURE: " + feedback + "\nDETAIL: " + detail + "\n"
            );
            instrumentation.sendStatus(0, checked);
            // Keep the failed capture visible for diagnosis; never submit a second frame or final enrollment.
            assertTrue(
                "First-frame camera inspection must accept a visible clear face: " +
                    feedback +
                    " | " +
                    detail,
                feedback.contains("Đã thấy")
            );
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitEnabled("btnProfileEkyc");
        } else click("android:id/button2"); // User declines camera/biometric consent, no enrollment occurs.
        AccessibilityNodeInfo after = waitEnabled("btnProfileEkyc");
        assertTrue(
            "Cancel must not mark profile enrolled",
            after.getText().toString().contains("Đăng ký")
        );
        Bundle status = new Bundle();
        status.putString(
            "stream",
            "\nPASS: Cá nhân → Đăng ký → mật khẩu + nhập tay OTP → đồng ý camera; từ chối vẫn chưa đăng ký. Không lưu ảnh mặt, không chuyển tiền.\n"
        );
        instrumentation.sendStatus(0, status);
    }

    @Test
    public void inspectLiveSecurityUi() {
        Intent intent = instrumentation
            .getTargetContext()
            .getPackageManager()
            .getLaunchIntentForPackage("vn.edu.tdtu.phlbanking");
        assertNotNull(intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        instrumentation.getTargetContext().startActivity(intent);
        String action = InstrumentationRegistry.getArguments().getString("uiAction", "dump");
        long deadline = SystemClock.uptimeMillis() + 10000;
        AccessibilityNodeInfo root = null;
        while (SystemClock.uptimeMillis() < deadline) {
            root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (
                root != null && "vn.edu.tdtu.phlbanking".contentEquals(root.getPackageName())
            ) break;
            SystemClock.sleep(100);
        }
        assertNotNull("No visible app window", root);
        if ("transactionGate".equals(action)) {
            checkTransactionGate();
        } else if ("selfEnrollment".equals(action)) {
            checkSelfEnrollment(false);
        } else if ("inspectCameraFrame".equals(action)) {
            checkSelfEnrollment(true);
        } else if (!"dump".equals(action)) {
            AccessibilityNodeInfo target = find(root, "vn.edu.tdtu.phlbanking:id/" + action);
            assertNotNull("Visible control not found: " + action, target);
            assertTrue(
                "Cannot click visible control",
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            );
            SystemClock.sleep(700);
        }
        root = instrumentation.getUiAutomation().getRootInActiveWindow();
        StringBuilder text = new StringBuilder();
        dump(root, text, 0);
        Bundle result = new Bundle();
        result.putString("stream", "\nLIVE_UI\n" + text);
        instrumentation.sendStatus(0, result);
        assertTrue("No live UI captured", text.length() > 0);
    }
}
