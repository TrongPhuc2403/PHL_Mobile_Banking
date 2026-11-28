package vn.edu.tdtu.phlbanking.data.remote;

import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import vn.edu.tdtu.phlbanking.data.model.ApiResponse;
import vn.edu.tdtu.phlbanking.data.model.CustomerModels;

public interface CustomerApiService {
    @GET("api/accounts")
    Call<ApiResponse<List<CustomerModels.BankAccountItem>>> getAccounts();

    @GET("api/accounts/lookup/{accountNumber}")
    Call<ApiResponse<Map<String, Object>>> lookupAccount(
        @Path("accountNumber") String accountNumber
    );

    @GET("api/accounts/mortgages")
    Call<ApiResponse<List<CustomerModels.MortgageLoanItem>>> getMortgages();

    @GET("api/accounts/history")
    Call<ApiResponse<List<CustomerModels.HistoryItem>>> getHistory();

    @GET("api/accounts/history")
    Call<CustomerModels.HistoryPage> getHistory(
        @Query("accountId") Long accountId,
        @Query("limit") int limit,
        @Query("offset") int offset
    );

    @POST("api/security/otp/request")
    Call<ApiResponse<Map<String, Object>>> requestOtp(@Body Map<String, Object> body);

    @GET("api/security/ekyc/status")
    Call<ApiResponse<Map<String, Object>>> getEkycStatus();

    @POST("api/security/ekyc/challenge")
    Call<ApiResponse<Map<String, Object>>> ekycChallenge(@Body Map<String, Object> body);

    @POST("api/security/ekyc/enrollment/request-otp")
    Call<ApiResponse<Map<String, Object>>> requestEnrollmentOtp(@Body Map<String, Object> body);

    @POST("api/security/ekyc/enrollment/confirm-otp")
    Call<ApiResponse<Map<String, Object>>> confirmEnrollmentOtp(@Body Map<String, Object> body);

    @POST("api/security/ekyc/enrollment/challenge")
    Call<ApiResponse<Map<String, Object>>> selfEnrollmentChallenge(@Body Map<String, Object> body);

    @POST("api/security/ekyc/enrollment")
    Call<ApiResponse<Map<String, Object>>> selfEnrollEkyc(@Body Map<String, Object> body);

    @POST("api/security/ekyc/frame/check")
    Call<ApiResponse<Map<String, Object>>> inspectEkycFrame(@Body Map<String, Object> body);

    @POST("api/security/ekyc/verify")
    Call<ApiResponse<Map<String, Object>>> verifyEkyc(@Body Map<String, Object> body);

    @POST("api/transactions/internal-transfer")
    Call<ApiResponse<CustomerModels.TransactionResult>> internalTransfer(
        @Body Map<String, Object> body
    );

    @POST("api/transactions/interbank-transfer")
    Call<ApiResponse<CustomerModels.TransactionResult>> interbankTransfer(
        @Body Map<String, Object> body
    );

    @POST("api/transactions/gateway-pay")
    Call<ApiResponse<CustomerModels.TransactionResult>> gatewayPay(@Body Map<String, Object> body);

    @POST("api/transactions/deposit")
    Call<ApiResponse<CustomerModels.TransactionResult>> deposit(@Body Map<String, Object> body);

    @POST("api/transactions/withdraw")
    Call<ApiResponse<CustomerModels.TransactionResult>> withdraw(@Body Map<String, Object> body);

    @GET("api/utilities/catalog/{serviceType}")
    Call<ApiResponse<Map<String, Object>>> getUtilityCatalog(
        @Path("serviceType") String serviceType
    );

    @POST("api/utilities/pay")
    Call<ApiResponse<CustomerModels.TransactionResult>> payUtility(@Body Map<String, Object> body);

    @GET("api/hub/notifications")
    Call<ApiResponse<CustomerModels.NotificationsResponse>> getNotifications();

    // Pass null for both coordinates to omit them and load the full branch list.
    @GET("api/hub/branches")
    Call<ApiResponse<CustomerModels.BranchesResponse>> getBranches(
        @Query("lat") Double lat,
        @Query("lng") Double lng
    );

    @POST("api/hub/ai/chat")
    Call<ApiResponse<CustomerModels.AiChatResponse>> askAi(@Body Map<String, String> body);

    @GET("api/auth/me")
    Call<ApiResponse<Map<String, Object>>> getMyProfile();

    @PATCH("api/auth/me")
    Call<ApiResponse<Map<String, Object>>> updateContact(@Body Map<String, String> body);

    @POST("api/auth/change-password")
    Call<ApiResponse<Void>> changePassword(@Body Map<String, String> body);
}
