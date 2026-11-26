package vn.edu.tdtu.phlbanking.data.model;

public class OfficerModels {

    public static class CustomerItem {

        public long customerId;
        public long userId;
        public String cifNumber;
        public String fullName;
        public String idCardNumber;
        public String dob;
        public String phone;
        public String email;
        public String address;
        public String eKYCStatus;
        public String username;
        public String accountStatus;
        public int totalAccounts;
    }

    public static class AuditLogItem {

        public long id;
        public long actorId;
        public String actorRole;
        public String actorUsername;
        public String action;
        public String entityName;
        public Long entityId;
        public String timestamp;
        public Object oldValue;
        public Object newValue;
    }
}
