-- Seed fictional AgroShop data into the dedicated agroshop_demo database only.
-- Run with psql --set ON_ERROR_STOP=1 so any error aborts the transaction.
BEGIN;

DO $guard$
BEGIN
    IF current_database() <> 'agroshop_demo' THEN
        RAISE EXCEPTION 'Refusing demo seed: connected database must be agroshop_demo (current: %)',
            current_database();
    END IF;

    IF to_regclass('sales_id_seq') IS NULL THEN
        RAISE EXCEPTION 'sales_id_seq is missing; start the application once with demo startup seeding disabled to create the schema';
    END IF;

    IF EXISTS (SELECT 1 FROM products WHERE brand = 'AgroShop Demo Catalogue')
       OR EXISTS (SELECT 1 FROM customers WHERE notes LIKE '[AGROSHOP DEMO DATA]%')
       OR EXISTS (SELECT 1 FROM sales WHERE request_key LIKE 'AGROSHOP-DEMO-V1-SALE-%')
       OR EXISTS (SELECT 1 FROM payments WHERE notes LIKE '[AGROSHOP DEMO DATA]%')
       OR EXISTS (SELECT 1 FROM payments WHERE request_key LIKE 'AGROSHOP-DEMO-V1-PAYMENT-%') THEN
        RAISE EXCEPTION 'Demo-looking records already exist; refusing to duplicate or overwrite data';
    END IF;
END;
$guard$;

CREATE TABLE IF NOT EXISTS agroshop_demo_records (
    entity_type varchar(24) NOT NULL,
    entity_id bigint NOT NULL,
    PRIMARY KEY (entity_type, entity_id)
);

CREATE TEMP TABLE demo_product_seed (
    ordinal integer PRIMARY KEY,
    name varchar(150) NOT NULL UNIQUE,
    unit varchar(50) NOT NULL,
    opening_quantity numeric(12, 3) NOT NULL,
    selling_price numeric(12, 2) NOT NULL,
    low_stock_threshold numeric(12, 3) NOT NULL,
    product_id bigint
) ON COMMIT DROP;

INSERT INTO demo_product_seed
    (ordinal, name, unit, opening_quantity, selling_price, low_stock_threshold)
VALUES
    (1,  'Demo Crop Care Product 01',     'bottle',  22.000, 135.00, 18.500),
    (2,  'Demo Crop Care Product 02',     'packet',  22.000, 210.00, 18.500),
    (3,  'Demo Crop Care Product 03',     'litre',   22.000, 385.00, 18.500),
    (4,  'Demo Crop Care Product 04',     'kg',      22.000, 165.00, 18.500),
    (5,  'Demo Orchard Care Product 05',  'bottle', 250.000, 275.00, 20.000),
    (6,  'Demo Orchard Care Product 06',  'packet', 250.000,  95.00, 20.000),
    (7,  'Demo Orchard Care Product 07',  'litre',  250.000, 520.00, 20.000),
    (8,  'Demo Orchard Care Product 08',  'kg',     250.000, 180.00, 20.000),
    (9,  'Demo Field Care Product 09',    'bottle', 250.000, 325.00, 20.000),
    (10, 'Demo Field Care Product 10',    'packet', 250.000, 120.00, 20.000),
    (11, 'Demo Field Care Product 11',    'litre',  250.000, 640.00, 20.000),
    (12, 'Demo Field Care Product 12',    'kg',     250.000, 235.00, 20.000),
    (13, 'Demo Plant Care Product 13',    'bottle', 250.000, 155.00, 20.000),
    (14, 'Demo Plant Care Product 14',    'packet', 250.000, 290.00, 20.000),
    (15, 'Demo Plant Care Product 15',    'litre',  250.000, 455.00, 20.000),
    (16, 'Demo Plant Care Product 16',    'kg',     250.000, 195.00, 20.000),
    (17, 'Demo Farm Care Product 17',     'bottle', 250.000, 345.00, 20.000),
    (18, 'Demo Farm Care Product 18',     'packet', 250.000, 110.00, 20.000),
    (19, 'Demo Farm Care Product 19',     'litre',  250.000, 575.00, 20.000),
    (20, 'Demo Farm Care Product 20',     'kg',     250.000, 225.00, 20.000);

