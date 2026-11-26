package vn.edu.tdtu.phlbanking.ui.customer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Base64;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.remote.ApiClient;
import vn.edu.tdtu.phlbanking.databinding.ActivityFaceEkycBinding;
import vn.edu.tdtu.phlbanking.ui.common.ApiErrors;
import vn.edu.tdtu.phlbanking.ui.common.MotionHelper;

/** Real-photo educational eKYC; movement checking is not certified anti-spoofing. */
public class FaceEkycActivity extends AppCompatActivity {

    public static final String RESULT_EKYC_TOKEN = "result_ekyc_token";
    public static final String EXTRA_INTENT_JSON = "ekyc_intent_json";
    public static final String EXTRA_CUSTOMER_ID = "ekyc_customer_id";
    public static final String EXTRA_SELF_ENROLLMENT_TOKEN = "self_enrollment_token";
    private ActivityFaceEkycBinding binding;
    private ImageCapture imageCapture;
    private ProcessCameraProvider provider;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private Call<ApiResponse<Map<String, Object>>> pendingCall;
    private Map<String, Object> intent;
    private String challengeId, firstImage, enrollmentToken;
    private boolean selfEnrollment;

    private boolean isEnrollment() {
        return selfEnrollment || customerId > 0;
    }

    private long customerId;
    private boolean cameraReady, cameraStreaming, busy;
    private Bitmap capturedPreview;
    private long challengeExpiresAt;
    private int captureGeneration;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        granted -> {
            if (granted) startFrontCamera();
            else showCameraError(
                "Chưa cấp quyền camera. Không thể xác minh hoặc tiếp tục OTP. Hãy cấp quyền trong Cài đặt rồi mở lại."
            );
        }
    );

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivityFaceEkycBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setResult(RESULT_CANCELED);
        imageExecutor.execute(this::clearExpiredCaptureFiles);
        customerId = getIntent().getLongExtra(EXTRA_CUSTOMER_ID, 0);
        enrollmentToken = getIntent().getStringExtra(EXTRA_SELF_ENROLLMENT_TOKEN);
        selfEnrollment = enrollmentToken != null && !enrollmentToken.isEmpty();
        String json = getIntent().getStringExtra(EXTRA_INTENT_JSON);
        if (json != null) {
            try {
                intent = new Gson().fromJson(
                    json,
                    new TypeToken<Map<String, Object>>() {}.getType()
                );
            } catch (Exception error) {
                finish();
                return;
            }
        }
        if (selfEnrollment && (customerId > 0 || intent != null)) {
            finish();
            return;
        }
        binding.toolbarEkyc.setTitle(
            isEnrollment() ? "Đăng ký sinh trắc học" : "Xác minh khuôn mặt eKYC"
        );
        binding.toolbarEkyc.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.toolbarEkyc.setNavigationOnClickListener(v -> finish());
        binding.btnCaptureVerifyFace.setEnabled(false);
        binding.btnCaptureVerifyFace.setOnClickListener(v -> capture());
        binding.btnRetakeEkyc.setOnClickListener(v -> {
            if (!busy) {
                clearCapturedPreview();
                requestChallenge();
            }
        });
        binding.previewCameraEkyc.getPreviewStreamState().observe(this, streamState -> {
            cameraStreaming = streamState == androidx.camera.view.PreviewView.StreamState.STREAMING;
            if (cameraStreaming) binding.tvCameraFallback.setVisibility(View.GONE);
            updateCaptureButton();
        });
        MotionHelper.staggerEntrance(
            binding.badgeEkycStatus,
            binding.tvEkycInstruction,
            binding.cardEkycChamber,
            binding.btnCaptureVerifyFace
        );
        MotionHelper.pulseView(binding.viewEkycReticle);
        binding.layoutEkycScanRegion.post(() -> {
            if (!isFinishing() && !isDestroyed()) MotionHelper.startScanlineLoop(
                binding.viewEkycScanline,
                binding.layoutEkycScanRegion
            );
        });
        new MaterialAlertDialogBuilder(this)
            .setTitle(isEnrollment() ? "Đồng ý đăng ký sinh trắc học" : "Đồng ý chụp và đối chiếu")
            .setMessage(
                (selfEnrollment
                    ? "Bạn đang đăng ký ảnh gốc cho chính tài khoản đã xác nhận mật khẩu và OTP. Chỉ chụp khuôn mặt của bạn. Ảnh và đặc trưng khuôn mặt sẽ được lưu trên máy chủ để đối chiếu giao dịch sau này; không tự thay ảnh gốc đã đăng ký.\n\n"
                    : customerId > 0
                      ? "Nhân viên cần kiểm tra CCCD và được khách hàng đồng ý. Ảnh gốc được lưu trong hệ thống để đối chiếu về sau.\n\n"
                      : "Hai ảnh chụp được gửi đến máy chủ để đối chiếu ảnh gốc; không lấy ảnh từ thư viện.\n\n") +
                    "Bạn sẽ chụp nhìn thẳng rồi xoay nhẹ đầu. Đây là đối chiếu OpenCV cho đồ án; kiểm tra chuyển động không phải chống giả mạo/liveness được chứng nhận."
            )
            .setNegativeButton("Không đồng ý", (d, w) -> finish())
            .setPositiveButton("Đồng ý và tiếp tục", (d, w) -> {
                requestChallenge();
                if (
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) startFrontCamera();
                else permissionLauncher.launch(Manifest.permission.CAMERA);
            })
            .setOnCancelListener(d -> finish())
            .show();
    }

    private void requestChallenge() {
        challengeId = null;
        firstImage = null;
        challengeExpiresAt = 0;
        clearCapturedPreview();
        busy = true;
        updateCaptureButton();
        binding.tvEkycInstruction.setText("Đang tạo phiên chụp an toàn...");
        Map<String, Object> body = new HashMap<>();
        if (intent != null) body.put("intent", intent);
        if (selfEnrollment) {
            body.put("enrollmentToken", enrollmentToken);
            pendingCall = ApiClient.customerApi(this).selfEnrollmentChallenge(body);
        } else pendingCall =
            customerId > 0
                ? ApiClient.officerApi(this).enrollmentChallenge(customerId)
                : ApiClient.customerApi(this).ekycChallenge(body);
        pendingCall.enqueue(
            new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Response<ApiResponse<Map<String, Object>>> response
                ) {
                    if (isFinishing() || isDestroyed() || call.isCanceled()) return;
                    busy = false;
                    if (
                        response.isSuccessful() &&
                        response.body() != null &&
                        response.body().data != null
                    ) {
                        Object id = response.body().data.get("challengeId");
                        if (id != null) {
                            challengeId = String.valueOf(id);
                            Object seconds = response.body().data.get("expiresInSeconds");
                            challengeExpiresAt =
                                android.os.SystemClock.elapsedRealtime() +
                                (seconds instanceof Number
                                    ? ((Number) seconds).longValue()
                                    : 120L) *
                                    1000L;
                            binding.tvEkycInstruction.setText(
                                "Bước 1/2 · Nhìn thẳng, đủ sáng, chỉ một người. Ảnh chụp sẽ được kiểm tra trước khi sang bước 2."
                            );
                        }
                    } else showError(ApiErrors.message(response, "Không tạo được phiên eKYC."));
                    updateCaptureButton();
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Throwable error
                ) {
                    if (isFinishing() || isDestroyed() || call.isCanceled()) return;
                    busy = false;
                    showError("Không kết nối được máy chủ. Chưa được xác minh.");
                    updateCaptureButton();
                }
            }
        );
    }

    private void startFrontCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                provider = future.get();
                if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    showCameraError(
                        "Thiết bị không có camera trước. Cần điện thoại hoặc Emulator cấu hình Front camera = Webcam."
                    );
                    return;
                }
                int rotation =
                    binding.previewCameraEkyc.getDisplay() == null
                        ? Surface.ROTATION_0
                        : binding.previewCameraEkyc.getDisplay().getRotation();
                Preview preview = new Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setTargetRotation(rotation)
                    .build();
                preview.setSurfaceProvider(binding.previewCameraEkyc.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setTargetRotation(rotation)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(85)
                    .build();
                provider.unbindAll();
                UseCaseGroup.Builder group = new UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageCapture);
                if (binding.previewCameraEkyc.getViewPort() != null) group.setViewPort(
                    binding.previewCameraEkyc.getViewPort()
                );
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, group.build());
                cameraReady = true;
                updateCaptureButton();
            } catch (Exception error) {
                showCameraError("Không mở được camera trước. Không có chế độ xác minh giả lập.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capture() {
        if (!cameraReady || !cameraStreaming || imageCapture == null || busy) return;
        if (challengeId == null || android.os.SystemClock.elapsedRealtime() >= challengeExpiresAt) {
            requestChallenge();
            return;
        }
        busy = true;
        updateCaptureButton();
        binding.progressEkycVerify.setVisibility(View.VISIBLE);
        final int generation = ++captureGeneration;
        File file;
        try {
            file = File.createTempFile("ekyc_", ".jpg", getCacheDir());
        } catch (Exception error) {
            captureFailed(generation, "Không tạo được ảnh tạm.");
            return;
        }
        imageCapture.setTargetRotation(binding.previewCameraEkyc.getDisplay().getRotation());
        imageCapture.takePicture(
            new ImageCapture.OutputFileOptions.Builder(file).build(),
            imageExecutor,
            new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults result) {
                    try {
                        String encoded = encodeJpeg(file);
                        deleteCaptureFile(file);
                        runOnUiThread(() -> {
                            if (
                                isFinishing() || isDestroyed() || generation != captureGeneration
                            ) return;
                            boolean second = firstImage != null;
                            showCapturedPreview(encoded, 0);
                            binding.tvCapturedFrameStatus.setText(
                                (second ? "Ảnh 2" : "Ảnh 1") + " · Đang kiểm tra ảnh chụp thật..."
                            );
                            inspectFrame(encoded, second, generation);
                        });
                    } catch (Exception error) {
                        deleteCaptureFile(file);
                        runOnUiThread(() ->
                            captureFailed(generation, "Ảnh chụp không đọc được. Vui lòng chụp lại.")
                        );
                    }
                }

                @Override
                public void onError(@NonNull ImageCaptureException error) {
                    deleteCaptureFile(file);
                    runOnUiThread(() ->
                        captureFailed(generation, "Chụp ảnh thất bại. Chưa được xác minh.")
                    );
                }
            }
        );
    }

    private void clearExpiredCaptureFiles() {
        File[] files = getCacheDir().listFiles(
            (directory, name) -> name.startsWith("ekyc_") && name.endsWith(".jpg")
        );
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - 5 * 60_000L;
        for (File file : files) {
            if (file.lastModified() < cutoff) deleteCaptureFile(file);
        }
    }

    private void deleteCaptureFile(File file) {
        if (file.exists() && !file.delete()) {
            android.util.Log.w(
                "EkycCapture",
                "Could not remove a private temporary capture; it will be retried on next launch."
            );
        }
    }

    private String encodeJpeg(File file) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        options.inSampleSize = Math.max(1, Math.max(options.outWidth, options.outHeight) / 1000);
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) throw new Exception("Invalid image");
        ExifInterface exif = new ExifInterface(file.getAbsolutePath());
        Matrix rotation = new Matrix();
        rotation.postRotate(exif.getRotationDegrees());
        if (exif.isFlipped()) rotation.postScale(-1, 1);
        Bitmap upright = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.getWidth(),
            bitmap.getHeight(),
            rotation,
            true
        );
        float scale = Math.min(1f, 960f / Math.max(upright.getWidth(), upright.getHeight()));
        Bitmap resized = Bitmap.createScaledBitmap(
            upright,
            Math.round(upright.getWidth() * scale),
            Math.round(upright.getHeight() * scale),
            true
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.JPEG, 82, output);
        byte[] bytes = output.toByteArray();
        if (resized != upright && resized != bitmap) resized.recycle();
        if (upright != bitmap) upright.recycle();
        bitmap.recycle();
        if (bytes.length > 300000 || bytes.length < 2000) throw new Exception("Image size");
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private void inspectFrame(String encoded, boolean second, int generation) {
        Map<String, Object> body = new HashMap<>();
        body.put("challengeId", challengeId);
        body.put("imageBase64", encoded);
        body.put("frameIndex", second ? 2 : 1);
        binding.btnRetakeEkyc.setEnabled(false);
        pendingCall =
            customerId > 0
                ? ApiClient.officerApi(this).inspectEkycFrame(body)
                : ApiClient.customerApi(this).inspectEkycFrame(body);
        pendingCall.enqueue(
            new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Response<ApiResponse<Map<String, Object>>> response
                ) {
                    if (
                        isFinishing() ||
                        isDestroyed() ||
                        call.isCanceled() ||
                        generation != captureGeneration
                    ) return;
                    Map<String, Object> data =
                        response.body() == null ? null : response.body().data;
                    if (
                        response.isSuccessful() &&
                        data != null &&
                        Boolean.TRUE.equals(data.get("frameAccepted"))
                    ) {
                        int rotation =
                            data.get("rotationDegrees") instanceof Number
                                ? ((Number) data.get("rotationDegrees")).intValue()
                                : 0;
                        showCapturedPreview(encoded, rotation);
                        binding.tvCapturedFrameStatus.setText(
                            (second ? "Ảnh 2" : "Ảnh 1") +
                                " · Đã thấy 1 khuôn mặt rõ." +
                                (rotation == 0 ? "" : " Đã sửa hướng ảnh webcam.") +
                                " Chưa xác minh danh tính."
                        );
                        if (second) submit(encoded);
                        else {
                            firstImage = encoded;
                            busy = false;
                            binding.progressEkycVerify.setVisibility(View.GONE);
                            binding.tvEkycInstruction.setText(
                                "Ảnh 1 đạt · Bước 2/2: xoay nhẹ đầu sang một bên, vẫn nhìn thấy hai mắt. Không xoay điện thoại. Nhấn chụp ảnh 2."
                            );
                            binding.btnRetakeEkyc.setEnabled(true);
                            updateCaptureButton();
                        }
                    } else {
                        busy = false;
                        binding.progressEkycVerify.setVisibility(View.GONE);
                        binding.btnRetakeEkyc.setEnabled(true);
                        ApiErrors.Detail error = ApiErrors.detail(
                            response,
                            "Không kiểm tra được ảnh chụp."
                        );
                        binding.tvCapturedFrameStatus.setText(
                            (second ? "Ảnh 2" : "Ảnh 1") +
                                " chưa đạt · " +
                                error.code +
                                (error.reason.isEmpty() ? "" : " / " + error.reason) +
                                "\n" +
                                error.message
                        );
                        android.util.Log.w(
                            "EkycFrameCheck",
                            "frame=" +
                                (second ? 2 : 1) +
                                " code=" +
                                error.code +
                                " reason=" +
                                error.reason
                        );
                        showError(
                            error.message +
                                (second
                                    ? " Ảnh 1 vẫn được giữ, hãy chụp lại ảnh 2."
                                    : " Hãy chụp lại ảnh nhìn thẳng.")
                        );
                        if (response.code() == 403 || response.code() == 401) {
                            challengeId = null;
                            firstImage = null;
                        }
                        updateCaptureButton();
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Throwable error
                ) {
                    if (
                        isFinishing() ||
                        isDestroyed() ||
                        call.isCanceled() ||
                        generation != captureGeneration
                    ) return;
                    busy = false;
                    binding.progressEkycVerify.setVisibility(View.GONE);
                    binding.btnRetakeEkyc.setEnabled(true);
                    showError(
                        "Mất kết nối khi kiểm tra ảnh chụp. Chưa đăng ký hoặc xác minh. Hãy chụp lại ảnh này."
                    );
                    updateCaptureButton();
                }
            }
        );
    }

    private void showCapturedPreview(String encoded, int rotationDegrees) {
        byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
        Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (decoded == null) return;
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        Bitmap upright =
            rotationDegrees == 0
                ? decoded
                : Bitmap.createBitmap(
                      decoded,
                      0,
                      0,
                      decoded.getWidth(),
                      decoded.getHeight(),
                      matrix,
                      true
                  );
        float scale = Math.min(1f, 160f / Math.max(upright.getWidth(), upright.getHeight()));
        Bitmap thumbnail = Bitmap.createScaledBitmap(
            upright,
            Math.max(1, Math.round(upright.getWidth() * scale)),
            Math.max(1, Math.round(upright.getHeight() * scale)),
            true
        );
        binding.imgCapturedFrame.setImageDrawable(null);
        if (capturedPreview != null) capturedPreview.recycle();
        capturedPreview = thumbnail;
        binding.imgCapturedFrame.setImageBitmap(thumbnail);
        binding.layoutCapturedFrame.setVisibility(View.VISIBLE);
        if (upright != thumbnail && upright != decoded) upright.recycle();
        if (decoded != thumbnail) decoded.recycle();
    }

    private void clearCapturedPreview() {
        binding.imgCapturedFrame.setImageDrawable(null);
        if (capturedPreview != null) {
            capturedPreview.recycle();
            capturedPreview = null;
        }
        binding.layoutCapturedFrame.setVisibility(View.GONE);
    }

    private void submit(String secondImage) {
        binding.btnCaptureVerifyFace.setText("Đang đối chiếu ảnh thật...");
        binding.tvCapturedFrameStatus.setText(
            "Hai ảnh đã có mặt rõ · Đang kiểm tra cùng người và chuyển động..."
        );
        Map<String, Object> body = new HashMap<>();
        body.put("challengeId", challengeId);
        body.put("imageBase64", firstImage);
        body.put("motionImageBase64", secondImage);
        if (intent != null) body.put("intent", intent);
        body.put("consent", true);
        if (selfEnrollment) {
            body.put("enrollmentToken", enrollmentToken);
            pendingCall = ApiClient.customerApi(this).selfEnrollEkyc(body);
        } else pendingCall =
            customerId > 0
                ? ApiClient.officerApi(this).enrollEkyc(customerId, body)
                : ApiClient.customerApi(this).verifyEkyc(body);
        pendingCall.enqueue(
            new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Response<ApiResponse<Map<String, Object>>> response
                ) {
                    if (isFinishing() || isDestroyed() || call.isCanceled()) return;
                    busy = false;
                    firstImage = null;
                    challengeId = null;
                    binding.progressEkycVerify.setVisibility(View.GONE);
                    Map<String, Object> data =
                        response.body() == null ? null : response.body().data;
                    boolean success =
                        response.isSuccessful() &&
                        data != null &&
                        Boolean.TRUE.equals(data.get(isEnrollment() ? "enrolled" : "verified"));
                    Object token = data == null ? null : data.get("ekycToken");
                    if (success && (intent == null || token != null)) {
                        binding.viewEkycReticle.setBackgroundResource(
                            R.drawable.bg_m3_ekyc_reticle_verified
                        );
                        Intent result = new Intent();
                        if (token != null) result.putExtra(
                            RESULT_EKYC_TOKEN,
                            String.valueOf(token)
                        );
                        if (intent != null) result.putExtra(
                            "idempotencyKey",
                            String.valueOf(intent.get("idempotencyKey"))
                        );
                        setResult(RESULT_OK, result);
                        Toast.makeText(
                            FaceEkycActivity.this,
                            isEnrollment()
                                ? "Đăng ký sinh trắc học thành công"
                                : "Khuôn mặt khớp ảnh gốc",
                            Toast.LENGTH_LONG
                        ).show();
                        finish();
                    } else {
                        String message = ApiErrors.message(
                            response,
                            "Đối chiếu thất bại. Chưa được đăng ký/xác minh."
                        );
                        binding.tvCapturedFrameStatus.setText(
                            "Chưa hoàn tất. Hãy tạo phiên chụp mới; không cần xin OTP mới nếu quyền đăng ký còn hạn."
                        );
                        binding.btnRetakeEkyc.setEnabled(true);
                        showError(message);
                        updateCaptureButton();
                    }
                }

                @Override
                public void onFailure(
                    @NonNull Call<ApiResponse<Map<String, Object>>> call,
                    @NonNull Throwable error
                ) {
                    if (isFinishing() || isDestroyed() || call.isCanceled()) return;
                    busy = false;
                    firstImage = null;
                    challengeId = null;
                    binding.progressEkycVerify.setVisibility(View.GONE);
                    showError("Mất kết nối. Chưa được xác minh; hãy bắt đầu lại.");
                    updateCaptureButton();
                }
            }
        );
    }

    private void updateCaptureButton() {
        binding.btnCaptureVerifyFace.setEnabled(cameraReady && cameraStreaming && !busy);
        binding.btnRetakeEkyc.setEnabled(!busy);
        binding.btnCaptureVerifyFace.setText(
            challengeId == null
                ? "Tạo lại phiên chụp"
                : firstImage == null
                  ? "1 · Chụp ảnh nhìn thẳng"
                  : "2 · Chụp ảnh xoay nhẹ đầu"
        );
    }

    private void captureFailed(int generation, String message) {
        if (isFinishing() || isDestroyed() || generation != captureGeneration) return;
        busy = false;
        binding.progressEkycVerify.setVisibility(View.GONE);
        showError(message);
        updateCaptureButton();
    }

    private void showCameraError(String message) {
        cameraReady = false;
        binding.tvCameraFallback.setVisibility(View.VISIBLE);
        binding.tvCameraFallback.setText(message);
        updateCaptureButton();
    }

    private void showError(String message) {
        binding.tvEkycInstruction.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStop() {
        // Never reuse captured frames after the app leaves the foreground.
        firstImage = null;
        challengeId = null;
        challengeExpiresAt = 0;
        captureGeneration++;
        clearCapturedPreview();
        if (pendingCall != null && busy) {
            pendingCall.cancel();
            busy = false;
        }
        if (binding != null) binding.progressEkycVerify.setVisibility(View.GONE);
        if (binding != null && !busy) updateCaptureButton();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (pendingCall != null) pendingCall.cancel();
        if (provider != null) provider.unbindAll();
        imageExecutor.shutdown();
        firstImage = null;
        enrollmentToken = null;
        clearCapturedPreview();
        if (binding != null) MotionHelper.stopViewAnimations(
            binding.badgeEkycStatus,
            binding.viewEkycReticle,
            binding.viewEkycScanline
        );
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        MotionHelper.applyActivityTransition(this);
    }
}
