package vn.edu.tdtu.phlbanking.ui.customer;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.databinding.DialogReceiptBinding;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;
import vn.edu.tdtu.phlbanking.utils.CurrencyUtils;

public final class ReceiptDialogHelper {

    private ReceiptDialogHelper() {}

    public static void showReceipt(
        Context context,
        String traceId,
        String typeLabel,
        long amount,
        long fee,
        String counterparty,
        String content,
        String timestamp
    ) {
        if (context == null) {
            return;
        }
        Activity activity = unwrapActivity(context);
        if (activity != null && (activity.isFinishing() || activity.isDestroyed())) {
            return;
        }

        String receiptText =
            "BIÊN LAI ĐIỆN TỬ - PHL BANK\n" +
            "-----------------------------\n" +
            "Mã giao dịch: " +
            safe(traceId) +
            "\n" +
            "Loại: " +
            safe(typeLabel) +
            "\n" +
            "Số tiền: " +
            CurrencyUtils.formatVnd(amount) +
            "\n" +
            "Phí: " +
            CurrencyUtils.formatVnd(fee) +
            "\n" +
            "Đối tác/Đích: " +
            safe(counterparty) +
            "\n" +
            "Nội dung: " +
            safe(content) +
            "\n" +
            "Thời gian: " +
            safe(timestamp) +
            "\n" +
            "Trạng thái: Thành công";

        Context themedContext = new ContextThemeWrapper(context, R.style.Theme_PHLBanking);
        BottomSheetDialog dialog = new BottomSheetDialog(themedContext);
        DialogReceiptBinding binding = DialogReceiptBinding.inflate(
            LayoutInflater.from(dialog.getContext())
        );
        dialog.setContentView(binding.getRoot());

        binding.tvReceiptType.setText(safe(typeLabel));
        binding.tvReceiptTraceId.setText(safe(traceId));
        binding.tvReceiptFee.setText(CurrencyUtils.formatVnd(fee));
        binding.tvReceiptCounterparty.setText(safe(counterparty));
        binding.tvReceiptContent.setText(safe(content));
        binding.tvReceiptTimestamp.setText(safe(timestamp));

        if (amount > 0L) {
            binding.tvReceiptAmount.setText(CurrencyUtils.formatVnd(0L));
            MotionHelper.animateVndCounter(binding.tvReceiptAmount, 0L, amount, 650L);
        } else {
            binding.tvReceiptAmount.setText(CurrencyUtils.formatVnd(amount));
        }

        MotionHelper.applyTactilePress(binding.btnReceiptShare, binding.btnReceiptClose);

        binding.btnReceiptShare.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "Biên lai PHL Bank - " + safe(traceId));
            share.putExtra(Intent.EXTRA_TEXT, receiptText);
            Intent chooser = Intent.createChooser(share, "Chia sẻ biên lai qua");
            if (activity == null) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(chooser);
        });

        binding.btnReceiptClose.setOnClickListener(v -> dialog.dismiss());

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
                binding.layoutReceiptHeader,
                binding.layoutReceiptHero,
                binding.cardReceiptDetails,
                binding.layoutReceiptActions
            );
        });

        dialog.show();
    }

    @Nullable
    private static Activity unwrapActivity(@Nullable Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    private static String safe(String v) {
        return v == null || v.trim().isEmpty() ? "--" : v;
    }
}
