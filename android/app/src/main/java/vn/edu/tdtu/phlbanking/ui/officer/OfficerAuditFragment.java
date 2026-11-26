package vn.edu.tdtu.phlbanking.ui.officer;

import android.content.Intent;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.local.SessionManager;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.model.OfficerModels;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.FragmentOfficerAuditBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemOfficerAuditBinding;
import vn.edu.tdtu.phlbanking.ui.auth.LoginActivity;
import vn.edu.tdtu.phlbanking.ui.common.ChangePasswordDialogHelper;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;
import vn.edu.tdtu.phlbanking.utils.CurrencyUtils;

public class OfficerAuditFragment extends Fragment {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private FragmentOfficerAuditBinding binding;

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentOfficerAuditBinding.inflate(inflater, container, false);

        bindOfficerIdentity();

        MotionHelper.applyTactilePress(binding.btnOfficerChangePassword, binding.btnOfficerLogout);
        MotionHelper.staggerEntrance(binding.layoutAuditHeader, binding.cardOfficerActions);

        binding.btnOfficerChangePassword.setOnClickListener(v ->
            ChangePasswordDialogHelper.show(requireContext())
        );
        binding.btnOfficerLogout.setOnClickListener(v -> logout());

        loadAuditLogs();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void bindOfficerIdentity() {
        if (binding == null) return;
        SessionManager session = new SessionManager(requireContext());
        String fullName = session.getFullName();
        String empCode = session.getIdentityCode();
        String role = session.getRole();
        String username = session.getUsername();

        binding.tvOfficerFullName.setText(
            fullName != null && !fullName.trim().isEmpty()
                ? fullName.trim()
                : username != null && !username.trim().isEmpty()
                  ? username.trim()
                  : "Cán bộ Ngân hàng"
        );

        binding.tvOfficerEmployeeCode.setText(
            empCode != null && !empCode.trim().isEmpty() ? empCode.trim() : "EMP-PHL"
        );

        binding.tvOfficerRoleBadge.setText(
            role != null && !role.trim().isEmpty() ? role.trim().toUpperCase(Locale.US) : "OFFICER"
        );
    }

