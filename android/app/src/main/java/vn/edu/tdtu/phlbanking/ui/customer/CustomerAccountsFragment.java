package vn.edu.tdtu.phlbanking.ui.customer;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.OneShotPreDrawListener;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
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
import vn.edu.tdtu.phlbanking.databinding.ItemHistoryRowBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemRepaymentRowBinding;
import vn.edu.tdtu.phlbanking.ui.auth.LoginActivity;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;
import vn.edu.tdtu.phlbanking.utils.CurrencyUtils;

public class CustomerAccountsFragment extends Fragment {

    public enum Section { HISTORY, SAVINGS, MORTGAGE }

    private static final String ARG_SECTION = "accounts_section";
    private static final String STATE_HISTORY_VISIBLE = "history_visible";

    private static final int HISTORY_PAGE_SIZE = 20;
    private static final int HISTORY_PREFETCH_ROWS = 4;
    private static final TimeZone BANK_TIME_ZONE = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");

    private FragmentCustomerAccountsBinding binding;
    private final Map<Long, ItemAccountMortgageBinding> mortgageCardsByAcc = new HashMap<>();
    private final List<ShimmerCardState> shimmerCards = new ArrayList<>();
    private final List<CustomerModels.BankAccountItem> filterAccounts = new ArrayList<>();
    private final List<CustomerModels.HistoryItem> historyRows = new ArrayList<>();
    private long lastTotalBalance = 0L;
    private Call<ApiResponse<List<CustomerModels.BankAccountItem>>> accountsCall;
    private Call<ApiResponse<List<CustomerModels.MortgageLoanItem>>> mortgagesCall;
    private Call<CustomerModels.HistoryPage> historyCall;
    private AlertDialog historyFilterDialog;
    private HistoryAdapter historyAdapter;
    private LinearLayoutManager historyLayoutManager;
    private Long historyAccountId;
    private int historyOffset;
    private boolean historyVisible;
    private boolean hasMoreHistory = true;
    private boolean historyFailed;
    private boolean accountDirectoryReady;
    private Section pendingPortfolioSection;

