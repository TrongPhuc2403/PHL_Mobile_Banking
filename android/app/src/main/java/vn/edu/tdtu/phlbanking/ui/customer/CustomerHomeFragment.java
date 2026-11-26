package vn.edu.tdtu.phlbanking.ui.customer;

import android.animation.Animator;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.local.SessionManager;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.model.CustomerModels;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.FragmentCustomerHomeBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemHistoryRowBinding;
import vn.edu.tdtu.phlbanking.ui.auth.LoginActivity;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;
import vn.edu.tdtu.phlbanking.utils.CurrencyUtils;

public class CustomerHomeFragment extends Fragment {

    private FragmentCustomerHomeBinding binding;
    private boolean balanceHidden = false;
    private boolean hasAnimatedInitialBalance = false;
    private long latestAvailableBalance = 0L;
    private long lastDisplayedBalance = 0L;
    private String primaryCheckingAccount = "";
    private final List<CustomerModels.HistoryItem> historyRows = new ArrayList<>();
    private HistoryAdapter historyAdapter;
    private int animatedRowsCount = 0;

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentCustomerHomeBinding.inflate(inflater, container, false);
        animatedRowsCount = 0;
        SessionManager session = new SessionManager(requireContext());
        binding.tvHomeGreeting.setText("Xin chào, " + session.getFullName());

        historyAdapter = new HistoryAdapter();
        binding.rvRecentHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRecentHistory.setAdapter(historyAdapter);

        MotionHelper.applyTactilePress(
            binding.btnQuickTransfer,
            binding.btnQuickDepositWithdraw,
            binding.btnQuickGateway,
            binding.btnToggleBalance
        );
        MotionHelper.staggerEntrance(
            binding.cardHomePrestige,
            binding.cardHomeQuickActions,
            binding.layoutHomeHistorySection
        );

        binding.btnToggleBalance.setOnClickListener(v -> {
            balanceHidden = !balanceHidden;
            renderBalance(false);
        });

