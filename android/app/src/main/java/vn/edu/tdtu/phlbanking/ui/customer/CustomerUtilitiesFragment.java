package vn.edu.tdtu.phlbanking.ui.customer;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.model.CustomerModels;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.BottomSheetTransferConfirmBinding;
import vn.edu.tdtu.phlbanking.databinding.BottomSheetUtilityBookingBinding;
import vn.edu.tdtu.phlbanking.databinding.FragmentCustomerUtilitiesBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemUtilityTileBinding;
import vn.edu.tdtu.phlbanking.ui.common.ApiErrors;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;
import vn.edu.tdtu.phlbanking.utils.CurrencyUtils;

/**
 * Fragment hiển thị danh mục và thực hiện thanh toán/đặt chỗ dịch vụ tiện ích.
 */
public class CustomerUtilitiesFragment extends Fragment {

    public enum Mode { ALL, BILL_PAYMENT, TOPUP }

    private static final String ARG_MODE = "utilities_mode";
    private static final long EKYC_THRESHOLD_VND = 10_000_000L;

    private FragmentCustomerUtilitiesBinding binding;
    private Mode mode = Mode.ALL;
    private String checkingAccount;

    private BottomSheetDialog activePaymentSheet;
    private BottomSheetTransferConfirmBinding activePaymentBinding;
    private Map<String, Object> activePaymentIntent;
    private String activeEkycToken;
    private String activeIdempotencyKey;
    private final List<Call<?>> pendingPaymentCalls = new ArrayList<>();

