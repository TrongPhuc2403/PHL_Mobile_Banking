package vn.edu.tdtu.phlbanking.ui.customer;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.databinding.ActivityCustomerMainBinding;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;

public class CustomerMainActivity extends AppCompatActivity {

    private ActivityCustomerMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.customerBottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tab_cust_home) {
                swapFragment(new CustomerHomeFragment());
                MotionHelper.animateTabSelection(binding.customerBottomNav);
                return true;
            } else if (id == R.id.tab_cust_accounts) {
                swapFragment(new CustomerAccountsFragment());
                MotionHelper.animateTabSelection(binding.customerBottomNav);
                return true;
            } else if (id == R.id.tab_cust_utilities) {
                swapFragment(new CustomerUtilitiesFragment());
                MotionHelper.animateTabSelection(binding.customerBottomNav);
                return true;
            } else if (id == R.id.tab_cust_explore) {
                swapFragment(new CustomerExploreFragment());
                MotionHelper.animateTabSelection(binding.customerBottomNav);
                return true;
            } else if (id == R.id.tab_cust_profile) {
                swapFragment(new CustomerProfileFragment());
                MotionHelper.animateTabSelection(binding.customerBottomNav);
                return true;
            }
            return false;
        });
        binding.customerBottomNav.setOnItemReselectedListener(item -> {
            // Avoid recreating the active tab fragment on re-tap.
        });

        if (savedInstanceState == null) {
            swapFragment(new CustomerHomeFragment());
        }
    }

    public void selectTab(int tabId) {
        binding.customerBottomNav.setSelectedItemId(tabId);
    }

    private void swapFragment(Fragment fragment) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        if (getSupportFragmentManager().findFragmentById(R.id.customerFragmentHost) != null) {
            MotionHelper.applyFragmentTransition(tx);
        }
        tx.replace(R.id.customerFragmentHost, fragment).commit();
    }
}
