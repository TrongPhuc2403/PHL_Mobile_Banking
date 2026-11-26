package vn.edu.tdtu.phlbanking.ui.officer;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.databinding.ActivityOfficerMainBinding;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;

public class OfficerMainActivity extends AppCompatActivity {

    private static final String KEY_ACTIVE_TAB_ID = "active_tab_id";

    private ActivityOfficerMainBinding binding;
    private int activeTabId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOfficerMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState != null) {
            activeTabId = savedInstanceState.getInt(KEY_ACTIVE_TAB_ID, R.id.tab_off_customers);
        }

        binding.officerBottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (
                id == activeTabId &&
                getSupportFragmentManager().findFragmentById(R.id.officerFragmentHost) != null
            ) {
                return true;
            }
            Fragment target = createFragmentForTab(id);
            if (target == null) {
                return false;
            }
            boolean animate = activeTabId != 0;
            activeTabId = id;
            swapFragment(target, animate);
            if (animate) {
                MotionHelper.animateTabSelection(binding.officerBottomNav);
            }
            return true;
        });

        if (savedInstanceState == null) {
            activeTabId = R.id.tab_off_customers;
            swapFragment(new OfficerCustomersFragment(), false);
            binding.officerBottomNav.setSelectedItemId(R.id.tab_off_customers);
        } else if (getSupportFragmentManager().findFragmentById(R.id.officerFragmentHost) == null) {
            Fragment target = createFragmentForTab(activeTabId);
            if (target != null) {
                swapFragment(target, false);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_ACTIVE_TAB_ID, activeTabId);
    }

    private Fragment createFragmentForTab(int tabId) {
        if (tabId == R.id.tab_off_customers) {
            return new OfficerCustomersFragment();
        } else if (tabId == R.id.tab_off_create) {
            return new OfficerCreateAccountFragment();
        } else if (tabId == R.id.tab_off_rates) {
            return new OfficerRatesFragment();
        } else if (tabId == R.id.tab_off_audit) {
            return new OfficerAuditFragment();
        }
        return null;
    }

    private void swapFragment(@NonNull Fragment fragment, boolean animate) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        if (animate) {
            MotionHelper.applyFragmentTransition(tx);
        }
        tx.replace(R.id.officerFragmentHost, fragment).commit();
    }
}
