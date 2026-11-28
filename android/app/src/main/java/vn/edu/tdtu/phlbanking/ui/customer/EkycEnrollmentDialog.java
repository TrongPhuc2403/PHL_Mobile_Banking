package vn.edu.tdtu.phlbanking.ui.customer;

import android.content.Context;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.DialogEkycEnrollmentBinding;
import vn.edu.tdtu.phlbanking.ui.common.ApiErrors;

/** Password + purpose-bound OTP. No images are collected before the separate consent screen. */
public final class EkycEnrollmentDialog {

    private final Context context;
    private final Consumer<String> onAuthorized;
    private final DialogEkycEnrollmentBinding binding;
    private final AlertDialog dialog;
    private Call<ApiResponse<Map<String, Object>>> pending;
    private CountDownTimer timer;
    private String requestId;
    private String otpNote = "";
    private long resendAt, expiresAt;
    private boolean busy;

    public static AlertDialog show(Context context, Consumer<String> onAuthorized) {
        EkycEnrollmentDialog controller = new EkycEnrollmentDialog(context, onAuthorized);
        controller.dialog.show();
        return controller.dialog;
    }

    private EkycEnrollmentDialog(Context context, Consumer<String> onAuthorized) {
        this.context = context;
        this.onAuthorized = onAuthorized;
        binding = DialogEkycEnrollmentBinding.inflate(LayoutInflater.from(context));
        dialog = new MaterialAlertDialogBuilder(context)
            .setTitle("Đăng ký sinh trắc học")
            .setView(binding.getRoot())
            .create();
        binding.btnSendEnrollmentOtp.setOnClickListener(v -> requestOtp());
        binding.btnConfirmEnrollmentOtp.setOnClickListener(v -> confirmOtp());
        binding.btnCancelEnrollment.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            if (pending != null) pending.cancel();
            if (timer != null) timer.cancel();
            requestId = null;
            binding.edtEnrollmentPassword.setText("");
            binding.edtEnrollmentOtp.setText("");
        });
    }

    private String text(android.widget.EditText value) {
        return value.getText() == null ? "" : value.getText().toString();
    }

    private void setBusy(boolean value) {
        busy = value;
        binding.progressEnrollment.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.btnSendEnrollmentOtp.setEnabled(
            !value && SystemClock.elapsedRealtime() >= resendAt
        );
        binding.edtEnrollmentPassword.setEnabled(!value);
        binding.edtEnrollmentOtp.setEnabled(!value);
        binding.btnConfirmEnrollmentOtp.setEnabled(
            !value && requestId != null && SystemClock.elapsedRealtime() < expiresAt
        );
    }

    private void error(String value) {
        binding.tvEnrollmentError.setText(value);
        binding.tvEnrollmentError.setVisibility(View.VISIBLE);
    }

    private void requestOtp() {
        String password = text(binding.edtEnrollmentPassword);
        binding.tilEnrollmentPassword.setError(null);
        binding.tvEnrollmentError.setVisibility(View.GONE);
        if (password.length() < 4) {
            binding.tilEnrollmentPassword.setError("Nhập mật khẩu hiện tại của bạn");
            return;
        }
        requestId = null;
        binding.edtEnrollmentOtp.setText("");
        if (timer != null) timer.cancel();
        setBusy(true);
        Map<String, Object> body = new HashMap<>();
        body.put("currentPassword", password);
        pending = ApiClient.customerApi(context).requestEnrollmentOtp(body);
        pending.enqueue(
            new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Response<ApiResponse<Map<String, Object>>> response
                ) {
                    if (!dialog.isShowing() || call.isCanceled()) return;
                    binding.edtEnrollmentPassword.setText("");
                    if (
                        response.isSuccessful() &&
                        response.body() != null &&
                        response.body().data != null
                    ) {
                        Map<String, Object> data = response.body().data;
                        Object id = data.get("requestId");
                        if (id == null) {
                            setBusy(false);
                            error("Không nhận được phiên đăng ký. Vui lòng thử lại.");
                            return;
                        }
                        requestId = String.valueOf(id);
                        long now = SystemClock.elapsedRealtime();
                        int seconds =
                            data.get("expiresInSeconds") instanceof Number
                                ? ((Number) data.get("expiresInSeconds")).intValue()
                                : 120;
                        resendAt = now + 30_000;
                        expiresAt = now + seconds * 1000L;
                        Object preview = otpPreview(data);
                        otpNote =
                            preview == null
                                ? "Nhập OTP đăng ký được gửi tới bạn."
                                : "CHẾ ĐỘ DEV · OTP đăng ký: " +
                                  preview +
                                  ". Hãy nhập tay, chưa có SMS thật.";
                        Toast.makeText(context.getApplicationContext(), otpNote, Toast.LENGTH_LONG).show();
                        binding.tilEnrollmentOtp.setVisibility(View.VISIBLE);
                        binding.tvEnrollmentOtpNote.setVisibility(View.VISIBLE);
                        setBusy(false);
                        timer = new CountDownTimer(seconds * 1000L, 1000) {
                            @Override
                            public void onTick(long remaining) {
                                binding.tvEnrollmentOtpNote.setText(
                                    otpNote + "\nCòn " + remaining / 1000 + " giây."
                                );
                                long wait = Math.max(
                                    0,
                                    (resendAt - SystemClock.elapsedRealtime() + 999) / 1000
                                );
                                binding.btnSendEnrollmentOtp.setText(
                                    wait > 0
                                        ? "Gửi lại sau " + wait + " giây"
                                        : "Nhập lại mật khẩu để gửi OTP mới"
                                );
                                if (!busy) binding.btnSendEnrollmentOtp.setEnabled(wait == 0);
                            }

                            @Override
                            public void onFinish() {
                                requestId = null;
                                binding.btnConfirmEnrollmentOtp.setEnabled(false);
                                binding.tvEnrollmentOtpNote.setText(
                                    "OTP đã hết hạn. Nhập lại mật khẩu để gửi mã mới."
                                );
                                if (!busy) binding.btnSendEnrollmentOtp.setEnabled(true);
                            }
                        }.start();
                    } else {
                        setBusy(false);
                        error(ApiErrors.message(response, "Không thể gửi OTP đăng ký"));
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Throwable t
                ) {
                    if (!dialog.isShowing() || call.isCanceled()) return;
                    binding.edtEnrollmentPassword.setText("");
                    setBusy(false);
                    error("Mất kết nối khi gửi OTP. Chưa được cấp quyền đăng ký.");
                }
            }
        );
    }

    private Object otpPreview(Map<String, Object> data) {
        for (String key : new String[] { "debugOtp", "otpCode", "otp", "devOtpPreview" }) {
            Object value = data.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()) return value;
        }
        return null;
    }

    private void confirmOtp() {
        binding.tilEnrollmentOtp.setError(null);
        binding.tvEnrollmentError.setVisibility(View.GONE);
        String code = text(binding.edtEnrollmentOtp).trim();
        if (!code.matches("[0-9]{6}")) {
            binding.tilEnrollmentOtp.setError("Nhập đủ 6 số OTP đăng ký");
            return;
        }
        if (requestId == null || SystemClock.elapsedRealtime() >= expiresAt) {
            error("Phiên OTP đã hết hạn. Vui lòng gửi mã mới.");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("requestId", requestId);
        body.put("otpCode", code);
        setBusy(true);
        pending = ApiClient.customerApi(context).confirmEnrollmentOtp(body);
        pending.enqueue(
            new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Response<ApiResponse<Map<String, Object>>> response
                ) {
                    if (!dialog.isShowing() || call.isCanceled()) return;
                    if (
                        response.isSuccessful() &&
                        response.body() != null &&
                        response.body().data != null &&
                        response.body().data.get("enrollmentToken") != null
                    ) {
                        String token = String.valueOf(response.body().data.get("enrollmentToken"));
                        dialog.dismiss();
                        onAuthorized.accept(token);
                    } else {
                        setBusy(false);
                        error(ApiErrors.message(response, "OTP đăng ký không hợp lệ"));
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Throwable t
                ) {
                    if (!dialog.isShowing() || call.isCanceled()) return;
                    setBusy(false);
                    error("Không xác nhận được OTP. Nếu mã đã được dùng, hãy gửi mã mới.");
                }
            }
        );
    }
}
