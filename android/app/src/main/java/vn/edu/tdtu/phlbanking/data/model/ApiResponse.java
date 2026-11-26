package vn.edu.tdtu.phlbanking.data.model;

public class ApiResponse<T> {

    public boolean success;
    public String message;
    public T data;
    public ApiError error;

    public static class ApiError {

        public String code;
        public String message;
    }
}
