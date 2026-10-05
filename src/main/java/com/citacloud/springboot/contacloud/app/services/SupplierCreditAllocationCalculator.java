package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.FacturaAplicacionCreditoDto;

import java.math.BigDecimal;
import java.util.*;

public final class SupplierCreditAllocationCalculator {
    private SupplierCreditAllocationCalculator() {}

    public static BigDecimal amountOnSelection(BigDecimal available,Map<UUID,BigDecimal> allocations,
            UUID invoiceId,BigDecimal invoiceBalance) {
        BigDecimal usedByOthers=allocations.entrySet().stream().filter(e->!e.getKey().equals(invoiceId))
            .map(Map.Entry::getValue).reduce(BigDecimal.ZERO,BigDecimal::add);
        return invoiceBalance.min(available.subtract(usedByOthers).max(BigDecimal.ZERO));
    }

    public static BigDecimal total(Map<UUID,BigDecimal> allocations) {
        return allocations.values().stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
    }

    public static BigDecimal remaining(BigDecimal available,Map<UUID,BigDecimal> allocations) {
        return available.subtract(total(allocations));
    }

    public static Map<UUID,BigDecimal> distribute(BigDecimal available,List<FacturaAplicacionCreditoDto> invoices) {
        List<FacturaAplicacionCreditoDto> ordered=new ArrayList<>(invoices);
        ordered.sort(Comparator.comparing(FacturaAplicacionCreditoDto::vencimiento,Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FacturaAplicacionCreditoDto::fecha,Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FacturaAplicacionCreditoDto::numeroInterno,Comparator.nullsLast(String::compareTo)));
        Map<UUID,BigDecimal> result=new LinkedHashMap<>();BigDecimal remaining=available;
        for(var invoice:ordered){if(remaining.signum()<=0)break;BigDecimal amount=invoice.saldo().min(remaining);if(amount.signum()>0){result.put(invoice.facturaId(),amount);remaining=remaining.subtract(amount);}}
        return result;
    }
}
