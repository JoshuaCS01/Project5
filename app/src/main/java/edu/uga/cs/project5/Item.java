// Item.java
package edu.uga.cs.project5;

public class Item {
    public String id;
    public String title;
    public String description;
    public Long createdAt = 0L;
    public Boolean isFree = false;
    public Long priceCents;
    public String authorId;
    public Boolean available = true;
    public String categoryId; // may be present if single-category creation existed

    public String createdByName;

    public Item() {}
}
