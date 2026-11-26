package vn.edu.tdtu.phlbanking.data.model;

public class LoginData {

    public String accessToken;
    public String refreshToken;
    public UserInfo user;

    public static class UserInfo {

        public long id;
        public String username;
        public String role;
        public ProfileInfo profile;
    }

    public static class ProfileInfo {

        public long id;
        public String fullName;
        public String cifNumber;
        public String employeeCode;
        public String email;
        public String phone;
        public String eKYCStatus;
    }
}
