package edu.uga.cs.project5;

// Item.java
public class Item {
    public String id;
    public String title;
    public String description;
    public Long createdAt = 0L;
    public Boolean isFree = false;
    public Long priceCents;
    public String authorId;
    public Boolean available = true;
    public String categoryId; // if you store mapping

    public String createdByName;
    public String category;   // filled when loading items with category name

    public Item() {}
}

