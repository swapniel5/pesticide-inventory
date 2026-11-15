-- Run against agroshop_demo after demo-data.sql.
SELECT
    (SELECT COUNT(*) FROM products
     WHERE brand = 'AgroShop Demo Catalogue') AS products,
    (SELECT COUNT(*) FROM customers
     WHERE notes LIKE '[AGROSHOP DEMO DATA]%') AS customers,
    (SELECT COUNT(*) FROM sales
     WHERE request_key LIKE 'AGROSHOP-DEMO-V1-SALE-%') AS invoices,
    (SELECT COUNT(*) FROM sale_items item
     JOIN sales sale ON sale.id = item.sale_id
     WHERE sale.request_key LIKE 'AGROSHOP-DEMO-V1-SALE-%') AS sale_items,
    (SELECT COUNT(*) FROM payments
     WHERE notes LIKE '[AGROSHOP DEMO DATA]%') AS payments,
    (SELECT COALESCE(SUM(total_amount), 0)::numeric(14, 2)
     FROM sales WHERE request_key LIKE 'AGROSHOP-DEMO-V1-SALE-%') AS total_sales,
    (SELECT COALESCE(SUM(amount), 0)::numeric(14, 2)
     FROM payments WHERE notes LIKE '[AGROSHOP DEMO DATA]%') AS total_payments,
    (SELECT COALESCE(SUM(sale.total_amount - COALESCE(paid.amount, 0)), 0)::numeric(14, 2)
     FROM sales sale
     LEFT JOIN (
         SELECT allocation.sale_id, SUM(allocation.allocated_amount) AS amount
         FROM payment_allocations allocation
         GROUP BY allocation.sale_id
     ) paid ON paid.sale_id = sale.id
     WHERE sale.request_key LIKE 'AGROSHOP-DEMO-V1-SALE-%') AS outstanding_credit,
    (SELECT COUNT(*) FROM products
     WHERE brand = 'AgroShop Demo Catalogue'
       AND active
       AND quantity <= low_stock_threshold) AS low_stock_products,
    (SELECT COUNT(*) FROM products
     WHERE brand = 'AgroShop Demo Catalogue'
       AND quantity < 0) AS negative_stock_products;

-- This should return no rows: every demo payment is fully allocated.
SELECT payment.id, payment.transaction_reference, payment.amount,
       COALESCE(SUM(allocation.allocated_amount), 0) AS allocated
FROM payments payment
LEFT JOIN payment_allocations allocation ON allocation.payment_id = payment.id
WHERE payment.notes LIKE '[AGROSHOP DEMO DATA]%'
GROUP BY payment.id, payment.transaction_reference, payment.amount
HAVING COALESCE(SUM(allocation.allocated_amount), 0) <> payment.amount;

-- Demo customers who still have a positive balance.
WITH paid_by_sale AS (
    SELECT allocation.sale_id, SUM(allocation.allocated_amount) AS amount
    FROM payment_allocations allocation
    GROUP BY allocation.sale_id
)
SELECT customer.name,
       customer.phone,
       customer.village,
       SUM(sale.total_amount - COALESCE(paid_by_sale.amount, 0))::numeric(14, 2)
           AS outstanding_balance
FROM customers customer
JOIN sales sale ON sale.customer_id = customer.id
LEFT JOIN paid_by_sale ON paid_by_sale.sale_id = sale.id
WHERE customer.notes LIKE '[AGROSHOP DEMO DATA]%'
  AND sale.request_key LIKE 'AGROSHOP-DEMO-V1-SALE-%'
  AND sale.status = 'COMPLETED'
GROUP BY customer.id, customer.name, customer.phone, customer.village
HAVING SUM(sale.total_amount - COALESCE(paid_by_sale.amount, 0)) > 0
ORDER BY outstanding_balance DESC, customer.name;
