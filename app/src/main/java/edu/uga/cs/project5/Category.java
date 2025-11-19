package edu.uga.cs.project5;

public class Category {
    public String id;
    public String name;
    public String createdBy;

    public String createdByName;
    public long createdAt; // server timestamp saved as long

    public Category() { } // required for Firebase

    public Category(String id, String name, String createdBy, long createdAt, String createdByName) {
        this.id = id;
        this.name = name;
        this.createdBy = createdBy;
        this.createdByName = createdByName;
        this.createdAt = createdAt;
    }
}
