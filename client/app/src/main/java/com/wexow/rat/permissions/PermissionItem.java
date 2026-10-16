package com.wexow.rat.permissions;

public class PermissionItem {
    public String id;
    public String title;
    public String description;
    public boolean required;
    public boolean granted;
    public Runnable opener;
    public java.util.function.Supplier<Boolean> checker;

    public PermissionItem(String id, String title, String description,
                          boolean required, Runnable opener,
                          java.util.function.Supplier<Boolean> checker) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.required = required;
        this.opener = opener;
        this.checker = checker;
    }

    public boolean isGranted() {
        try { return checker.get(); }
        catch (Exception e) { return false; }
    }
}