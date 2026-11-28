package vn.edu.tdtu.phlbanking.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CustomerModels {

    public static class BankAccountItem {

        public long id;
        public String accountNumber;
        public String accountType;
        public long currentBalance;
        public long availableBalance;
        public String status;
        public Integer savingsTermMonths;
        public Double savingsInterestRate;
        public String maturityDate;
        public String productType;
        public Long accruedInterest;
        public Long loanAmount;
        public Long remainingPrincipal;
        public Double loanInterestRate;
        public String repaymentCycle;
        public String collateralInfo;
    }

    // Pagination metadata is at the same level as success and data in /accounts/history.
    public static class HistoryPage extends ApiResponse<List<HistoryItem>> {

        public int limit;
        public int offset;
        public boolean hasMore;
    }

    public static class HistoryItem {

        public long fluctuationId;
        public long changeAmount;
        public long balanceBefore;
        public long balanceAfter;
        public String direction;
        public String timestamp;
        public String accountNumber;
        public String traceId;
        public long amount;
        public long fee;
        public String content;
        public String transactionType;
        public String toBankCode;
        public String toAccName;
    }

    public static class MortgageLoanItem {

        public long id;
        public long bankAccountId;
        public String accountNumber;
        public long loanAmount;
        public long remainingPrincipal;
        public double interestRate;
        public int termMonths;
        public String repaymentCycle;
        public String collateralInfo;
        public List<RepaymentItem> schedules;
    }

    public static class RepaymentItem {

        public int periodNo;
        public String dueDate;
        public long principalAmount;
        public long interestAmount;
        public long totalAmount;
        public String status;
    }

    public static class TransactionResult {

        public boolean idempotentReplay;
        public TransactionRecord transaction;
    }

    public static class TransactionRecord {

        public long id;
        public String traceId;
        public long amount;
        public long fee;
        public String content;
        public String type;
        public String status;
        public String toBankCode;
        public String toAccName;
        public String createdAt;
    }

    public static class BranchItem {

        @SerializedName("id") public long id;
        @SerializedName("branchName") public String branchName;
        @SerializedName("address") public String address;
        // Missing coordinates must not be treated as a real point at (0, 0).
        @SerializedName("latitude") public double latitude = Double.NaN;
        @SerializedName("longitude") public double longitude = Double.NaN;
        @SerializedName("phone") public String phone;
        @SerializedName("distanceMeters") public int distanceMeters = -1;
        @SerializedName("estimatedWalkMinutes") public int estimatedWalkMinutes = -1;
        @SerializedName("googleMapsUrl") public String googleMapsUrl;
    }

    public static class BranchesResponse {

        @SerializedName("branches") public List<BranchItem> branches;
        @SerializedName("nearest") public BranchItem nearest;
        @SerializedName("walkingRoute") public WalkingRoute walkingRoute;
    }

    public static class WalkingRoute {

        @SerializedName("mode") public String mode;
        @SerializedName("algorithm") public String algorithm;
        @SerializedName("note") public String note;
        @SerializedName("distanceMeters") public int distanceMeters;
        @SerializedName("estimatedMinutes") public int estimatedMinutes;
        @SerializedName("waypoints") public List<Waypoint> waypoints;
        @SerializedName("googleMapsUrl") public String googleMapsUrl;
    }

    public static class Waypoint {

        @SerializedName("lat") public double lat = Double.NaN;
        @SerializedName("lng") public double lng = Double.NaN;
        @SerializedName("label") public String label;
    }

    public static class AiChatResponse {

        public String reply;
        public SuggestedAction suggestedAction;
        public String source;
    }

    public static class SuggestedAction {

        public String label;
        public String route;
        public String actionToken;
    }

    public static class NotificationsResponse {

        public List<HistoryItem> balanceAlerts;
        public List<PolicyAlert> policyAlerts;
    }

    public static class PolicyAlert {

        public String id;
        public String title;
        public String message;
        public String publishedAt;
    }
}
