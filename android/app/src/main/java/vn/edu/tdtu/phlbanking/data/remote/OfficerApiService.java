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
import vn.edu.tdtu.phlbanking.data.model.OfficerModels;

public interface OfficerApiService {
    @GET("api/officer/customers")
    Call<ApiResponse<List<OfficerModels.CustomerItem>>> searchCustomers(@Query("q") String keyword);

    @POST("api/officer/customers")
    Call<ApiResponse<Map<String, Object>>> createCustomer(@Body Map<String, Object> body);

    @PATCH("api/officer/customers/{customerId}")
    Call<ApiResponse<Map<String, Object>>> updateCustomer(
        @Path("customerId") long customerId,
        @Body Map<String, Object> body
    );

    @PATCH("api/officer/interest-rates")
    Call<ApiResponse<Map<String, Object>>> adjustInterestRate(@Body Map<String, Object> body);

    @POST("api/officer/customers/{customerId}/ekyc/challenge")
    Call<ApiResponse<Map<String, Object>>> enrollmentChallenge(@Path("customerId") long customerId);

    @POST("api/officer/customers/{customerId}/ekyc/enroll")
    Call<ApiResponse<Map<String, Object>>> enrollEkyc(
        @Path("customerId") long customerId,
        @Body Map<String, Object> body
    );

    @POST("api/security/ekyc/frame/check")
    Call<ApiResponse<Map<String, Object>>> inspectEkycFrame(@Body Map<String, Object> body);

    @GET("api/officer/audit-logs")
    Call<ApiResponse<List<OfficerModels.AuditLogItem>>> getAuditLogs(@Query("limit") int limit);
}
