package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SupplierPaymentReportData(UUID tenantId,UUID companyId,UUID paymentId,String paymentNumber,
        String companyTradeName,String companyLegalName,String companyTaxIdentification,String companyAddress,
        String companyPhone,String companyEmail,byte[] companyLogo,String supplierName,
        String supplierTaxIdentification,String supplierPhone,String supplierEmail,LocalDate paymentDate,
        String status,String currencyCode,String paymentMethod,String source,String checkNumber,String reference,
        String notes,String voidReason,BigDecimal amount,BigDecimal applied,BigDecimal available,
        String generatedByDisplayName,OffsetDateTime generatedAt,List<SupplierPaymentReportLine> applications) {}
