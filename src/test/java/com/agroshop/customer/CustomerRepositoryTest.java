package com.agroshop.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void searchesCustomersByNameIgnoringCase() {
        saveCustomers();

        assertThat(search("asha")).extracting(Customer::getName).containsExactly("Asha Devi");
    }

    @Test
    void searchesCustomersByPhone() {
        saveCustomers();

        assertThat(search("912345")).extracting(Customer::getName).containsExactly("Meera Devi");
    }

    @Test
    void searchesCustomersByVillageIgnoringCase() {
        saveCustomers();

        assertThat(search("rampur")).extracting(Customer::getName).containsExactly("Asha Devi");
    }

    private List<Customer> search(String term) {
        return customerRepository
                .findByNameContainingIgnoreCaseOrPhoneContainingIgnoreCaseOrVillageContainingIgnoreCaseOrderByNameAsc(
                        term, term, term);
    }

    private void saveCustomers() {
        customerRepository.saveAll(List.of(
                new Customer("Asha Devi", "9876543210", "Rampur", null, null),
                new Customer("Meera Devi", "9123456789", "Sundarpur", null, null)));
    }
}
