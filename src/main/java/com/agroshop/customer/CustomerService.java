package com.agroshop.customer;

import java.util.List;

import com.agroshop.payment.PaymentAllocationRepository;
import com.agroshop.sales.SaleRepository;
import com.agroshop.sales.SaleStatus;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;

    public CustomerService(
            CustomerRepository customerRepository,
            SaleRepository saleRepository,
            PaymentAllocationRepository paymentAllocationRepository) {
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
    }

    public Customer createCustomer(CustomerForm form) {
        Customer customer = new Customer(
                form.getName().trim(),
                form.getPhone().trim(),
                form.getVillage().trim(),
                trimToNull(form.getAddress()),
                trimToNull(form.getNotes()));
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public List<Customer> searchCustomers(String search) {
        if (StringUtils.hasText(search)) {
            String term = search.trim();
            return customerRepository
                    .findByNameContainingIgnoreCaseOrPhoneContainingIgnoreCaseOrVillageContainingIgnoreCaseOrderByNameAsc(
                            term, term, term);
        }
        return customerRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Transactional(readOnly = true)
    public Customer getCustomer(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse getCustomerProfile(Long id) {
        Customer customer = getCustomer(id);
        List<CustomerPurchaseHistoryItem> purchaseHistory =
                saleRepository.findCustomerPurchaseHistory(id, SaleStatus.COMPLETED);
        CustomerFinancialSummary financialSummary = CustomerFinancialSummary.from(
                saleRepository.sumCompletedSalesForCustomer(id, SaleStatus.COMPLETED),
                paymentAllocationRepository.sumValidAllocatedPaymentsForCustomer(id, SaleStatus.COMPLETED));
        return new CustomerProfileResponse(CustomerResponse.from(customer), purchaseHistory, financialSummary);
    }

    @Transactional(readOnly = true)
    public List<Customer> findActiveCustomers() {
        return customerRepository.findByActiveTrueOrderByNameAsc();
    }

    public Customer updateCustomer(Long id, CustomerForm form) {
        Customer customer = getCustomer(id);
        customer.updateDetails(
                form.getName().trim(),
                form.getPhone().trim(),
                form.getVillage().trim(),
                trimToNull(form.getAddress()),
                trimToNull(form.getNotes()));
        return customerRepository.save(customer);
    }

    public Customer deactivateCustomer(Long id) {
        Customer customer = getCustomer(id);
        customer.deactivate();
        return customerRepository.save(customer);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
