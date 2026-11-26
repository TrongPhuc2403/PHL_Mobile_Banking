package vn.edu.tdtu.phlbanking.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.transition.TransitionManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.local.SessionManager;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.model.CustomerModels;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.FragmentCustomerAccountsBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemAccountCheckingBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemAccountMortgageBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemAccountSavingsBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemRepaymentRowBinding;
import vn.edu.tdtu.phlbanking.ui.auth.LoginActivity;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;
import vn.edu.tdtu.phlbanking.utils.CurrencyUtils;

public class CustomerAccountsFragment extends Fragment {

    private FragmentCustomerAccountsBinding binding;
    private final Map<String, ItemAccountMortgageBinding> mortgageCardsByAcc = new HashMap<>();
    private final List<ShimmerCardState> shimmerCards = new ArrayList<>();
    private long lastTotalBalance = 0L;

    private static class ShimmerCardState {

        final View shimmerOverlay;
        final View frontView;

        ShimmerCardState(View shimmerOverlay, View frontView) {
            this.shimmerOverlay = shimmerOverlay;
            this.frontView = frontView;
        }
    }

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentCustomerAccountsBinding.inflate(inflater, container, false);
        MotionHelper.staggerEntrance(binding.layoutAccountsHeader);
        loadData();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        for (ShimmerCardState state : shimmerCards) {
            if (state.frontView.getVisibility() == View.VISIBLE) {
                MotionHelper.startShimmerSweep(state.shimmerOverlay);
            }
        }
    }

    @Override
    public void onDestroyView() {
        for (ShimmerCardState state : shimmerCards) {
            MotionHelper.stopShimmer(state.shimmerOverlay);
        }
        shimmerCards.clear();
        mortgageCardsByAcc.clear();
        binding = null;
        super.onDestroyView();
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

    private void showAccountsLoadError(String message) {
        if (!isAdded() || binding == null) {
            return;
        }
        if (binding.containerAccounts.getChildCount() == 0) {
            binding.tvAccountsEmpty.setText(message);
            binding.tvAccountsEmpty.setVisibility(View.VISIBLE);
            binding.tvAccountsEmpty.setOnClickListener(v -> {
                binding.tvAccountsEmpty.setText("Đang tải danh mục tài khoản...");
                loadData();
            });
        }
    }

    private void loadData() {
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
                        showAccountsLoadError(
                            "Không thể tải danh mục tài khoản (" +
                                response.code() +
                                "). Chạm để thử lại."
                        );
                        return;
                    }
                    for (ShimmerCardState state : shimmerCards) {
                        MotionHelper.stopShimmer(state.shimmerOverlay);
                    }
                    shimmerCards.clear();
                    mortgageCardsByAcc.clear();
                    binding.containerAccounts.removeAllViews();

                    List<CustomerModels.BankAccountItem> list = response.body().data;
                    long totalDeposit = 0L;
                    int count = 0;

                    if (list != null) {
                        for (CustomerModels.BankAccountItem acc : list) {
                            count++;
                            if ("CHECKING".equalsIgnoreCase(acc.accountType)) {
                                totalDeposit += Math.max(0L, acc.availableBalance);
                                addCheckingCard(acc);
                            } else if ("SAVINGS".equalsIgnoreCase(acc.accountType)) {
                                totalDeposit += Math.max(0L, acc.currentBalance);
                                addSavingsCard(acc);
                            } else {
                                addMortgageAccountShell(acc);
                            }
                        }
                    }

                    binding.tvAccountsEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
                    binding.tvAccountsCountBadge.setText(count + " tài khoản");
                    MotionHelper.animateVndCounter(
                        binding.tvAccountsTotalBalance,
                        lastTotalBalance,
                        totalDeposit,
                        650L
                    );
                    lastTotalBalance = totalDeposit;
                    MotionHelper.staggerChildren(binding.containerAccounts, 60L);

                    loadMortgageSchedules();
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<List<CustomerModels.BankAccountItem>>> call,
                    @NonNull Throwable t
                ) {
                    showAccountsLoadError("Không thể kết nối máy chủ. Chạm để thử lại.");
                }
            }
        );
    }

    private void loadMortgageSchedules() {
        ApiClient.customerApi(requireContext()).getMortgages().enqueue(
            new Callback<ApiResponse<List<CustomerModels.MortgageLoanItem>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<List<CustomerModels.MortgageLoanItem>>> call,
                    @NonNull Response<ApiResponse<List<CustomerModels.MortgageLoanItem>>> response
                ) {
                    if (
                        !isAdded() ||
                        binding == null ||
                        !response.isSuccessful() ||
                        response.body() == null
                    ) {
                        return;
                    }
                    List<CustomerModels.MortgageLoanItem> loans = response.body().data;
                    if (loans == null || loans.isEmpty()) {
                        return;
                    }
                    binding.tvAccountsEmpty.setVisibility(View.GONE);
                    for (CustomerModels.MortgageLoanItem loan : loans) {
                        ItemAccountMortgageBinding cardBinding = mortgageCardsByAcc.get(
                            loan.accountNumber
                        );
                        if (cardBinding == null) {
                            cardBinding = ItemAccountMortgageBinding.inflate(
                                LayoutInflater.from(requireContext()),
                                binding.containerAccounts,
                                false
                            );
                            mortgageCardsByAcc.put(loan.accountNumber, cardBinding);
                            binding.containerAccounts.addView(cardBinding.getRoot());
                        }
                        bindMortgageLoan(cardBinding, loan);
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<List<CustomerModels.MortgageLoanItem>>> call,
                    @NonNull Throwable t
                ) {}
            }
        );
    }

    private void addCheckingCard(@NonNull CustomerModels.BankAccountItem acc) {
        ItemAccountCheckingBinding card = ItemAccountCheckingBinding.inflate(
            LayoutInflater.from(requireContext()),
            binding.containerAccounts,
            false
        );

        String status = acc.status != null && !acc.status.isEmpty() ? acc.status : "ACTIVE";
        card.tvCheckingAccountNo.setText("Số tài khoản: " + safe(acc.accountNumber));
        card.tvCheckingStatus.setText(status);
        MotionHelper.animateVndCounter(card.tvCheckingAvailable, 0L, acc.availableBalance, 650L);

        card.tvCheckingBackAccountNo.setText("STK: " + safe(acc.accountNumber));
        card.tvCheckingBackCurrent.setText(CurrencyUtils.formatVnd(acc.currentBalance));
        card.tvCheckingBackAvailable.setText(CurrencyUtils.formatVnd(acc.availableBalance));
        card.tvCheckingBackStatus.setText("Trạng thái: " + status);

        ShimmerCardState shimmerState = new ShimmerCardState(
            card.viewCheckingShimmer,
            card.layoutCheckingFront
        );
        shimmerCards.add(shimmerState);
        MotionHelper.startShimmerSweep(card.viewCheckingShimmer);

        final boolean[] isBack = { false };
        MotionHelper.applyTactilePress(card.getRoot());
        card.getRoot().setOnClickListener(v -> {
            isBack[0] = !isBack[0];
            if (isBack[0]) {
                MotionHelper.stopShimmer(card.viewCheckingShimmer);
            }
            MotionHelper.flipCard3D(card.layoutCheckingFront, card.layoutCheckingBack, isBack[0]);
            if (!isBack[0]) {
                MotionHelper.startShimmerSweep(card.viewCheckingShimmer);
            }
        });

        binding.containerAccounts.addView(card.getRoot());
    }

    private void addSavingsCard(@NonNull CustomerModels.BankAccountItem acc) {
        ItemAccountSavingsBinding card = ItemAccountSavingsBinding.inflate(
            LayoutInflater.from(requireContext()),
            binding.containerAccounts,
            false
        );

        int termMonths = acc.savingsTermMonths != null ? acc.savingsTermMonths : 0;
        double rate = acc.savingsInterestRate != null ? acc.savingsInterestRate : 0.0;
        long accrued = acc.accruedInterest != null ? acc.accruedInterest : 0L;

        card.tvSavingsAccountNo.setText("Số tài khoản: " + safe(acc.accountNumber));
        card.tvSavingsTermBadge.setText(
            String.format(Locale.US, "%d tháng · %.1f%%/năm", termMonths, rate)
        );
        MotionHelper.animateVndCounter(card.tvSavingsBalance, 0L, acc.currentBalance, 650L);
        card.tvSavingsAccrued.setText("Lãi dự kiến: +" + CurrencyUtils.formatVnd(accrued));

        card.tvSavingsBackProduct.setText(
            acc.productType != null && !acc.productType.isEmpty()
                ? acc.productType
                : "Tiết kiệm có kỳ hạn"
        );
        card.tvSavingsBackRate.setText(String.format(Locale.US, "%.2f%%/năm", rate));
        card.tvSavingsBackTerm.setText(termMonths + " tháng");
        card.tvSavingsBackMaturity.setText(
            acc.maturityDate != null && !acc.maturityDate.isEmpty()
                ? acc.maturityDate
                : "Theo kỳ hạn"
        );
        card.tvSavingsBackAccrued.setText("+" + CurrencyUtils.formatVnd(accrued));

        ShimmerCardState shimmerState = new ShimmerCardState(
            card.viewSavingsShimmer,
            card.layoutSavingsFront
        );
        shimmerCards.add(shimmerState);
        MotionHelper.startShimmerSweep(card.viewSavingsShimmer);

        final boolean[] isBack = { false };
        MotionHelper.applyTactilePress(card.getRoot());
        card.getRoot().setOnClickListener(v -> {
            isBack[0] = !isBack[0];
            if (isBack[0]) {
                MotionHelper.stopShimmer(card.viewSavingsShimmer);
            }
            MotionHelper.flipCard3D(card.layoutSavingsFront, card.layoutSavingsBack, isBack[0]);
            if (!isBack[0]) {
                MotionHelper.startShimmerSweep(card.viewSavingsShimmer);
            }
        });

        binding.containerAccounts.addView(card.getRoot());
    }

    private void addMortgageAccountShell(@NonNull CustomerModels.BankAccountItem acc) {
        ItemAccountMortgageBinding card = ItemAccountMortgageBinding.inflate(
            LayoutInflater.from(requireContext()),
            binding.containerAccounts,
            false
        );

        card.tvMortgageAccountNo.setText("Số hợp đồng: " + safe(acc.accountNumber));
        double rate = acc.loanInterestRate != null ? acc.loanInterestRate : 0.0;
        card.tvMortgageRateBadge.setText(String.format(Locale.US, "%.1f%%/năm", rate));
        card.tvMortgageCollateral.setText("Đang tải thông tin tài sản thế chấp...");
        long loanAmt = acc.loanAmount != null ? acc.loanAmount : 0L;
        long remAmt = acc.remainingPrincipal != null ? acc.remainingPrincipal : 0L;
        card.tvMortgageLoanAmount.setText(CurrencyUtils.formatVnd(loanAmt));
        card.tvMortgageRemaining.setText(CurrencyUtils.formatVnd(remAmt));
        card.tvMortgageScheduleHeader.setText("Lịch trả nợ");

        mortgageCardsByAcc.put(acc.accountNumber, card);
        binding.containerAccounts.addView(card.getRoot());
    }

    private void bindMortgageLoan(
        @NonNull ItemAccountMortgageBinding card,
        @NonNull CustomerModels.MortgageLoanItem loan
    ) {
        card.tvMortgageAccountNo.setText("Số hợp đồng: " + safe(loan.accountNumber));
        card.tvMortgageRateBadge.setText(
            String.format(Locale.US, "%.1f%%/năm · %d tháng", loan.interestRate, loan.termMonths)
        );
        card.tvMortgageCollateral.setText(
            "Tài sản thế chấp: " + (loan.collateralInfo != null ? loan.collateralInfo : "--")
        );
        card.tvMortgageLoanAmount.setText(CurrencyUtils.formatVnd(loan.loanAmount));
        card.tvMortgageRemaining.setText(CurrencyUtils.formatVnd(loan.remainingPrincipal));

        List<CustomerModels.RepaymentItem> schedules = loan.schedules;
        int scheduleCount = schedules != null ? schedules.size() : 0;
        card.tvMortgageScheduleHeader.setText("Lịch trả nợ (" + scheduleCount + " kỳ)");

        card.layoutRepaymentSchedule.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        if (schedules != null) {
            for (CustomerModels.RepaymentItem item : schedules) {
                ItemRepaymentRowBinding row = ItemRepaymentRowBinding.inflate(
                    inflater,
                    card.layoutRepaymentSchedule,
                    false
                );
                row.tvRepaymentPeriod.setText("Kỳ " + item.periodNo);
                row.tvRepaymentDueDate.setText(item.dueDate != null ? item.dueDate : "");
                row.tvRepaymentBreakdown.setText(
                    "Gốc: " +
                        CurrencyUtils.formatVnd(item.principalAmount) +
                        " · Lãi: " +
                        CurrencyUtils.formatVnd(item.interestAmount)
                );
                row.tvRepaymentTotal.setText(CurrencyUtils.formatVnd(item.totalAmount));
                applyRepaymentStatusStyle(row.tvRepaymentStatus, item.status);
                card.layoutRepaymentSchedule.addView(row.getRoot());
            }
        }

        final boolean[] expanded = { false };
        card.layoutRepaymentSchedule.setVisibility(View.GONE);
        card.ivMortgageChevron.setRotation(0f);
        MotionHelper.applyTactilePress(card.btnToggleSchedule);
        card.btnToggleSchedule.setOnClickListener(v -> {
            if (binding == null) {
                return;
            }
            expanded[0] = !expanded[0];
            TransitionManager.beginDelayedTransition(binding.containerAccounts);
            card.layoutRepaymentSchedule.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            card.ivMortgageChevron
                .animate()
                .rotation(expanded[0] ? 180f : 0f)
                .setDuration(220L)
                .start();
        });
    }

    private void applyRepaymentStatusStyle(@NonNull TextView tvStatus, @Nullable String status) {
        String normalized = status != null ? status.trim().toUpperCase(Locale.US) : "PENDING";
        tvStatus.setText(normalized);
        if ("PAID".equals(normalized)) {
            tvStatus.setBackgroundResource(R.drawable.bg_pill_emerald);
            tvStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.phl_emerald_dark)
            );
        } else if ("OVERDUE".equals(normalized)) {
            tvStatus.setBackgroundResource(R.drawable.bg_pill_danger);
            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.phl_danger));
        } else {
            tvStatus.setBackgroundResource(R.drawable.bg_m2_pill_warning);
            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.phl_warning));
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
