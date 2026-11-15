(() => {
    const form = document.querySelector('form[data-item-count]');
    if (!form) {
        return;
    }

    const rows = document.getElementById('saleItems');
    const template = document.getElementById('saleItemTemplate');
    const search = document.getElementById('productSearch');
    const amountPaid = document.getElementById('amountPaid');
    const paymentMethod = document.getElementById('paymentMethod');
    let nextIndex = Number(form.dataset.itemCount);

    const parseScaledInteger = (value, scale) => {
        const match = /^(\d*)(?:\.(\d*))?$/.exec(String(value).trim());
        if (!match || (!match[1] && !match[2]) || (match[2] ?? '').length > scale) {
            return null;
        }
        const whole = match[1] || '0';
        const fraction = (match[2] ?? '').padEnd(scale, '0');
        return BigInt(whole) * (10n ** BigInt(scale)) + BigInt(fraction || '0');
    };

    const formatCents = (cents) => {
        const whole = cents / 100n;
        const fraction = String(cents % 100n).padStart(2, '0');
        return `${whole}.${fraction}`;
    };

    const updateRow = (row) => {
        const select = row.querySelector('.product-select');
        const selected = select.selectedOptions[0];
        const price = selected?.dataset.price ?? '';
        const unit = selected?.dataset.unit ?? '';
        const quantity = parseScaledInteger(row.querySelector('.quantity-input').value, 3);
        const unitPriceCents = parseScaledInteger(price, 2);
        row.dataset.lineTotalCents = '0';
        row.querySelector('.unit-price').textContent = price ? `${formatCents(unitPriceCents)} / ${unit}` : '—';
        if (quantity !== null && quantity > 0n && unitPriceCents !== null) {
            row.dataset.lineTotalCents = String((quantity * unitPriceCents + 500n) / 1000n);
        }
        row.querySelector('.line-total').textContent = formatCents(BigInt(row.dataset.lineTotalCents));
    };

    const updateTotals = () => {
        const total = [...rows.querySelectorAll('.sale-item-row')]
            .reduce((sum, row) => sum + BigInt(row.dataset.lineTotalCents || '0'), 0n);
        const paid = parseScaledInteger(amountPaid.value, 2) ?? 0n;
        document.getElementById('grandTotal').textContent = formatCents(total);
        document.getElementById('remainingBalance').textContent = formatCents(total > paid ? total - paid : 0n);
        paymentMethod.required = paid > 0n;
    };

    const updateAllRows = () => {
        rows.querySelectorAll('.sale-item-row').forEach(updateRow);
        updateTotals();
    };

    const reindexRows = () => {
        const saleRows = [...rows.querySelectorAll('.sale-item-row')];
        saleRows.forEach((row, index) => {
            row.querySelector('.product-select').name = `items[${index}].productId`;
            row.querySelector('.quantity-input').name = `items[${index}].quantity`;
        });
        nextIndex = saleRows.length;
        form.dataset.itemCount = String(nextIndex);
    };

    const filterProducts = () => {
        const term = search.value.trim().toLocaleLowerCase();
        rows.querySelectorAll('.product-select').forEach((select) => {
            [...select.options].forEach((option) => {
                if (!option.value || option.selected) {
                    option.hidden = false;
                } else {
                    option.hidden = !option.dataset.name.toLocaleLowerCase().includes(term);
                }
            });
        });
    };

    const addRow = () => {
        const html = template.innerHTML.replaceAll('__index__', String(nextIndex++));
        const temporaryBody = document.createElement('tbody');
        temporaryBody.innerHTML = html.trim();
        const row = temporaryBody.firstElementChild;
        rows.append(row);
        filterProducts();
        updateAllRows();
    };

    rows.addEventListener('change', (event) => {
        if (event.target.matches('.product-select')) {
            updateAllRows();
        }
    });
    rows.addEventListener('input', (event) => {
        if (event.target.matches('.quantity-input')) {
            updateAllRows();
        }
    });
    rows.addEventListener('click', (event) => {
        if (event.target.matches('.remove-item')) {
            const row = event.target.closest('.sale-item-row');
            if (rows.querySelectorAll('.sale-item-row').length > 1) {
                row.remove();
                reindexRows();
                updateTotals();
            } else {
                row.querySelector('.product-select').value = '';
                row.querySelector('.quantity-input').value = '';
                updateAllRows();
            }
        }
    });
    search.addEventListener('input', filterProducts);
    amountPaid.addEventListener('input', updateTotals);
    document.getElementById('addSaleItem').addEventListener('click', addRow);
    updateAllRows();
})();
