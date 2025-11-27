package edu.uga.cs.project5;

public class Transaction {
    public String id;
    public String itemId;
    public String buyerId;
    public String sellerId;
    public String status;    // e.g., "pending", "completed", "cancelled"
    public Long createdAt;

    public String itemTitle;
    public Long completedAt;
    public Long amount;    // in cents

    public Transaction() { } // required for Firebase

    // convenience constructor (optional)
    public Transaction(String itemId, String buyerId, String sellerId, String status, String itemTitle) {
        this.itemId = itemId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.status = status;
        this.itemTitle = itemTitle;
    }
}
