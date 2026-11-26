package vn.edu.tdtu.phlbanking.data.remote;

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.model.LoginData;

public interface AuthApiService {
    @POST("api/auth/login")
    Call<ApiResponse<LoginData>> login(@Body Map<String, String> body);

    @POST("api/auth/logout")
    Call<ApiResponse<Void>> logout();

    @POST("api/auth/forgot-password/request-otp")
    Call<ApiResponse<Map<String, Object>>> requestForgotOtp(@Body Map<String, String> body);

    @POST("api/auth/forgot-password/confirm")
    Call<ApiResponse<Void>> confirmForgotPassword(@Body Map<String, String> body);
}
