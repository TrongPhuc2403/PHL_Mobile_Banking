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
        if (username.isEmpty()) {
            String msg = "Vui lòng nhập tên đăng nhập";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            showFeedbackError(msg);
            MotionHelper.shakeError(binding.cardForgotStep1);
            return;
        }
        hideFeedback();
        setOtpLoading(true);

        Map<String, String> body = new HashMap<>();
        body.put("username", username);

        ApiClient.authApi(this).requestForgotOtp(body).enqueue(
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
                        Object preview = response.body().data.get("devOtpPreview");
                        String msg =
                            preview != null
                                ? "Đã gửi OTP (Dev preview: " + preview + ")"
                                : "Đã gửi mã OTP về số điện thoại đăng ký";
                        Toast.makeText(ForgotPasswordActivity.this, msg, Toast.LENGTH_LONG).show();
                        showFeedbackSuccess(msg);
                        advanceToResetStep();
                    } else {
                        String err = "Không tìm thấy tài khoản khách hàng";
                        Toast.makeText(ForgotPasswordActivity.this, err, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(ForgotPasswordActivity.this, err, Toast.LENGTH_SHORT).show();
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

        if (username.isEmpty() || otp.length() != 6 || newPass.length() < 6) {
            String msg = "Nhập đủ tên đăng nhập, OTP 6 số và mật khẩu >= 6 ký tự";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(
                            ForgotPasswordActivity.this,
                            "Đặt lại mật khẩu thành công",
                            Toast.LENGTH_SHORT
                        ).show();
                        finish();
                        MotionHelper.applyActivityTransition(ForgotPasswordActivity.this);
                    } else {
                        String err = "OTP không hợp lệ hoặc đã hết hạn";
                        Toast.makeText(ForgotPasswordActivity.this, err, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(ForgotPasswordActivity.this, err, Toast.LENGTH_SHORT).show();
                    showFeedbackError(err);
                    MotionHelper.shakeError(binding.cardForgotStep2);
                }
            }
        );
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
