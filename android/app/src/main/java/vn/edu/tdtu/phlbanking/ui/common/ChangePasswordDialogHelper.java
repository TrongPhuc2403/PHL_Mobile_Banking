package vn.edu.tdtu.phlbanking.ui.common;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.DialogChangePasswordBinding;

public final class ChangePasswordDialogHelper {

    private ChangePasswordDialogHelper() {}

    public static void show(Context context) {
        if (context == null) {
            return;
        }
        Activity activity = unwrapActivity(context);
        if (activity != null && (activity.isFinishing() || activity.isDestroyed())) {
            return;
        }

        Context appContext = context.getApplicationContext();
        Context themedContext = new ContextThemeWrapper(context, R.style.Theme_PHLBanking);
        BottomSheetDialog dialog = new BottomSheetDialog(themedContext);
        DialogChangePasswordBinding binding = DialogChangePasswordBinding.inflate(
            LayoutInflater.from(dialog.getContext())
        );
        dialog.setContentView(binding.getRoot());

        Window window = dialog.getWindow();
        configureSoftInputMode(window);

        MotionHelper.applyTactilePress(
            binding.btnConfirmChangePassword,
            binding.btnCancelChangePassword
        );

        binding.btnCancelChangePassword.setOnClickListener(v -> dialog.dismiss());

        binding.btnConfirmChangePassword.setOnClickListener(v -> {
            binding.tilCurrentPassword.setError(null);
            binding.tilNewPassword.setError(null);
            binding.tilConfirmPassword.setError(null);
            binding.tvChangePasswordError.setVisibility(View.GONE);

            String currentPass =
                binding.edtCurrentPassword.getText() != null
                    ? binding.edtCurrentPassword.getText().toString()
                    : "";
            String newPass =
                binding.edtNewPassword.getText() != null
                    ? binding.edtNewPassword.getText().toString()
                    : "";
            String confirmPass =
                binding.edtConfirmPassword.getText() != null
                    ? binding.edtConfirmPassword.getText().toString()
                    : "";

            if (currentPass.isEmpty()) {
                binding.tilCurrentPassword.setError(
                    appContext.getString(R.string.change_pw_err_empty)
                );
                MotionHelper.shakeError(binding.tilCurrentPassword);
                return;
            }
            if (newPass.length() < 6) {
                binding.tilNewPassword.setError(
                    appContext.getString(R.string.change_pw_err_length)
                );
                MotionHelper.shakeError(binding.tilNewPassword);
                return;
            }
            if (!newPass.equals(confirmPass)) {
                binding.tilConfirmPassword.setError(
                    appContext.getString(R.string.change_pw_err_mismatch)
                );
                MotionHelper.shakeError(binding.tilConfirmPassword);
                return;
            }

            setLoading(dialog, binding, true);

            Map<String, String> body = new HashMap<>();
            body.put("currentPassword", currentPass);
            body.put("newPassword", newPass);

            ApiClient.customerApi(appContext).changePassword(body).enqueue(
                new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(
                        @NonNull Call<ApiResponse<Void>> call,
                        @NonNull Response<ApiResponse<Void>> response
                    ) {
                        if (
                            !dialog.isShowing() ||
                            (activity != null && (activity.isFinishing() || activity.isDestroyed()))
                        ) {
                            return;
                        }
                        if (response.isSuccessful()) {
                            dialog.dismiss();
                            Toast.makeText(
                                appContext,
                                R.string.change_pw_success,
                                Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            setLoading(dialog, binding, false);
                            binding.tvChangePasswordError.setText(R.string.change_pw_err_current);
                            binding.tvChangePasswordError.setVisibility(View.VISIBLE);
                            MotionHelper.shakeError(binding.tvChangePasswordError);
                        }
                    }

                    @Override
                    public void onFailure(
                        @NonNull Call<ApiResponse<Void>> call,
                        @NonNull Throwable t
                    ) {
                        if (
                            !dialog.isShowing() ||
                            (activity != null && (activity.isFinishing() || activity.isDestroyed()))
                        ) {
                            return;
                        }
                        setLoading(dialog, binding, false);
                        binding.tvChangePasswordError.setText(R.string.err_network);
                        binding.tvChangePasswordError.setVisibility(View.VISIBLE);
                        MotionHelper.shakeError(binding.tvChangePasswordError);
                    }
                }
            );
        });

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet
            );
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            MotionHelper.staggerEntrance(
                binding.layoutChangePasswordHeader,
                binding.layoutChangePasswordForm,
                binding.layoutChangePasswordActions
            );
        });

        dialog.show();
    }

    private static void setLoading(
        @NonNull BottomSheetDialog dialog,
        @NonNull DialogChangePasswordBinding binding,
        boolean loading
    ) {
        dialog.setCancelable(!loading);
        binding.edtCurrentPassword.setEnabled(!loading);
        binding.edtNewPassword.setEnabled(!loading);
        binding.edtConfirmPassword.setEnabled(!loading);
        binding.btnConfirmChangePassword.setEnabled(!loading);
        binding.btnCancelChangePassword.setEnabled(!loading);
        binding.progressChangePassword.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @SuppressWarnings("deprecation")
    private static void configureSoftInputMode(@Nullable Window window) {
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Nullable
    private static Activity unwrapActivity(@Nullable Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }
}