    private final ActivityResultLauncher<Intent> utilityEkycLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent data = result.getData();
            if (result.getResultCode() != Activity.RESULT_OK || data == null ||
                activePaymentSheet == null || !activePaymentSheet.isShowing() || activePaymentIntent == null) return;
            if (!activeIdempotencyKey.equals(data.getStringExtra("idempotencyKey"))) return;
            activeEkycToken = data.getStringExtra(FaceEkycActivity.RESULT_EKYC_TOKEN);
            if (activePaymentBinding != null && activeEkycToken != null) {
                activePaymentBinding.tvSecurityProgress.setText("Đã xác minh eKYC. Bạn có thể yêu cầu OTP.");
                activePaymentBinding.btnSendOtp.setEnabled(true);
            }
        }
    );

    private static final String[][] SERVICES = {
        {"ELECTRIC", "Tiền điện", "EVN HCMC", "685000"},
        {"WATER", "SaWaCo", "SAWACO", "142000"},
        {"TOPUP", "Nạp điện thoại", "Viettel", "100000"},
        {"FLIGHT", "Vé máy bay", "Vietnam Airlines", "1850000"},
        {"CINEMA", "Vé xem phim", "CGV Crescent Mall", "110000"},
        {"HOTEL", "Đặt khách sạn", "Oakwood Q7", "2450000"},
        {"ECOMMERCE", "Thương mại điện tử", "ShopeePay", "459000"}
    };

    public static CustomerUtilitiesFragment newInstance(@NonNull Mode mode) {
        CustomerUtilitiesFragment fragment = new CustomerUtilitiesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, mode.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCustomerUtilitiesBinding.inflate(inflater, container, false);
        Bundle state = savedInstanceState != null ? savedInstanceState : getArguments();
        mode = state == null ? Mode.ALL : Mode.valueOf(state.getString(ARG_MODE, Mode.ALL.name()));
        binding.btnShowAllUtilities.setOnClickListener(v -> {
            mode = Mode.ALL;
            renderUtilityGrid();
        });
        MotionHelper.staggerEntrance(binding.layoutUtilitiesHeader);
        loadCheckingAccount();
        renderUtilityGrid();
        return binding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ARG_MODE, mode.name());
    }

    @Override
    public void onDestroyView() {
        dismissActivePayment();
        super.onDestroyView();
        binding = null;
    }

    private void dismissActivePayment() {
        for (Call<?> call : pendingPaymentCalls) {
            if (call != null && !call.isCanceled()) {
                call.cancel();
            }
        }
        pendingPaymentCalls.clear();
        if (activePaymentSheet != null && activePaymentSheet.isShowing()) {
            activePaymentSheet.dismiss();
        }
        activePaymentSheet = null;
        activePaymentBinding = null;
        activePaymentIntent = null;
        activeEkycToken = null;
        activeIdempotencyKey = null;
    }

    private void loadCheckingAccount() {
        Call<ApiResponse<List<CustomerModels.BankAccountItem>>> call = ApiClient.customerApi(requireContext()).getAccounts();
        call.enqueue(new Callback<ApiResponse<List<CustomerModels.BankAccountItem>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<CustomerModels.BankAccountItem>>> c,
                                   @NonNull Response<ApiResponse<List<CustomerModels.BankAccountItem>>> response) {
                if (!isAdded() || binding == null || !response.isSuccessful() || response.body() == null || response.body().data == null) return;
                for (CustomerModels.BankAccountItem account : response.body().data) {
                    if ("CHECKING".equalsIgnoreCase(account.accountType)) {
                        checkingAccount = account.accountNumber;
                        binding.tvUtilitiesCheckingPill.setText("TK Thanh toán: " + checkingAccount);
                        break;
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<CustomerModels.BankAccountItem>>> c, @NonNull Throwable t) { }
        });
    }

    private void renderUtilityGrid() {
        if (binding == null || getContext() == null) return;
        binding.tvUtilitiesSectionTitle.setText(mode == Mode.TOPUP ? "Nạp tiền điện thoại" : mode == Mode.BILL_PAYMENT ? "Thanh toán hóa đơn" : "Danh mục dịch vụ");
        binding.btnShowAllUtilities.setVisibility(mode == Mode.ALL ? View.GONE : View.VISIBLE);
        binding.gridUtilities.removeAllViews();
        int margin = getResources().getDimensionPixelSize(R.dimen.space_xs);
        for (int i = 0; i < SERVICES.length; i++) {
            String[] service = SERVICES[i];
            String type = service[0];
            if (mode == Mode.TOPUP && !"TOPUP".equals(type)) continue;
            if (mode == Mode.BILL_PAYMENT && !"ELECTRIC".equals(type) && !"WATER".equals(type)) continue;
            String label = service[1], supplier = service[2];
            long amount = Long.parseLong(service[3]);
            ItemUtilityTileBinding tile = ItemUtilityTileBinding.inflate(getLayoutInflater(), binding.gridUtilities, false);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.setMargins(margin, margin, margin, margin);
            boolean featured = mode == Mode.ALL && i == SERVICES.length - 1;
            if (featured || mode == Mode.TOPUP) {
                lp.columnSpec = GridLayout.spec(0, 2, 1f);
                lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED);
            } else {
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            }
            tile.getRoot().setLayoutParams(lp);
            tile.imgUtilIcon.setImageResource(resolveServiceIcon(type));
            applyTileAccent(tile, type, featured);
            tile.tvUtilTitle.setText(label);
            tile.tvUtilSupplier.setText(supplier);
            tile.tvUtilPrice.setText(CurrencyUtils.formatVnd(amount));
            MotionHelper.applyTactilePress(tile.getRoot());
            tile.getRoot().setOnClickListener(v -> {
                if ("ELECTRIC".equals(type) || "WATER".equals(type) || "ECOMMERCE".equals(type)) {
                    openUtilityPaySheet(type, label, supplier, amount);
                } else {
                    openBookingForm(type, label, supplier, amount);
                }
            });
            binding.gridUtilities.addView(tile.getRoot());
        }
        MotionHelper.staggerChildren(binding.gridUtilities, 60L);
    }

    @DrawableRes
    private int resolveServiceIcon(String type) {
        switch (type) {
            case "ELECTRIC": return R.drawable.ic_util_electric;
            case "WATER": return R.drawable.ic_util_water;
            case "TOPUP": return R.drawable.ic_util_topup;
            case "FLIGHT": return R.drawable.ic_util_flight;
            case "CINEMA": return R.drawable.ic_util_cinema;
            default: return R.drawable.ic_util_ecommerce;
        }
    }

    private void applyTileAccent(ItemUtilityTileBinding tile, String type, boolean featured) {
        int color = featured || "ELECTRIC".equals(type) || "TOPUP".equals(type) ? R.color.phl_gold
            : "FLIGHT".equals(type) || "HOTEL".equals(type) ? R.color.phl_emerald : R.color.phl_navy;
        tile.badgeUtilIcon.setBackgroundResource(featured ? R.drawable.bg_icon_circle_gold
            : color == R.color.phl_gold ? R.drawable.bg_icon_circle_gold
            : color == R.color.phl_emerald ? R.drawable.bg_icon_circle_emerald : R.drawable.bg_icon_circle_navy);
        tile.imgUtilIcon.setColorFilter(ContextCompat.getColor(requireContext(), color));
        if (featured) {
            tile.tvUtilPrice.setBackgroundResource(R.drawable.bg_pill_gold);
            tile.tvUtilPrice.setTextColor(ContextCompat.getColor(requireContext(), R.color.phl_gold));
        }
    }

    private void openBookingForm(String type, String label, String defaultSupplier, long suggestedAmount) {
        if (!isAdded() || getContext() == null) return;
        final BottomSheetDialog dialog = new BottomSheetDialog(new ContextThemeWrapper(requireContext(), R.style.Theme_PHLBanking));
        final BottomSheetUtilityBookingBinding form = BottomSheetUtilityBookingBinding.inflate(LayoutInflater.from(dialog.getContext()));
        final List<Call<?>> calls = new ArrayList<>();
        final CountDownTimer[] timer = {null};
        final long[] amount = {suggestedAmount};
        final String[] supplier = {defaultSupplier};
        final String[] billCode = {null};
        final int[] nights = {1};
        final long[] roomRate = {2_450_000};
        final Set<String> chosenSeats = new LinkedHashSet<>();
        final Map<String, Object> ticket = new HashMap<>();

        final TextInputEditText[] phoneInput = {null};
        final TextInputEditText[] flightNameInput = {null};
        final TextInputEditText[] flightIdInput = {null};

        form.tvBookingTitle.setText(label);
        form.edtBookingSupplier.setVisibility(View.GONE);
        form.edtBookingDetails.setVisibility(View.GONE);
        form.edtBookingAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        form.edtBookingAmount.setEnabled(false);
        LinearLayout content = form.layoutBookingSpecific;

        java.util.function.Consumer<String> addSection = title -> {
            TextView text = new TextView(dialog.getContext());
            text.setText(title);
            text.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = getResources().getDimensionPixelSize(R.dimen.space_sm);
            content.addView(text, params);
        };

        java.util.function.Function<String, TextInputEditText> addField = hint -> {
            TextInputLayout layout = new TextInputLayout(dialog.getContext());
            layout.setHint(hint);
            layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = getResources().getDimensionPixelSize(R.dimen.space_xs);
            TextInputEditText edit = new TextInputEditText(dialog.getContext());
            layout.addView(edit);
            content.addView(layout, params);
            return edit;
        };

        java.util.function.Consumer<String> setAmount = value -> {
            try {
                amount[0] = Long.parseLong(value);
                form.edtBookingAmount.setText(String.valueOf(amount[0]));
            } catch (NumberFormatException ignored) { }
        };

        java.util.function.Function<String[], ChipGroup> addChoices = values -> {
            ChipGroup group = new ChipGroup(dialog.getContext());
            group.setSingleSelection(true);
            group.setSelectionRequired(true);
            for (String value : values) {
                Chip chip = new Chip(dialog.getContext());
                chip.setText(value);
                chip.setCheckable(true);
                chip.setTag(value);
                group.addView(chip);
            }
            content.addView(group);
            if (group.getChildCount() > 0) ((Chip) group.getChildAt(0)).setChecked(true);
            return group;
        };

        java.util.function.Consumer<String> startHoldTimer = name -> {
            long minutes = "CINEMA".equals(type) ? 5 : "FLIGHT".equals(type) ? 10 : 15;
            timer[0] = new CountDownTimer(minutes * 60_000L, 1000L) {
                @Override
                public void onTick(long left) {
                    if (dialog.isShowing()) {
                        form.tvBookingCatalog.setText(name + " · " + String.format(Locale.US, "%02d:%02d", left / 60_000, (left / 1000) % 60) + " · Mô phỏng trên ứng dụng, chưa được máy chủ giữ chỗ.");
                    }
                }

                @Override
                public void onFinish() {
                    if (dialog.isShowing()) {
                        Toast.makeText(requireContext().getApplicationContext(), "Thời gian thao tác đã hết. Vui lòng bắt đầu lại.", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    }
                }
            }.start();
        };

        if ("TOPUP".equals(type)) {
            addSection.accept("Số điện thoại");
            TextInputEditText phoneField = addField.apply("SĐT (10 chữ số, bắt đầu bằng 0)");
            phoneInput[0] = phoneField;
            phoneField.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            phoneField.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});

            Call<ApiResponse<Map<String, Object>>> profileCall = ApiClient.customerApi(requireContext()).getMyProfile();
            calls.add(profileCall);
            profileCall.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                    if (!dialog.isShowing() || phoneField.length() > 0 || response.body() == null || response.body().data == null) return;
                    Object profile = response.body().data.get("profile");
                    if (profile instanceof Map) {
                        Object phone = ((Map<?, ?>) profile).get("phone");
                        if (phone != null) phoneField.setText(String.valueOf(phone));
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable error) { }
            });

            addSection.accept("Nhà mạng");
            ChipGroup carriers = addChoices.apply(new String[]{"Viettel", "Mobifone", "Vinaphone"});
            carriers.setOnCheckedStateChangeListener((group, ids) -> {
                if (!ids.isEmpty()) supplier[0] = ((Chip) group.findViewById(ids.get(0))).getText().toString();
            });

            addSection.accept("Mệnh giá chuẩn");
            GridLayout denominations = new GridLayout(dialog.getContext());
            denominations.setColumnCount(3);
            content.addView(denominations);
            long[] values = {10_000, 20_000, 50_000, 100_000, 200_000, 500_000};
            for (long value : values) {
                MaterialButton chip = new MaterialButton(dialog.getContext());
                chip.setText(CurrencyUtils.formatVnd(value));
                denominations.addView(chip);
                chip.setOnClickListener(v -> {
                    setAmount.accept(String.valueOf(value));
                    ticket.put("denomination", value);
                });
            }
            setAmount.accept("10000");

        } else if ("CINEMA".equals(type)) {
            addSection.accept("Cụm rạp");
            ChipGroup cinemas = addChoices.apply(new String[]{"CGV Crescent Mall", "Lotte Nam Sài Gòn", "Beta Ung Văn Khiêm"});
            cinemas.setOnCheckedStateChangeListener((g, ids) -> {
                if (!ids.isEmpty()) supplier[0] = ((Chip) g.findViewById(ids.get(0))).getText().toString();
            });

            addSection.accept("Phim");
            ChipGroup movies = addChoices.apply(new String[]{"Đất Rừng Phương Nam", "Interstellar IMAX"});
            movies.setOnCheckedStateChangeListener((g, ids) -> {
                if (!ids.isEmpty()) ticket.put("movie", ((Chip) g.findViewById(ids.get(0))).getText().toString());
            });

            addSection.accept("Suất chiếu");
            ChipGroup showtimes = addChoices.apply(new String[]{"14:30", "17:15", "19:45", "21:30"});
            showtimes.setOnCheckedStateChangeListener((g, ids) -> {
                if (!ids.isEmpty()) ticket.put("showtime", ((Chip) g.findViewById(ids.get(0))).getText().toString());
            });

            addSection.accept("Chọn ghế (xám: đã đặt)");
            GridLayout seats = new GridLayout(dialog.getContext());
            seats.setColumnCount(6);
            content.addView(seats);
            Set<String> sold = new HashSet<>(Arrays.asList("A3", "B2", "C5", "D1"));
            TextView selectedSeatsLabel = new TextView(dialog.getContext());
            content.addView(selectedSeatsLabel);

            for (int row = 0; row < 4; row++) {
                for (int col = 1; col <= 6; col++) {
                    String seat = "" + (char) ('A' + row) + col;
                    MaterialButton button = new MaterialButton(dialog.getContext());
                    button.setText(seat);
                    button.setMinWidth(0);
                    button.setPadding(2, 2, 2, 2);
                    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                    params.width = 0;
                    params.columnSpec = GridLayout.spec(col - 1, 1f);
                    params.setMargins(2, 2, 2, 2);
                    button.setLayoutParams(params);

                    if (sold.contains(seat)) {
                        button.setEnabled(false);
                        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.phl_border));
                    } else {
                        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.phl_surface));
                        button.setOnClickListener(v -> {
                            if (chosenSeats.remove(seat)) {
                                button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.phl_surface));
                                button.setTextColor(ContextCompat.getColor(requireContext(), R.color.phl_text_primary));
                            } else {
                                chosenSeats.add(seat);
                                button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.phl_navy));
                                button.setTextColor(ContextCompat.getColor(requireContext(), R.color.phl_white));
                            }
                            selectedSeatsLabel.setText(chosenSeats.isEmpty() ? "Chưa chọn ghế" : "Ghế đang chọn: " + TextUtils.join(", ", chosenSeats));
                            ticket.put("selectedSeats", new ArrayList<>(chosenSeats));
                            ticket.put("selectedSeatCount", chosenSeats.size());
                            setAmount.accept(String.valueOf(110_000L * chosenSeats.size()));
                        });
                    }
                    seats.addView(button);
                }
            }
            setAmount.accept("0");
            startHoldTimer.accept("Giữ ghế");

        } else if ("FLIGHT".equals(type)) {
            addSection.accept("Chặng bay");
            ChipGroup origins = addChoices.apply(new String[]{"SGN - TP.HCM"});
            ChipGroup destinations = addChoices.apply(new String[]{"HAN - Hà Nội", "DAD - Đà Nẵng", "PXU - Pleiku"});
            destinations.setOnCheckedStateChangeListener((g, ids) -> {
                if (!ids.isEmpty()) ticket.put("destination", ((Chip) g.findViewById(ids.get(0))).getText().toString());
            });

            MaterialButton reverse = new MaterialButton(dialog.getContext());
            reverse.setText("Đảo chiều nhanh");
            content.addView(reverse);
            reverse.setOnClickListener(v -> ticket.put("routeReversed", true));

            Button date = new Button(dialog.getContext());
            date.setText("Chọn ngày bay");
            content.addView(date);
            date.setOnClickListener(v -> showDatePicker(dialog, date, new String[]{""}));

            addSection.accept("Hãng / chuyến bay");
            ChipGroup flights = addChoices.apply(new String[]{"Vietnam Airlines - VN248", "Vietjet Air - VJ134"});
            flights.setOnCheckedStateChangeListener((g, ids) -> {
                if (!ids.isEmpty()) ticket.put("flight", ((Chip) g.findViewById(ids.get(0))).getText().toString());
            });

            addSection.accept("Hạng vé");
            ChipGroup fares = addChoices.apply(new String[]{"Phổ thông · 1.850.000đ", "Thương gia · 3.400.000đ"});
            fares.setOnCheckedStateChangeListener((g, ids) -> {
                if (!ids.isEmpty()) {
                    String fare = ((Chip) g.findViewById(ids.get(0))).getText().toString();
                    ticket.put("fare", fare);
                    setAmount.accept(fare.contains("3.400") ? "3400000" : "1850000");
                }
            });

            addSection.accept("Hành khách");
            flightNameInput[0] = addField.apply("Họ tên hành khách");
            flightIdInput[0] = addField.apply("CCCD (12 chữ số)");
            flightIdInput[0].setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            flightIdInput[0].setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(12)});

            Call<ApiResponse<Map<String, Object>>> profile = ApiClient.customerApi(requireContext()).getMyProfile();
            calls.add(profile);
            profile.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> c, @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                    if (!dialog.isShowing() || r.body() == null || r.body().data == null) return;
                    Object p = r.body().data.get("profile");
                    if (p instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) p;
                        if (flightNameInput[0].length() == 0 && m.get("fullName") != null) flightNameInput[0].setText(String.valueOf(m.get("fullName")));
                        if (flightIdInput[0].length() == 0 && m.get("idCardNumber") != null) flightIdInput[0].setText(String.valueOf(m.get("idCardNumber")));
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> c, @NonNull Throwable t) { }
            });
            ticket.put("origin", "SGN - TP.HCM");
            startHoldTimer.accept("Giữ chỗ");

        } else if ("HOTEL".equals(type)) {
            addSection.accept("Khách sạn");
            ChipGroup hotels = addChoices.apply(new String[]{"Oakwood Residence Q7", "Fusion Suites Sài Gòn", "Mường Thanh Luxury Đà Nẵng"});
            hotels.setOnCheckedStateChangeListener((g, ids) -> {
                if (!ids.isEmpty()) supplier[0] = ((Chip) g.findViewById(ids.get(0))).getText().toString();
            });

            Button checkInButton = new Button(dialog.getContext());
            checkInButton.setText("Chọn ngày nhận phòng");
            content.addView(checkInButton);
            final String[] checkIn = {""};
            TextView checkOut = new TextView(dialog.getContext());
            content.addView(checkOut);

            checkInButton.setOnClickListener(v -> {
                Calendar calendar = Calendar.getInstance();
                new DatePickerDialog(dialog.getContext(), (picker, y, m, d) -> {
                    checkIn[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                    checkInButton.setText(String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y));
                    updateCheckout(checkOut, checkIn[0], nights[0]);
                    ticket.put("checkIn", checkIn[0]);
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
            });

            addSection.accept("Số đêm lưu trú");
            LinearLayout nightRow = new LinearLayout(dialog.getContext());
            nightRow.setGravity(Gravity.CENTER);
            Button minus = new Button(dialog.getContext());
            minus.setText("−");
            TextView nightLabel = new TextView(dialog.getContext());
            Button plus = new Button(dialog.getContext());
            plus.setText("+");
            nightRow.addView(minus);
            nightRow.addView(nightLabel);
            nightRow.addView(plus);
            content.addView(nightRow);

            Runnable updateNights = () -> {
                nightLabel.setText(nights[0] + " đêm");
                updateCheckout(checkOut, checkIn[0], nights[0]);
                setAmount.accept(String.valueOf(roomRate[0] * nights[0]));
                ticket.put("nights", nights[0]);
            };
            minus.setOnClickListener(v -> {
                nights[0] = Math.max(1, nights[0] - 1);
                updateNights.run();
            });
            plus.setOnClickListener(v -> {
                nights[0] = Math.min(7, nights[0] + 1);
                updateNights.run();
            });

            addSection.accept("Hạng phòng");
            ChipGroup rooms = addChoices.apply(new String[]{"Deluxe · 2.450.000đ/đêm", "Executive · 3.900.000đ/đêm"});
            rooms.setOnCheckedStateChangeListener((g, ids) -> {
                if (!ids.isEmpty()) {
                    String choice = ((Chip) g.findViewById(ids.get(0))).getText().toString();
                    roomRate[0] = choice.contains("3.900") ? 3_900_000 : 2_450_000;
                    ticket.put("roomType", choice);
                    updateNights.run();
                }
            });
            updateNights.run();
            startHoldTimer.accept("Giữ phòng");
        }

        if (!"TOPUP".equals(type)) {
            Call<ApiResponse<Map<String, Object>>> catalog = ApiClient.customerApi(requireContext()).getUtilityCatalog(type);
            calls.add(catalog);
            catalog.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> c, @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                    if (dialog.isShowing()) {
                        form.tvBookingCatalog.setText(r.isSuccessful() ? "Danh mục sandbox tải xong; chưa có xác nhận giữ chỗ từ máy chủ." : "Danh mục tạm thời không khả dụng.");
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> c, @NonNull Throwable t) {
                    if (dialog.isShowing()) form.tvBookingCatalog.setText("Không tải được danh mục sandbox.");
                }
            });
        } else {
            form.tvBookingCatalog.setText("Mệnh giá nạp được chọn trong ứng dụng.");
        }

        dialog.setContentView(form.getRoot());
        dialog.setOnDismissListener(d -> {
            if (timer[0] != null) timer[0].cancel();
            for (Call<?> call : calls) call.cancel();
        });

        form.btnBookingContinue.setOnClickListener(v -> {
            if ("TOPUP".equals(type)) {
                String number = text(phoneInput[0]);
                if (!number.matches("^0[0-9]{9}$")) {
                    if (phoneInput[0] != null) phoneInput[0].setError("Nhập đúng 10 chữ số, bắt đầu bằng 0");
                    return;
                }
                billCode[0] = number;
                ticket.put("phoneNumber", number);
                ticket.put("mobileCarrier", supplier[0]);
            }
            if ("CINEMA".equals(type) && chosenSeats.isEmpty()) {
                Toast.makeText(requireContext(), "Chọn ít nhất một ghế", Toast.LENGTH_SHORT).show();
                return;
            }
            if ("FLIGHT".equals(type)) {
                String cccd = text(flightIdInput[0]);
                if (!cccd.matches("[0-9]{12}")) {
                    if (flightIdInput[0] != null) flightIdInput[0].setError("CCCD hành khách cần đủ 12 chữ số");
                    Toast.makeText(requireContext(), "CCCD hành khách cần đủ 12 chữ số", Toast.LENGTH_SHORT).show();
                    return;
                }
                ticket.put("passengerName", text(flightNameInput[0]));
                ticket.put("passengerIdCard", cccd);
            }
            if (amount[0] <= 0) {
                Toast.makeText(requireContext(), "Vui lòng chọn mệnh giá hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
            ticket.put("serviceType", type);
            ticket.put("selectedAmount", amount[0]);
            dialog.dismiss();
            openUtilityPaySheet(type, label, supplier[0], amount[0], billCode[0], new HashMap<>(ticket));
        });
        dialog.show();
    }

    private static String text(EditText input) {
        return (input == null || input.getText() == null) ? "" : input.getText().toString().trim();
    }

    private void showDatePicker(BottomSheetDialog dialog, Button button, String[] selectedDate) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(dialog.getContext(), (view, year, month, day) -> {
            selectedDate[0] = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            button.setText(String.format(Locale.US, "%02d/%02d/%04d", day, month + 1, year));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateCheckout(TextView view, String checkin, int nights) {
        if (checkin == null || checkin.isEmpty()) {
            view.setText("Chọn ngày nhận phòng để xem ngày trả phòng");
            return;
        }
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d = f.parse(checkin);
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            c.add(Calendar.DAY_OF_YEAR, nights);
            view.setText("Trả phòng: " + f.format(c.getTime()));
        } catch (Exception e) {
            view.setText("Chọn ngày nhận phòng để xem ngày trả phòng");
        }
    }

    private Object otpPreview(Map<String, Object> data) {
        if (data == null) return null;
        for (String key : new String[]{"debugOtp", "otpCode", "otp", "devOtpPreview"}) {
            Object v = data.get(key);
            if (v != null && !String.valueOf(v).trim().isEmpty()) return v;
        }
        return null;
    }

    private void openUtilityPaySheet(String serviceType, String label, String supplier, long amount) {
        openUtilityPaySheet(serviceType, label, supplier, amount, null, null);
    }

    private void openUtilityPaySheet(String serviceType, String label, String supplier, long amount, @Nullable String billCode) {
        openUtilityPaySheet(serviceType, label, supplier, amount, billCode, null);
    }

    private void openUtilityPaySheet(String serviceType, String label, String supplier, long amount, @Nullable String billCode, @Nullable Map<String, Object> bookingData) {
        if (!isAdded() || getContext() == null) return;
        if (checkingAccount == null) {
            Toast.makeText(requireContext(), "Chưa tải được tài khoản thanh toán. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
            return;
        }
        dismissActivePayment();

        final BottomSheetDialog sheet = new BottomSheetDialog(new ContextThemeWrapper(requireContext(), R.style.Theme_PHLBanking));
        final BottomSheetTransferConfirmBinding sb = BottomSheetTransferConfirmBinding.inflate(LayoutInflater.from(sheet.getContext()));

        final String idem = UUID.randomUUID().toString();
        final Map<String, Object> txIntent = new HashMap<>();
        txIntent.put("operation", "UTILITY");
        txIntent.put("fromAccountNumber", checkingAccount);
        txIntent.put("serviceType", serviceType);
        txIntent.put("supplier", supplier);
        txIntent.put("amount", amount);
        txIntent.put("idempotencyKey", idem);
        if (billCode != null && !billCode.trim().isEmpty()) txIntent.put("billCode", billCode.trim());
        if (bookingData != null) txIntent.put("ticketData", bookingData);

        activePaymentIntent = txIntent;
        activePaymentSheet = sheet;
        activePaymentBinding = sb;
        activeIdempotencyKey = idem;
        activeEkycToken = null;

        sb.tvConfirmSummary.setText("Dịch vụ: " + label + "\nNhà cung cấp: " + supplier + "\nTài khoản thanh toán: " + checkingAccount + "\nSố tiền: " + CurrencyUtils.formatVnd(amount));
        sb.boxEkycStep.setVisibility(amount > EKYC_THRESHOLD_VND ? View.VISIBLE : View.GONE);
        sb.btnSendOtp.setEnabled(amount <= EKYC_THRESHOLD_VND);
        sb.btnExecuteTransaction.setEnabled(false);

        sheet.setContentView(sb.getRoot());

        sheet.setOnDismissListener(d -> {
            for (Call<?> call : pendingPaymentCalls) {
                if (call != null && !call.isCanceled()) {
                    call.cancel();
                }
            }
            pendingPaymentCalls.clear();
            if (activePaymentSheet == sheet) {
                activePaymentSheet = null;
                activePaymentBinding = null;
                activePaymentIntent = null;
                activeEkycToken = null;
                activeIdempotencyKey = null;
            }
        });

        sheet.setOnShowListener(d -> {
            FrameLayout frame = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (frame != null) {
                frame.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(frame);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            MotionHelper.staggerEntrance(sb.layoutSheetHeader, sb.cardConfirmSummary, sb.layoutOtpRow, sb.btnExecuteTransaction);
        });

        MotionHelper.applyTactilePress(sb.btnSendOtp, sb.btnExecuteTransaction);

        sb.btnVerifyEkyc.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), FaceEkycActivity.class);
            intent.putExtra(FaceEkycActivity.EXTRA_INTENT_JSON, new Gson().toJson(txIntent));
            utilityEkycLauncher.launch(intent);
        });

        sb.tvSecurityProgress.setText(amount > EKYC_THRESHOLD_VND ? "Giao dịch trên 10 triệu cần xác minh khuôn mặt eKYC trước OTP." : "Bắt buộc gửi và nhập OTP trước khi thanh toán.");

        sb.btnSendOtp.setOnClickListener(v -> {
            if (amount > EKYC_THRESHOLD_VND && (activeEkycToken == null || activeEkycToken.isEmpty())) {
                sb.tvSecurityProgress.setText("Trước OTP cần xác minh khuôn mặt eKYC.");
                return;
            }
            Map<String, Object> req = new HashMap<>();
            req.put("otpType", "TRANSACTION");
            req.put("intent", txIntent);
            if (activeEkycToken != null) req.put("ekycToken", activeEkycToken);

            sb.btnSendOtp.setEnabled(false);
            sb.btnExecuteTransaction.setEnabled(false);

            Call<ApiResponse<Map<String, Object>>> call = ApiClient.customerApi(requireContext()).requestOtp(req);
            pendingPaymentCalls.add(call);
            call.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> c, @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                    if (c.isCanceled() || !sheet.isShowing() || activePaymentSheet != sheet) return;
                    sb.btnSendOtp.setEnabled(true);
                    if (r.isSuccessful() && r.body() != null && r.body().data != null) {
                        sb.btnExecuteTransaction.setEnabled(true);
                        Map<String, Object> d = r.body().data;
                        Object code = otpPreview(d);
                        Object exp = d.get("expiresInSeconds");
                        String msg = code != null ? "MÔI TRƯỜNG DEV · OTP: " + code + (exp == null ? "" : " · Hiệu lực " + exp + " giây")
                            : r.body().message != null ? r.body().message : "OTP đã được gửi. Nhập mã để tiếp tục.";
                        sb.tvOtpBanner.setText(msg);
                        sb.tvOtpBanner.setVisibility(View.VISIBLE);
                        Toast.makeText(requireContext().getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                    } else {
                        sb.tvSecurityProgress.setText(ApiErrors.message(r, "Không gửi được OTP."));
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> c, @NonNull Throwable t) {
                    if (c.isCanceled() || !sheet.isShowing()) return;
                    sb.btnSendOtp.setEnabled(true);
                    sb.tvSecurityProgress.setText("Không gửi được OTP. Vui lòng thử lại.");
                }
            });
        });

        sb.btnExecuteTransaction.setOnClickListener(v -> {
            String otp = text(sb.edtConfirmOtp);
            if (!otp.matches("[0-9]{6}")) {
                MotionHelper.shakeError(sb.edtConfirmOtp);
                Toast.makeText(requireContext(), "Nhập đủ 6 số OTP", Toast.LENGTH_SHORT).show();
                return;
            }
            if (amount > EKYC_THRESHOLD_VND && (activeEkycToken == null || activeEkycToken.isEmpty())) {
                sb.btnExecuteTransaction.setEnabled(false);
                Toast.makeText(requireContext(), "Cần xác minh eKYC trước giao dịch", Toast.LENGTH_LONG).show();
                return;
            }
            Map<String, Object> body = new HashMap<>(txIntent);
            body.remove("operation");
            body.put("otpCode", otp);
            if (activeEkycToken != null) body.put("ekycToken", activeEkycToken);

            sb.btnExecuteTransaction.setEnabled(false);
            Call<ApiResponse<CustomerModels.TransactionResult>> call = ApiClient.customerApi(requireContext()).payUtility(body);
            pendingPaymentCalls.add(call);
            call.enqueue(new Callback<ApiResponse<CustomerModels.TransactionResult>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<CustomerModels.TransactionResult>> c, @NonNull Response<ApiResponse<CustomerModels.TransactionResult>> r) {
                    if (c.isCanceled() || !sheet.isShowing() || activePaymentSheet != sheet) return;
                    if (r.isSuccessful() && r.body() != null && r.body().data != null) {
                        CustomerModels.TransactionRecord tx = r.body().data.transaction;
                        sheet.dismiss();
                        if (tx != null) {
                            ReceiptDialogHelper.showReceipt(requireContext(), tx.traceId, tx.type, tx.amount, tx.fee, tx.toAccName, tx.content, tx.createdAt);
                        }
                    } else {
                        sb.btnExecuteTransaction.setEnabled(true);
                        MotionHelper.shakeError(sb.btnExecuteTransaction);
                        Toast.makeText(requireContext().getApplicationContext(), ApiErrors.message(r, "Thanh toán thất bại"), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<CustomerModels.TransactionResult>> c, @NonNull Throwable t) {
                    if (c.isCanceled() || !sheet.isShowing()) return;
                    sb.btnExecuteTransaction.setEnabled(true);
                    Toast.makeText(requireContext().getApplicationContext(), "Lỗi kết nối máy chủ", Toast.LENGTH_SHORT).show();
                }
            });
        });

        sheet.show();
    }
}
