package vn.edu.tdtu.phlbanking.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.textfield.TextInputEditText;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.ActivityForgotPasswordBinding;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbarForgot.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.toolbarForgot.setNavigationOnClickListener(v -> {
            finish();
            MotionHelper.applyActivityTransition(this);
        });

        MotionHelper.staggerEntrance(
            binding.layoutForgotHero,
            binding.layoutForgotSteps,
            binding.cardForgotStep1,
            binding.cardForgotStep2
        );
        MotionHelper.applyTactilePress(binding.btnRequestOtp, binding.btnConfirmReset);

        binding.btnRequestOtp.setOnClickListener(v -> requestOtp());
        binding.btnConfirmReset.setOnClickListener(v -> confirmReset());
    }

    private void requestOtp() {
        String username = textOf(binding.edtForgotUser);
        String idCardNumber = textOf(binding.edtForgotIdCard);
        if (username.isEmpty() || !idCardNumber.matches("[0-9]{12}")) {
            String msg = username.isEmpty()
                ? "Vui lòng nhập tên đăng nhập"
                : "CCCD phải gồm đúng 12 chữ số";
            showToast(msg, Toast.LENGTH_SHORT);
            showFeedbackError(msg);
            MotionHelper.shakeError(binding.cardForgotStep1);
            return;
        }
        hideFeedback();
        setOtpLoading(true);

        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("idCardNumber", idCardNumber);

        ApiClient.authApi(getApplicationContext()).requestForgotOtp(body).enqueue(
            new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Response<ApiResponse<Map<String, Object>>> response
                ) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setOtpLoading(false);
                    if (
                        response.isSuccessful() &&
                        response.body() != null &&
                        response.body().data != null
                    ) {
                        Map<String, Object> data = response.body().data;
                        Object preview = null;
                        for (String key : new String[] {"debugOtp", "otpCode", "otp", "devOtpPreview"}) {
                            if (data.containsKey(key) && data.get(key) != null) {
                                preview = data.get(key);
                                break;
                            }
                        }
                        String serverMessage = response.body().message;
                        Object expiresIn = data.get("expiresInSeconds");
                        String msg;
                        if (preview != null) {
                            msg = "Đã gửi OTP thành công (Dev preview: " + preview + ")";
                            if (expiresIn != null) {
                                msg += " · Hết hạn sau " + expiresIn + " giây";
                            }
                        } else {
                            msg = serverMessage != null && !serverMessage.trim().isEmpty()
                                ? serverMessage
                                : "Đã gửi mã OTP về số điện thoại đăng ký";
                        }
                        binding.tvOtpBanner.setText(msg);
                        binding.tvOtpBanner.setVisibility(View.VISIBLE);
                        showToast(msg, Toast.LENGTH_LONG);
                        advanceToResetStep();
                    } else {
                        String err = "Không tìm thấy tài khoản khách hàng";
                        showToast(err, Toast.LENGTH_SHORT);
                        showFeedbackError(err);
                        MotionHelper.shakeError(binding.cardForgotStep1);
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Throwable t
                ) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setOtpLoading(false);
                    String err = "Lỗi kết nối máy chủ";
                    showToast(err, Toast.LENGTH_SHORT);
                    showFeedbackError(err);
                    MotionHelper.shakeError(binding.cardForgotStep1);
                }
            }
        );
    }

    private void confirmReset() {
        String username = textOf(binding.edtForgotUser);
        String otp = textOf(binding.edtForgotOtp);
        String newPass = textOf(binding.edtForgotNewPass);

        boolean strongPassword = newPass.length() >= 8 && newPass.length() <= 20
            && newPass.matches("[\\x21-\\x7E]+")
            && newPass.matches(".*[A-Z].*")
            && newPass.matches(".*[a-z].*")
            && newPass.matches(".*[0-9].*")
            && newPass.matches(".*[^A-Za-z0-9].*");
        if (username.isEmpty() || otp.length() != 6 || !otp.matches("[0-9]{6}") || !strongPassword) {
            String msg = "Nhập tên đăng nhập, OTP 6 số và mật khẩu 8–20 ký tự gồm chữ hoa, chữ thường, số và ký tự đặc biệt";
            showToast(msg, Toast.LENGTH_SHORT);
            showFeedbackError(msg);
            MotionHelper.shakeError(binding.cardForgotStep2);
            return;
        }
        hideFeedback();
        setResetLoading(true);

        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("otpCode", otp);
        body.put("newPassword", newPass);

        ApiClient.authApi(this).confirmForgotPassword(body).enqueue(
            new Callback<ApiResponse<Void>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Void>> call,
                    @NonNull Response<ApiResponse<Void>> response
                ) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setResetLoading(false);
                    if (response.isSuccessful()) {
                    showToast("Đặt lại mật khẩu thành công", Toast.LENGTH_SHORT);
                        finish();
                        MotionHelper.applyActivityTransition(ForgotPasswordActivity.this);
                    } else {
                        String err = "OTP không hợp lệ hoặc đã hết hạn";
                        showToast(err, Toast.LENGTH_SHORT);
                        showFeedbackError(err);
                        MotionHelper.shakeError(binding.cardForgotStep2);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Void>> call, @NonNull Throwable t) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setResetLoading(false);
                    String err = "Lỗi kết nối máy chủ";
                    showToast(err, Toast.LENGTH_SHORT);
                    showFeedbackError(err);
                    MotionHelper.shakeError(binding.cardForgotStep2);
                }
            }
        );
    }

    private void showToast(String message, int duration) {
        Toast.makeText(getApplicationContext(), message, duration).show();
    }

    private void advanceToResetStep() {
        binding.tvStepOtpBadge.setBackgroundResource(R.drawable.bg_pill_emerald);
        binding.tvStepOtpBadge.setTextColor(ContextCompat.getColor(this, R.color.phl_emerald_dark));
        binding.tvStepOtpBadge.setText("✓ 1 · Đã gửi OTP");

        binding.tvStepResetBadge.setBackgroundResource(R.drawable.bg_pill_navy);
        binding.tvStepResetBadge.setTextColor(ContextCompat.getColor(this, R.color.phl_navy));
        MotionHelper.pulseView(binding.tvStepResetBadge);
    }

    private void showFeedbackSuccess(String msg) {
        binding.tvForgotFeedback.setBackgroundResource(R.drawable.bg_m2_success_banner);
        binding.tvForgotFeedback.setTextColor(
            ContextCompat.getColor(this, R.color.phl_emerald_dark)
        );
        binding.tvForgotFeedback.setText(msg);
        binding.tvForgotFeedback.setVisibility(View.VISIBLE);
    }

    private void showFeedbackError(String msg) {
        binding.tvForgotFeedback.setBackgroundResource(R.drawable.bg_error_banner);
        binding.tvForgotFeedback.setTextColor(ContextCompat.getColor(this, R.color.phl_danger));
        binding.tvForgotFeedback.setText(msg);
        binding.tvForgotFeedback.setVisibility(View.VISIBLE);
        MotionHelper.shakeError(binding.tvForgotFeedback);
    }

    private void hideFeedback() {
        binding.tvForgotFeedback.setVisibility(View.GONE);
    }

    private void setOtpLoading(boolean loading) {
        binding.btnRequestOtp.setEnabled(!loading);
        binding.progressForgotOtp.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void setResetLoading(boolean loading) {
        binding.btnConfirmReset.setEnabled(!loading);
        binding.progressForgotReset.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String textOf(TextInputEditText edt) {
        return edt.getText() == null ? "" : edt.getText().toString().trim();
    }
}