        binding.btnHomeEkyc.setOnClickListener(v ->
            ((CustomerMainActivity) requireActivity()).selectTab(R.id.tab_cust_profile)
        );
        binding.btnQuickTransfer.setOnClickListener(v -> openTransfer("INTERNAL"));
        binding.btnQuickDepositWithdraw.setOnClickListener(v -> openTransfer("DEPOSIT"));
        binding.btnQuickGateway.setOnClickListener(v -> openTransfer("GATEWAY"));

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            MotionHelper.startShimmerSweep(binding.viewHomeShimmer);
        }
        loadAccountsAndHistory();
        loadEkycStatus();
    }

    @Override
    public void onDestroyView() {
        if (binding != null) {
            MotionHelper.stopShimmer(binding.viewHomeShimmer);
            cancelBalanceAnimators();
        }
        binding = null;
        super.onDestroyView();
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
                        boolean enrolled = Boolean.TRUE.equals(
                            response.body().data.get("enrolled")
                        );
                        binding.btnHomeEkyc.setText(
                            enrolled
                                ? "✓ Đã có ảnh gốc eKYC · Xem bảo mật"
                                : "Chưa đăng ký sinh trắc học · Đăng ký ngay"
                        );
                    } else binding.btnHomeEkyc.setText(
                        "Không tải được trạng thái eKYC · Xem hồ sơ"
                    );
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Throwable error
                ) {
                    if (binding != null) binding.btnHomeEkyc.setText(
                        "Mất kết nối eKYC · Xem hồ sơ"
                    );
                }
            }
        );
    }

    private void openTransfer(String mode) {
        Intent intent = new Intent(requireContext(), TransferActivity.class);
        intent.putExtra(TransferActivity.EXTRA_MODE, mode);
        intent.putExtra(TransferActivity.EXTRA_SOURCE_ACC, primaryCheckingAccount);
        intent.putExtra(TransferActivity.EXTRA_AVAILABLE_BAL, latestAvailableBalance);
        startActivity(intent);
        MotionHelper.applyActivityTransition(requireActivity());
    }

    private boolean isSessionRedirected = false;

    private void handleSessionExpired() {
        if (isSessionRedirected || !isAdded() || getContext() == null) {
            return;
        }
        isSessionRedirected = true;
        new SessionManager(requireContext()).clearSession();
        Toast.makeText(
            requireContext(),
            "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại",
            Toast.LENGTH_SHORT
        ).show();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            MotionHelper.applyActivityTransition(getActivity());
        }
    }

    private void loadAccountsAndHistory() {
        ApiClient.customerApi(requireContext()).getAccounts().enqueue(
            new Callback<ApiResponse<List<CustomerModels.BankAccountItem>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<List<CustomerModels.BankAccountItem>>> call,
                    @NonNull Response<ApiResponse<List<CustomerModels.BankAccountItem>>> response
                ) {
                    if (!isAdded() || binding == null) {
                        return;
                    }
                    if (response.code() == 401) {
                        handleSessionExpired();
                        return;
                    }
                    if (!response.isSuccessful() || response.body() == null) {
                        return;
                    }
                    List<CustomerModels.BankAccountItem> items = response.body().data;
                    if (items != null) {
                        for (CustomerModels.BankAccountItem acc : items) {
                            if ("CHECKING".equalsIgnoreCase(acc.accountType)) {
                                primaryCheckingAccount = acc.accountNumber;
                                latestAvailableBalance = acc.availableBalance;
                                binding.tvHomeAccountNo.setText(
                                    "TK Thanh toán: " + acc.accountNumber
                                );
                                renderBalance(true);
                                break;
                            }
                        }
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<List<CustomerModels.BankAccountItem>>> call,
                    @NonNull Throwable t
                ) {}
            }
        );

        ApiClient.customerApi(requireContext()).getHistory().enqueue(
            new Callback<ApiResponse<List<CustomerModels.HistoryItem>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<List<CustomerModels.HistoryItem>>> call,
                    @NonNull Response<ApiResponse<List<CustomerModels.HistoryItem>>> response
                ) {
                    if (!isAdded() || binding == null) {
                        return;
                    }
                    if (response.code() == 401) {
                        handleSessionExpired();
                        return;
                    }
                    if (!response.isSuccessful() || response.body() == null) {
                        if (historyRows.isEmpty()) {
                            binding.tvHistoryEmpty.setText(
                                "Không thể tải lịch sử giao dịch. Chạm để thử lại."
                            );
                            binding.tvHistoryEmpty.setVisibility(View.VISIBLE);
                            binding.tvHistoryEmpty.setOnClickListener(v ->
                                loadAccountsAndHistory()
                            );
                        }
                        return;
                    }
                    historyRows.clear();
                    if (response.body().data != null) {
                        historyRows.addAll(response.body().data);
                    }
                    animatedRowsCount = 0;
                    binding.tvHistoryEmpty.setVisibility(
                        historyRows.isEmpty() ? View.VISIBLE : View.GONE
                    );
                    if (historyRows.isEmpty()) {
                        binding.tvHistoryCount.setVisibility(View.GONE);
                    } else {
                        binding.tvHistoryCount.setText(historyRows.size() + " giao dịch");
                        binding.tvHistoryCount.setVisibility(View.VISIBLE);
                    }
                    historyAdapter.notifyDataSetChanged();
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<List<CustomerModels.HistoryItem>>> call,
                    @NonNull Throwable t
                ) {}
            }
        );
    }

    private void cancelBalanceAnimators() {
        if (binding == null) {
            return;
        }
        Object counterTag = binding.tvHomeBalance.getTag(R.id.phl_tag_counter_anim);
        if (counterTag instanceof Animator) {
            ((Animator) counterTag).cancel();
            binding.tvHomeBalance.setTag(R.id.phl_tag_counter_anim, null);
        }
        binding.tvHomeBalance.animate().cancel();
        binding.tvHomeBalance.setAlpha(1f);
        binding.btnToggleBalance.animate().cancel();
        binding.btnToggleBalance.setAlpha(1f);
    }

    private void renderBalance(boolean animateIfChanged) {
        if (binding == null) {
            return;
        }
        cancelBalanceAnimators();

        if (balanceHidden) {
            MotionHelper.crossfadeText(binding.tvHomeBalance, "•••••••• VNĐ");
            MotionHelper.crossfadeText(binding.btnToggleBalance, "Hiện");
            binding.btnToggleBalance.setIconResource(R.drawable.ic_eye_open);
        } else {
            MotionHelper.crossfadeText(binding.btnToggleBalance, "Ẩn");
            binding.btnToggleBalance.setIconResource(R.drawable.ic_eye_off);
            if (
                animateIfChanged &&
                (!hasAnimatedInitialBalance || lastDisplayedBalance != latestAvailableBalance)
            ) {
                long fromValue = hasAnimatedInitialBalance ? lastDisplayedBalance : 0L;
                MotionHelper.animateVndCounter(
                    binding.tvHomeBalance,
                    fromValue,
                    latestAvailableBalance,
                    700L
                );
                hasAnimatedInitialBalance = true;
                lastDisplayedBalance = latestAvailableBalance;
            } else {
                MotionHelper.crossfadeText(
                    binding.tvHomeBalance,
                    CurrencyUtils.formatVnd(latestAvailableBalance)
                );
            }
        }
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.RowHolder> {

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemHistoryRowBinding rowBinding = ItemHistoryRowBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
            );
            RowHolder holder = new RowHolder(rowBinding);
            MotionHelper.applyTactilePress(holder.itemView);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            resetItemTransforms(holder.itemView);

            CustomerModels.HistoryItem item = historyRows.get(position);
            holder.rowBinding.tvHistoryTitle.setText(
                item.content != null && !item.content.isEmpty()
                    ? item.content
                    : item.transactionType
            );
            holder.rowBinding.tvHistoryMeta.setText(
                item.traceId + " · " + (item.timestamp != null ? item.timestamp : "")
            );
            holder.rowBinding.tvHistoryAmount.setText(
                CurrencyUtils.formatSignedVnd(item.changeAmount)
            );

            boolean incoming = item.changeAmount >= 0;
            int amountColor = ContextCompat.getColor(
                requireContext(),
                incoming ? R.color.phl_emerald : R.color.phl_danger
            );
            holder.rowBinding.tvHistoryAmount.setTextColor(amountColor);
            holder.rowBinding.badgeHistoryDirection.setBackgroundResource(
                incoming ? R.drawable.bg_icon_circle_emerald : R.drawable.bg_icon_circle_danger
            );
            holder.rowBinding.ivHistoryDirection.setImageResource(
                incoming ? R.drawable.ic_txn_in : R.drawable.ic_txn_out
            );
            holder.rowBinding.ivHistoryDirection.setColorFilter(amountColor);

            if (
                animatedRowsCount < 8 &&
                MotionHelper.areAnimationsEnabled(holder.itemView.getContext())
            ) {
                long delayMs = animatedRowsCount * 45L;
                animatedRowsCount++;
                float offsetPx = 14f * holder.itemView.getResources().getDisplayMetrics().density;
                holder.itemView.setAlpha(0f);
                holder.itemView.setTranslationY(offsetPx);
                holder.itemView
                    .animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(260L)
                    .setStartDelay(delayMs)
                    .start();
            }

            holder.itemView.setOnClickListener(v ->
                ReceiptDialogHelper.showReceipt(
                    requireContext(),
                    item.traceId,
                    item.transactionType,
                    item.amount,
                    item.fee,
                    item.toAccName,
                    item.content,
                    item.timestamp
                )
            );
        }

        @Override
        public void onViewRecycled(@NonNull RowHolder holder) {
            resetItemTransforms(holder.itemView);
            super.onViewRecycled(holder);
        }

        private void resetItemTransforms(@NonNull View view) {
            view.animate().cancel();
            view.setAlpha(1f);
            view.setTranslationY(0f);
            view.setScaleX(1f);
            view.setScaleY(1f);
        }

        @Override
        public int getItemCount() {
            return historyRows.size();
        }

        class RowHolder extends RecyclerView.ViewHolder {

            final ItemHistoryRowBinding rowBinding;

            RowHolder(ItemHistoryRowBinding rowBinding) {
                super(rowBinding.getRoot());
                this.rowBinding = rowBinding;
            }
        }
    }
}
