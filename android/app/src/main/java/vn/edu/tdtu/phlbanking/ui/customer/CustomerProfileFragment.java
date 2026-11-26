package vn.edu.tdtu.phlbanking.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.data.local.SessionManager;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.model.CustomerModels;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.FragmentCustomerProfileBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemPolicyAlertBinding;
import vn.edu.tdtu.phlbanking.ui.auth.LoginActivity;
import vn.edu.tdtu.phlbanking.ui.common.ChangePasswordDialogHelper;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;

public class CustomerProfileFragment extends Fragment {

    private FragmentCustomerProfileBinding binding;
    private SessionManager session;
    private boolean biometricEnrolled, biometricStatusLoaded;
    private AlertDialog enrollmentDialog;
    private final ActivityResultLauncher<Intent> biometricLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> loadEkycStatus()
    );

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentCustomerProfileBinding.inflate(inflater, container, false);
        session = new SessionManager(requireContext());
        String fullName = session.getFullName();
        binding.tvProfileHeader.setText(fullName + " · " + session.getIdentityCode());
        binding.tvProfileMonogram.setText(extractMonogram(fullName));

        MotionHelper.staggerEntrance(
            binding.layoutProfileBanner,
            binding.cardProfileBiometric,
            binding.cardProfileContact,
            binding.cardProfileNotifications,
            binding.cardProfileSecurity
        );
        MotionHelper.pulseView(binding.avatarProfileCrest);
        MotionHelper.applyTactilePress(
            binding.btnSaveContact,
            binding.btnCustomerChangePassword,
            binding.btnCustomerLogout
        );

        binding.btnProfileEkyc.setEnabled(false);
        binding.btnProfileEkyc.setOnClickListener(v -> openBiometricAction());
        binding.btnSaveContact.setOnClickListener(v -> updateContact());
        binding.btnCustomerChangePassword.setOnClickListener(v ->
            ChangePasswordDialogHelper.show(requireContext())
        );
        binding.btnCustomerLogout.setOnClickListener(v -> logout());

        loadProfile();
        loadNotifications();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        if (enrollmentDialog != null) {
            enrollmentDialog.dismiss();
            enrollmentDialog = null;
        }
        biometricStatusLoaded = false;
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadEkycStatus();
    }

    private void openBiometricAction() {
        if (!biometricStatusLoaded || binding == null) return;
        if (biometricEnrolled) {
            biometricLauncher.launch(new Intent(requireContext(), FaceEkycActivity.class));
        } else {
            if (enrollmentDialog != null && enrollmentDialog.isShowing()) return;
            enrollmentDialog = EkycEnrollmentDialog.show(requireContext(), token -> {
                if (!isAdded() || binding == null) return;
                Intent camera = new Intent(requireContext(), FaceEkycActivity.class);
                camera.putExtra(FaceEkycActivity.EXTRA_SELF_ENROLLMENT_TOKEN, token);
                biometricLauncher.launch(camera);
            });
        }
    }

    private void loadEkycStatus() {
        if (!isAdded() || binding == null) return;
        ApiClient.customerApi(requireContext()).getEkycStatus().enqueue(
            new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Response<ApiResponse<Map<String, Object>>> response
                ) {
                    if (!isAdded() || binding == null) return;
                    if (
                        response.isSuccessful() &&
                        response.body() != null &&
                        response.body().data != null
                    ) {
                        Map<String, Object> data = response.body().data;
                        biometricEnrolled = Boolean.TRUE.equals(data.get("enrolled"));
                        biometricStatusLoaded = true;
                        boolean ready = Boolean.TRUE.equals(data.get("engineReady"));
                        binding.tvProfileEkycStatus.setText(
                            (biometricEnrolled
                                ? "✓ Đã đăng ký sinh trắc học\n"
                                : "Chưa đăng ký sinh trắc học\n") + data.get("message")
                        );
                        binding.btnProfileEkyc.setText(
                            biometricEnrolled
                                ? "Kiểm tra khuôn mặt đã đăng ký"
                                : "Đăng ký sinh trắc học ngay"
                        );
                        binding.btnProfileEkyc.setEnabled(ready);
                        if (!ready) binding.tvProfileEkycStatus.append(
                            "\nDịch vụ khuôn mặt chưa sẵn sàng. Vui lòng thử lại sau hoặc liên hệ nhân viên."
                        );
                    } else {
                        biometricStatusLoaded = false;
                        binding.tvProfileEkycStatus.setText(
                            "Không tải được trạng thái eKYC. Vui lòng kiểm tra kết nối/đăng nhập."
                        );
                        binding.btnProfileEkyc.setEnabled(false);
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Throwable error
                ) {
                    if (binding != null) {
                        biometricStatusLoaded = false;
                        binding.tvProfileEkycStatus.setText("Mất kết nối khi kiểm tra eKYC.");
                        binding.btnProfileEkyc.setEnabled(false);
                    }
                }
            }
        );
    }

    private String extractMonogram(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "PH";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        char first = parts[0].charAt(0);
        char last = parts[parts.length - 1].charAt(0);
        return ("" + first + last).toUpperCase(Locale.ROOT);
    }

    private void loadProfile() {
        ApiClient.customerApi(requireContext()).getMyProfile().enqueue(
            new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Response<ApiResponse<Map<String, Object>>> response
                ) {
                    if (
                        !isAdded() ||
                        binding == null ||
                        getContext() == null ||
                        !response.isSuccessful() ||
                        response.body() == null ||
                        response.body().data == null
                    ) {
                        return;
                    }
                    Object profileObj = response.body().data.get("profile");
                    if (profileObj instanceof Map) {
                        Map<?, ?> p = (Map<?, ?>) profileObj;
                        binding.edtProfilePhone.setText(safe(p.get("phone")));
                        binding.edtProfileEmail.setText(safe(p.get("email")));
                        binding.edtProfileAddress.setText(safe(p.get("address")));
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Throwable t
                ) {}
            }
        );
    }

    private void updateContact() {
        if (binding == null || getContext() == null) {
            return;
        }
        Map<String, String> body = new HashMap<>();
        body.put("phone", safe(binding.edtProfilePhone.getText()));
        body.put("email", safe(binding.edtProfileEmail.getText()));
        body.put("address", safe(binding.edtProfileAddress.getText()));

        binding.btnSaveContact.setEnabled(false);
        ApiClient.customerApi(requireContext()).updateContact(body).enqueue(
            new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Response<ApiResponse<Map<String, Object>>> response
                ) {
                    if (!isAdded() || binding == null || getContext() == null) return;
                    binding.btnSaveContact.setEnabled(true);
                    if (response.isSuccessful()) {
                        Toast.makeText(
                            requireContext(),
                            "Đã cập nhật thông tin liên lạc",
                            Toast.LENGTH_SHORT
                        ).show();
                        binding.btnSaveContact.setText("Đã lưu thay đổi ✓");
                        if (MotionHelper.areAnimationsEnabled(requireContext())) {
                            binding.btnSaveContact
                                .animate()
                                .scaleX(1.02f)
                                .scaleY(1.02f)
                                .setDuration(120L)
                                .withEndAction(() -> {
                                    if (binding != null) {
                                        binding.btnSaveContact
                                            .animate()
                                            .scaleX(1f)
                                            .scaleY(1f)
                                            .setDuration(120L)
                                            .start();
                                    }
                                })
                                .start();
                        }
                        binding.btnSaveContact.postDelayed(() -> {
                            if (binding != null && isAdded()) {
                                binding.btnSaveContact.setText("Cập nhật thông tin liên lạc");
                            }
                        }, 1400L);
                    } else {
                        MotionHelper.shakeError(binding.btnSaveContact);
                        Toast.makeText(
                            requireContext(),
                            "Cập nhật thất bại",
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Throwable t
                ) {
                    if (!isAdded() || binding == null || getContext() == null) return;
                    binding.btnSaveContact.setEnabled(true);
                    Toast.makeText(
                        requireContext(),
                        "Lỗi kết nối máy chủ",
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }
        );
    }

    private void loadNotifications() {
        ApiClient.customerApi(requireContext()).getNotifications().enqueue(
            new Callback<ApiResponse<CustomerModels.NotificationsResponse>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<CustomerModels.NotificationsResponse>> call,
                    @NonNull Response<ApiResponse<CustomerModels.NotificationsResponse>> response
                ) {
                    if (
                        !isAdded() ||
                        binding == null ||
                        getContext() == null ||
                        !response.isSuccessful() ||
                        response.body() == null ||
                        response.body().data == null
                    ) {
                        return;
                    }
                    List<CustomerModels.PolicyAlert> alerts = response.body().data.policyAlerts;
                    binding.containerPolicyAlerts.removeAllViews();
                    if (alerts != null && !alerts.isEmpty()) {
                        binding.tvPolicyCountBadge.setText(String.valueOf(alerts.size()));
                        binding.tvPolicyCountBadge.setVisibility(View.VISIBLE);
                        binding.containerPolicyAlerts.setVisibility(View.VISIBLE);
                        binding.tvNotificationsBody.setVisibility(View.GONE);
                        for (CustomerModels.PolicyAlert p : alerts) {
                            ItemPolicyAlertBinding alertBinding = ItemPolicyAlertBinding.inflate(
                                getLayoutInflater(),
                                binding.containerPolicyAlerts,
                                false
                            );
                            alertBinding.tvPolicyTitle.setText(safe(p.title));
                            alertBinding.tvPolicyMessage.setText(safe(p.message));
                            if (p.publishedAt != null && !p.publishedAt.trim().isEmpty()) {
                                alertBinding.tvPolicyDate.setText(p.publishedAt.trim());
                                alertBinding.tvPolicyDate.setVisibility(View.VISIBLE);
                            } else {
                                alertBinding.tvPolicyDate.setVisibility(View.GONE);
                            }
                            binding.containerPolicyAlerts.addView(alertBinding.getRoot());
                        }
                        MotionHelper.staggerChildren(binding.containerPolicyAlerts, 60L);
                    } else {
                        binding.tvPolicyCountBadge.setVisibility(View.GONE);
                        binding.containerPolicyAlerts.setVisibility(View.GONE);
                        binding.tvNotificationsBody.setVisibility(View.VISIBLE);
                        binding.tvNotificationsBody.setText("Không có thông báo chính sách mới.");
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<CustomerModels.NotificationsResponse>> call,
                    @NonNull Throwable t
                ) {
                    if (!isAdded() || binding == null) return;
                    binding.tvNotificationsBody.setVisibility(View.VISIBLE);
                    binding.tvNotificationsBody.setText("Không tải được thông báo.");
                }
            }
        );
    }

    private void logout() {
        ApiClient.authApi(requireContext()).logout().enqueue(
            new Callback<ApiResponse<Void>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Void>> call,
                    @NonNull Response<ApiResponse<Void>> response
                ) {
                    finishToLogin();
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Void>> call, @NonNull Throwable t) {
                    finishToLogin();
                }
            }
        );
    }

    private void finishToLogin() {
        session.clearSession();
        if (!isAdded() || getContext() == null) return;
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        MotionHelper.applyActivityTransition(getActivity());
    }

    private String safe(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
