package vn.edu.tdtu.phlbanking.ui.customer;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
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

    private static final String LOCATION_PERMISSION_REQUESTED = "location_permission_requested";

    private FragmentCustomerExploreBinding binding;
    private FusedLocationProviderClient fusedLocationClient;
    private CancellationTokenSource locationCancelTokenSource;
    private Call<ApiResponse<CustomerModels.BranchesResponse>> branchCall;
    private BranchAdapter branchAdapter;
    private boolean mapResumed;
    private boolean permissionRequested;
    private boolean permissionRequestInFlight;

    private CustomerModels.BranchItem nearestBranch;
    private CustomerModels.WalkingRoute currentWalkingRoute;
    private Location userLocation;
    private Call<ApiResponse<CustomerModels.AiChatResponse>> aiCall;
    private final List<ObjectAnimator> typingAnimators = new ArrayList<>();

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            permissionRequestInFlight = false;
            if (isMapActive()) {
                // Recheck the actual grants. A coarse-only result must not start a GPS lookup.
                refreshBranches(false);
            }
        });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            permissionRequested = savedInstanceState.getBoolean(LOCATION_PERMISSION_REQUESTED);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(LOCATION_PERMISSION_REQUESTED, permissionRequested);
    }

    @Nullable
    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState
    ) {
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        binding = FragmentCustomerExploreBinding.inflate(inflater, container, false);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext().getApplicationContext());

        setupBranchRecyclerView();
        setupOsmMap();

        MotionHelper.staggerEntrance(binding.layoutExploreHeader);
        MotionHelper.pulseView(binding.badgeNearestPin);
        MotionHelper.applyTactilePress(
            binding.btnOpenWalkingMap,
            binding.btnRequestLocation,
            binding.btnSendAi,
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
            updateMapLifecycle();
            if (showMap) {
                refreshBranches(!permissionRequested);
            } else {
                cancelBranchWork();
            }
        });

        binding.btnOpenWalkingMap.setOnClickListener(v -> openBranchDirections(nearestBranch));
        binding.btnRequestLocation.setOnClickListener(v -> refreshBranches(true));
        binding.btnRetryBranches.setOnClickListener(v -> refreshBranches(false));
        binding.btnSendAi.setOnClickListener(v -> sendAiQuestion());

        binding.chipPromptTransfer.setOnClickListener(v ->
            sendQuickPrompt("Hướng dẫn chuyển tiền nhanh")
        );
        binding.chipPromptSavings.setOnClickListener(v ->
            sendQuickPrompt("Tư vấn gửi tiết kiệm PHL")
        );
        binding.chipPromptBranch.setOnClickListener(v -> sendQuickPrompt("Tìm chi nhánh gần nhất"));

        appendChatBubble(
            "Trợ lý PHL",
            "Tôi có thể hỗ trợ bạn tra cứu thông tin tài khoản, chuyển tiền, tiết kiệm hoặc tìm chi nhánh gần nhất.",
            false
        );

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshBranches(!permissionRequested);
    }

    @Override
    public void onPause() {
        cancelBranchWork();
        pauseMap();
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            cancelBranchWork();
            pauseMap();
        } else {
            refreshBranches(!permissionRequested);
        }
    }

    @Override
    public void onDestroyView() {
        cancelBranchWork();
        pauseMap();
        if (aiCall != null) {
            Call<ApiResponse<CustomerModels.AiChatResponse>> call = aiCall;
            aiCall = null;
            call.cancel();
        }
        hideTypingIndicator();
        if (binding != null) {
            binding.rvBranchList.setAdapter(null);
            binding.mapBranches.onDetach();
        }
        branchAdapter = null;
        userLocation = null;
        nearestBranch = null;
        currentWalkingRoute = null;
        binding = null;
        super.onDestroyView();
    }

    private boolean isMapActive() {
        return binding != null && isAdded() && isResumed() && !isHidden()
            && binding.panelMapBranches.getVisibility() == View.VISIBLE;
    }

    private void updateMapLifecycle() {
        if (isMapActive() && binding.cardMapContainer.getVisibility() == View.VISIBLE) {
            if (!mapResumed) {
                binding.mapBranches.onResume();
                mapResumed = true;
            }
        } else {
            pauseMap();
        }
    }

    private void pauseMap() {
        if (mapResumed && binding != null) {
            binding.mapBranches.onPause();
            mapResumed = false;
        }
    }

    private void cancelBranchWork() {
        if (locationCancelTokenSource != null) {
            CancellationTokenSource token = locationCancelTokenSource;
            locationCancelTokenSource = null;
            token.cancel();
        }
        if (branchCall != null) {
            Call<ApiResponse<CustomerModels.BranchesResponse>> call = branchCall;
            branchCall = null;
            call.cancel();
        }
    }

    private void setupBranchRecyclerView() {
        branchAdapter = new BranchAdapter(this::openBranchDirections);
        binding.rvBranchList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvBranchList.setAdapter(branchAdapter);
    }

    private void setupOsmMap() {
        // The fragment owns teardown; temporary window detach must not destroy the map.
        binding.mapBranches.setDestroyMode(false);
        binding.mapBranches.setTileSource(TileSourceFactory.MAPNIK);
        binding.mapBranches.setMultiTouchControls(true);
        binding.mapBranches.getController().setZoom(15.5);
        binding.mapBranches.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                view.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false; // OSMDroid still handles gestures and marker taps.
        });
    }

    private void refreshBranches(boolean mayRequestPermission) {
        if (!isMapActive() || permissionRequestInFlight) return;
        cancelBranchWork();
        userLocation = null;
        nearestBranch = null;
        currentWalkingRoute = null;
        InfoWindow.closeAllInfoWindowsOn(binding.mapBranches);
        binding.mapBranches.getOverlays().clear();
        binding.cardMapContainer.setVisibility(View.GONE);
        binding.cardNearestBranch.setVisibility(View.GONE);
        binding.cardLocationFallback.setVisibility(View.GONE);
        branchAdapter.submitList(null, false);
        updateMapLifecycle();
        showBranchStatus("Đang xác định vị trí GPS…", false);

        if (!hasLocationPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (mayRequestPermission) {
                permissionRequested = true;
                permissionRequestInFlight = true;
                locationPermissionLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                });
            } else {
                showPermissionFallback();
            }
            return;
        }

        LocationManager manager = (LocationManager) requireContext().getSystemService(
            Context.LOCATION_SERVICE
        );
        if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
            enterLocationFallbackMode("Dịch vụ vị trí đang tắt. Hãy bật GPS và thử lại.");
            return;
        }
        fetchCurrentLocation();
    }

    private boolean hasLocationPermission(String permission) {
        return ContextCompat.checkSelfPermission(requireContext(), permission)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionFallback() {
        boolean approximate = hasLocationPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        enterLocationFallbackMode(approximate
            ? "Bạn chỉ cấp vị trí gần đúng. Hãy bật Vị trí chính xác để tìm chi nhánh gần nhất."
            : "Chưa có quyền vị trí chính xác. Bạn có thể cấp quyền trong Cài đặt.");
    }

    private void fetchCurrentLocation() {
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0L)
            .setDurationMillis(15_000L)
            .build();
        CancellationTokenSource source = new CancellationTokenSource();
        locationCancelTokenSource = source;
        try {
            fusedLocationClient.getCurrentLocation(request, source.getToken())
                .addOnCompleteListener(task -> {
                    if (!isMapActive() || locationCancelTokenSource != source) return;
                    locationCancelTokenSource = null;
                    if (!hasLocationPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        showPermissionFallback();
                        return;
                    }
                    Location location = task.isSuccessful() ? task.getResult() : null;
                    if (location == null || !validCoordinates(location.getLatitude(), location.getLongitude())) {
                        enterLocationFallbackMode("Không lấy được vị trí GPS hiện tại. Hãy thử lại.");
                        return;
                    }
                    userLocation = location;
                    renderBranchesOnMap(null, null, null);
                    loadBranches(location.getLatitude(), location.getLongitude());
                });
        } catch (SecurityException e) {
            locationCancelTokenSource = null;
            source.cancel();
            showPermissionFallback();
        }
    }

    private void enterLocationFallbackMode(String message) {
        binding.cardLocationFallback.setVisibility(View.VISIBLE);
        binding.tvLocationFallback.setText(message + " Bạn vẫn có thể xem toàn bộ chi nhánh.");
        loadBranches(null, null);
    }

    private void sendQuickPrompt(String promptText) {
        if (binding == null || aiCall != null) return;
        binding.edtAiPrompt.setText(promptText);
        sendAiQuestion();
    }

    private void loadBranches(@Nullable Double lat, @Nullable Double lng) {
        if (!isMapActive()) return;
        showBranchStatus("Đang tải danh sách chi nhánh…", false);
        branchCall = ApiClient.customerApi(requireContext()).getBranches(lat, lng);
        branchCall.enqueue(new Callback<ApiResponse<CustomerModels.BranchesResponse>>() {
            @Override
            public void onResponse(
                @NonNull Call<ApiResponse<CustomerModels.BranchesResponse>> call,
                @NonNull Response<ApiResponse<CustomerModels.BranchesResponse>> response
            ) {
                if (!takeBranchResult(call)) return;
                ApiResponse<CustomerModels.BranchesResponse> body = response.body();
                if (!response.isSuccessful() || body == null || !body.success || body.data == null) {
                    showBranchLoadError();
                    return;
                }

                CustomerModels.BranchesResponse data = body.data;
                boolean nearby = lat != null && lng != null;
                // The no-coordinate endpoint may return the first branch as "nearest".
                nearestBranch = nearby ? data.nearest : null;
                currentWalkingRoute = nearestBranch != null ? data.walkingRoute : null;
                List<CustomerModels.BranchItem> branches = new ArrayList<>();
                if (data.branches != null) {
                    for (CustomerModels.BranchItem branch : data.branches) {
                        if (branch != null) branches.add(branch);
                    }
                }
                if (nearby) {
                    renderNearestBranch();
                    renderBranchesOnMap(branches, nearestBranch, currentWalkingRoute);
                }
                branchAdapter.submitList(branches, nearby);
                showBranchStatus(branches.isEmpty() ? "Không có chi nhánh để hiển thị." : null, false);
            }

            @Override
            public void onFailure(
                @NonNull Call<ApiResponse<CustomerModels.BranchesResponse>> call,
                @NonNull Throwable t
            ) {
                if (takeBranchResult(call)) showBranchLoadError();
            }
        });
    }

    private boolean takeBranchResult(Call<ApiResponse<CustomerModels.BranchesResponse>> call) {
        if (!isMapActive() || call != branchCall || call.isCanceled()) return false;
        branchCall = null;
        return true;
    }

    private void showBranchLoadError() {
        showBranchStatus("Không tải được danh sách chi nhánh. Vui lòng thử lại.", true);
    }

    private void showBranchStatus(@Nullable String message, boolean retry) {
        binding.tvBranchStatus.setText(message);
        binding.tvBranchStatus.setVisibility(message != null ? View.VISIBLE : View.GONE);
        binding.btnRetryBranches.setVisibility(retry ? View.VISIBLE : View.GONE);
    }

    private void renderNearestBranch() {
        binding.cardNearestBranch.setVisibility(nearestBranch != null ? View.VISIBLE : View.GONE);
        if (nearestBranch == null) return;

        binding.tvNearestBranch.setText(branchDetails(nearestBranch));
        binding.tvNearestDistancePill.setText(nearestBranch.distanceMeters + " m");
        binding.tvNearestWalkPill.setText("~" + nearestBranch.estimatedWalkMinutes + " phút đi bộ");
        binding.tvNearestDistancePill.setVisibility(nearestBranch.distanceMeters >= 0 ? View.VISIBLE : View.GONE);
        binding.tvNearestWalkPill.setVisibility(nearestBranch.estimatedWalkMinutes >= 0 ? View.VISIBLE : View.GONE);
        String note = currentWalkingRoute == null ? null : currentWalkingRoute.note;
        binding.tvWalkingRouteNote.setText(note);
        binding.tvWalkingRouteNote.setVisibility(TextUtils.isEmpty(note) ? View.GONE : View.VISIBLE);
        binding.btnOpenWalkingMap.setEnabled(directionsUrl(nearestBranch, currentWalkingRoute) != null);
    }

    private void renderBranchesOnMap(
        @Nullable List<CustomerModels.BranchItem> branches,
        @Nullable CustomerModels.BranchItem nearest,
        @Nullable CustomerModels.WalkingRoute walkingRoute
    ) {
        if (userLocation == null) return;
        binding.cardMapContainer.setVisibility(View.VISIBLE);
        updateMapLifecycle();
        InfoWindow.closeAllInfoWindowsOn(binding.mapBranches);
        binding.mapBranches.getOverlays().clear();

        GeoPoint userPoint = new GeoPoint(userLocation.getLatitude(), userLocation.getLongitude());
        if (walkingRoute != null && walkingRoute.waypoints != null) {
            List<GeoPoint> points = new ArrayList<>();
            for (CustomerModels.Waypoint point : walkingRoute.waypoints) {
                if (point == null || !validCoordinates(point.lat, point.lng)) {
                    points.clear();
                    break;
                }
                points.add(new GeoPoint(point.lat, point.lng));
            }
            if (points.size() >= 2) {
                Polyline line = new Polyline(binding.mapBranches);
                line.setPoints(points);
                line.getOutlinePaint().setColor(ContextCompat.getColor(requireContext(), R.color.phl_info));
                line.getOutlinePaint().setStrokeWidth(7f);
                binding.mapBranches.getOverlays().add(line);
            }
        }

        addMarker(userPoint, R.color.phl_info, "Vị trí của bạn", null);
        if (branches != null) {
            for (CustomerModels.BranchItem branch : branches) {
                if (hasCoordinates(branch) && (nearest == null || branch.id != nearest.id)) {
                    addBranchMarker(branch, R.color.phl_danger);
                }
            }
        }

        // Add nearest last so it stays tappable when pins overlap.
        if (nearest != null && hasCoordinates(nearest)) {
            Marker marker = addBranchMarker(nearest, R.color.phl_emerald_dark);
            binding.mapBranches.getController().setZoom(16.0);
            binding.mapBranches.getController().setCenter(marker.getPosition());
            marker.showInfoWindow();
        } else {
            binding.mapBranches.getController().setZoom(15.0);
            binding.mapBranches.getController().setCenter(userPoint);
        }
        binding.mapBranches.invalidate();
    }

    private Marker addBranchMarker(CustomerModels.BranchItem branch, int colorRes) {
        String snippet = TextUtils.htmlEncode(nonBlank(branch.address, "Chưa có địa chỉ"));
        if (!TextUtils.isEmpty(branch.phone)) {
            snippet += "<br>Hotline: " + TextUtils.htmlEncode(branch.phone);
        }
        if (branch.distanceMeters >= 0) snippet += "<br>" + branch.distanceMeters + " m";
        if (branch.estimatedWalkMinutes >= 0) {
            snippet += " · ~" + branch.estimatedWalkMinutes + " phút đi bộ";
        }
        return addMarker(new GeoPoint(branch.latitude, branch.longitude), colorRes,
            nonBlank(branch.branchName, "Chi nhánh PHL Bank"), snippet);
    }

    private Marker addMarker(GeoPoint point, int colorRes, String title, @Nullable String snippet) {
        Marker marker = new Marker(binding.mapBranches);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setIcon(createCircleMarkerDrawable(ContextCompat.getColor(requireContext(), colorRes)));
        marker.setTitle(title);
        if (snippet != null) marker.setSnippet(snippet);
        binding.mapBranches.getOverlays().add(marker);
        return marker;
    }

    private static String branchDetails(CustomerModels.BranchItem branch) {
        String details = nonBlank(branch.branchName, "Chi nhánh PHL Bank") + "\n"
            + nonBlank(branch.address, "Chưa có địa chỉ");
        if (!TextUtils.isEmpty(branch.phone)) details += "\nHotline: " + branch.phone;
        return details;
    }

    private static String nonBlank(@Nullable String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static boolean validCoordinates(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    private static boolean hasCoordinates(CustomerModels.BranchItem branch) {
        return validCoordinates(branch.latitude, branch.longitude);
    }

    private Drawable createCircleMarkerDrawable(int fillColor) {
        int sizePx = Math.round(20f * getResources().getDisplayMetrics().density);
        int strokePx = Math.round(3f * getResources().getDisplayMetrics().density);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setSize(sizePx, sizePx);
        drawable.setColor(fillColor);
        drawable.setStroke(strokePx, Color.WHITE);
        return drawable;
    }

    private void openBranchDirections(@Nullable CustomerModels.BranchItem branch) {
        if (!isMapActive() || branch == null) return;
        Uri webUrl = directionsUrl(branch, branch == nearestBranch ? currentWalkingRoute : null);
        if (webUrl == null) {
            Toast.makeText(requireContext(), "Chưa có tọa độ chi nhánh", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri mapsUri = hasCoordinates(branch)
            ? Uri.parse("google.navigation:q=" + branch.latitude + "," + branch.longitude + "&mode=w")
            : webUrl;
        Intent mapsIntent = new Intent(Intent.ACTION_VIEW, mapsUri);
        mapsIntent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(mapsIntent);
        } catch (ActivityNotFoundException | SecurityException e) {
            // Launch the same destination as a browser-compatible HTTPS link, without a package.
            Intent webIntent = new Intent(Intent.ACTION_VIEW, webUrl);
            webIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            try {
                startActivity(webIntent);
            } catch (ActivityNotFoundException | SecurityException ignored) {
                Toast.makeText(requireContext(), "Không tìm thấy ứng dụng bản đồ hoặc trình duyệt",
                    Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Nullable
    private static Uri directionsUrl(
        CustomerModels.BranchItem branch, @Nullable CustomerModels.WalkingRoute route
    ) {
        // Prefer the validated branch coordinates over arbitrary destinations in an API link.
        String destination = hasCoordinates(branch) ? branch.latitude + "," + branch.longitude
            : mapsDestination(branch.googleMapsUrl);
        if (destination == null && route != null) destination = mapsDestination(route.googleMapsUrl);
        if (destination == null) return null;
        return new Uri.Builder().scheme("https").authority("www.google.com")
            .path("/maps/dir/")
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", destination)
            .appendQueryParameter("travelmode", "walking")
            .build();
    }

    @Nullable
    private static String mapsDestination(@Nullable String candidate) {
        if (candidate == null) return null;
        try {
            URI parsed = new URI(candidate.trim());
            String authority = parsed.getRawAuthority();
            if (!"https".equalsIgnoreCase(parsed.getScheme())
                || !("www.google.com".equalsIgnoreCase(authority) || "google.com".equalsIgnoreCase(authority))
                || !("/maps/dir/".equals(parsed.getRawPath()) || "/maps/dir".equals(parsed.getRawPath()))) {
                return null;
            }
            String destination = Uri.parse(parsed.toString()).getQueryParameter("destination");
            return nonBlank(destination, null);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private void sendAiQuestion() {
        if (!isAdded() || binding == null || aiCall != null) return;
        String prompt =
            binding.edtAiPrompt.getText() == null
                ? ""
                : binding.edtAiPrompt.getText().toString().trim();
        if (prompt.isEmpty()) return;

        Map<String, String> body = new HashMap<>();
        body.put("message", prompt);
        aiCall = ApiClient.customerApi(requireContext()).askAi(body);
        binding.edtAiPrompt.setText("");
        appendChatBubble("Bạn", prompt, true);
        setAiSending(true);
        showTypingIndicator();

        aiCall.enqueue(new Callback<ApiResponse<CustomerModels.AiChatResponse>>() {
            @Override
            public void onResponse(
                @NonNull Call<ApiResponse<CustomerModels.AiChatResponse>> call,
                @NonNull Response<ApiResponse<CustomerModels.AiChatResponse>> response
            ) {
                if (!finishAiRequest(call)) return;
                ApiResponse<CustomerModels.AiChatResponse> result = response.body();
                if (!response.isSuccessful() || result == null || !result.success ||
                    result.data == null || TextUtils.isEmpty(result.data.reply)) {
                    appendChatBubble("Trợ lý PHL", "Không nhận được câu trả lời. Vui lòng thử lại.", false);
                    return;
                }
                appendChatBubble("Trợ lý PHL", result.data.reply, false, result.data.suggestedAction);
            }

            @Override
            public void onFailure(
                @NonNull Call<ApiResponse<CustomerModels.AiChatResponse>> call,
                @NonNull Throwable t
            ) {
                if (!finishAiRequest(call)) return;
                appendChatBubble("Trợ lý PHL", "Không thể kết nối máy chủ AI.", false);
            }
        });
    }

    private boolean finishAiRequest(Call<ApiResponse<CustomerModels.AiChatResponse>> call) {
        if (binding == null || !isAdded() || call != aiCall) return false;
        aiCall = null;
        hideTypingIndicator();
        setAiSending(false);
        return true;
    }

    private void setAiSending(boolean sending) {
        binding.btnSendAi.setEnabled(!sending);
        binding.chipPromptTransfer.setEnabled(!sending);
        binding.chipPromptSavings.setEnabled(!sending);
        binding.chipPromptBranch.setEnabled(!sending);
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

    @Nullable
    private Runnable resolveAiAction(@Nullable String token) {
        if (token == null || binding == null || !(getActivity() instanceof CustomerMainActivity)) return null;
        CustomerMainActivity activity = (CustomerMainActivity) getActivity();
        switch (token) {
            case "[ACTION_TRANSFER]":
                return () -> {
                    startActivity(new Intent(activity, TransferActivity.class));
                    MotionHelper.applyActivityTransition(activity);
                };
            case "[ACTION_PAY_BILL]":
                return () -> activity.openUtilities(CustomerUtilitiesFragment.Mode.BILL_PAYMENT);
            case "[ACTION_TOPUP]":
                return () -> activity.openUtilities(CustomerUtilitiesFragment.Mode.TOPUP);
            case "[ACTION_HISTORY]":
                return () -> activity.openAccounts(CustomerAccountsFragment.Section.HISTORY);
            case "[ACTION_SAVINGS]":
                return () -> activity.openAccounts(CustomerAccountsFragment.Section.SAVINGS);
            case "[ACTION_MORTGAGE]":
                return () -> activity.openAccounts(CustomerAccountsFragment.Section.MORTGAGE);
            case "[ACTION_MAP]":
                return () -> binding.chipMapBranch.setChecked(true);
            case "[ACTION_PROFILE]":
                return () -> activity.selectTab(R.id.tab_cust_profile);
            default:
                return null;
        }
    }

    private void appendChatBubble(String sender, String text, boolean isUser) {
        appendChatBubble(sender, text, isUser, null);
    }

    private void appendChatBubble(
        String sender, String text, boolean isUser, @Nullable CustomerModels.SuggestedAction suggestion
    ) {
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

        FragmentCustomerExploreBinding viewBinding = binding;
        if (!isUser && suggestion != null && !TextUtils.isEmpty(suggestion.label)) {
            Runnable action = resolveAiAction(suggestion.actionToken);
            if (action != null) {
                msgBinding.btnMessageAction.setText(suggestion.label);
                msgBinding.btnMessageAction.setVisibility(View.VISIBLE);
                MotionHelper.applyTactilePress(msgBinding.btnMessageAction);
                msgBinding.btnMessageAction.setOnClickListener(v -> {
                    if (binding == viewBinding && isAdded()) action.run();
                });
            }
        }

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
            if (binding == viewBinding) {
                binding.scrollAiMessages.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private interface OnDirectionsClickListener {
        void onDirectionsClick(CustomerModels.BranchItem branch);
    }

    private static class BranchAdapter extends RecyclerView.Adapter<BranchAdapter.BranchViewHolder> {

        private final List<CustomerModels.BranchItem> items = new ArrayList<>();
        private final OnDirectionsClickListener directionsClickListener;
        private boolean showDistancePills = false;

        BranchAdapter(OnDirectionsClickListener directionsClickListener) {
            this.directionsClickListener = directionsClickListener;
        }

        void submitList(
            @Nullable List<CustomerModels.BranchItem> newItems,
            boolean showDistancePills
        ) {
            this.items.clear();
            if (newItems != null) {
                this.items.addAll(newItems);
            }
            this.showDistancePills = showDistancePills;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public BranchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemBranchCardBinding binding = ItemBranchCardBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
            );
            return new BranchViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull BranchViewHolder holder, int position) {
            holder.bind(items.get(position), showDistancePills, directionsClickListener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class BranchViewHolder extends RecyclerView.ViewHolder {

            private final ItemBranchCardBinding itemBinding;

            BranchViewHolder(ItemBranchCardBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            void bind(
                CustomerModels.BranchItem b,
                boolean showDistancePills,
                OnDirectionsClickListener listener
            ) {
                itemBinding.tvBranchName.setText(nonBlank(b.branchName, "Chi nhánh PHL Bank"));
                itemBinding.tvBranchAddress.setText(nonBlank(b.address, "Chưa có địa chỉ"));
                if (b.phone != null && !b.phone.trim().isEmpty()) {
                    itemBinding.tvBranchPhone.setText("Hotline: " + b.phone);
                    itemBinding.tvBranchPhone.setVisibility(View.VISIBLE);
                } else {
                    itemBinding.tvBranchPhone.setVisibility(View.GONE);
                }

                boolean showDistance = showDistancePills && b.distanceMeters >= 0;
                boolean showWalkTime = showDistancePills && b.estimatedWalkMinutes >= 0;
                itemBinding.tvBranchDistance.setText(b.distanceMeters + " m");
                itemBinding.tvBranchWalkTime.setText("~" + b.estimatedWalkMinutes + " phút đi bộ");
                itemBinding.tvBranchDistance.setVisibility(showDistance ? View.VISIBLE : View.GONE);
                itemBinding.tvBranchWalkTime.setVisibility(showWalkTime ? View.VISIBLE : View.GONE);

                itemBinding.btnBranchDirections.setEnabled(directionsUrl(b, null) != null);
                itemBinding.btnBranchDirections.setOnClickListener(v ->
                    listener.onDirectionsClick(b)
                );
                MotionHelper.applyTactilePress(itemBinding.btnBranchDirections);
            }
        }
    }
}
