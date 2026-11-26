package vn.edu.tdtu.phlbanking.ui.officer;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.local.SessionManager;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.FragmentOfficerCreateBinding;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;
import vn.edu.tdtu.phlbanking.ui.customer.FaceEkycActivity;
import vn.edu.tdtu.phlbanking.utils.CurrencyUtils;

public class OfficerCreateAccountFragment extends Fragment {

    private static final Pattern DOB_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final long MAX_SAFE_DEPOSIT = 9_000_000_000_000_000L;

    private FragmentOfficerCreateBinding binding;

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentOfficerCreateBinding.inflate(inflater, container, false);

        bindOfficerMeta();
        setupDepositPreview();

        MotionHelper.applyTactilePress(binding.btnCreateCustomer);
        MotionHelper.staggerEntrance(
            binding.layoutCreateHeader,
            binding.cardSectionCredentials,
            binding.cardSectionKyc,
            binding.cardSectionDeposit,
            binding.layoutCreateActions
        );

        binding.btnCreateCustomer.setOnClickListener(v -> submitCreate());
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void bindOfficerMeta() {
        if (binding == null) return;
        SessionManager session = new SessionManager(requireContext());
        String fullName = session.getFullName();
        String empCode = session.getIdentityCode();
        if (fullName != null && !fullName.trim().isEmpty()) {
            String suffix =
                empCode != null && !empCode.trim().isEmpty() ? " (" + empCode.trim() + ")" : "";
            binding.tvCreateOfficerMeta.setText("Cán bộ thực hiện: " + fullName.trim() + suffix);
        } else {
            binding.tvCreateOfficerMeta.setText("Khởi tạo hồ sơ CIF & tài khoản thanh toán");
        }
    }

