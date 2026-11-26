package vn.edu.tdtu.phlbanking.ui.officer;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Patterns;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.model.OfficerModels;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.BottomSheetOfficerCustomerEditBinding;
import vn.edu.tdtu.phlbanking.databinding.FragmentOfficerCustomersBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemOfficerCustomerBinding;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;
import vn.edu.tdtu.phlbanking.ui.customer.FaceEkycActivity;

public class OfficerCustomersFragment extends Fragment {

    private FragmentOfficerCustomersBinding binding;
    private final ActivityResultLauncher<Intent> enrollmentLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (isAdded() && binding != null) loadCustomers();
        }
    );

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentOfficerCustomersBinding.inflate(inflater, container, false);

        MotionHelper.applyTactilePress(binding.btnSearchCustomer);
        MotionHelper.staggerEntrance(binding.layoutCustomersHeader, binding.cardCustomerSearch);

        binding.btnSearchCustomer.setOnClickListener(v -> loadCustomers());
        binding.edtSearchCustomer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                loadCustomers();
                return true;
            }
            return false;
        });

        loadCustomers();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        if (binding != null) {
            binding.tvCustomersLoadingHint.clearAnimation();
        }
        super.onDestroyView();
        binding = null;
    }

    private void loadCustomers() {
        if (binding == null) return;
        String q =
            binding.edtSearchCustomer.getText() == null
                ? ""
                : binding.edtSearchCustomer.getText().toString().trim();
        setLoading(true);
        ApiClient.officerApi(requireContext())
            .searchCustomers(q.isEmpty() ? null : q)
            .enqueue(
                new Callback<ApiResponse<List<OfficerModels.CustomerItem>>>() {
                    @Override
                    public void onResponse(
                        @NonNull Call<ApiResponse<List<OfficerModels.CustomerItem>>> call,
                        @NonNull Response<ApiResponse<List<OfficerModels.CustomerItem>>> response
                    ) {
                        if (!isAdded() || binding == null) return;
                        setLoading(false);
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(
                                requireContext(),
                                "Không tải được danh sách khách hàng",
                                Toast.LENGTH_SHORT
                            ).show();
                            if (binding.containerCustomers.getChildCount() == 0) {
                                showEmpty(
                                    "Không tải được danh sách khách hàng (" + response.code() + ")."
                                );
                            }
                            return;
                        }
                        renderCustomers(response.body().data);
                    }

                    @Override
                    public void onFailure(
                        @NonNull Call<ApiResponse<List<OfficerModels.CustomerItem>>> call,
                        @NonNull Throwable t
                    ) {
                        if (!isAdded() || binding == null) return;
                        setLoading(false);
                        if (binding.containerCustomers.getChildCount() == 0) {
                            showEmpty(getString(R.string.err_network));
                        }
                        Toast.makeText(
                            requireContext(),
                            R.string.err_network,
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                }
            );
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;
        binding.btnSearchCustomer.setEnabled(!loading);
        binding.progressCustomers.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            binding.layoutCustomersEmpty.setVisibility(View.GONE);
            binding.tvCustomersLoadingHint.setVisibility(View.VISIBLE);
            if (MotionHelper.areAnimationsEnabled(requireContext())) {
                Animation pulse = AnimationUtils.loadAnimation(requireContext(), R.anim.pulse_soft);
                binding.tvCustomersLoadingHint.startAnimation(pulse);
            }
        } else {
            binding.tvCustomersLoadingHint.clearAnimation();
            binding.tvCustomersLoadingHint.setVisibility(View.GONE);
        }
    }

    private void showEmpty(@NonNull String message) {
        if (binding == null) return;
        binding.tvCustomersEmptyMessage.setText(message);
        binding.layoutCustomersEmpty.setVisibility(View.VISIBLE);
    }

    private void renderCustomers(@Nullable List<OfficerModels.CustomerItem> items) {
        if (binding == null) return;
        binding.containerCustomers.removeAllViews();
        int count = items == null ? 0 : items.size();
        MotionHelper.crossfadeText(binding.tvCustomerCountBadge, count + " hồ sơ");

        if (items == null || items.isEmpty()) {
            showEmpty("Không tìm thấy hồ sơ khách hàng phù hợp.");
            return;
        }

        binding.layoutCustomersEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (OfficerModels.CustomerItem c : items) {
            ItemOfficerCustomerBinding itemBinding = ItemOfficerCustomerBinding.inflate(
                inflater,
                binding.containerCustomers,
                false
            );
            bindCustomerItem(itemBinding, c);
            binding.containerCustomers.addView(itemBinding.getRoot());
        }
        MotionHelper.staggerChildren(binding.containerCustomers, 35L);
    }

    private void bindCustomerItem(
        @NonNull ItemOfficerCustomerBinding itemBinding,
        @NonNull OfficerModels.CustomerItem c
    ) {
        String name = safeText(c.fullName, "Khách hàng");
        String cif = safeText(c.cifNumber, "N/A");
        String phone = safeText(c.phone, "--");
        String idCard = safeText(c.idCardNumber, "--");
        String status = safeText(c.accountStatus, "ACTIVE").toUpperCase(Locale.US);
        String ekyc = safeText(c.eKYCStatus, "PENDING").toUpperCase(Locale.US);

        itemBinding.tvCustomerName.setText(name);
        itemBinding.tvCustomerCif.setText(cif);
        itemBinding.tvCustomerContact.setText("SĐT: " + phone + " · CCCD: " + idCard);

        if (c.email != null && !c.email.trim().isEmpty()) {
            itemBinding.tvCustomerEmail.setText(c.email.trim());
            itemBinding.tvCustomerEmail.setVisibility(View.VISIBLE);
        } else {
            itemBinding.tvCustomerEmail.setVisibility(View.GONE);
        }

        boolean isActive = "ACTIVE".equalsIgnoreCase(status);
        itemBinding.tvStatusBadge.setText(status);
        itemBinding.tvStatusBadge.setBackgroundResource(
            isActive ? R.drawable.bg_pill_emerald : R.drawable.bg_pill_danger
        );
        itemBinding.tvStatusBadge.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                isActive ? R.color.phl_emerald_dark : R.color.phl_danger
            )
        );

        boolean isVerified = "VERIFIED".equalsIgnoreCase(ekyc);
        itemBinding.tvEkycBadge.setText("eKYC: " + ekyc);
        itemBinding.tvEkycBadge.setBackgroundResource(
            isVerified ? R.drawable.bg_pill_emerald : R.drawable.bg_pill_gold
        );
        itemBinding.tvEkycBadge.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                isVerified ? R.color.phl_emerald_dark : R.color.phl_gold
            )
        );

        itemBinding.tvAccountsBadge.setText(c.totalAccounts + " TK");

        MotionHelper.applyTactilePress(itemBinding.getRoot());
        itemBinding.getRoot().setOnClickListener(v -> showEditBottomSheet(c));
    }

    private void showEditBottomSheet(@NonNull OfficerModels.CustomerItem c) {
        if (!isAdded()) return;
        Context themedContext = new ContextThemeWrapper(requireContext(), R.style.Theme_PHLBanking);
        BottomSheetDialog dialog = new BottomSheetDialog(themedContext);
        BottomSheetOfficerCustomerEditBinding sheetBinding =
            BottomSheetOfficerCustomerEditBinding.inflate(LayoutInflater.from(dialog.getContext()));
        dialog.setContentView(sheetBinding.getRoot());

        configureSoftInputMode(dialog.getWindow());

        sheetBinding.tvEditCustomerName.setText(safeText(c.fullName, "Khách hàng"));
        sheetBinding.tvEditCustomerCif.setText(safeText(c.cifNumber, "N/A"));

        boolean isActive = "ACTIVE".equalsIgnoreCase(c.accountStatus);
        sheetBinding.tvEditStatusPill.setText(isActive ? "ACTIVE" : "LOCKED");
        sheetBinding.tvEditStatusPill.setBackgroundResource(
            isActive ? R.drawable.bg_pill_emerald : R.drawable.bg_pill_danger
        );
        sheetBinding.tvEditStatusPill.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                isActive ? R.color.phl_emerald_dark : R.color.phl_danger
            )
        );

        sheetBinding.edtEditPhone.setText(c.phone != null ? c.phone : "");
        sheetBinding.edtEditEmail.setText(c.email != null ? c.email : "");
        sheetBinding.edtEditAddress.setText(c.address != null ? c.address : "");

        String nextStatus = isActive ? "LOCKED" : "ACTIVE";
        if (isActive) {
            sheetBinding.btnToggleCustomerStatus.setText("Khóa tài khoản");
            sheetBinding.btnToggleCustomerStatus.setIconResource(R.drawable.ic_lock);
            int danger = ContextCompat.getColor(requireContext(), R.color.phl_danger);
            int dangerStroke = ContextCompat.getColor(requireContext(), R.color.phl_danger_stroke);
            sheetBinding.btnToggleCustomerStatus.setTextColor(danger);
            sheetBinding.btnToggleCustomerStatus.setIconTint(ColorStateList.valueOf(danger));
            sheetBinding.btnToggleCustomerStatus.setStrokeColor(
                ColorStateList.valueOf(dangerStroke)
            );
        } else {
            sheetBinding.btnToggleCustomerStatus.setText("Mở khóa tài khoản");
            sheetBinding.btnToggleCustomerStatus.setIconResource(R.drawable.ic_unlock);
            int emeraldDark = ContextCompat.getColor(requireContext(), R.color.phl_emerald_dark);
            int emeraldStroke = ContextCompat.getColor(
                requireContext(),
                R.color.phl_emerald_stroke
            );
            sheetBinding.btnToggleCustomerStatus.setTextColor(emeraldDark);
            sheetBinding.btnToggleCustomerStatus.setIconTint(ColorStateList.valueOf(emeraldDark));
            sheetBinding.btnToggleCustomerStatus.setStrokeColor(
                ColorStateList.valueOf(emeraldStroke)
            );
        }

        MotionHelper.applyTactilePress(
            sheetBinding.btnSaveCustomerContact,
            sheetBinding.btnToggleCustomerStatus,
            sheetBinding.btnCloseCustomerEdit
        );

        boolean enrolled = "VERIFIED".equals(c.eKYCStatus);
        sheetBinding.btnEnrollCustomerEkyc.setEnabled(!enrolled);
        sheetBinding.btnEnrollCustomerEkyc.setText(
            enrolled ? "Đã có ảnh gốc eKYC" : "Đăng ký ảnh gốc eKYC (camera)"
        );
        sheetBinding.btnEnrollCustomerEkyc.setOnClickListener(v -> {
            dialog.dismiss();
            Intent camera = new Intent(requireContext(), FaceEkycActivity.class);
            camera.putExtra(FaceEkycActivity.EXTRA_CUSTOMER_ID, c.customerId);
            enrollmentLauncher.launch(camera);
        });
        sheetBinding.btnCloseCustomerEdit.setOnClickListener(v -> dialog.dismiss());

        sheetBinding.btnSaveCustomerContact.setOnClickListener(v -> {
            sheetBinding.tilEditPhone.setError(null);
            sheetBinding.tilEditEmail.setError(null);
            sheetBinding.tilEditAddress.setError(null);
            sheetBinding.tvEditCustomerError.setVisibility(View.GONE);

            String phone =
                sheetBinding.edtEditPhone.getText() == null
                    ? ""
                    : sheetBinding.edtEditPhone.getText().toString().trim();
            String email =
                sheetBinding.edtEditEmail.getText() == null
                    ? ""
                    : sheetBinding.edtEditEmail.getText().toString().trim();
            String address =
                sheetBinding.edtEditAddress.getText() == null
                    ? ""
                    : sheetBinding.edtEditAddress.getText().toString().trim();

            if (phone.length() < 9 || phone.length() > 20) {
                sheetBinding.tilEditPhone.setError("Số điện thoại phải từ 9 đến 20 ký tự");
                MotionHelper.shakeError(sheetBinding.tilEditPhone);
                return;
            }
            if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                sheetBinding.tilEditEmail.setError("Định dạng email không hợp lệ");
                MotionHelper.shakeError(sheetBinding.tilEditEmail);
                return;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("phone", phone);
            if (!email.isEmpty()) {
                body.put("email", email);
            }
            if (!address.isEmpty()) {
                body.put("address", address);
            }
            callUpdate(c.customerId, body, dialog, sheetBinding);
        });

        sheetBinding.btnToggleCustomerStatus.setOnClickListener(v -> {
            sheetBinding.tvEditCustomerError.setVisibility(View.GONE);
            Map<String, Object> body = new HashMap<>();
            body.put("status", nextStatus);
            callUpdate(c.customerId, body, dialog, sheetBinding);
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
                sheetBinding.layoutEditCustomerHeader,
                sheetBinding.layoutEditCustomerForm,
                sheetBinding.layoutEditCustomerActions
            );
        });

        dialog.show();
    }

    private void callUpdate(
        long customerId,
        @NonNull Map<String, Object> body,
        @NonNull BottomSheetDialog dialog,
        @NonNull BottomSheetOfficerCustomerEditBinding sheetBinding
    ) {
        setSheetLoading(dialog, sheetBinding, true);
        ApiClient.officerApi(requireContext()).updateCustomer(customerId, body).enqueue(
            new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Response<ApiResponse<Map<String, Object>>> response
                ) {
                    if (!isAdded() || binding == null || !dialog.isShowing()) return;
                    if (response.isSuccessful()) {
                        dialog.dismiss();
                        Toast.makeText(
                            requireContext(),
                            "Đã cập nhật & ghi AuditLog",
                            Toast.LENGTH_SHORT
                        ).show();
                        loadCustomers();
                    } else {
                        setSheetLoading(dialog, sheetBinding, false);
                        sheetBinding.tvEditCustomerError.setText(
                            "Cập nhật thất bại (" + response.code() + ")"
                        );
                        sheetBinding.tvEditCustomerError.setVisibility(View.VISIBLE);
                        MotionHelper.shakeError(sheetBinding.tvEditCustomerError);
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
                    if (!isAdded() || binding == null || !dialog.isShowing()) return;
                    setSheetLoading(dialog, sheetBinding, false);
                    sheetBinding.tvEditCustomerError.setText(R.string.err_network);
                    sheetBinding.tvEditCustomerError.setVisibility(View.VISIBLE);
                    MotionHelper.shakeError(sheetBinding.tvEditCustomerError);
                    Toast.makeText(
                        requireContext(),
                        R.string.err_network,
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }
        );
    }

    private void setSheetLoading(
        @NonNull BottomSheetDialog dialog,
        @NonNull BottomSheetOfficerCustomerEditBinding sheetBinding,
        boolean loading
    ) {
        dialog.setCancelable(!loading);
        sheetBinding.edtEditPhone.setEnabled(!loading);
        sheetBinding.edtEditEmail.setEnabled(!loading);
        sheetBinding.edtEditAddress.setEnabled(!loading);
        sheetBinding.btnSaveCustomerContact.setEnabled(!loading);
        sheetBinding.btnToggleCustomerStatus.setEnabled(!loading);
        sheetBinding.btnCloseCustomerEdit.setEnabled(!loading);
        sheetBinding.progressEditCustomer.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @SuppressWarnings("deprecation")
    private static void configureSoftInputMode(@Nullable Window window) {
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private static String safeText(@Nullable String value, @NonNull String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
