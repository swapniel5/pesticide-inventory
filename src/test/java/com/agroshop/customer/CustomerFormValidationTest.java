package com.agroshop.customer;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CustomerFormValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void rejectsInvalidIndianMobileNumber() {
        CustomerForm form = validForm();
        form.setPhone("1234567890");

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("phone");
    }

    @Test
    void rejectsMissingMandatoryFields() {
        CustomerForm form = new CustomerForm();

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "phone", "village");
    }

    @Test
    void trimsWhitespaceAndAcceptsCountryCode() {
        CustomerForm form = validForm();
        form.setName("  Asha Devi  ");
        form.setPhone(" +91 9876543210 ");
        form.setVillage("  Rampur  ");

        assertThat(form.getName()).isEqualTo("Asha Devi");
        assertThat(form.getPhone()).isEqualTo("+91 9876543210");
        assertThat(form.getVillage()).isEqualTo("Rampur");
        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void rejectsExcessivelyLongCustomerName() {
        CustomerForm form = validForm();
        form.setName("A".repeat(151));

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    private CustomerForm validForm() {
        CustomerForm form = new CustomerForm();
        form.setName("Asha Devi");
        form.setPhone("9876543210");
        form.setVillage("Rampur");
        return form;
    }
}
