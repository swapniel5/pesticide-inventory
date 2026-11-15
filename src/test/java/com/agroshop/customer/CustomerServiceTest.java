package com.agroshop.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void createsCustomerWithTrimmedFields() {
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Customer customer = customerService.createCustomer(form("  Asha Devi  ", "9876543210", "  Rampur  "));

        assertEquals("Asha Devi", customer.getName());
        assertEquals("9876543210", customer.getPhone());
        assertEquals("Rampur", customer.getVillage());
    }

    @Test
    void updatesCustomerDetails() {
        Customer existing = customer("Asha Devi", "9876543210", "Rampur");
        when(customerRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer updated = customerService.updateCustomer(4L, form("Meera Devi", "9123456789", "Sundarpur"));

        assertEquals("Meera Devi", updated.getName());
        assertEquals("Sundarpur", updated.getVillage());
    }

    @Test
    void retrievesCustomerById() {
        Customer expected = customer("Asha Devi", "9876543210", "Rampur");
        when(customerRepository.findById(4L)).thenReturn(Optional.of(expected));

        assertSame(expected, customerService.getCustomer(4L));
    }

    @Test
    void throwsNotFoundWhenRetrievingMissingCustomer() {
        when(customerRepository.findById(44L)).thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class, () -> customerService.getCustomer(44L));
    }

    @Test
    void searchesAcrossNamePhoneAndVillage() {
        List<Customer> matches = List.of(customer("Asha Devi", "9876543210", "Rampur"));
        when(customerRepository
                .findByNameContainingIgnoreCaseOrPhoneContainingIgnoreCaseOrVillageContainingIgnoreCaseOrderByNameAsc(
                        "Rampur", "Rampur", "Rampur"))
                .thenReturn(matches);

        assertSame(matches, customerService.searchCustomers(" Rampur "));
    }

    @Test
    void deactivatesCustomerWithoutDeletingIt() {
        Customer existing = customer("Asha Devi", "9876543210", "Rampur");
        when(customerRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer deactivated = customerService.deactivateCustomer(4L);

        assertEquals(false, deactivated.isActive());
        verify(customerRepository).save(existing);
    }

    private CustomerForm form(String name, String phone, String village) {
        CustomerForm form = new CustomerForm();
        form.setName(name);
        form.setPhone(phone);
        form.setVillage(village);
        return form;
    }

    private Customer customer(String name, String phone, String village) {
        return new Customer(name, phone, village, null, null);
    }
}
