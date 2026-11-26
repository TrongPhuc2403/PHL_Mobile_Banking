package vn.edu.tdtu.phlbanking.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.data.local.SessionManager;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.model.LoginData;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.ActivityLoginBinding;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;
import vn.edu.tdtu.phlbanking.ui.customer.CustomerMainActivity;
import vn.edu.tdtu.phlbanking.ui.officer.OfficerMainActivity;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        MotionHelper.staggerEntrance(
            binding.layoutLoginHero,
            binding.cardLoginForm,
            binding.cardLoginDemo
        );
        MotionHelper.pulseView(binding.layoutLoginCrest);
        MotionHelper.startShimmerSweep(binding.viewLoginHeroShimmer);
        MotionHelper.applyTactilePress(
            binding.btnLogin,
            binding.tvForgotPassword,
            binding.btnDemoCustomer,
            binding.btnDemoOfficer
        );

        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.tvForgotPassword.setOnClickListener(v -> {
            startActivity(new Intent(this, ForgotPasswordActivity.class));
            MotionHelper.applyActivityTransition(this);
        });

        binding.btnDemoCustomer.setOnClickListener(v -> {
            binding.edtUsername.setText("phuc.customer");
            binding.edtPassword.setText("Phl@2026");
            hideError();
        });

        binding.btnDemoOfficer.setOnClickListener(v -> {
            binding.edtUsername.setText("long.officer");
            binding.edtPassword.setText("Phl@2026");
            hideError();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) {
            MotionHelper.startShimmerSweep(binding.viewLoginHeroShimmer);
            MotionHelper.pulseView(binding.layoutLoginCrest);
        }
    }

    @Override
    protected void onDestroy() {
        if (binding != null) {
            MotionHelper.stopShimmer(binding.viewLoginHeroShimmer);
        }
        super.onDestroy();
    }

    private void attemptLogin() {
        hideError();
        String username =
            binding.edtUsername.getText() == null
                ? ""
                : binding.edtUsername.getText().toString().trim();
        String password =
            binding.edtPassword.getText() == null ? "" : binding.edtPassword.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            return;
        }

        setLoading(true);
        Map<String, String> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("password", password);

        ApiClient.authApi(this).login(payload).enqueue(
            new Callback<ApiResponse<LoginData>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<LoginData>> call,
                    @NonNull Response<ApiResponse<LoginData>> response
                ) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setLoading(false);
                    if (
                        response.isSuccessful() &&
                        response.body() != null &&
                        response.body().data != null
                    ) {
                        LoginData data = response.body().data;
                        String role = data.user.role;
                        String fullName =
                            data.user.profile != null
                                ? data.user.profile.fullName
                                : data.user.username;
                        String identityCode = "";
                        if (data.user.profile != null) {
                            identityCode = "OFFICER".equalsIgnoreCase(role)
                                ? safe(data.user.profile.employeeCode)
                                : safe(data.user.profile.cifNumber);
                        }

                        sessionManager.saveSession(
                            data.accessToken,
                            data.refreshToken,
                            role,
                            data.user.username,
                            fullName,
                            identityCode
                        );

                        Intent next = "OFFICER".equalsIgnoreCase(role)
                            ? new Intent(LoginActivity.this, OfficerMainActivity.class)
                            : new Intent(LoginActivity.this, CustomerMainActivity.class);
                        next.setFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        );
                        startActivity(next);
                        MotionHelper.applyActivityTransition(LoginActivity.this);
                        return;
                    }

                    showError(extractErrorMessage(response));
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<LoginData>> call,
                    @NonNull Throwable t
                ) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setLoading(false);
                    showError(
                        "Không thể kết nối máy chủ. Hãy kiểm tra Backend đang chạy ở cổng 8080."
                    );
                }
            }
        );
    }

    private String extractErrorMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                ApiResponse<?> parsed = new Gson().fromJson(
                    response.errorBody().string(),
                    ApiResponse.class
                );
                if (parsed != null && parsed.error != null && parsed.error.message != null) {
                    return parsed.error.message;
                }
            }
        } catch (Exception ignored) {}
        return "Đăng nhập không thành công (Mã lỗi " + response.code() + ").";
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String msg) {
        binding.tvErrorBanner.setText(msg);
        binding.tvErrorBanner.setVisibility(View.VISIBLE);
        MotionHelper.shakeError(binding.tvErrorBanner);
    }

    private void hideError() {
        binding.tvErrorBanner.setVisibility(View.GONE);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
