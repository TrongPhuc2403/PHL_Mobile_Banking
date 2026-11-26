package vn.edu.tdtu.phlbanking.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import vn.edu.tdtu.phlbanking.data.local.SessionManager;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;
import vn.edu.tdtu.phlbanking.ui.customer.CustomerMainActivity;
import vn.edu.tdtu.phlbanking.ui.officer.OfficerMainActivity;

public class SplashRouterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = new SessionManager(this);
        Intent nextIntent;
        if (!session.isLoggedIn()) {
            nextIntent = new Intent(this, LoginActivity.class);
        } else if ("OFFICER".equalsIgnoreCase(session.getRole())) {
            nextIntent = new Intent(this, OfficerMainActivity.class);
        } else {
            nextIntent = new Intent(this, CustomerMainActivity.class);
        }

        startActivity(nextIntent);
        MotionHelper.applyActivityTransition(this);
        finish();
    }
}
