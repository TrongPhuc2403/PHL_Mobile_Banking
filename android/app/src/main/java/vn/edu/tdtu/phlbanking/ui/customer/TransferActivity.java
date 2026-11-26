package vn.edu.tdtu.phlbanking.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.model.CustomerModels;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.ActivityTransferBinding;
import vn.edu.tdtu.phlbanking.databinding.BottomSheetTransferConfirmBinding;
import vn.edu.tdtu.phlbanking.ui.common.ApiErrors;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;
import vn.edu.tdtu.phlbanking.utils.CurrencyUtils;

public class TransferActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_SOURCE_ACC = "extra_source_acc";
    public static final String EXTRA_AVAILABLE_BAL = "extra_available_bal";

    private static final long EKYC_THRESHOLD_VND = 10_000_000L;

    private ActivityTransferBinding binding;
    private String sourceAccount = "";
    private String currentMode = "INTERNAL";
    private String verifiedEkycToken = null;
    private BottomSheetTransferConfirmBinding activeSheetBinding = null;
    private Map<String, Object> activeIntent;
    private long verifiedUntil;
    private boolean otpRequested;

    private final ActivityResultLauncher<Intent> ekycLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (
                result.getResultCode() == RESULT_OK &&
                result.getData() != null &&
                activeIntent != null &&
                String.valueOf(activeIntent.get("idempotencyKey")).equals(
                    result.getData().getStringExtra("idempotencyKey")
                )
            ) {
                verifiedEkycToken = result
                    .getData()
                    .getStringExtra(FaceEkycActivity.RESULT_EKYC_TOKEN);
                if (verifiedEkycToken != null && activeSheetBinding != null) {
                    activeSheetBinding.btnVerifyEkyc.setText("Đã xác minh khuôn mặt eKYC");
                    verifiedUntil = android.os.SystemClock.elapsedRealtime() + 300_000;
                    activeSheetBinding.btnSendOtp.setEnabled(true);
                    activeSheetBinding.edtConfirmOtp.setEnabled(true);
                    activeSheetBinding.tvSecurityProgress.setText(
                        "✓ Khuôn mặt khớp ảnh gốc · Bước 2: nhấn Gửi OTP rồi nhập mã nhận được."
                    );
                    activeSheetBinding.boxEkycStep.setBackgroundResource(
                        R.drawable.bg_pill_emerald
                    );
                    activeSheetBinding.imgEkycStepIcon.setImageResource(
                        R.drawable.ic_check_verified
                    );
                    activeSheetBinding.imgEkycStepIcon.setColorFilter(
                        ContextCompat.getColor(this, R.color.phl_emerald)
                    );
                    activeSheetBinding.tvEkycStepNote.setText(
                        "Đã hoàn tất xác minh sinh trắc học eKYC"
                    );
                    activeSheetBinding.tvEkycStepNote.setTextColor(
                        ContextCompat.getColor(this, R.color.phl_emerald_dark)
                    );
                }
            }
        }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTransferBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbarTransfer.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.toolbarTransfer.setNavigationOnClickListener(v -> finish());

        sourceAccount = getIntent().getStringExtra(EXTRA_SOURCE_ACC);
        if (sourceAccount == null || sourceAccount.isEmpty()) {
            sourceAccount = "";
            binding.btnContinueTransfer.setEnabled(false);
            loadSourceAccount();
        }
        long bal = getIntent().getLongExtra(EXTRA_AVAILABLE_BAL, 0);
        binding.tvSourceAccountInfo.setText(
            "TK nguồn: " + sourceAccount + " · Khả dụng: " + CurrencyUtils.formatVnd(bal)
        );

        String initialMode = getIntent().getStringExtra(EXTRA_MODE);
        if ("DEPOSIT".equalsIgnoreCase(initialMode)) {
            binding.chipDeposit.setChecked(true);
            applyMode("DEPOSIT", false);
        } else if ("GATEWAY".equalsIgnoreCase(initialMode)) {
            binding.chipGateway.setChecked(true);
            applyMode("GATEWAY", false);
        } else {
            applyMode("INTERNAL", false);
        }

        binding.chipTransferMode.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipInternal) applyMode("INTERNAL", true);
            else if (id == R.id.chipInterbank) applyMode("INTERBANK", true);
            else if (id == R.id.chipGateway) applyMode("GATEWAY", true);
            else if (id == R.id.chipDeposit) applyMode("DEPOSIT", true);
            else if (id == R.id.chipWithdraw) applyMode("WITHDRAW", true);
        });

        setupQuickAmountPresets();
        setupAmountWatcher();

        MotionHelper.staggerEntrance(binding.cardSourceAccount, binding.cardTransferForm);
        MotionHelper.applyTactilePress(
            binding.btnContinueTransfer,
            binding.chipPreset100k,
            binding.chipPreset500k,
            binding.chipPreset1m,
            binding.chipPreset5m,
            binding.chipPreset15m
        );

        binding.btnContinueTransfer.setOnClickListener(v -> openConfirmBottomSheet());
    }

    @Override
    public void finish() {
        super.finish();
        MotionHelper.applyActivityTransition(this);
    }

    private void setupQuickAmountPresets() {
        binding.chipPreset100k.setOnClickListener(v -> applyPresetAmount(100_000L));
        binding.chipPreset500k.setOnClickListener(v -> applyPresetAmount(500_000L));
        binding.chipPreset1m.setOnClickListener(v -> applyPresetAmount(1_000_000L));
        binding.chipPreset5m.setOnClickListener(v -> applyPresetAmount(5_000_000L));
        binding.chipPreset15m.setOnClickListener(v -> applyPresetAmount(15_000_000L));
    }

    private void applyPresetAmount(long presetVnd) {
        binding.edtAmount.setText(String.valueOf(presetVnd));
        if (binding.edtAmount.getText() != null) {
            binding.edtAmount.setSelection(binding.edtAmount.getText().length());
        }
    }

    private void setupAmountWatcher() {
        binding.edtAmount.addTextChangedListener(
            new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    binding.tilAmount.setError(null);
                    String raw = s == null ? "" : s.toString().trim();
                    if (raw.isEmpty()) {
                        binding.tvAmountFormattedPreview.setVisibility(View.GONE);
                        return;
                    }
                    try {
                        long parsed = Long.parseLong(raw);
                        if (parsed > 0) {
                            binding.tvAmountFormattedPreview.setText(
                                "= " +
                                    CurrencyUtils.formatVnd(parsed) +
                                    (parsed > EKYC_THRESHOLD_VND
                                        ? "\nBắt buộc: Khuôn mặt eKYC → OTP → Thực hiện"
                                        : "\nYêu cầu OTP trước khi ghi nhận giao dịch")
                            );
                            binding.tvAmountFormattedPreview.setVisibility(View.VISIBLE);
                        } else {
                            binding.tvAmountFormattedPreview.setVisibility(View.GONE);
                        }
                    } catch (NumberFormatException e) {
                        binding.tvAmountFormattedPreview.setVisibility(View.GONE);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            }
        );
    }

    private void applyMode(String mode, boolean animate) {
        currentMode = mode;
        if (animate && MotionHelper.areAnimationsEnabled(this)) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(220L);
            TransitionManager.beginDelayedTransition(binding.layoutTransferFields, transition);
        }
        binding.tilBankCode.setVisibility(
            "INTERBANK".equals(mode) || "GATEWAY".equals(mode) ? View.VISIBLE : View.GONE
        );
        binding.tilDestAccount.setVisibility(
            "DEPOSIT".equals(mode) || "WITHDRAW".equals(mode) || "GATEWAY".equals(mode)
                ? View.GONE
                : View.VISIBLE
        );
        binding.tilDestName.setVisibility(
            "INTERBANK".equals(mode) || "GATEWAY".equals(mode) ? View.VISIBLE : View.GONE
        );

        if ("GATEWAY".equals(mode)) {
            binding.edtBankCode.setText("VNPAY");
            binding.edtDestName.setText("PHL Merchant Demo");
        } else if ("INTERBANK".equals(mode)) {
            binding.edtBankCode.setText("VCB");
        }
    }

    private void openConfirmBottomSheet() {
        String amountStr =
            binding.edtAmount.getText() == null
                ? ""
                : binding.edtAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            binding.tilAmount.setError("Vui lòng nhập số tiền");
            MotionHelper.shakeError(binding.tilAmount);
            Toast.makeText(this, "Vui lòng nhập số tiền", Toast.LENGTH_SHORT).show();
            return;
        }
        final long amount;
        try {
            amount = Long.parseLong(amountStr);
        } catch (NumberFormatException e) {
            binding.tilAmount.setError("Số tiền không hợp lệ");
            MotionHelper.shakeError(binding.tilAmount);
            Toast.makeText(this, "Số tiền không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            binding.tilAmount.setError("Số tiền phải lớn hơn 0");
            MotionHelper.shakeError(binding.tilAmount);
            Toast.makeText(this, "Số tiền phải lớn hơn 0", Toast.LENGTH_SHORT).show();
            return;
        }

        String destAcc =
            binding.edtDestAccount.getText() == null
                ? ""
                : binding.edtDestAccount.getText().toString().trim();
        String destName =
            binding.edtDestName.getText() == null
                ? ""
                : binding.edtDestName.getText().toString().trim();
        String bankCode =
            binding.edtBankCode.getText() == null
                ? ""
                : binding.edtBankCode.getText().toString().trim();
        String content =
            binding.edtContent.getText() == null
                ? ""
                : binding.edtContent.getText().toString().trim();

        if (sourceAccount == null || sourceAccount.isEmpty()) {
            Toast.makeText(
                this,
                "Chưa tải được tài khoản nguồn. Vui lòng mở lại màn hình.",
                Toast.LENGTH_LONG
            ).show();
            return;
        }
        if (
            ("INTERNAL".equals(currentMode) || "INTERBANK".equals(currentMode)) &&
            destAcc.length() < 6
        ) {
            binding.tilDestAccount.setError("Nhập số tài khoản người nhận hợp lệ");
            return;
        }
        verifiedEkycToken = null;
        verifiedUntil = 0;
        otpRequested = false;
        final String idempotencyKey = UUID.randomUUID().toString();
        final boolean requiresEkyc = amount > EKYC_THRESHOLD_VND;

        BottomSheetDialog sheet = new BottomSheetDialog(
            new ContextThemeWrapper(this, R.style.Theme_PHLBanking)
        );
        BottomSheetTransferConfirmBinding sheetBinding = BottomSheetTransferConfirmBinding.inflate(
            LayoutInflater.from(sheet.getContext())
        );
        activeSheetBinding = sheetBinding;
        activeIntent = buildIntent(amount, destAcc, destName, bankCode, content, idempotencyKey);
        sheetBinding.btnExecuteTransaction.setEnabled(false);
        sheetBinding.btnSendOtp.setEnabled(!requiresEkyc);
        sheetBinding.edtConfirmOtp.setEnabled(!requiresEkyc);
        sheetBinding.tvSecurityProgress.setText(
            requiresEkyc
                ? "Bắt buộc: 1 · Chụp và đối chiếu khuôn mặt → 2 · OTP → 3 · Thực hiện"
                : "Giao dịch không quá 10 triệu: gửi và nhập OTP. Bạn có thể kiểm tra eKYC tại Cá nhân → Bảo mật."
        );
        sheet.setContentView(sheetBinding.getRoot());
        sheet.setOnDismissListener(d -> {
            activeSheetBinding = null;
            activeIntent = null;
            verifiedEkycToken = null;
            otpRequested = false;
        });

        sheet.setOnShowListener(d -> {
            FrameLayout bottomSheet = sheet.findViewById(
                com.google.android.material.R.id.design_bottom_sheet
            );
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            MotionHelper.staggerEntrance(
                sheetBinding.layoutSheetHeader,
                sheetBinding.cardConfirmSummary,
                sheetBinding.boxEkycStep,
                sheetBinding.layoutOtpRow,
                sheetBinding.btnExecuteTransaction
            );
        });

        MotionHelper.applyTactilePress(
            sheetBinding.btnVerifyEkyc,
            sheetBinding.btnSendOtp,
            sheetBinding.btnExecuteTransaction
        );

        String summary =
            "Loại giao dịch: " +
            currentMode +
            "\n" +
            "Tài khoản nguồn: " +
            sourceAccount +
            "\n" +
            (!destAcc.isEmpty() && binding.tilDestAccount.getVisibility() == View.VISIBLE
                ? "Tài khoản nhận: " + destAcc + "\n"
                : "") +
            "Số tiền: " +
            CurrencyUtils.formatVnd(amount) +
            "\n" +
            "Nội dung: " +
            content;
        sheetBinding.tvConfirmSummary.setText(summary);

        if (requiresEkyc) {
            sheetBinding.boxEkycStep.setVisibility(View.VISIBLE);
            sheetBinding.btnVerifyEkyc.setOnClickListener(v -> {
                verifiedEkycToken = null;
                otpRequested = false;
                sheetBinding.btnSendOtp.setEnabled(false);
                sheetBinding.edtConfirmOtp.setEnabled(false);
                sheetBinding.btnExecuteTransaction.setEnabled(false);
                sheetBinding.edtConfirmOtp.setText("");
                Intent camera = new Intent(this, FaceEkycActivity.class);
                camera.putExtra(
                    FaceEkycActivity.EXTRA_INTENT_JSON,
                    new Gson().toJson(activeIntent)
                );
                ekycLauncher.launch(camera);
                MotionHelper.applyActivityTransition(this);
            });
        }

        sheetBinding.btnSendOtp.setOnClickListener(v -> requestTransactionOtp(sheetBinding));
        sheetBinding.btnExecuteTransaction.setOnClickListener(v -> {
            if (requiresEkyc && !hasValidEkyc()) {
                MotionHelper.shakeError(sheetBinding.boxEkycStep);
                Toast.makeText(
                    this,
                    "Vui lòng hoàn tất xác minh eKYC trước",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }
            String otp =
                sheetBinding.edtConfirmOtp.getText() == null
                    ? ""
                    : sheetBinding.edtConfirmOtp.getText().toString().trim();
            if (otp.length() != 6) {
                MotionHelper.shakeError(sheetBinding.edtConfirmOtp);
                Toast.makeText(this, "Vui lòng nhập đủ 6 số OTP", Toast.LENGTH_SHORT).show();
                return;
            }
            executeTransaction(
                sheet,
                sheetBinding,
                amount,
                destAcc,
                destName,
                bankCode,
                content,
                idempotencyKey,
                otp
            );
        });

        sheet.show();
    }

    private boolean hasValidEkyc() {
        return (
            verifiedEkycToken != null && android.os.SystemClock.elapsedRealtime() < verifiedUntil
        );
    }

    private Map<String, Object> buildIntent(
        long amount,
        String destAcc,
        String destName,
        String bankCode,
        String content,
        String key
    ) {
        Map<String, Object> value = new HashMap<>();
        value.put("operation", currentMode);
        value.put("amount", amount);
        value.put("idempotencyKey", key);
        if ("DEPOSIT".equals(currentMode) || "WITHDRAW".equals(currentMode)) value.put(
            "accountNumber",
            sourceAccount
        );
        else value.put("fromAccountNumber", sourceAccount);
        if ("INTERNAL".equals(currentMode) || "INTERBANK".equals(currentMode)) {
            value.put("toAccountNumber", destAcc);
            value.put("content", content);
        }
        if ("INTERBANK".equals(currentMode)) {
            value.put("toBankCode", bankCode);
            value.put("toAccName", destName);
        }
        if ("GATEWAY".equals(currentMode)) {
            value.put("provider", "STRIPE".equalsIgnoreCase(bankCode) ? "STRIPE" : "VNPAY");
            value.put("merchantName", destName);
            value.put("orderInfo", content);
        }
        return value;
    }

    private void loadSourceAccount() {
        ApiClient.customerApi(this).getAccounts().enqueue(
            new Callback<ApiResponse<List<CustomerModels.BankAccountItem>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<List<CustomerModels.BankAccountItem>>> call,
                    @NonNull Response<ApiResponse<List<CustomerModels.BankAccountItem>>> response
                ) {
                    if (isFinishing() || isDestroyed()) return;
                    if (
                        response.isSuccessful() &&
                        response.body() != null &&
                        response.body().data != null
                    ) {
                        for (CustomerModels.BankAccountItem account : response.body().data) {
                            if (
                                "CHECKING".equals(account.accountType) &&
                                "ACTIVE".equals(account.status)
                            ) {
                                sourceAccount = account.accountNumber;
                                binding.tvSourceAccountInfo.setText(
                                    "TK nguồn: " +
                                        sourceAccount +
                                        " · Khả dụng: " +
                                        CurrencyUtils.formatVnd(account.availableBalance)
                                );
                                binding.btnContinueTransfer.setEnabled(true);
                                return;
                            }
                        }
                    }
                    binding.tvSourceAccountInfo.setText(
                        "Không tải được tài khoản nguồn. Vui lòng đăng nhập lại hoặc thử lại sau."
                    );
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<List<CustomerModels.BankAccountItem>>> call,
                    @NonNull Throwable error
                ) {
                    if (!isFinishing() && !isDestroyed()) binding.tvSourceAccountInfo.setText(
                        "Mất kết nối khi tải tài khoản nguồn. Không thể giao dịch."
                    );
                }
            }
        );
    }

    private void requestTransactionOtp(BottomSheetTransferConfirmBinding sheetBinding) {
        if (activeIntent == null) return;
        Number amount = (Number) activeIntent.get("amount");
        if (amount.longValue() > EKYC_THRESHOLD_VND && !hasValidEkyc()) {
            sheetBinding.btnExecuteTransaction.setEnabled(false);
            sheetBinding.tvSecurityProgress.setText(
                "Cần xác minh lại khuôn mặt trước khi gửi OTP."
            );
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("otpType", "TRANSACTION");
        body.put("intent", activeIntent);
        if (verifiedEkycToken != null) body.put("ekycToken", verifiedEkycToken);
        sheetBinding.btnSendOtp.setEnabled(false);
        sheetBinding.btnExecuteTransaction.setEnabled(false);
        otpRequested = false;
        ApiClient.customerApi(this).requestOtp(body).enqueue(
            new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Response<ApiResponse<Map<String, Object>>> response
                ) {
                    if (
                        isFinishing() || isDestroyed() || activeSheetBinding != sheetBinding
                    ) return;
                    sheetBinding.btnSendOtp.setEnabled(true);
                    if (
                        response.isSuccessful() &&
                        response.body() != null &&
                        response.body().data != null
                    ) {
                        otpRequested = true;
                        sheetBinding.btnExecuteTransaction.setEnabled(true);
                        Object preview = response.body().data.get("devOtpPreview");
                        sheetBinding.tvSecurityProgress.setText(
                            preview == null
                                ? "OTP đã gửi. Nhập đủ 6 số để tiếp tục."
                                : "MÔI TRƯỜNG DEV · OTP: " +
                                      preview +
                                      " · Hiệu lực " +
                                      response.body().data.get("expiresInSeconds") +
                                      " giây. Hãy nhập mã bên dưới."
                        );
                    } else sheetBinding.tvSecurityProgress.setText(
                        ApiErrors.message(response, "Không gửi được OTP.")
                    );
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Throwable error
                ) {
                    if (
                        isFinishing() || isDestroyed() || activeSheetBinding != sheetBinding
                    ) return;
                    sheetBinding.btnSendOtp.setEnabled(true);
                    sheetBinding.tvSecurityProgress.setText(
                        "Không gửi được OTP. Vui lòng thử lại."
                    );
                }
            }
        );
    }

    private void executeTransaction(
        BottomSheetDialog sheet,
        BottomSheetTransferConfirmBinding sheetBinding,
        long amount,
        String destAcc,
        String destName,
        String bankCode,
        String content,
        String idempotencyKey,
        String otp
    ) {
        if (!otpRequested || activeIntent == null) return;
        sheetBinding.btnExecuteTransaction.setEnabled(false);

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("idempotencyKey", idempotencyKey);
        body.put("otpCode", otp);
        if (verifiedEkycToken != null) {
            body.put("ekycToken", verifiedEkycToken);
        }

        Call<ApiResponse<CustomerModels.TransactionResult>> call;
        if ("INTERNAL".equals(currentMode)) {
            body.put("fromAccountNumber", sourceAccount);
            body.put("toAccountNumber", destAcc);
            body.put("content", content);
            call = ApiClient.customerApi(this).internalTransfer(body);
        } else if ("INTERBANK".equals(currentMode)) {
            body.put("fromAccountNumber", sourceAccount);
            body.put("toBankCode", bankCode);
            body.put("toAccountNumber", destAcc);
            body.put("toAccName", destName);
            body.put("content", content);
            call = ApiClient.customerApi(this).interbankTransfer(body);
        } else if ("GATEWAY".equals(currentMode)) {
            body.put("fromAccountNumber", sourceAccount);
            body.put("provider", "STRIPE".equalsIgnoreCase(bankCode) ? "STRIPE" : "VNPAY");
            body.put("merchantName", destName);
            body.put("orderInfo", content);
            call = ApiClient.customerApi(this).gatewayPay(body);
        } else if ("DEPOSIT".equals(currentMode)) {
            body.put("accountNumber", sourceAccount);
            call = ApiClient.customerApi(this).deposit(body);
        } else {
            body.put("accountNumber", sourceAccount);
            call = ApiClient.customerApi(this).withdraw(body);
        }

        call.enqueue(
            new Callback<ApiResponse<CustomerModels.TransactionResult>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<CustomerModels.TransactionResult>> call,
                    @NonNull Response<ApiResponse<CustomerModels.TransactionResult>> response
                ) {
                    if (isFinishing() || isDestroyed()) return;
                    sheetBinding.btnExecuteTransaction.setEnabled(true);
                    if (
                        response.isSuccessful() &&
                        response.body() != null &&
                        response.body().data != null
                    ) {
                        if (sheet.isShowing()) {
                            sheet.dismiss();
                        }
                        CustomerModels.TransactionRecord tx = response.body().data.transaction;
                        if (tx != null) {
                            ReceiptDialogHelper.showReceipt(
                                TransferActivity.this,
                                tx.traceId,
                                tx.type,
                                tx.amount,
                                tx.fee,
                                tx.toAccName,
                                tx.content,
                                tx.createdAt
                            );
                        }
                    } else {
                        MotionHelper.shakeError(sheetBinding.btnExecuteTransaction);
                        String errMsg = ApiErrors.message(response, "Giao dịch thất bại");
                        Toast.makeText(TransferActivity.this, errMsg, Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<CustomerModels.TransactionResult>> call,
                    @NonNull Throwable t
                ) {
                    if (isFinishing() || isDestroyed()) return;
                    sheetBinding.btnExecuteTransaction.setEnabled(true);
                    Toast.makeText(
                        TransferActivity.this,
                        "Lỗi kết nối máy chủ",
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }
        );
    }
}
