package vn.edu.tdtu.phlbanking.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.data.local.SessionManager;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.FragmentRolePlaceholderBinding;
import vn.edu.tdtu.phlbanking.ui.auth.LoginActivity;

public class RolePlaceholderFragment extends Fragment {

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_SUMMARY = "arg_summary";
    private static final String ARG_SHOW_LOGOUT = "arg_show_logout";

    public static RolePlaceholderFragment newInstance(
        String title,
        String summary,
        boolean showLogout
    ) {
        RolePlaceholderFragment f = new RolePlaceholderFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUMMARY, summary);
        args.putBoolean(ARG_SHOW_LOGOUT, showLogout);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        FragmentRolePlaceholderBinding binding = FragmentRolePlaceholderBinding.inflate(
            inflater,
            container,
            false
        );

        SessionManager session = new SessionManager(requireContext());
        Bundle args = getArguments();
        String title = args != null ? args.getString(ARG_TITLE, "") : "";
        String summary = args != null ? args.getString(ARG_SUMMARY, "") : "";
        boolean showLogout = args != null && args.getBoolean(ARG_SHOW_LOGOUT, false);

        binding.tvTabRoleBadge.setText(session.getRole());
        binding.tvTabTitle.setText(title);
        binding.tvTabSummary.setText(
            "Xin chào " +
                session.getFullName() +
                " (" +
                session.getIdentityCode() +
                ")\n\n" +
                summary
        );

        if (showLogout) {
            binding.btnTabLogout.setVisibility(View.VISIBLE);
            binding.btnTabLogout.setOnClickListener(v -> performLogout(session));
            MotionHelper.applyTactilePress(binding.btnTabLogout);
        }

        MotionHelper.staggerEntrance(binding.layoutPlaceholderHeader, binding.cardPlaceholderBody);

        return binding.getRoot();
    }

    private void performLogout(SessionManager session) {
        ApiClient.authApi(requireContext()).logout().enqueue(
            new Callback<ApiResponse<Void>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Void>> call,
                    @NonNull Response<ApiResponse<Void>> response
                ) {
                    navigateToLogin(session);
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Void>> call, @NonNull Throwable t) {
                    navigateToLogin(session);
                }
            }
        );
    }

    private void navigateToLogin(SessionManager session) {
        session.clearSession();
        if (!isAdded()) {
            return;
        }
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            MotionHelper.applyActivityTransition(getActivity());
        }
    }
}
