package vn.edu.tdtu.phlbanking.ui.officer;

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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.FragmentOfficerRatesBinding;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;

public class OfficerRatesFragment extends Fragment {

    private FragmentOfficerRatesBinding binding;

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentOfficerRatesBinding.inflate(inflater, container, false);

        MotionHelper.applyTactilePress(
            binding.btnUpdateRate,
            binding.chipRateSavings,
            binding.chipRateMortgage
        );
        MotionHelper.staggerEntrance(
            binding.layoutRatesHeader,
            binding.cardRateProductBanner,
            binding.cardRateForm
        );

        binding.chipRateTarget.setOnCheckedStateChangeListener((group, checkedIds) ->
            applyProductMode(true)
        );
        applyProductMode(false);

        binding.edtNewInterestRate.addTextChangedListener(
            new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (binding == null) return;
                    updateRateLivePreview(s == null ? "" : s.toString().trim());
                }
            }
        );

        binding.btnUpdateRate.setOnClickListener(v -> updateRate());
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void applyProductMode(boolean animate) {
        if (binding == null) return;
        boolean isMortgage = binding.chipRateMortgage.isChecked();
        if (animate && MotionHelper.areAnimationsEnabled(requireContext())) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(220L);
            TransitionManager.beginDelayedTransition(binding.layoutRatesRoot, transition);
        }

        binding.cardRateProductBanner.setBackgroundResource(
            isMortgage ? R.drawable.bg_navy_card_prestige : R.drawable.bg_m4_savings_banner
        );

        String badge = isMortgage ? "TÍN DỤNG THẾ CHẤP · MORTGAGE" : "SẢN PHẨM TIẾT KIỆM · YIELD";
        String title = isMortgage
            ? "Khoản vay thế chấp (MORTGAGE)"
            : "Tiết kiệm có kỳ hạn (SAVINGS)";
        String subtitle = isMortgage
            ? "Demo ID: 1 · Lãi suất hiện hành tham khảo: 8.20%/năm"
            : "Demo ID: 1 · Lãi suất hiện hành tham khảo: 5.80%/năm";

        if (animate) {
            MotionHelper.crossfadeText(binding.tvRateBannerBadge, badge);
            MotionHelper.crossfadeText(binding.tvRateBannerTitle, title);
            MotionHelper.crossfadeText(binding.tvRateBannerSubtitle, subtitle);
        } else {
            binding.tvRateBannerBadge.setText(badge);
            binding.tvRateBannerTitle.setText(title);
            binding.tvRateBannerSubtitle.setText(subtitle);
        }
    }

    private void updateRateLivePreview(@NonNull String raw) {
        if (binding == null) return;
        if (raw.isEmpty()) {
            binding.tvRateLivePreview.setText("-- %/năm");
            return;
        }
        try {
            double val = Double.parseDouble(raw);
            if (val > 0d && val <= 30d) {
                binding.tvRateLivePreview.setText(String.format(Locale.US, "%.2f%%/năm", val));
            } else {
                binding.tvRateLivePreview.setText("-- %/năm");
            }
        } catch (NumberFormatException ex) {
            binding.tvRateLivePreview.setText("-- %/năm");
        }
    }

    private void updateRate() {
        if (binding == null) return;
        binding.tilRateTargetId.setError(null);
        binding.tilNewInterestRate.setError(null);
        binding.tvRatesError.setVisibility(View.GONE);

        String idStr =
            binding.edtRateTargetId.getText() == null
                ? ""
                : binding.edtRateTargetId.getText().toString().trim();
        String rateStr =
            binding.edtNewInterestRate.getText() == null
                ? ""
                : binding.edtNewInterestRate.getText().toString().trim();

        if (idStr.isEmpty() || rateStr.isEmpty()) {
            String msg = "Nhập ID sản phẩm và lãi suất mới";
            if (idStr.isEmpty()) binding.tilRateTargetId.setError("Bắt buộc");
            if (rateStr.isEmpty()) binding.tilNewInterestRate.setError("Bắt buộc");
            showValidationError(msg);
            return;
        }

        final long targetId;
        final double newInterestRate;
        try {
            targetId = Long.parseLong(idStr);
            if (targetId <= 0L) {
                binding.tilRateTargetId.setError("ID sản phẩm phải lớn hơn 0");
                showValidationError("ID sản phẩm phải là số nguyên dương");
                return;
            }
        } catch (NumberFormatException ex) {
            binding.tilRateTargetId.setError("ID sản phẩm không hợp lệ");
            showValidationError("ID sản phẩm phải là số nguyên dương");
            return;
        }

        try {
            newInterestRate = Double.parseDouble(rateStr);
            if (
                Double.isNaN(newInterestRate) ||
                Double.isInfinite(newInterestRate) ||
                newInterestRate <= 0d ||
                newInterestRate > 30d
            ) {
                binding.tilNewInterestRate.setError("Lãi suất phải > 0 và <= 30%/năm");
                showValidationError("Lãi suất phải nằm trong khoảng (0, 30] %/năm");
                return;
            }
        } catch (NumberFormatException ex) {
            binding.tilNewInterestRate.setError("Định dạng lãi suất không hợp lệ");
            showValidationError("Vui lòng nhập lãi suất thập phân hợp lệ (VD: 6.25)");
            return;
        }

        String targetType = binding.chipRateMortgage.isChecked() ? "MORTGAGE" : "SAVINGS";
        Map<String, Object> body = new HashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        body.put("newInterestRate", newInterestRate);

        setLoading(true);
        ApiClient.officerApi(requireContext()).adjustInterestRate(body).enqueue(
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
                        Map<String, Object> data = response.body().data;
                        String oldRate = formatRateValue(data.get("oldInterestRate"));
                        String newRate = formatRateValue(data.get("newInterestRate"));
                        showSuccessCard(targetType, targetId, oldRate, newRate);
                        Toast.makeText(
                            requireContext(),
                            "Đã điều chỉnh lãi suất & ghi AuditLog",
                            Toast.LENGTH_SHORT
                        ).show();
                    } else if (response.isSuccessful()) {
                        showSuccessCard(
                            targetType,
                            targetId,
                            "--",
                            String.format(Locale.US, "%.2f", newInterestRate)
                        );
                        Toast.makeText(
                            requireContext(),
                            "Đã điều chỉnh lãi suất & ghi AuditLog",
                            Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        String msg =
                            response.code() == 404
                                ? "Không tìm thấy sản phẩm #" + targetId + " (404)"
                                : "Cập nhật thất bại (" + response.code() + ")";
                        binding.tvRatesError.setText(msg);
                        binding.tvRatesError.setVisibility(View.VISIBLE);
                        MotionHelper.shakeError(binding.tvRatesError);
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
                    if (!isAdded() || binding == null) return;
                    setLoading(false);
                    binding.tvRatesError.setText(R.string.err_network);
                    binding.tvRatesError.setVisibility(View.VISIBLE);
                    MotionHelper.shakeError(binding.tvRatesError);
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
        @NonNull String targetType,
        long targetId,
        @NonNull String oldRate,
        @NonNull String newRate
    ) {
        if (binding == null) return;
        if (MotionHelper.areAnimationsEnabled(requireContext())) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(220L);
            TransitionManager.beginDelayedTransition(binding.layoutRatesRoot, transition);
        }
        binding.tvRateSuccessSummary.setText(
            targetType + " #" + targetId + ": " + oldRate + "% → " + newRate + "%/năm"
        );
        binding.cardRateSuccess.setVisibility(View.VISIBLE);
        MotionHelper.pulseView(binding.ivRateSuccessBadge);
    }

    private void showValidationError(@NonNull String message) {
        if (binding == null) return;
        binding.tvRatesError.setText(message);
        binding.tvRatesError.setVisibility(View.VISIBLE);
        MotionHelper.shakeError(binding.cardRateForm);
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;
        binding.btnUpdateRate.setEnabled(!loading);
        binding.progressUpdateRate.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @NonNull
    private static String formatRateValue(@Nullable Object raw) {
        if (raw == null) return "--";
        if (raw instanceof Number) {
            return String.format(Locale.US, "%.2f", ((Number) raw).doubleValue());
        }
        String text = String.valueOf(raw).trim();
        try {
            return String.format(Locale.US, "%.2f", Double.parseDouble(text));
        } catch (NumberFormatException ex) {
            return text.isEmpty() ? "--" : text;
        }
    }
}
