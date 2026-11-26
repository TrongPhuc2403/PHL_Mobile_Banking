package vn.edu.tdtu.phlbanking.ui.customer;

import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.Toast;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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
import vn.edu.tdtu.phlbanking.databinding.BottomSheetTransferConfirmBinding;
import vn.edu.tdtu.phlbanking.databinding.FragmentCustomerUtilitiesBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemUtilityTileBinding;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;
import vn.edu.tdtu.phlbanking.utils.CurrencyUtils;

public class CustomerUtilitiesFragment extends Fragment {

    private FragmentCustomerUtilitiesBinding binding;
    private String checkingAccount = null;

    private static final String[][] SERVICES = {
        { "ELECTRIC", "Tiền điện", "EVN HCMC", "685000" },
        { "WATER", "Tiền nước", "SAWACO", "142000" },
        { "TOPUP", "Nạp điện thoại", "Viettel", "100000" },
        { "FLIGHT", "Vé máy bay", "Vietnam Airlines", "1850000" },
        { "CINEMA", "Vé xem phim", "CGV Crescent Mall", "110000" },
        { "HOTEL", "Đặt khách sạn", "Oakwood Q7", "2450000" },
        { "ECOMMERCE", "Thương mại điện tử", "ShopeePay", "459000" },
    };

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentCustomerUtilitiesBinding.inflate(inflater, container, false);
        MotionHelper.staggerEntrance(binding.layoutUtilitiesHeader);
        loadCheckingAccount();
        renderUtilityGrid();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void loadCheckingAccount() {
        ApiClient.customerApi(requireContext()).getAccounts().enqueue(
            new Callback<ApiResponse<List<CustomerModels.BankAccountItem>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<List<CustomerModels.BankAccountItem>>> call,
                    @NonNull Response<ApiResponse<List<CustomerModels.BankAccountItem>>> response
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
                    for (CustomerModels.BankAccountItem acc : response.body().data) {
                        if ("CHECKING".equalsIgnoreCase(acc.accountType)) {
                            checkingAccount = acc.accountNumber;
                            binding.tvUtilitiesCheckingPill.setText(
                                "TK Thanh toán: " + checkingAccount
                            );
                            break;
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
    }

    private void renderUtilityGrid() {
        if (binding == null || getContext() == null) {
            return;
        }
        binding.gridUtilities.removeAllViews();
        int margin = getResources().getDimensionPixelSize(R.dimen.space_xs);

        for (int i = 0; i < SERVICES.length; i++) {
            String[] svc = SERVICES[i];
            String serviceType = svc[0];
            String label = svc[1];
            String supplier = svc[2];
            long amount = Long.parseLong(svc[3]);

            ItemUtilityTileBinding tileBinding = ItemUtilityTileBinding.inflate(
                getLayoutInflater(),
                binding.gridUtilities,
                false
            );

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setMargins(margin, margin, margin, margin);

            boolean isFeaturedLast = SERVICES.length % 2 == 1 && i == SERVICES.length - 1;
            if (isFeaturedLast) {
                params.columnSpec = GridLayout.spec(0, 2, 1f);
                params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED);
            } else {
                params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            }
            tileBinding.getRoot().setLayoutParams(params);

            tileBinding.imgUtilIcon.setImageResource(resolveServiceIcon(serviceType));
            applyTileAccent(tileBinding, serviceType, isFeaturedLast);

            tileBinding.tvUtilTitle.setText(label);
            tileBinding.tvUtilSupplier.setText(supplier);
            tileBinding.tvUtilPrice.setText(CurrencyUtils.formatVnd(amount));

            MotionHelper.applyTactilePress(tileBinding.getRoot());
            tileBinding
                .getRoot()
                .setOnClickListener(v -> openUtilityPaySheet(serviceType, label, supplier, amount));
            binding.gridUtilities.addView(tileBinding.getRoot());
        }

        MotionHelper.staggerChildren(binding.gridUtilities, 60L);
    }

    @DrawableRes
    private int resolveServiceIcon(String serviceType) {
        switch (serviceType) {
            case "ELECTRIC":
                return R.drawable.ic_util_electric;
            case "WATER":
                return R.drawable.ic_util_water;
            case "TOPUP":
                return R.drawable.ic_util_topup;
            case "FLIGHT":
                return R.drawable.ic_util_flight;
            case "CINEMA":
                return R.drawable.ic_util_cinema;
            case "HOTEL":
                return R.drawable.ic_util_hotel;
            case "ECOMMERCE":
            default:
                return R.drawable.ic_util_ecommerce;
        }
    }

    private void applyTileAccent(
        ItemUtilityTileBinding tileBinding,
        String serviceType,
        boolean isFeaturedLast
    ) {
        if (isFeaturedLast) {
            tileBinding.badgeUtilIcon.setBackgroundResource(R.drawable.bg_icon_circle_gold);
            tileBinding.imgUtilIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.phl_gold)
            );
            tileBinding.tvUtilPrice.setBackgroundResource(R.drawable.bg_pill_gold);
            tileBinding.tvUtilPrice.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.phl_gold)
            );
            return;
        }

        if ("ELECTRIC".equals(serviceType) || "TOPUP".equals(serviceType)) {
            tileBinding.badgeUtilIcon.setBackgroundResource(R.drawable.bg_icon_circle_gold);
            tileBinding.imgUtilIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.phl_gold)
            );
        } else if ("FLIGHT".equals(serviceType) || "HOTEL".equals(serviceType)) {
            tileBinding.badgeUtilIcon.setBackgroundResource(R.drawable.bg_icon_circle_emerald);
            tileBinding.imgUtilIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.phl_emerald)
            );
        } else {
            tileBinding.badgeUtilIcon.setBackgroundResource(R.drawable.bg_icon_circle_navy);
            tileBinding.imgUtilIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.phl_navy)
            );
        }
    }

    private void openUtilityPaySheet(
        String serviceType,
        String label,
        String supplier,
        long amount
    ) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        if (checkingAccount == null) {
            Toast.makeText(
                requireContext(),
                "Chưa tải được tài khoản thanh toán. Vui lòng thử lại.",
                Toast.LENGTH_LONG
            ).show();
            return;
        }
        BottomSheetDialog sheet = new BottomSheetDialog(
            new ContextThemeWrapper(requireContext(), R.style.Theme_PHLBanking)
        );
        BottomSheetTransferConfirmBinding sheetBinding = BottomSheetTransferConfirmBinding.inflate(
            LayoutInflater.from(sheet.getContext())
        );
        sheet.setContentView(sheetBinding.getRoot());

        sheetBinding.tvConfirmSummary.setText(
            "Dịch vụ: " +
                label +
                "\n" +
                "Nhà cung cấp: " +
                supplier +
                "\n" +
                "Tài khoản thanh toán: " +
                checkingAccount +
                "\n" +
                "Số tiền: " +
                CurrencyUtils.formatVnd(amount)
        );

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
                sheetBinding.layoutOtpRow,
                sheetBinding.btnExecuteTransaction
            );
        });

        MotionHelper.applyTactilePress(sheetBinding.btnSendOtp, sheetBinding.btnExecuteTransaction);

        final String idempotencyKey = UUID.randomUUID().toString();
        final Map<String, Object> intent = new HashMap<>();
        intent.put("operation", "UTILITY");
        intent.put("fromAccountNumber", checkingAccount);
        intent.put("serviceType", serviceType);
        intent.put("supplier", supplier);
        intent.put("amount", amount);
        intent.put("idempotencyKey", idempotencyKey);
        sheetBinding.tvSecurityProgress.setText(
            "Giao dịch không quá 10 triệu · Bắt buộc gửi và nhập OTP trước khi thanh toán."
        );
        sheetBinding.btnExecuteTransaction.setEnabled(false);
        sheetBinding.btnSendOtp.setOnClickListener(v -> {
            if (!isAdded() || getContext() == null) {
                return;
            }
            Map<String, Object> otpReq = new HashMap<>();
            otpReq.put("otpType", "TRANSACTION");
            otpReq.put("intent", intent);
            sheetBinding.btnExecuteTransaction.setEnabled(false);
            sheetBinding.btnSendOtp.setEnabled(false);
            ApiClient.customerApi(requireContext()).requestOtp(otpReq).enqueue(
                new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(
                        @NonNull Call<ApiResponse<Map<String, Object>>> call,
                        @NonNull Response<ApiResponse<Map<String, Object>>> response
                    ) {
                        if (!isAdded() || getContext() == null || !sheet.isShowing()) {
                            return;
                        }
                        sheetBinding.btnSendOtp.setEnabled(true);
                        if (
                            response.isSuccessful() &&
                            response.body() != null &&
                            response.body().data != null
                        ) {
                            sheetBinding.btnExecuteTransaction.setEnabled(true);
                            Object preview = response.body().data.get("devOtpPreview");
                            sheetBinding.tvSecurityProgress.setText(
                                preview == null
                                    ? "OTP đã gửi. Nhập 6 số để tiếp tục."
                                    : "DEV · OTP: " + preview + " · Hãy nhập mã bên dưới."
                            );
                        } else sheetBinding.tvSecurityProgress.setText(
                            vn.edu.tdtu.phlbanking.ui.common.ApiErrors.message(
                                response,
                                "Không gửi được OTP"
                            )
                        );
                    }

                    @Override
                    public void onFailure(
                        @NonNull Call<ApiResponse<Map<String, Object>>> call,
                        @NonNull Throwable t
                    ) {
                        if (!isAdded() || getContext() == null || !sheet.isShowing()) {
                            return;
                        }
                        sheetBinding.btnSendOtp.setEnabled(true);
                        Toast.makeText(
                            requireContext(),
                            "Không thể lấy OTP",
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                }
            );
        });

        sheetBinding.btnExecuteTransaction.setOnClickListener(v -> {
            if (!isAdded() || getContext() == null) {
                return;
            }
            String otp =
                sheetBinding.edtConfirmOtp.getText() == null
                    ? ""
                    : sheetBinding.edtConfirmOtp.getText().toString().trim();
            if (otp.length() != 6) {
                MotionHelper.shakeError(sheetBinding.edtConfirmOtp);
                Toast.makeText(requireContext(), "Nhập đủ 6 số OTP", Toast.LENGTH_SHORT).show();
                return;
            }
            sheetBinding.btnExecuteTransaction.setEnabled(false);

            Map<String, Object> body = new HashMap<>();
            body.put("fromAccountNumber", checkingAccount);
            body.put("serviceType", serviceType);
            body.put("supplier", supplier);
            body.put("amount", amount);
            body.put("idempotencyKey", idempotencyKey);
            body.put("otpCode", otp);

            ApiClient.customerApi(requireContext()).payUtility(body).enqueue(
                new Callback<ApiResponse<CustomerModels.TransactionResult>>() {
                    @Override
                    public void onResponse(
                        @NonNull Call<ApiResponse<CustomerModels.TransactionResult>> call,
                        @NonNull Response<ApiResponse<CustomerModels.TransactionResult>> response
                    ) {
                        if (!isAdded() || getContext() == null || !sheet.isShowing()) {
                            return;
                        }
                        sheetBinding.btnExecuteTransaction.setEnabled(true);
                        if (
                            response.isSuccessful() &&
                            response.body() != null &&
                            response.body().data != null
                        ) {
                            sheet.dismiss();
                            CustomerModels.TransactionRecord tx = response.body().data.transaction;
                            if (tx != null) {
                                ReceiptDialogHelper.showReceipt(
                                    requireContext(),
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
                            Toast.makeText(
                                requireContext(),
                                vn.edu.tdtu.phlbanking.ui.common.ApiErrors.message(
                                    response,
                                    "Thanh toán thất bại"
                                ),
                                Toast.LENGTH_LONG
                            ).show();
                        }
                    }

                    @Override
                    public void onFailure(
                        @NonNull Call<ApiResponse<CustomerModels.TransactionResult>> call,
                        @NonNull Throwable t
                    ) {
                        if (!isAdded() || getContext() == null || !sheet.isShowing()) {
                            return;
                        }
                        sheetBinding.btnExecuteTransaction.setEnabled(true);
                        Toast.makeText(
                            requireContext(),
                            "Lỗi kết nối máy chủ",
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                }
            );
        });

        sheet.show();
    }
}
