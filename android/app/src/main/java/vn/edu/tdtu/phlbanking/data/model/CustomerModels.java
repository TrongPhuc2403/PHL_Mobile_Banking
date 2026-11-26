package vn.edu.tdtu.phlbanking.data.model;

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
        public String accountNumber;
        public long loanAmount;
        public long remainingPrincipal;
        public double interestRate;
        public int termMonths;
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

        public long id;
        public String branchName;
        public String address;
        public double latitude;
        public double longitude;
        public String phone;
        public int distanceMeters;
        public int estimatedWalkMinutes;
    }

    public static class BranchesResponse {

        public List<BranchItem> branches;
        public BranchItem nearest;
        public WalkingRoute walkingRoute;
    }

    public static class WalkingRoute {

        public String mode;
        public int distanceMeters;
        public int estimatedMinutes;
    }

    public static class AiChatResponse {

        public String reply;
        public SuggestedAction suggestedAction;
        public String source;
    }

    public static class SuggestedAction {

        public String label;
        public String route;
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
