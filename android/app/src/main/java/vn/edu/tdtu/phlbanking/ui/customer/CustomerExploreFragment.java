package vn.edu.tdtu.phlbanking.ui.customer;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.model.CustomerModels;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.FragmentCustomerExploreBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemAiMessageBinding;
import vn.edu.tdtu.phlbanking.databinding.ItemBranchCardBinding;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;

public class CustomerExploreFragment extends Fragment {

    private FragmentCustomerExploreBinding binding;
    private CustomerModels.BranchItem nearestBranch;
    private String lastSuggestedRoute = null;
    private final List<ObjectAnimator> typingAnimators = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentCustomerExploreBinding.inflate(inflater, container, false);

        MotionHelper.staggerEntrance(binding.layoutExploreHeader);
        MotionHelper.pulseView(binding.badgeNearestPin);
        MotionHelper.applyTactilePress(
            binding.btnOpenWalkingMap,
            binding.btnSendAi,
            binding.btnAiSuggestedAction,
            binding.chipPromptTransfer,
            binding.chipPromptSavings,
            binding.chipPromptBranch
        );

        binding.chipExploreMode.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty() || binding == null || getContext() == null) return;
            boolean showMap = checkedIds.get(0) == R.id.chipMapBranch;
            if (MotionHelper.areAnimationsEnabled(requireContext())) {
                AutoTransition transition = new AutoTransition();
                transition.setDuration(220L);
                TransitionManager.beginDelayedTransition(binding.layoutExploreRoot, transition);
            }
            binding.panelMapBranches.setVisibility(showMap ? View.VISIBLE : View.GONE);
            binding.panelAiChat.setVisibility(showMap ? View.GONE : View.VISIBLE);
        });

        binding.btnOpenWalkingMap.setOnClickListener(v -> openWalkingDirections());
        binding.btnSendAi.setOnClickListener(v -> sendAiQuestion());
        binding.btnAiSuggestedAction.setOnClickListener(v -> handleAiNavigation());

        binding.chipPromptTransfer.setOnClickListener(v ->
            sendQuickPrompt("Hướng dẫn chuyển tiền nhanh")
        );
        binding.chipPromptSavings.setOnClickListener(v ->
            sendQuickPrompt("Tư vấn gửi tiết kiệm PHL")
        );
        binding.chipPromptBranch.setOnClickListener(v -> sendQuickPrompt("Tìm chi nhánh gần nhất"));

        loadBranches();
        appendChatBubble(
            "Trợ lý PHL",
            "Tôi có thể hỗ trợ bạn tra cứu thông tin tài khoản, chuyển tiền, tiết kiệm hoặc tìm chi nhánh gần nhất.",
            false
        );

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        hideTypingIndicator();
        super.onDestroyView();
        binding = null;
    }

    private void sendQuickPrompt(String promptText) {
        if (binding == null) return;
        binding.edtAiPrompt.setText(promptText);
        sendAiQuestion();
    }

    private void loadBranches() {
        // Tọa độ mặc định gần ĐH Tôn Đức Thắng Q7 khi chạy Emulator
        ApiClient.customerApi(requireContext()).getBranches(10.7315, 106.6980).enqueue(
            new Callback<ApiResponse<CustomerModels.BranchesResponse>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<CustomerModels.BranchesResponse>> call,
                    @NonNull Response<ApiResponse<CustomerModels.BranchesResponse>> response
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
                    CustomerModels.BranchesResponse data = response.body().data;
                    nearestBranch = data.nearest;
                    if (nearestBranch != null) {
                        binding.tvNearestBranch.setText(
                            nearestBranch.branchName +
                                "\n" +
                                nearestBranch.address +
                                "\n" +
                                "Khoảng cách: " +
                                nearestBranch.distanceMeters +
                                " m (~" +
                                nearestBranch.estimatedWalkMinutes +
                                " phút đi bộ)"
                        );
                        binding.tvNearestDistancePill.setText(nearestBranch.distanceMeters + " m");
                        binding.tvNearestWalkPill.setText(
                            "~" + nearestBranch.estimatedWalkMinutes + " phút đi bộ"
                        );
                        binding.tvNearestDistancePill.setVisibility(View.VISIBLE);
                        binding.tvNearestWalkPill.setVisibility(View.VISIBLE);
                    }

                    binding.containerBranchList.removeAllViews();
                    if (data.branches != null) {
                        for (CustomerModels.BranchItem b : data.branches) {
                            addBranchCard(b);
                        }
                        MotionHelper.staggerChildren(binding.containerBranchList, 50L);
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<CustomerModels.BranchesResponse>> call,
                    @NonNull Throwable t
                ) {
                    if (!isAdded() || binding == null) {
                        return;
                    }
                    binding.tvNearestBranch.setText("Không tải được danh sách chi nhánh.");
                }
            }
        );
    }

    private void addBranchCard(CustomerModels.BranchItem b) {
        if (binding == null || getContext() == null) {
            return;
        }
        ItemBranchCardBinding itemBinding = ItemBranchCardBinding.inflate(
            getLayoutInflater(),
            binding.containerBranchList,
            false
        );
        itemBinding.tvBranchName.setText(b.branchName);
        itemBinding.tvBranchAddress.setText(b.address);
        if (b.phone != null && !b.phone.trim().isEmpty()) {
            itemBinding.tvBranchPhone.setText("Hotline: " + b.phone);
            itemBinding.tvBranchPhone.setVisibility(View.VISIBLE);
        } else {
            itemBinding.tvBranchPhone.setVisibility(View.GONE);
        }
        itemBinding.tvBranchDistance.setText(b.distanceMeters + " m");
        itemBinding.tvBranchWalkTime.setText(b.estimatedWalkMinutes + " phút đi bộ");

        MotionHelper.applyTactilePress(itemBinding.getRoot());
        binding.containerBranchList.addView(itemBinding.getRoot());
    }

    private void openWalkingDirections() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        if (nearestBranch == null) {
            Toast.makeText(requireContext(), "Chưa có tọa độ chi nhánh", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri gmmIntentUri = Uri.parse(
            "google.navigation:q=" +
                nearestBranch.latitude +
                "," +
                nearestBranch.longitude +
                "&mode=w"
        );
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        try {
            startActivity(mapIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(
                requireContext(),
                "Thiết bị chưa cài ứng dụng bản đồ",
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void sendAiQuestion() {
        if (!isAdded() || binding == null || getContext() == null) {
            return;
        }
        String prompt =
            binding.edtAiPrompt.getText() == null
                ? ""
                : binding.edtAiPrompt.getText().toString().trim();
        if (prompt.isEmpty()) return;

        binding.edtAiPrompt.setText("");
        appendChatBubble("Bạn", prompt, true);
        showTypingIndicator();

        Map<String, String> body = new HashMap<>();
        body.put("message", prompt);
        ApiClient.customerApi(requireContext()).askAi(body).enqueue(
            new Callback<ApiResponse<CustomerModels.AiChatResponse>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<CustomerModels.AiChatResponse>> call,
                    @NonNull Response<ApiResponse<CustomerModels.AiChatResponse>> response
                ) {
                    if (!isAdded() || binding == null || getContext() == null) {
                        return;
                    }
                    hideTypingIndicator();
                    if (
                        !response.isSuccessful() ||
                        response.body() == null ||
                        response.body().data == null
                    ) {
                        return;
                    }
                    CustomerModels.AiChatResponse ai = response.body().data;
                    appendChatBubble("Trợ lý PHL", ai.reply, false);
                    if (MotionHelper.areAnimationsEnabled(requireContext())) {
                        AutoTransition transition = new AutoTransition();
                        transition.setDuration(200L);
                        TransitionManager.beginDelayedTransition(binding.panelAiChat, transition);
                    }
                    if (ai.suggestedAction != null) {
                        lastSuggestedRoute = ai.suggestedAction.route;
                        binding.btnAiSuggestedAction.setText(ai.suggestedAction.label);
                        binding.btnAiSuggestedAction.setVisibility(View.VISIBLE);
                        MotionHelper.staggerEntrance(binding.btnAiSuggestedAction);
                    } else {
                        binding.btnAiSuggestedAction.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<CustomerModels.AiChatResponse>> call,
                    @NonNull Throwable t
                ) {
                    if (!isAdded() || binding == null) {
                        return;
                    }
                    hideTypingIndicator();
                    appendChatBubble("Trợ lý PHL", "Không thể kết nối máy chủ AI.", false);
                }
            }
        );
    }

    private void showTypingIndicator() {
        if (binding == null || getContext() == null) {
            return;
        }
        hideTypingIndicator();
        binding.layoutAiTyping.setVisibility(View.VISIBLE);
        if (!MotionHelper.areAnimationsEnabled(requireContext())) {
            return;
        }
        View[] dots = { binding.typingDot1, binding.typingDot2, binding.typingDot3 };
        for (int i = 0; i < dots.length; i++) {
            View dot = dots[i];
            dot.setAlpha(0.25f);
            ObjectAnimator anim = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.25f, 1f);
            anim.setDuration(480L);
            anim.setStartDelay(i * 150L);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setRepeatMode(ValueAnimator.REVERSE);
            anim.start();
            typingAnimators.add(anim);
        }
    }

    private void hideTypingIndicator() {
        for (ObjectAnimator anim : typingAnimators) {
            anim.cancel();
        }
        typingAnimators.clear();
        if (binding != null) {
            binding.layoutAiTyping.setVisibility(View.GONE);
            binding.typingDot1.setAlpha(1f);
            binding.typingDot2.setAlpha(1f);
            binding.typingDot3.setAlpha(1f);
        }
    }

    private void handleAiNavigation() {
        if (!isAdded() || binding == null || getContext() == null) {
            return;
        }
        if ("phl://transfer".equals(lastSuggestedRoute)) {
            startActivity(new Intent(requireContext(), TransferActivity.class));
            MotionHelper.applyActivityTransition(getActivity());
        } else if ("phl://map".equals(lastSuggestedRoute)) {
            binding.chipMapBranch.setChecked(true);
        } else if (getActivity() instanceof CustomerMainActivity) {
            CustomerMainActivity act = (CustomerMainActivity) getActivity();
            if (
                "phl://savings".equals(lastSuggestedRoute) ||
                "phl://mortgage".equals(lastSuggestedRoute)
            ) {
                act.selectTab(R.id.tab_cust_accounts);
            } else if ("phl://utilities".equals(lastSuggestedRoute)) {
                act.selectTab(R.id.tab_cust_utilities);
            }
        }
    }

    private void appendChatBubble(String sender, String text, boolean isUser) {
        if (binding == null || getContext() == null) {
            return;
        }
        ItemAiMessageBinding msgBinding = ItemAiMessageBinding.inflate(
            getLayoutInflater(),
            binding.containerAiMessages,
            false
        );

        LinearLayout.LayoutParams lp =
            (LinearLayout.LayoutParams) msgBinding.layoutBubble.getLayoutParams();
        lp.gravity = isUser ? Gravity.END : Gravity.START;
        msgBinding.layoutBubble.setLayoutParams(lp);
        msgBinding.layoutBubble.setBackgroundResource(
            isUser ? R.drawable.bg_chat_user : R.drawable.bg_chat_ai
        );

        msgBinding.tvMessageSender.setText(sender);
        msgBinding.tvMessageSender.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                isUser ? R.color.phl_gold_light : R.color.phl_gold
            )
        );

        msgBinding.tvMessageBody.setText(text);
        msgBinding.tvMessageBody.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                isUser ? R.color.phl_white : R.color.phl_text_primary
            )
        );

        View bubbleRoot = msgBinding.getRoot();
        if (MotionHelper.areAnimationsEnabled(requireContext())) {
            float offsetPx = 12f * getResources().getDisplayMetrics().density;
            bubbleRoot.setAlpha(0f);
            bubbleRoot.setTranslationY(offsetPx);
            bubbleRoot
                .animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(new FastOutSlowInInterpolator())
                .start();
        }

        binding.containerAiMessages.addView(bubbleRoot);
        binding.scrollAiMessages.post(() -> {
            if (binding != null) {
                binding.scrollAiMessages.fullScroll(View.FOCUS_DOWN);
            }
        });
    }
}