WITH inserted AS (
    INSERT INTO products
        (name, brand, unit, quantity, selling_price, low_stock_threshold, active, created_at, updated_at)
    SELECT name, 'AgroShop Demo Catalogue', unit, opening_quantity, selling_price,
           low_stock_threshold, true, LOCALTIMESTAMP, LOCALTIMESTAMP
    FROM demo_product_seed
    ORDER BY ordinal
    RETURNING id, name
)
UPDATE demo_product_seed seed
SET product_id = inserted.id
FROM inserted
WHERE inserted.name = seed.name;

INSERT INTO stock_movements (product_id, movement_type, quantity, remarks, created_at)
SELECT product_id, 'OPENING', opening_quantity,
       '[AGROSHOP DEMO DATA] Demo opening stock', LOCALTIMESTAMP - INTERVAL '31 days'
FROM demo_product_seed
ORDER BY ordinal;

CREATE TEMP TABLE demo_customer_seed (
    ordinal integer PRIMARY KEY,
    name varchar(150) NOT NULL UNIQUE,
    phone varchar(14) NOT NULL UNIQUE,
    village varchar(100) NOT NULL,
    customer_id bigint
) ON COMMIT DROP;

INSERT INTO demo_customer_seed (ordinal, name, phone, village)
VALUES
    (1,  'Demo Aarav Patil',        '0000000001', 'Niphad'),
    (2,  'Demo Meera Shinde',       '0000000002', 'Dindori'),
    (3,  'Demo Vihaan Pawar',       '0000000003', 'Sinnar'),
    (4,  'Demo Anaya Jadhav',       '0000000004', 'Yeola'),
    (5,  'Demo Ishaan Deshmukh',    '0000000005', 'Igatpuri'),
    (6,  'Demo Saanvi More',        '0000000006', 'Chandwad'),
    (7,  'Demo Aditya Borse',       '0000000007', 'Kalwan'),
    (8,  'Demo Myra Chavan',        '0000000008', 'Deola'),
    (9,  'Demo Reyansh Nikam',      '0000000009', 'Trimbakeshwar'),
    (10, 'Demo Aadhya Gaikwad',     '0000000010', 'Baglan'),
    (11, 'Demo Rohan Ahire',        '0000000011', 'Surgana'),
    (12, 'Demo Gauri Mahale',       '0000000012', 'Peth'),
    (13, 'Demo Kabir Sonawane',     '0000000013', 'Lasalgaon'),
    (14, 'Demo Ira Wagh',           '0000000014', 'Ozar'),
    (15, 'Demo Arjun Kharat',       '0000000015', 'Pimpalgaon Baswant'),
    (16, 'Demo Diya Bhamre',        '0000000016', 'Nandgaon'),
    (17, 'Demo Vedant Boraste',     '0000000017', 'Satana'),
    (18, 'Demo Riya Nikam',         '0000000018', 'Ghoti'),
    (19, 'Demo Dhruv Suryawanshi',  '0000000019', 'Vinchur'),
    (20, 'Demo Tara Pawar',         '0000000020', 'Niphad');

WITH inserted AS (
    INSERT INTO customers
        (name, phone, village, address, notes, active, created_at, updated_at)
    SELECT name, phone, village, 'Demo Lane, ' || village,
           '[AGROSHOP DEMO DATA] Fictional test profile; not a real person or phone number. Do not contact.',
           true, LOCALTIMESTAMP, LOCALTIMESTAMP
    FROM demo_customer_seed
    ORDER BY ordinal
    RETURNING id, name
)
UPDATE demo_customer_seed seed
SET customer_id = inserted.id
FROM inserted
WHERE inserted.name = seed.name;

