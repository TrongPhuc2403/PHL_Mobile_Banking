package vn.edu.tdtu.phlbanking.ui.common;

import org.json.JSONObject;
import retrofit2.Response;

public final class ApiErrors {

    private ApiErrors() {}

    public static final class Detail {

        public final String code, message, reason;

        Detail(String code, String message, String reason) {
            this.code = code;
            this.message = message;
            this.reason = reason;
        }
    }

    public static Detail detail(Response<?> response, String fallback) {
        try {
            if (response.errorBody() != null) {
                JSONObject json = new JSONObject(response.errorBody().string());
                JSONObject error = json.optJSONObject("error");
                String message =
                    error == null ? json.optString("message") : error.optString("message");
                JSONObject details = error == null ? null : error.optJSONObject("details");
                if (!message.isEmpty()) return new Detail(
                    error == null
                        ? "HTTP_" + response.code()
                        : error.optString("code", "HTTP_" + response.code()),
                    message,
                    details == null ? "" : details.optString("reason")
                );
            }
        } catch (Exception ignored) {}
        return new Detail("HTTP_" + response.code(), fallback + " (" + response.code() + ")", "");
    }

    public static String message(Response<?> response, String fallback) {
        return detail(response, fallback).message;
    }
}
