package com.agroshop.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomerForm {

    @NotBlank(message = "Customer name is required")
    @Size(max = 150, message = "Name must be 150 characters or fewer")
    private String name;

    @NotBlank(message = "Phone number is required")
    @Size(max = 14, message = "Phone number is too long")
    @Pattern(
            regexp = "^(?:\\+91[ -]?)?[6-9][0-9]{9}$",
            message = "Enter a valid Indian mobile number (10 digits, optionally prefixed with +91)")
    private String phone;

    @NotBlank(message = "Village is required")
    @Size(max = 100, message = "Village must be 100 characters or fewer")
    private String village;

    @Size(max = 300, message = "Address must be 300 characters or fewer")
    private String address;

    @Size(max = 1000, message = "Notes must be 1000 characters or fewer")
    private String notes;

    public static CustomerForm from(Customer customer) {
        CustomerForm form = new CustomerForm();
        form.name = customer.getName();
        form.phone = customer.getPhone();
        form.village = customer.getVillage();
        form.address = customer.getAddress();
        form.notes = customer.getNotes();
        return form;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = trimToNull(name);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = trimToNull(phone);
    }

    public String getVillage() {
        return village;
    }

    public void setVillage(String village) {
        this.village = trimToNull(village);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = trimToNull(address);
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = trimToNull(notes);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