CREATE TEMP TABLE demo_sales (
    ordinal integer PRIMARY KEY,
    customer_ordinal integer NOT NULL,
    sale_id bigint,
    invoice_total numeric(14, 2),
    initial_payment numeric(14, 2),
    created_at timestamp NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_sales (ordinal, customer_ordinal, created_at)
SELECT sale_number,
       ((sale_number - 1) % 20) + 1,
       LOCALTIMESTAMP - make_interval(days => ((sale_number * 7) % 30))
FROM generate_series(1, 30) AS generated(sale_number);

CREATE TEMP TABLE demo_sale_items (
    sale_ordinal integer NOT NULL,
    line_number integer NOT NULL,
    product_ordinal integer NOT NULL,
    quantity numeric(12, 3) NOT NULL,
    unit_price numeric(12, 2) NOT NULL,
    line_total numeric(14, 2) NOT NULL,
    PRIMARY KEY (sale_ordinal, line_number)
) ON COMMIT DROP;

INSERT INTO demo_sale_items
    (sale_ordinal, line_number, product_ordinal, quantity, unit_price, line_total)
SELECT sale.ordinal,
       line.line_number,
       (((sale.ordinal - 1) * 3 + (line.line_number - 1) * 7) % 20) + 1,
       (1.000 + 0.500 * (((sale.ordinal - 1) + (line.line_number - 1)) % 3))::numeric(12, 3),
       product.selling_price,
       ROUND(
           (1.000 + 0.500 * (((sale.ordinal - 1) + (line.line_number - 1)) % 3))
               * product.selling_price,
           2)
FROM demo_sales sale
CROSS JOIN LATERAL generate_series(1, 1 + ((sale.ordinal - 1) % 4)) AS line(line_number)
JOIN demo_product_seed product
  ON product.ordinal = (((sale.ordinal - 1) * 3 + (line.line_number - 1) * 7) % 20) + 1;

WITH totals AS (
    SELECT sale_ordinal, SUM(line_total)::numeric(14, 2) AS total
    FROM demo_sale_items
    GROUP BY sale_ordinal
)
UPDATE demo_sales sale
SET invoice_total = totals.total,
    initial_payment = CASE (sale.ordinal - 1) % 4
        WHEN 0 THEN totals.total
        WHEN 1 THEN ROUND(totals.total * 0.40, 2)
        WHEN 2 THEN 0.00
        ELSE ROUND(totals.total * 0.30, 2)
    END
FROM totals
WHERE totals.sale_ordinal = sale.ordinal;

WITH generated_ids AS (
    SELECT ordinal, nextval('sales_id_seq') AS sale_id
    FROM demo_sales
    ORDER BY ordinal
)
UPDATE demo_sales sale
SET sale_id = generated_ids.sale_id
FROM generated_ids
WHERE generated_ids.ordinal = sale.ordinal;

INSERT INTO sales
    (id, invoice_number, request_key, customer_id, total_amount, status, created_at, updated_at)
SELECT sale.sale_id,
       'INV-' || EXTRACT(YEAR FROM CURRENT_DATE)::integer || '-' || LPAD(sale.sale_id::text, 4, '0'),
       'AGROSHOP-DEMO-V1-SALE-' || LPAD(sale.ordinal::text, 2, '0'),
       customer.customer_id,
       sale.invoice_total,
       'COMPLETED',
       sale.created_at,
       sale.created_at
FROM demo_sales sale
JOIN demo_customer_seed customer ON customer.ordinal = sale.customer_ordinal
ORDER BY sale.ordinal;

INSERT INTO sale_items
    (sale_id, product_id, product_name_snapshot, quantity, unit_price, line_total)
SELECT sale.sale_id,
       product.product_id,
       product.name,
       item.quantity,
       item.unit_price,
       item.line_total
FROM demo_sale_items item
JOIN demo_sales sale ON sale.ordinal = item.sale_ordinal
JOIN demo_product_seed product ON product.ordinal = item.product_ordinal
ORDER BY item.sale_ordinal, item.line_number;

INSERT INTO stock_movements (product_id, movement_type, quantity, remarks, created_at)
SELECT product.product_id,
       'SALE',
       item.quantity,
       '[AGROSHOP DEMO DATA] Sale INV-' || EXTRACT(YEAR FROM CURRENT_DATE)::integer || '-'
           || LPAD(sale.sale_id::text, 4, '0'),
       sale.created_at
FROM demo_sale_items item
JOIN demo_sales sale ON sale.ordinal = item.sale_ordinal
JOIN demo_product_seed product ON product.ordinal = item.product_ordinal
ORDER BY item.sale_ordinal, item.line_number;

WITH sold AS (
    SELECT item.product_ordinal, SUM(item.quantity)::numeric(12, 3) AS quantity
    FROM demo_sale_items item
    GROUP BY item.product_ordinal
)
UPDATE products product
SET quantity = product.quantity - sold.quantity,
    updated_at = LOCALTIMESTAMP
FROM demo_product_seed seed
JOIN sold ON sold.product_ordinal = seed.ordinal
WHERE product.id = seed.product_id;

CREATE TEMP TABLE demo_payment_plan (
    payment_key varchar(40) PRIMARY KEY,
    payment_id bigint,
    customer_ordinal integer NOT NULL,
    amount numeric(14, 2) NOT NULL,
    payment_date timestamp NOT NULL,
    payment_method varchar(20) NOT NULL,
    transaction_reference varchar(120) NOT NULL UNIQUE,
    notes varchar(500) NOT NULL,
    request_key varchar(64),
    initial_sale_ordinal integer
) ON COMMIT DROP;

CREATE TEMP TABLE demo_payment_allocation_plan (
    payment_key varchar(40) NOT NULL,
    sale_ordinal integer NOT NULL,
    allocated_amount numeric(14, 2) NOT NULL,
    PRIMARY KEY (payment_key, sale_ordinal)
) ON COMMIT DROP;

INSERT INTO demo_payment_plan
    (payment_key, customer_ordinal, amount, payment_date, payment_method,
     transaction_reference, notes, request_key, initial_sale_ordinal)
SELECT 'initial-' || LPAD(sale.ordinal::text, 2, '0'),
       sale.customer_ordinal,
       sale.initial_payment,
       sale.created_at,
       CASE WHEN (sale.ordinal - 1) % 2 = 0 THEN 'CASH' ELSE 'UPI' END,
       'DEMO-INITIAL-' || LPAD(sale.ordinal::text, 2, '0'),
       '[AGROSHOP DEMO DATA] Synthetic initial invoice payment; not a real transaction.',
       NULL,
       sale.ordinal
FROM demo_sales sale
WHERE sale.initial_payment > 0;

INSERT INTO demo_payment_allocation_plan (payment_key, sale_ordinal, allocated_amount)
SELECT payment.payment_key, payment.initial_sale_ordinal, payment.amount
FROM demo_payment_plan payment
WHERE payment.initial_sale_ordinal IS NOT NULL;

DO $subsequent_payments$
DECLARE
    customer_number integer;
    payment_number integer := 0;
    first_sale integer;
    second_sale integer;
    first_outstanding numeric(14, 2);
    second_outstanding numeric(14, 2);
    first_amount numeric(14, 2);
    first_to_first_invoice numeric(14, 2);
    first_to_second_invoice numeric(14, 2);
    remaining_first numeric(14, 2);
    remaining_second numeric(14, 2);
    second_amount numeric(14, 2);
    second_to_first_invoice numeric(14, 2);
    second_to_second_invoice numeric(14, 2);
    final_amount numeric(14, 2);
BEGIN
    FOREACH customer_number IN ARRAY ARRAY[2, 3, 4, 6, 7, 8, 10] LOOP
        first_sale := customer_number;
        second_sale := customer_number + 20;

        SELECT invoice_total - initial_payment
        INTO first_outstanding
        FROM demo_sales WHERE ordinal = first_sale;
        SELECT invoice_total - initial_payment
        INTO second_outstanding
        FROM demo_sales WHERE ordinal = second_sale;

        IF first_outstanding <= 0 OR second_outstanding <= 0 THEN
            RAISE EXCEPTION 'Expected two outstanding invoices for demo customer ordinal %',
                customer_number;
        END IF;

        first_amount := first_outstanding + ROUND(second_outstanding * 0.25, 2);
        payment_number := payment_number + 1;
        INSERT INTO demo_payment_plan
            (payment_key, customer_ordinal, amount, payment_date, payment_method,
             transaction_reference, notes, request_key)
        VALUES (
            'subsequent-' || LPAD(payment_number::text, 2, '0'),
            customer_number,
            first_amount,
            LOCALTIMESTAMP,
            CASE WHEN payment_number % 2 = 1 THEN 'CASH' ELSE 'UPI' END,
            'DEMO-PAY-' || LPAD(payment_number::text, 2, '0'),
            '[AGROSHOP DEMO DATA] Synthetic subsequent payment; not a real transaction.',
            'AGROSHOP-DEMO-V1-PAYMENT-' || LPAD(payment_number::text, 2, '0')
        );

        first_to_first_invoice := LEAST(first_outstanding, first_amount);
        first_to_second_invoice := first_amount - first_to_first_invoice;
        INSERT INTO demo_payment_allocation_plan
            (payment_key, sale_ordinal, allocated_amount)
        VALUES
            ('subsequent-' || LPAD(payment_number::text, 2, '0'),
             first_sale, first_to_first_invoice),
            ('subsequent-' || LPAD(payment_number::text, 2, '0'),
             second_sale, first_to_second_invoice);

        remaining_first := first_outstanding - first_to_first_invoice;
        -- The first payment applies its excess only to the second invoice.
        remaining_second := second_outstanding - first_to_second_invoice;

        second_amount := ROUND((remaining_first + remaining_second) * 0.25, 2);
        payment_number := payment_number + 1;
        INSERT INTO demo_payment_plan
            (payment_key, customer_ordinal, amount, payment_date, payment_method,
             transaction_reference, notes, request_key)
        VALUES (
            'subsequent-' || LPAD(payment_number::text, 2, '0'),
            customer_number,
            second_amount,
            LOCALTIMESTAMP,
            CASE WHEN payment_number % 2 = 1 THEN 'CASH' ELSE 'UPI' END,
            'DEMO-PAY-' || LPAD(payment_number::text, 2, '0'),
            '[AGROSHOP DEMO DATA] Synthetic subsequent payment; not a real transaction.',
            'AGROSHOP-DEMO-V1-PAYMENT-' || LPAD(payment_number::text, 2, '0')
        );

        second_to_first_invoice := LEAST(remaining_first, second_amount);
        second_to_second_invoice := second_amount - second_to_first_invoice;
        INSERT INTO demo_payment_allocation_plan
            (payment_key, sale_ordinal, allocated_amount)
        VALUES
            ('subsequent-' || LPAD(payment_number::text, 2, '0'),
             first_sale, second_to_first_invoice),
            ('subsequent-' || LPAD(payment_number::text, 2, '0'),
             second_sale, second_to_second_invoice);

        remaining_first := remaining_first - second_to_first_invoice;
        remaining_second := remaining_second - second_to_second_invoice;

        IF customer_number IN (3, 7, 10) THEN
            final_amount := remaining_first + remaining_second;
            IF final_amount > 0 THEN
                payment_number := payment_number + 1;
                INSERT INTO demo_payment_plan
                    (payment_key, customer_ordinal, amount, payment_date, payment_method,
                     transaction_reference, notes, request_key)
                VALUES (
                    'subsequent-' || LPAD(payment_number::text, 2, '0'),
                    customer_number,
                    final_amount,
                    LOCALTIMESTAMP,
                    CASE WHEN payment_number % 2 = 1 THEN 'CASH' ELSE 'UPI' END,
                    'DEMO-PAY-' || LPAD(payment_number::text, 2, '0'),
                    '[AGROSHOP DEMO DATA] Synthetic subsequent payment; not a real transaction.',
                    'AGROSHOP-DEMO-V1-PAYMENT-' || LPAD(payment_number::text, 2, '0')
                );

                IF remaining_first > 0 THEN
                    INSERT INTO demo_payment_allocation_plan
                        (payment_key, sale_ordinal, allocated_amount)
                    VALUES
                        ('subsequent-' || LPAD(payment_number::text, 2, '0'),
                         first_sale, remaining_first);
                END IF;
                IF remaining_second > 0 THEN
                    INSERT INTO demo_payment_allocation_plan
                        (payment_key, sale_ordinal, allocated_amount)
                    VALUES
                        ('subsequent-' || LPAD(payment_number::text, 2, '0'),
                         second_sale, remaining_second);
                END IF;
            END IF;
        END IF;
    END LOOP;

    IF payment_number <> 17 THEN
        RAISE EXCEPTION 'Expected 17 subsequent payments; generated %', payment_number;
    END IF;
END;
$subsequent_payments$;

WITH inserted AS (
    INSERT INTO payments
        (customer_id, amount, payment_method, payment_date, transaction_reference,
         notes, request_key)
    SELECT customer.customer_id,
           payment.amount,
           payment.payment_method,
           payment.payment_date,
           payment.transaction_reference,
           payment.notes,
           payment.request_key
    FROM demo_payment_plan payment
    JOIN demo_customer_seed customer ON customer.ordinal = payment.customer_ordinal
    ORDER BY payment.payment_key
    RETURNING id, transaction_reference
)
UPDATE demo_payment_plan payment
SET payment_id = inserted.id
FROM inserted
WHERE inserted.transaction_reference = payment.transaction_reference;

INSERT INTO payment_allocations (payment_id, sale_id, allocated_amount)
SELECT payment.payment_id, sale.sale_id, allocation.allocated_amount
FROM demo_payment_allocation_plan allocation
JOIN demo_payment_plan payment USING (payment_key)
JOIN demo_sales sale ON sale.ordinal = allocation.sale_ordinal
WHERE allocation.allocated_amount > 0
ORDER BY payment.payment_key, allocation.sale_ordinal;

UPDATE customers customer
SET active = false,
    updated_at = LOCALTIMESTAMP
FROM demo_customer_seed seed
WHERE customer.id = seed.customer_id
  AND seed.ordinal IN (19, 20);

DO $verify_seed$
DECLARE
    product_count integer;
    customer_count integer;
    sale_count integer;
    item_count integer;
    payment_count integer;
    low_stock_count integer;
    invalid_count integer;
BEGIN
    SELECT COUNT(*) INTO product_count
    FROM products WHERE brand = 'AgroShop Demo Catalogue';
    SELECT COUNT(*) INTO customer_count
    FROM customers WHERE notes LIKE '[AGROSHOP DEMO DATA]%';
    SELECT COUNT(*) INTO sale_count
    FROM sales WHERE request_key LIKE 'AGROSHOP-DEMO-V1-SALE-%';
    SELECT COUNT(*) INTO item_count
    FROM sale_items item
    JOIN sales sale ON sale.id = item.sale_id
    WHERE sale.request_key LIKE 'AGROSHOP-DEMO-V1-SALE-%';
    SELECT COUNT(*) INTO payment_count
    FROM payments WHERE notes LIKE '[AGROSHOP DEMO DATA]%';
    SELECT COUNT(*) INTO low_stock_count
    FROM products
    WHERE brand = 'AgroShop Demo Catalogue'
      AND active
      AND quantity <= low_stock_threshold;

    IF product_count <> 20 OR customer_count <> 20 OR sale_count <> 30
       OR item_count <> 73 OR payment_count <> 40 OR low_stock_count <> 4 THEN
        RAISE EXCEPTION 'Demo count check failed: products %, customers %, sales %, items %, payments %, low-stock %',
            product_count, customer_count, sale_count, item_count, payment_count, low_stock_count;
    END IF;

    SELECT COUNT(*) INTO invalid_count
    FROM products
    WHERE brand = 'AgroShop Demo Catalogue'
      AND quantity < 0;
    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'Demo seed would leave negative product stock';
    END IF;

    SELECT COUNT(*) INTO invalid_count
    FROM (
        SELECT payment.id
        FROM payments payment
        LEFT JOIN payment_allocations allocation ON allocation.payment_id = payment.id
        WHERE payment.notes LIKE '[AGROSHOP DEMO DATA]%'
        GROUP BY payment.id, payment.amount
        HAVING COALESCE(SUM(allocation.allocated_amount), 0) <> payment.amount
    ) unpaid_or_overallocated;
    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'Every demo payment must be fully allocated exactly once';
    END IF;

    SELECT COUNT(*) INTO invalid_count
    FROM (
        SELECT sale.id
        FROM sales sale
        LEFT JOIN payment_allocations allocation ON allocation.sale_id = sale.id
        WHERE sale.request_key LIKE 'AGROSHOP-DEMO-V1-SALE-%'
        GROUP BY sale.id, sale.total_amount
        HAVING COALESCE(SUM(allocation.allocated_amount), 0) > sale.total_amount
    ) overpaid_invoices;
    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'A demo invoice has allocations greater than its total';
    END IF;

    SELECT COUNT(*) INTO invalid_count
    FROM payment_allocations allocation
    JOIN payments payment ON payment.id = allocation.payment_id
    JOIN sales sale ON sale.id = allocation.sale_id
    WHERE payment.notes LIKE '[AGROSHOP DEMO DATA]%'
      AND payment.customer_id <> sale.customer_id;
    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'A demo payment is allocated to another customer';
    END IF;
END;
$verify_seed$;

INSERT INTO agroshop_demo_records (entity_type, entity_id)
SELECT 'PRODUCT', product_id FROM demo_product_seed
UNION ALL
SELECT 'CUSTOMER', customer_id FROM demo_customer_seed
UNION ALL
SELECT 'SALE', sale_id FROM demo_sales
UNION ALL
SELECT 'PAYMENT', payment_id FROM demo_payment_plan;

COMMIT;

SELECT 'Demo seed completed' AS result;