    private void setupDepositPreview() {
        updateDepositPreview(textOf(binding.edtInitialDeposit), false);
        binding.edtInitialDeposit.addTextChangedListener(
            new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (binding == null) return;
                    updateDepositPreview(s == null ? "" : s.toString().trim(), true);
                }
            }
        );
    }

    private void updateDepositPreview(@NonNull String raw, boolean animatePop) {
        if (binding == null) return;
        if (raw.isEmpty()) {
            binding.tvInitialDepositPreview.setText(CurrencyUtils.formatVnd(0L));
            return;
        }
        try {
            long parsed = Long.parseLong(raw);
            if (parsed < 0L || parsed > MAX_SAFE_DEPOSIT) {
                binding.tvInitialDepositPreview.setText("Vượt giới hạn hợp lệ");
                return;
            }
            binding.tvInitialDepositPreview.setText(CurrencyUtils.formatVnd(parsed));
            if (animatePop && MotionHelper.areAnimationsEnabled(requireContext())) {
                binding.tvInitialDepositPreview.animate().cancel();
                binding.tvInitialDepositPreview.setScaleX(1.03f);
                binding.tvInitialDepositPreview.setScaleY(1.03f);
                binding.tvInitialDepositPreview
                    .animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120L)
                    .start();
            }
        } catch (NumberFormatException ex) {
            binding.tvInitialDepositPreview.setText("Giá trị không hợp lệ");
        }
    }

    private void submitCreate() {
        if (binding == null) return;
        clearErrors();

        String username = textOf(binding.edtNewUsername);
        String password = textOf(binding.edtNewPassword);
        String fullName = textOf(binding.edtNewFullName);
        String idCard = textOf(binding.edtNewIdCard);
        String dob = textOf(binding.edtNewDob);
        String phone = textOf(binding.edtNewPhone);
        String depositStr = textOf(binding.edtInitialDeposit);

        if (username.length() < 4) {
            showFieldError(
                binding.tilNewUsername,
                binding.cardSectionCredentials,
                "Tên đăng nhập phải có ít nhất 4 ký tự"
            );
            return;
        }
        if (password.length() < 6) {
            showFieldError(
                binding.tilNewPassword,
                binding.cardSectionCredentials,
                "Mật khẩu khởi tạo phải có ít nhất 6 ký tự"
            );
            return;
        }
        if (fullName.length() < 2) {
            showFieldError(
                binding.tilNewFullName,
                binding.cardSectionKyc,
                "Vui lòng nhập họ và tên đầy đủ"
            );
            return;
        }
        if (idCard.length() != 12) {
            showFieldError(
                binding.tilNewIdCard,
                binding.cardSectionKyc,
                "CCCD phải gồm đúng 12 chữ số"
            );
            return;
        }
        if (!DOB_PATTERN.matcher(dob).matches()) {
            showFieldError(
                binding.tilNewDob,
                binding.cardSectionKyc,
                "Ngày sinh phải theo định dạng YYYY-MM-DD"
            );
            return;
        }
        if (phone.length() < 9 || phone.length() > 20) {
            showFieldError(
                binding.tilNewPhone,
                binding.cardSectionKyc,
                "Số điện thoại phải từ 9 đến 20 ký tự"
            );
            return;
        }

        final long initialDeposit;
        try {
            initialDeposit = depositStr.isEmpty() ? 0L : Long.parseLong(depositStr);
            if (initialDeposit < 0L || initialDeposit > MAX_SAFE_DEPOSIT) {
                showFieldError(
                    binding.tilInitialDeposit,
                    binding.cardSectionDeposit,
                    "Số tiền nạp ban đầu không hợp lệ"
                );
                return;
            }
        } catch (NumberFormatException ex) {
            showFieldError(
                binding.tilInitialDeposit,
                binding.cardSectionDeposit,
                "Số tiền nạp ban đầu phải là số nguyên hợp lệ"
            );
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("fullName", fullName);
        body.put("idCardNumber", idCard);
        body.put("dob", dob);
        body.put("phone", phone);
        body.put("initialDeposit", initialDeposit);

        setLoading(true);
        ApiClient.officerApi(requireContext()).createCustomer(body).enqueue(
            new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Response<ApiResponse<Map<String, Object>>> response
                ) {
                    if (!isAdded() || binding == null) return;
                    setLoading(false);
                    if (
                        response.isSuccessful() &&
                        response.body() != null &&
                        response.body().data != null
                    ) {
                        Object cif = response.body().data.get("cifNumber");
                        Object accNo = response.body().data.get("accountNumber");
                        showSuccessCard(String.valueOf(cif), String.valueOf(accNo), initialDeposit);
                        Object id = response.body().data.get("customerId");
                        if (id instanceof Number) new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Bước tiếp theo: đăng ký eKYC")
                            .setMessage(
                                "Hồ sơ chưa có ảnh gốc. Sau khi kiểm tra CCCD và được khách hàng đồng ý, hãy chụp ảnh gốc để dùng cho giao dịch trên 10 triệu."
                            )
                            .setNegativeButton("Để sau (chưa xác minh)", null)
                            .setPositiveButton("Mở camera đăng ký", (dialog, which) -> {
                                Intent camera = new Intent(
                                    requireContext(),
                                    FaceEkycActivity.class
                                );
                                camera.putExtra(
                                    FaceEkycActivity.EXTRA_CUSTOMER_ID,
                                    ((Number) id).longValue()
                                );
                                startActivity(camera);
                            })
                            .show();
                        Toast.makeText(
                            requireContext(),
                            "Tạo thành công CIF: " + cif + " · STK: " + accNo,
                            Toast.LENGTH_LONG
                        ).show();
                    } else {
                        String msg =
                            response.code() == 409
                                ? "Tên đăng nhập đã tồn tại trong hệ thống (409)"
                                : "Tạo tài khoản thất bại (" + response.code() + ")";
                        binding.tvCreateError.setText(msg);
                        binding.tvCreateError.setVisibility(View.VISIBLE);
                        MotionHelper.shakeError(binding.tvCreateError);
                        Toast.makeText(
                            requireContext(),
                            "Tạo tài khoản thất bại (" + response.code() + ")",
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Throwable t
                ) {
                    if (!isAdded() || binding == null) return;
                    setLoading(false);
                    binding.tvCreateError.setText(R.string.err_network);
                    binding.tvCreateError.setVisibility(View.VISIBLE);
                    MotionHelper.shakeError(binding.tvCreateError);
                    Toast.makeText(
                        requireContext(),
                        R.string.err_network,
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }
        );
    }

    private void showSuccessCard(
        @NonNull String cif,
        @NonNull String accountNo,
        long initialDeposit
    ) {
        if (binding == null) return;
        if (MotionHelper.areAnimationsEnabled(requireContext())) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(240L);
            TransitionManager.beginDelayedTransition(binding.layoutCreateRoot, transition);
        }
        binding.tvCreatedCif.setText("CIF: " + cif);
        binding.tvCreatedAccountNo.setText("STK: " + accountNo);
        binding.cardCreateSuccess.setVisibility(View.VISIBLE);
        MotionHelper.animateVndCounter(binding.tvCreatedDeposit, 0L, initialDeposit, 650L);
        MotionHelper.pulseView(binding.ivCreateSuccessBadge);
    }

    private void clearErrors() {
        if (binding == null) return;
        binding.tilNewUsername.setError(null);
        binding.tilNewPassword.setError(null);
        binding.tilNewFullName.setError(null);
        binding.tilNewIdCard.setError(null);
        binding.tilNewDob.setError(null);
        binding.tilNewPhone.setError(null);
        binding.tilInitialDeposit.setError(null);
        binding.tvCreateError.setVisibility(View.GONE);
    }

    private void showFieldError(
        @NonNull com.google.android.material.textfield.TextInputLayout til,
        @NonNull View sectionCard,
        @NonNull String message
    ) {
        til.setError(message);
        binding.tvCreateError.setText(message);
        binding.tvCreateError.setVisibility(View.VISIBLE);
        MotionHelper.shakeError(sectionCard);
        MotionHelper.shakeError(til);
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;
        binding.btnCreateCustomer.setEnabled(!loading);
        binding.progressCreateCustomer.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String textOf(com.google.android.material.textfield.TextInputEditText edt) {
        return edt.getText() == null ? "" : edt.getText().toString().trim();
    }
}
