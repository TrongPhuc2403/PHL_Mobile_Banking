package vn.edu.tdtu.phlbanking.data.remote;

import android.content.Context;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.edu.tdtu.phlbanking.BuildConfig;
import vn.edu.tdtu.phlbanking.data.local.SessionManager;

public final class ApiClient {

    private static volatile Retrofit retrofitInstance;

    private ApiClient() {}

    public static Retrofit getInstance(Context context) {
        if (retrofitInstance == null) {
            synchronized (ApiClient.class) {
                if (retrofitInstance == null) {
                    SessionManager session = new SessionManager(context);

                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                    logging.setLevel(
                        BuildConfig.DEBUG
                            ? HttpLoggingInterceptor.Level.BASIC
                            : HttpLoggingInterceptor.Level.NONE
                    );

                    OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(45, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .addInterceptor(chain -> {
                            Request original = chain.request();
                            String token = session.getAccessToken();
                            if (token == null || token.isEmpty()) {
                                return chain.proceed(original);
                            }
                            Request authed = original
                                .newBuilder()
                                .header("Authorization", "Bearer " + token)
                                .build();
                            return chain.proceed(authed);
                        })
                        .addInterceptor(logging)
                        .build();

                    retrofitInstance = new Retrofit.Builder()
                        .baseUrl(BuildConfig.BASE_URL)
                        .client(client)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
                }
            }
        }
        return retrofitInstance;
    }

    public static AuthApiService authApi(Context context) {
        return getInstance(context).create(AuthApiService.class);
    }

    public static CustomerApiService customerApi(Context context) {
        return getInstance(context).create(CustomerApiService.class);
    }

    public static OfficerApiService officerApi(Context context) {
        return getInstance(context).create(OfficerApiService.class);
    }
}
