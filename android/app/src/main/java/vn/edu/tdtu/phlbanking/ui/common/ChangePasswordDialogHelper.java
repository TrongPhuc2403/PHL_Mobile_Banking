package vn.edu.tdtu.phlbanking.ui.common;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.DialogChangePasswordBinding;

public final class ChangePasswordDialogHelper {
    private static final Pattern PASSWORD_POLICY = Pattern.compile("(?=.{8,20}$)(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[^A-Za-z0-9])[\\x21-\\x7E]+");
    private ChangePasswordDialogHelper() {}

    public static void show(Context context) {
        if (context == null) return;
        Activity activity = unwrapActivity(context);
        if (activity != null && (activity.isFinishing() || activity.isDestroyed())) return;
        Context appContext = context.getApplicationContext();
        BottomSheetDialog dialog = new BottomSheetDialog(new ContextThemeWrapper(context, R.style.Theme_PHLBanking));
        DialogChangePasswordBinding binding = DialogChangePasswordBinding.inflate(LayoutInflater.from(dialog.getContext()));
        dialog.setContentView(binding.getRoot());
        configureSoftInputMode(dialog.getWindow());
        MotionHelper.applyTactilePress(binding.btnConfirmChangePassword, binding.btnCancelChangePassword, binding.btnRequestOtp);
        AtomicBoolean otpBusy = new AtomicBoolean(false);
        AtomicBoolean submitBusy = new AtomicBoolean(false);
        Call<?>[] pendingOtp = new Call<?>[1];
        Call<?>[] pendingSubmit = new Call<?>[1];
        dialog.setOnDismissListener(d -> {
            if (pendingOtp[0] != null) pendingOtp[0].cancel();
            if (pendingSubmit[0] != null) pendingSubmit[0].cancel();
        });
        binding.btnCancelChangePassword.setOnClickListener(v -> dialog.dismiss());

        binding.btnRequestOtp.setOnClickListener(v -> {
            if (!dialog.isShowing() || !otpBusy.compareAndSet(false, true) || submitBusy.get()) return;
            binding.tvChangePasswordError.setVisibility(View.GONE);
            setOtpLoading(binding, true);
            Map<String, Object> request = new HashMap<>();
            request.put("otpType", "CHANGE_PASSWORD");
            Call<ApiResponse<Map<String, Object>>> call = ApiClient.customerApi(appContext).requestOtp(request);
            pendingOtp[0] = call;
            call.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> c, @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                    otpBusy.set(false);
                    if (!dialog.isShowing()) return;
                    setOtpLoading(binding, false);
                    if (!response.isSuccessful() || response.body() == null || !response.body().success) {
                        showError(binding, appContext.getString(R.string.err_network)); return;
                    }
                    Map<String, Object> data = response.body().data;
                    String otp = null;
                    if (data != null) {
                        for (String key : new String[]{"debugOtp", "otpCode", "otp", "devOtpPreview"}) {
                            Object value = data.get(key);
                            if (value != null && !String.valueOf(value).isEmpty()) { otp = String.valueOf(value); break; }
                        }
                    }
                    String message = "OTP đã được gửi. Vui lòng kiểm tra tin nhắn.";
                    if (otp != null) message += " Mã OTP thử nghiệm: " + otp;
                    Object expiry = data == null ? null : data.get("expiresInSeconds");
                    if (expiry != null) message += " (hiệu lực " + expiry + " giây)";
                    binding.tvOtpBanner.setText(message);
                    binding.tvOtpBanner.setVisibility(View.VISIBLE);
                    Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
                }
                @Override public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> c, @NonNull Throwable t) {
                    otpBusy.set(false);
                    if (!dialog.isShowing()) return;
                    setOtpLoading(binding, false);
                    showError(binding, appContext.getString(R.string.err_network));
                }
            });
        });

        binding.btnConfirmChangePassword.setOnClickListener(v -> {
            if (!dialog.isShowing() || !submitBusy.compareAndSet(false, true) || otpBusy.get()) return;
            binding.tilCurrentPassword.setError(null);
            binding.tilNewPassword.setError(null);
            binding.tilConfirmPassword.setError(null);
            binding.tilOtpCode.setError(null);
            binding.tvChangePasswordError.setVisibility(View.GONE);
            String current = text(binding.edtCurrentPassword);
            String password = text(binding.edtNewPassword);
            String confirmation = text(binding.edtConfirmPassword);
            String otp = text(binding.edtOtpCode);
            if (current.isEmpty()) { submitBusy.set(false); binding.tilCurrentPassword.setError(appContext.getString(R.string.change_pw_err_empty)); return; }
            if (!PASSWORD_POLICY.matcher(password).matches()) { submitBusy.set(false); binding.tilNewPassword.setError("Mật khẩu cần 8–20 ký tự ASCII, gồm chữ hoa, chữ thường, số và ký tự đặc biệt."); return; }
            if (!password.equals(confirmation)) { submitBusy.set(false); binding.tilConfirmPassword.setError(appContext.getString(R.string.change_pw_err_mismatch)); return; }
            if (!otp.matches("[0-9]{6}")) { submitBusy.set(false); binding.tilOtpCode.setError("Vui lòng nhập mã OTP gồm 6 chữ số."); return; }
            setLoading(dialog, binding, true);
            Map<String, String> body = new HashMap<>();
            body.put("currentPassword", current);
            body.put("newPassword", password);
            body.put("otpCode", otp);
            Call<ApiResponse<Void>> call = ApiClient.customerApi(appContext).changePassword(body);
            pendingSubmit[0] = call;
            call.enqueue(new Callback<ApiResponse<Void>>() {
                @Override public void onResponse(@NonNull Call<ApiResponse<Void>> c, @NonNull Response<ApiResponse<Void>> response) {
                    submitBusy.set(false);
                    if (!dialog.isShowing() || (activity != null && (activity.isFinishing() || activity.isDestroyed()))) return;
                    if (response.isSuccessful()) {
                        dialog.dismiss();
                        Toast.makeText(appContext, R.string.change_pw_success, Toast.LENGTH_SHORT).show();
                    } else {
                        setLoading(dialog, binding, false);
                        showError(binding, response.message().isEmpty() ? appContext.getString(R.string.change_pw_err_current) : response.message());
                    }
                }
                @Override public void onFailure(@NonNull Call<ApiResponse<Void>> c, @NonNull Throwable t) {
                    submitBusy.set(false);
                    if (!dialog.isShowing() || (activity != null && (activity.isFinishing() || activity.isDestroyed()))) return;
                    setLoading(dialog, binding, false);
                    showError(binding, appContext.getString(R.string.err_network));
                }
            });
        });
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) { sheet.setBackgroundResource(android.R.color.transparent); BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet); behavior.setSkipCollapsed(true); behavior.setState(BottomSheetBehavior.STATE_EXPANDED); }
            MotionHelper.staggerEntrance(binding.layoutChangePasswordHeader, binding.layoutChangePasswordForm, binding.layoutChangePasswordActions);
        });
        dialog.show();
    }

    private static String text(android.widget.EditText editText) { return editText.getText() == null ? "" : editText.getText().toString().trim(); }
    private static void showError(DialogChangePasswordBinding binding, String message) { binding.tvChangePasswordError.setText(message); binding.tvChangePasswordError.setVisibility(View.VISIBLE); MotionHelper.shakeError(binding.tvChangePasswordError); }
    private static void setOtpLoading(DialogChangePasswordBinding binding, boolean loading) { binding.btnRequestOtp.setEnabled(!loading); binding.btnRequestOtp.setText(loading ? "Đang gửi OTP…" : "Gửi mã OTP"); }
    private static void setLoading(@NonNull BottomSheetDialog dialog, @NonNull DialogChangePasswordBinding binding, boolean loading) {
        dialog.setCancelable(!loading);
        binding.edtCurrentPassword.setEnabled(!loading); binding.edtNewPassword.setEnabled(!loading); binding.edtConfirmPassword.setEnabled(!loading); binding.edtOtpCode.setEnabled(!loading);
        binding.btnConfirmChangePassword.setEnabled(!loading); binding.btnRequestOtp.setEnabled(!loading); binding.btnCancelChangePassword.setEnabled(!loading);
        binding.progressChangePassword.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
    @SuppressWarnings("deprecation") private static void configureSoftInputMode(@Nullable Window window) { if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE); }
    @Nullable private static Activity unwrapActivity(@Nullable Context context) { Context current = context; while (current instanceof ContextWrapper) { if (current instanceof Activity) return (Activity) current; current = ((ContextWrapper) current).getBaseContext(); } return null; }
}