    private void loadAuditLogs() {
        if (binding == null) return;
        binding.progressAuditLogs.setVisibility(View.VISIBLE);
        binding.layoutAuditEmpty.setVisibility(View.GONE);

        ApiClient.officerApi(requireContext()).getAuditLogs(50).enqueue(
            new Callback<ApiResponse<List<OfficerModels.AuditLogItem>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<List<OfficerModels.AuditLogItem>>> call,
                    @NonNull Response<ApiResponse<List<OfficerModels.AuditLogItem>>> response
                ) {
                    if (!isAdded() || binding == null) return;
                    binding.progressAuditLogs.setVisibility(View.GONE);
                    if (!response.isSuccessful() || response.body() == null) {
                        if (binding.containerAuditLogs.getChildCount() == 0) {
                            binding.tvAuditEmptyMessage.setText(
                                "Không tải được nhật ký kiểm toán (" + response.code() + ")."
                            );
                            binding.layoutAuditEmpty.setVisibility(View.VISIBLE);
                        }
                        return;
                    }
                    renderAuditLogs(response.body().data);
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<List<OfficerModels.AuditLogItem>>> call,
                    @NonNull Throwable t
                ) {
                    if (!isAdded() || binding == null) return;
                    binding.progressAuditLogs.setVisibility(View.GONE);
                    if (binding.containerAuditLogs.getChildCount() == 0) {
                        binding.tvAuditEmptyMessage.setText(R.string.err_network);
                        binding.layoutAuditEmpty.setVisibility(View.VISIBLE);
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

    private void renderAuditLogs(@Nullable List<OfficerModels.AuditLogItem> logs) {
        if (binding == null) return;
        binding.containerAuditLogs.removeAllViews();
        int count = logs == null ? 0 : logs.size();
        MotionHelper.crossfadeText(binding.tvAuditCountBadge, count + " bản ghi");

        if (logs == null || logs.isEmpty()) {
            binding.tvAuditEmptyMessage.setText("Chưa có dữ liệu nhật ký kiểm toán.");
            binding.layoutAuditEmpty.setVisibility(View.VISIBLE);
            return;
        }

        binding.layoutAuditEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (OfficerModels.AuditLogItem log : logs) {
            ItemOfficerAuditBinding itemBinding = ItemOfficerAuditBinding.inflate(
                inflater,
                binding.containerAuditLogs,
                false
            );
            bindAuditItem(itemBinding, log);
            binding.containerAuditLogs.addView(itemBinding.getRoot());
        }
        MotionHelper.staggerChildren(binding.containerAuditLogs, 35L);
    }

    private void bindAuditItem(
        @NonNull ItemOfficerAuditBinding itemBinding,
        @NonNull OfficerModels.AuditLogItem log
    ) {
        String rawAction = log.action == null ? "" : log.action.toUpperCase(Locale.US);
        if (rawAction.contains("CREATE")) {
            itemBinding.tvAuditActionBadge.setText("CREATE");
            itemBinding.tvAuditActionBadge.setBackgroundResource(R.drawable.bg_pill_emerald);
            itemBinding.tvAuditActionBadge.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.phl_emerald_dark)
            );
            itemBinding.vAuditTimelineDot.setBackgroundResource(R.drawable.bg_m4_dot_emerald);
        } else if (rawAction.contains("UPDATE")) {
            itemBinding.tvAuditActionBadge.setText("UPDATE");
            itemBinding.tvAuditActionBadge.setBackgroundResource(R.drawable.bg_pill_gold);
            itemBinding.tvAuditActionBadge.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.phl_gold)
            );
            itemBinding.vAuditTimelineDot.setBackgroundResource(R.drawable.bg_m4_dot_gold);
        } else {
            itemBinding.tvAuditActionBadge.setText(
                rawAction.contains("RATE") ? "RATE" : rawAction.isEmpty() ? "AUDIT" : rawAction
            );
            itemBinding.tvAuditActionBadge.setBackgroundResource(R.drawable.bg_pill_navy);
            itemBinding.tvAuditActionBadge.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.phl_navy)
            );
            itemBinding.vAuditTimelineDot.setBackgroundResource(R.drawable.bg_m4_dot_navy);
        }

        String entity =
            (log.entityName != null ? log.entityName : "Record") +
            (log.entityId != null ? " #" + log.entityId : "");
        itemBinding.tvAuditEntity.setText(entity);

        String actor =
            log.actorUsername != null && !log.actorUsername.trim().isEmpty()
                ? "@" + log.actorUsername.trim()
                : "@officer";
        itemBinding.tvAuditActorBadge.setText(actor);
        itemBinding.tvAuditTimestamp.setText(log.timestamp != null ? log.timestamp : "--");

        Map<String, Object> oldMap = asMap(log.oldValue);
        Map<String, Object> newMap = asMap(log.newValue);

        bindHighlightRow(itemBinding, newMap);

        itemBinding.tvAuditOldValue.setText(formatValueSummary(log.oldValue, oldMap));
        itemBinding.tvAuditNewValue.setText(formatValueSummary(log.newValue, newMap));

        MotionHelper.applyTactilePress(itemBinding.cardAuditItem);
        itemBinding.cardAuditItem.setOnClickListener(v -> {
            if (binding == null) return;
            if (MotionHelper.areAnimationsEnabled(requireContext())) {
                AutoTransition transition = new AutoTransition();
                transition.setDuration(200L);
                TransitionManager.beginDelayedTransition(binding.containerAuditLogs, transition);
            }
            boolean expand = itemBinding.layoutAuditDiffPanel.getVisibility() != View.VISIBLE;
            itemBinding.layoutAuditDiffPanel.setVisibility(expand ? View.VISIBLE : View.GONE);
            itemBinding.ivAuditExpand
                .animate()
                .rotation(expand ? 90f : 0f)
                .setDuration(200L)
                .start();
        });
    }

    private void bindHighlightRow(
        @NonNull ItemOfficerAuditBinding itemBinding,
        @Nullable Map<String, Object> newMap
    ) {
        if (newMap != null && newMap.containsKey("initialDeposit")) {
            Long deposit = toSafeLong(newMap.get("initialDeposit"));
            if (deposit != null) {
                itemBinding.tvAuditHighlightLabel.setText("Số tiền nạp ban đầu");
                itemBinding.tvAuditHighlightValue.setText(CurrencyUtils.formatVnd(deposit));
                itemBinding.tvAuditHighlightValue.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.phl_emerald_dark)
                );
                itemBinding.layoutAuditHighlight.setVisibility(View.VISIBLE);
                return;
            }
        }
        if (newMap != null && newMap.containsKey("interestRate")) {
            String rateText = formatRateText(newMap.get("interestRate"));
            if (rateText != null) {
                itemBinding.tvAuditHighlightLabel.setText("Lãi suất mới");
                itemBinding.tvAuditHighlightValue.setText(rateText);
                itemBinding.tvAuditHighlightValue.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.phl_navy)
                );
                itemBinding.layoutAuditHighlight.setVisibility(View.VISIBLE);
                return;
            }
        }
        itemBinding.layoutAuditHighlight.setVisibility(View.GONE);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(@Nullable Object raw) {
        if (raw == null) return null;
        if (raw instanceof Map) {
            try {
                return (Map<String, Object>) raw;
            } catch (ClassCastException ignored) {
                return null;
            }
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.startsWith("{") && s.endsWith("}")) {
                try {
                    return GSON.fromJson(s, MAP_TYPE);
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    @NonNull
    private static String formatValueSummary(
        @Nullable Object raw,
        @Nullable Map<String, Object> map
    ) {
        if (raw == null) {
            return "Không có";
        }
        if (map == null || map.isEmpty()) {
            String text = String.valueOf(raw).trim();
            return text.isEmpty() || "null".equalsIgnoreCase(text) ? "Không có" : text;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            if ("initialDeposit".equals(key)) {
                Long deposit = toSafeLong(val);
                sb.append("Số tiền: ").append(
                    deposit != null ? CurrencyUtils.formatVnd(deposit) : String.valueOf(val)
                );
            } else if ("interestRate".equals(key)) {
                String rate = formatRateText(val);
                sb.append("Lãi suất: ").append(rate != null ? rate : String.valueOf(val));
            } else if ("cifNumber".equals(key)) {
                sb.append("CIF: ").append(val);
            } else if ("accountNumber".equals(key)) {
                sb.append("STK: ").append(val);
            } else if ("fullName".equals(key)) {
                sb.append("Họ tên: ").append(val);
            } else if ("status".equals(key)) {
                sb.append("Trạng thái: ").append(val);
            } else if ("phone".equals(key)) {
                sb.append("SĐT: ").append(val);
            } else if ("email".equals(key)) {
                sb.append("Email: ").append(val);
            } else if ("address".equals(key)) {
                sb.append("Địa chỉ: ").append(val);
            } else {
                sb.append(key).append(": ").append(val);
            }
        }
        return sb.length() == 0 ? "Không có" : sb.toString();
    }

    @Nullable
    private static Long toSafeLong(@Nullable Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number) {
            return Math.round(((Number) raw).doubleValue());
        }
        try {
            return Math.round(Double.parseDouble(String.valueOf(raw).trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Nullable
    private static String formatRateText(@Nullable Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number) {
            return String.format(Locale.US, "%.2f%%/năm", ((Number) raw).doubleValue());
        }
        try {
            return String.format(
                Locale.US,
                "%.2f%%/năm",
                Double.parseDouble(String.valueOf(raw).trim())
            );
        } catch (NumberFormatException ex) {
            String text = String.valueOf(raw).trim();
            return text.isEmpty() ? null : text + "%/năm";
        }
    }

    private void logout() {
        if (!isAdded()) return;
        SessionManager session = new SessionManager(requireContext());
        ApiClient.authApi(requireContext()).logout().enqueue(
            new Callback<ApiResponse<Void>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Void>> call,
                    @NonNull Response<ApiResponse<Void>> response
                ) {
                    toLogin(session);
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Void>> call, @NonNull Throwable t) {
                    toLogin(session);
                }
            }
        );
    }

    private void toLogin(SessionManager session) {
        session.clearSession();
        if (!isAdded()) return;
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        MotionHelper.applyActivityTransition(getActivity());
    }
}