    public static CustomerAccountsFragment newInstance(@NonNull Section section) {
        CustomerAccountsFragment fragment = new CustomerAccountsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SECTION, section.name());
        fragment.setArguments(args);
        return fragment;
    }

    private final ActivityResultLauncher<Intent> transferLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (binding != null) {
                loadData();
                resetHistory();
            }
        }
    );

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
        setupHistory();
        Bundle args = getArguments();
        String sectionName = args == null ? null : args.getString(ARG_SECTION);
        Section section = sectionName == null ? null : Section.valueOf(sectionName);
        pendingPortfolioSection = section == Section.HISTORY ? null : section;
        if (savedInstanceState == null ? section == Section.HISTORY : savedInstanceState.getBoolean(STATE_HISTORY_VISIBLE)) {
            showHistory(true);
        }
        loadData();
        return binding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_HISTORY_VISIBLE, historyVisible);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && isAdded()) {
            // Re-fetch on every return so staff-updated interest rates and monthly estimates are current.
            if (accountsCall != null) accountsCall.cancel();
            accountsCall = null;
            if (mortgagesCall != null) mortgagesCall.cancel();
            mortgagesCall = null;
            loadData();
        }
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
        if (accountsCall != null) accountsCall.cancel();
        if (mortgagesCall != null) mortgagesCall.cancel();
        accountsCall = null;
        mortgagesCall = null;
        cancelHistoryCall();
        if (historyFilterDialog != null) historyFilterDialog.dismiss();
        historyFilterDialog = null;
        binding.rvAccountHistory.clearOnScrollListeners();
        binding.rvAccountHistory.setAdapter(null);
        historyAdapter = null;
        historyLayoutManager = null;
        filterAccounts.clear();
        historyRows.clear();
        historyAccountId = null;
        historyOffset = 0;
        lastTotalBalance = 0L;
        historyVisible = false;
        hasMoreHistory = true;
        historyFailed = false;
        accountDirectoryReady = false;
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
        if (accountsCall != null) return;
        accountsCall = ApiClient.customerApi(requireContext()).getAccounts();
        accountsCall.enqueue(
            new Callback<ApiResponse<List<CustomerModels.BankAccountItem>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<List<CustomerModels.BankAccountItem>>> call,
                    @NonNull Response<ApiResponse<List<CustomerModels.BankAccountItem>>> response
                ) {
                    if (binding == null || call != accountsCall) return;
                    accountsCall = null;
                    if (response.code() == 401) {
                        handleSessionExpired();
                        return;
                    }
                    if (!response.isSuccessful() || response.body() == null || !response.body().success) {
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
                    if (mortgagesCall != null) mortgagesCall.cancel();
                    mortgagesCall = null;
                    mortgageCardsByAcc.clear();
                    binding.containerAccounts.removeAllViews();
                    filterAccounts.clear();

                    List<CustomerModels.BankAccountItem> list = response.body().data;
                    long totalDeposit = 0L;
                    int count = 0;
                    View sectionTarget = null;

                    if (list != null) {
                        for (CustomerModels.BankAccountItem acc : list) {
                            if ("CHECKING".equalsIgnoreCase(acc.accountType)) {
                                totalDeposit += Math.max(0L, acc.availableBalance);
                                addCheckingCard(acc);
                            } else if ("SAVINGS".equalsIgnoreCase(acc.accountType)) {
                                totalDeposit += Math.max(0L, acc.currentBalance);
                                addSavingsCard(acc);
                                if (sectionTarget == null && pendingPortfolioSection == Section.SAVINGS) {
                                    sectionTarget = binding.containerAccounts.getChildAt(count);
                                }
                            } else if ("MORTGAGE".equalsIgnoreCase(acc.accountType)) {
                                addMortgageAccountShell(acc);
                                if (sectionTarget == null && pendingPortfolioSection == Section.MORTGAGE) {
                                    sectionTarget = binding.containerAccounts.getChildAt(count);
                                }
                            } else {
                                continue;
                            }
                            count++;
                            if (acc.id > 0) filterAccounts.add(acc);
                        }
                    }

                    accountDirectoryReady = true;
                    if (historyAccountId != null && selectedHistoryAccount() == null) {
                        selectHistoryAccount(null);
                    }
                    binding.tvAccountsEmpty.setOnClickListener(null);
                    binding.tvAccountsEmpty.setText("Chưa có tài khoản nào được ghi nhận");
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
                    revealPortfolioSection(sectionTarget);
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<List<CustomerModels.BankAccountItem>>> call,
                    @NonNull Throwable t
                ) {
                    if (binding == null || call != accountsCall) return;
                    accountsCall = null;
                    showAccountsLoadError("Không thể kết nối máy chủ. Chạm để thử lại.");
                }
            }
        );
    }

    private void revealPortfolioSection(@Nullable View target) {
        Section section = pendingPortfolioSection;
        pendingPortfolioSection = null;
        if (section == null || historyVisible) return;
        if (target == null) {
            Toast.makeText(requireContext(), section == Section.SAVINGS
                ? "Chưa có tài khoản tiết kiệm" : "Chưa có khoản vay thế chấp", Toast.LENGTH_SHORT).show();
            return;
        }
        FragmentCustomerAccountsBinding viewBinding = binding;
        OneShotPreDrawListener.add(viewBinding.scrollAccountPortfolio, () -> {
            if (binding != viewBinding || historyVisible || !target.isAttachedToWindow()) return;
            Rect position = new Rect();
            target.getDrawingRect(position);
            viewBinding.scrollAccountPortfolio.offsetDescendantRectToMyCoords(target, position);
            viewBinding.scrollAccountPortfolio.scrollTo(0, position.top);
        });
    }

    private void loadMortgageSchedules() {
        if (mortgagesCall != null || mortgageCardsByAcc.isEmpty()) return;
        for (ItemAccountMortgageBinding card : mortgageCardsByAcc.values()) {
            if (card.layoutRepaymentSchedule.getChildCount() == 0) {
                card.tvMortgageScheduleHeader.setText("Đang tải lịch trả nợ…");
                card.btnToggleSchedule.setEnabled(false);
            }
        }
        mortgagesCall = ApiClient.customerApi(requireContext()).getMortgages();
        mortgagesCall.enqueue(
            new Callback<ApiResponse<List<CustomerModels.MortgageLoanItem>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<List<CustomerModels.MortgageLoanItem>>> call,
                    @NonNull Response<ApiResponse<List<CustomerModels.MortgageLoanItem>>> response
                ) {
                    if (binding == null || call != mortgagesCall) return;
                    mortgagesCall = null;
                    if (response.code() == 401) {
                        handleSessionExpired();
                        return;
                    }
                    ApiResponse<List<CustomerModels.MortgageLoanItem>> body = response.body();
                    if (!response.isSuccessful() || body == null || !body.success || body.data == null) {
                        showMortgageLoadError();
                        return;
                    }
                    for (ItemAccountMortgageBinding card : mortgageCardsByAcc.values()) {
                        if (card.layoutRepaymentSchedule.getChildCount() == 0) {
                            card.tvMortgageScheduleHeader.setText("Chưa có lịch trả nợ");
                            card.tvMortgageNextPayment.setText("Chưa có thông tin kỳ trả tiếp theo");
                            card.ivMortgageChevron.setVisibility(View.GONE);
                        }
                    }
                    for (CustomerModels.MortgageLoanItem loan : body.data) {
                        ItemAccountMortgageBinding card = mortgageCardsByAcc.get(loan.bankAccountId);
                        if (card != null) bindMortgageLoan(card, loan);
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<List<CustomerModels.MortgageLoanItem>>> call,
                    @NonNull Throwable t
                ) {
                    if (binding == null || call != mortgagesCall) return;
                    mortgagesCall = null;
                    showMortgageLoadError();
                }
            }
        );
    }

    private void showMortgageLoadError() {
        for (ItemAccountMortgageBinding card : mortgageCardsByAcc.values()) {
            if (card.layoutRepaymentSchedule.getChildCount() != 0) continue;
            card.tvMortgageScheduleHeader.setText("Không tải được lịch trả nợ. Chạm để thử lại.");
            card.tvMortgageNextPayment.setText("Chưa có thông tin kỳ trả tiếp theo");
            card.ivMortgageChevron.setVisibility(View.GONE);
            card.btnToggleSchedule.setEnabled(true);
            card.btnToggleSchedule.setOnClickListener(v -> loadMortgageSchedules());
        }
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

        boolean canTransact = "ACTIVE".equalsIgnoreCase(status) && !safe(acc.accountNumber).isEmpty();
        card.btnCheckingTransfer.setEnabled(canTransact);
        card.btnCheckingDeposit.setEnabled(canTransact);
        card.btnCheckingWithdraw.setEnabled(canTransact);
        card.btnCheckingTransfer.setOnClickListener(v -> openTransfer(acc, "INTERNAL"));
        card.btnCheckingDeposit.setOnClickListener(v -> openTransfer(acc, "DEPOSIT"));
        card.btnCheckingWithdraw.setOnClickListener(v -> openTransfer(acc, "WITHDRAW"));

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

    private void openTransfer(CustomerModels.BankAccountItem account, String mode) {
        Intent intent = new Intent(requireContext(), TransferActivity.class);
        intent.putExtra(TransferActivity.EXTRA_MODE, mode);
        intent.putExtra(TransferActivity.EXTRA_SOURCE_ACC, account.accountNumber);
        intent.putExtra(TransferActivity.EXTRA_AVAILABLE_BAL, account.availableBalance);
        transferLauncher.launch(intent);
        MotionHelper.applyActivityTransition(requireActivity());
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
        String maturity = formatBankDate(acc.maturityDate, false);
        long monthlyInterest = BigDecimal.valueOf(acc.currentBalance)
            .multiply(BigDecimal.valueOf(rate))
            .divide(BigDecimal.valueOf(1200), -3, RoundingMode.HALF_UP)
            .longValue();

        card.tvSavingsAccountNo.setText("Số tài khoản: " + safe(acc.accountNumber));
        card.tvSavingsTermBadge.setText(
            String.format(Locale.US, "%d tháng · %.2f%%/năm", termMonths, rate)
        );
        MotionHelper.animateVndCounter(card.tvSavingsBalance, 0L, acc.currentBalance, 650L);
        card.tvSavingsMaturity.setText("Ngày đáo hạn: " + maturity);
        card.tvSavingsAccrued.setText(
            "Lãi dự kiến: " + CurrencyUtils.formatVnd(monthlyInterest) + "/tháng"
        );

        card.tvSavingsBackProduct.setText(
            acc.productType != null && !acc.productType.isEmpty()
                ? acc.productType
                : "Tiết kiệm có kỳ hạn"
        );
        card.tvSavingsBackRate.setText(String.format(Locale.US, "%.2f%%/năm", rate));
        card.tvSavingsBackTerm.setText(termMonths + " tháng");
        card.tvSavingsBackMaturity.setText(maturity);
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
        card.tvMortgageCollateral.setText(
            "Tài sản thế chấp: " + (acc.collateralInfo != null ? acc.collateralInfo : "—")
        );
        long loanAmt = acc.loanAmount != null ? acc.loanAmount : 0L;
        long remAmt = acc.remainingPrincipal != null ? acc.remainingPrincipal : 0L;
        card.tvMortgageLoanAmount.setText(CurrencyUtils.formatVnd(loanAmt));
        card.tvMortgageRemaining.setText(CurrencyUtils.formatVnd(remAmt));
        card.tvMortgageScheduleHeader.setText("Lịch trả nợ");
        card.tvMortgageCycle.setText("Chu kỳ trả nợ: " + repaymentCycleLabel(acc.repaymentCycle));
        card.tvMortgageNextPayment.setText("Đang tải kỳ trả tiếp theo…");

        mortgageCardsByAcc.put(acc.id, card);
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
        card.tvMortgageCycle.setText("Chu kỳ trả nợ: " + repaymentCycleLabel(loan.repaymentCycle));
        card.tvMortgageNextPayment.setText("Không có kỳ trả nợ đang chờ");

        List<CustomerModels.RepaymentItem> schedules = loan.schedules;
        int scheduleCount = schedules != null ? schedules.size() : 0;
        card.tvMortgageScheduleHeader.setText("Lịch trả nợ (" + scheduleCount + " kỳ)");
        card.btnToggleSchedule.setEnabled(scheduleCount > 0);
        card.ivMortgageChevron.setVisibility(scheduleCount > 0 ? View.VISIBLE : View.GONE);

        card.layoutRepaymentSchedule.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        boolean nextPendingFound = false;
        if (schedules != null) {
            // The backend returns this list in ascending period order.
            for (CustomerModels.RepaymentItem item : schedules) {
                ItemRepaymentRowBinding row = ItemRepaymentRowBinding.inflate(
                    inflater,
                    card.layoutRepaymentSchedule,
                    false
                );
                String dueDate = formatBankDate(item.dueDate, false);
                row.tvRepaymentPeriod.setText("Kỳ " + item.periodNo);
                row.tvRepaymentDueDate.setText(dueDate);
                if (!nextPendingFound && "PENDING".equalsIgnoreCase(item.status)) {
                    nextPendingFound = true;
                    row.getRoot().setBackgroundResource(R.drawable.bg_m2_pill_warning);
                    row.tvRepaymentNext.setVisibility(View.VISIBLE);
                    card.tvMortgageNextPayment.setText(
                        "Kỳ tiếp theo: " + dueDate + " · " + CurrencyUtils.formatVnd(item.totalAmount)
                    );
                }
                row.tvRepaymentBreakdown.setText(
                    "Gốc: " +
                        CurrencyUtils.formatVnd(item.principalAmount) +
                        " · Lãi: " +
                        CurrencyUtils.formatVnd(item.interestAmount)
                );
                row.tvRepaymentTotal.setText(CurrencyUtils.formatVnd(item.totalAmount));
                row.tvRepaymentTotal.setTextColor(applyRepaymentStatusStyle(row.tvRepaymentStatus, item.status));
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

    private int applyRepaymentStatusStyle(@NonNull TextView tvStatus, @Nullable String status) {
        String normalized = status != null ? status.trim().toUpperCase(Locale.US) : "—";
        tvStatus.setText(normalized);
        int colorRes;
        switch (normalized) {
            case "PAID":
                tvStatus.setBackgroundResource(R.drawable.bg_pill_emerald);
                colorRes = R.color.phl_emerald_dark;
                break;
            case "OVERDUE":
                tvStatus.setBackgroundResource(R.drawable.bg_pill_danger);
                colorRes = R.color.phl_danger;
                break;
            case "PENDING":
                tvStatus.setBackgroundResource(R.drawable.bg_m2_pill_warning);
                colorRes = R.color.phl_warning;
                break;
            default:
                tvStatus.setBackgroundResource(R.drawable.bg_pill_navy);
                colorRes = R.color.phl_navy;
                break;
        }
        int color = ContextCompat.getColor(requireContext(), colorRes);
        tvStatus.setTextColor(color);
        return color;
    }

    private String repaymentCycleLabel(@Nullable String cycle) {
        if ("MONTHLY".equalsIgnoreCase(cycle)) return "Hàng tháng";
        if ("BIWEEKLY".equalsIgnoreCase(cycle)) return "Mỗi 2 tuần";
        return cycle == null || cycle.isEmpty() ? "—" : cycle;
    }

    private String formatBankDate(@Nullable String value, boolean includeTime) {
        if (value == null || value.isEmpty()) return "—";
        String pattern = value.contains("T")
            ? (value.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" : "yyyy-MM-dd'T'HH:mm:ssXXX")
            : (value.contains(" ") ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd");
        SimpleDateFormat source = new SimpleDateFormat(pattern, Locale.US);
        source.setTimeZone(BANK_TIME_ZONE);
        source.setLenient(false);
        try {
            Date date = source.parse(value);
            SimpleDateFormat output = new SimpleDateFormat(
                includeTime ? "dd/MM/yyyy HH:mm:ss" : "dd/MM/yyyy", Locale.US
            );
            output.setTimeZone(BANK_TIME_ZONE);
            return output.format(date);
        } catch (ParseException e) {
            return value;
        }
    }

    private void setupHistory() {
        historyLayoutManager = new LinearLayoutManager(requireContext());
        historyAdapter = new HistoryAdapter();
        binding.rvAccountHistory.setLayoutManager(historyLayoutManager);
        binding.rvAccountHistory.setAdapter(historyAdapter);
        binding.rvAccountHistory.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                recyclerView.post(() -> {
                    if (binding != null && binding.rvAccountHistory == recyclerView) {
                        maybeLoadNextHistoryPage();
                    }
                });
            }
        });
        binding.btnToggleAccountHistory.setOnClickListener(v -> showHistory(!historyVisible));
        binding.btnHistoryAccountFilter.setOnClickListener(v -> showHistoryAccountFilter());
        binding.btnRetryAccountHistory.setOnClickListener(v -> loadHistoryPage());
    }

    private void showHistory(boolean visible) {
        historyVisible = visible;
        binding.scrollAccountPortfolio.setVisibility(visible ? View.GONE : View.VISIBLE);
        binding.layoutAccountHistory.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.btnToggleAccountHistory.setText(
            visible ? "Về danh mục tài khoản" : "Xem lịch sử giao dịch"
        );
        if (visible) {
            maybeLoadNextHistoryPage();
        } else if (historyCall != null) {
            cancelHistoryCall();
            renderHistoryState();
        }
    }

    private void showHistoryAccountFilter() {
        if (!accountDirectoryReady) {
            loadData();
            Toast.makeText(requireContext(), "Chưa tải được danh mục tài khoản. Vui lòng chờ rồi thử lại.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Each option names its type and number, so accounts of the same type are distinct.
        List<CustomerModels.BankAccountItem> options = new ArrayList<>(filterAccounts);
        String[] labels = new String[options.size() + 1];
        labels[0] = "Tất cả tài khoản";
        int selected = 0;
        for (int i = 0; i < options.size(); i++) {
            CustomerModels.BankAccountItem account = options.get(i);
            labels[i + 1] = historyFilterLabel(account);
            if (Objects.equals(historyAccountId, account.id)) selected = i + 1;
        }
        historyFilterDialog = new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Lọc lịch sử theo tài khoản")
            .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                dialog.dismiss();
                selectHistoryAccount(which == 0 ? null : options.get(which - 1));
            })
            .setNegativeButton("Đóng", null)
            .create();
        historyFilterDialog.setOnDismissListener(dialog -> historyFilterDialog = null);
        historyFilterDialog.show();
    }

    @Nullable
    private CustomerModels.BankAccountItem selectedHistoryAccount() {
        for (CustomerModels.BankAccountItem account : filterAccounts) {
            if (Objects.equals(historyAccountId, account.id)) return account;
        }
        return null;
    }

    private String historyFilterLabel(@Nullable CustomerModels.BankAccountItem account) {
        if (account == null) return "Tất cả tài khoản";
        String type;
        switch (account.accountType.toUpperCase(Locale.US)) {
            case "CHECKING": type = "Thanh toán"; break;
            case "SAVINGS": type = "Tiết kiệm"; break;
            case "MORTGAGE": type = "Thế chấp"; break;
            default: type = account.accountType; break;
        }
        return type + " · " + safe(account.accountNumber);
    }

    private void selectHistoryAccount(@Nullable CustomerModels.BankAccountItem account) {
        Long accountId = account == null ? null : account.id;
        if (Objects.equals(historyAccountId, accountId)) return;
        historyAccountId = accountId;
        binding.btnHistoryAccountFilter.setText("Lọc: " + historyFilterLabel(account));
        resetHistory();
    }

    private void cancelHistoryCall() {
        Call<CustomerModels.HistoryPage> previous = historyCall;
        historyCall = null;
        if (previous != null) previous.cancel();
    }

    private void resetHistory() {
        cancelHistoryCall();
        int oldCount = historyRows.size();
        historyRows.clear();
        if (oldCount > 0) historyAdapter.notifyItemRangeRemoved(0, oldCount);
        binding.rvAccountHistory.scrollToPosition(0);
        historyOffset = 0;
        hasMoreHistory = true;
        historyFailed = false;
        if (historyVisible) loadHistoryPage();
        else renderHistoryState();
    }

    private void maybeLoadNextHistoryPage() {
        if (!historyVisible || historyFailed || historyCall != null || !hasMoreHistory) return;
        int lastVisible = historyLayoutManager.findLastVisibleItemPosition();
        if (historyRows.isEmpty() || (lastVisible != RecyclerView.NO_POSITION &&
            lastVisible >= historyRows.size() - HISTORY_PREFETCH_ROWS)) {
            loadHistoryPage();
        }
    }

    private void loadHistoryPage() {
        if (!historyVisible || historyCall != null || !hasMoreHistory || isSessionRedirected) return;
        historyFailed = false;
        final int offset = historyOffset;
        historyCall = ApiClient.customerApi(requireContext()).getHistory(historyAccountId, HISTORY_PAGE_SIZE, offset);
        renderHistoryState();
        historyCall.enqueue(new Callback<CustomerModels.HistoryPage>() {
            @Override
            public void onResponse(
                @NonNull Call<CustomerModels.HistoryPage> call,
                @NonNull Response<CustomerModels.HistoryPage> response
            ) {
                // A callback is valid only if it is still the active request for this view/filter.
                if (binding == null || call != historyCall) return;
                historyCall = null;
                if (response.code() == 401) {
                    renderHistoryState();
                    handleSessionExpired();
                    return;
                }
                CustomerModels.HistoryPage page = response.body();
                if (!response.isSuccessful() || page == null || !page.success || page.data == null ||
                    page.offset != offset || page.limit <= 0 || page.limit > Integer.MAX_VALUE - offset) {
                    historyFailed = true;
                    renderHistoryState();
                    return;
                }
                int start = historyRows.size();
                historyRows.addAll(page.data);
                historyOffset = offset + page.limit;
                hasMoreHistory = page.hasMore && !page.data.isEmpty();
                if (!page.data.isEmpty()) historyAdapter.notifyItemRangeInserted(start, page.data.size());
                renderHistoryState();
            }

            @Override
            public void onFailure(@NonNull Call<CustomerModels.HistoryPage> call, @NonNull Throwable t) {
                if (binding == null || call != historyCall) return;
                historyCall = null;
                historyFailed = true;
                renderHistoryState();
            }
        });
    }

    private void renderHistoryState() {
        binding.progressAccountHistory.setVisibility(historyCall != null ? View.VISIBLE : View.GONE);
        binding.btnRetryAccountHistory.setVisibility(historyFailed ? View.VISIBLE : View.GONE);
        String message = historyFailed ? "Không thể tải lịch sử giao dịch." :
            (historyRows.isEmpty() && !hasMoreHistory ? "Không có giao dịch cho bộ lọc này." : null);
        binding.tvAccountHistoryState.setText(message);
        binding.tvAccountHistoryState.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryRowHolder> {

        @NonNull
        @Override
        public HistoryRowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemHistoryRowBinding row = ItemHistoryRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
            );
            MotionHelper.applyTactilePress(row.getRoot());
            return new HistoryRowHolder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryRowHolder holder, int position) {
            ItemHistoryRowBinding row = holder.row;
            CustomerModels.HistoryItem item = historyRows.get(position);
            boolean incoming = item.changeAmount >= 0;
            String title = safe(item.content).trim();
            row.tvHistoryTitle.setText(title.isEmpty() ? safe(item.transactionType) : title);
            // History exposes the recipient, not the sender of an incoming transfer.
            String recipient = safe(item.toAccName).trim();
            boolean incomingTransfer = incoming && safe(item.transactionType).endsWith("_TRANSFER");
            row.tvHistoryCounterparty.setText(incomingTransfer
                ? "Người gửi: Không có thông tin"
                : "Đối tác: " + (recipient.isEmpty() ? "—" : recipient));
            row.tvHistoryCounterparty.setVisibility(View.VISIBLE);
            row.tvHistoryMeta.setText(formatBankDate(item.timestamp, true));
            row.tvHistoryAmount.setText(CurrencyUtils.formatSignedVnd(item.changeAmount));
            int amountColor = ContextCompat.getColor(row.getRoot().getContext(),
                incoming ? R.color.phl_emerald_dark : R.color.phl_danger);
            row.tvHistoryAmount.setTextColor(amountColor);
            row.badgeHistoryDirection.setBackgroundResource(
                incoming ? R.drawable.bg_icon_circle_emerald : R.drawable.bg_icon_circle_danger
            );
            row.ivHistoryDirection.setImageResource(incoming ? R.drawable.ic_txn_in : R.drawable.ic_txn_out);
            row.ivHistoryDirection.setColorFilter(amountColor);
            row.getRoot().setOnClickListener(v -> ReceiptDialogHelper.showReceipt(
                v.getContext(), item.traceId, item.transactionType, item.amount, item.fee,
                item.toAccName, item.content, item.timestamp
            ));
        }

        @Override
        public int getItemCount() {
            return historyRows.size();
        }
    }

    private static class HistoryRowHolder extends RecyclerView.ViewHolder {

        final ItemHistoryRowBinding row;

        HistoryRowHolder(ItemHistoryRowBinding row) {
            super(row.getRoot());
            this.row = row;
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
