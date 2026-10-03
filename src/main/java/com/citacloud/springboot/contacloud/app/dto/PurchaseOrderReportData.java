package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderReportData(UUID tenantId,UUID companyId,UUID orderId,String orderNumber,
        String companyTradeName,String companyLegalName,String companyTaxIdentification,String companyAddress,
        String companyPhone,String companyEmail,byte[] companyLogo,String supplierName,String supplierTaxIdentification,
        String supplierContactName,String supplierPhone,String supplierEmail,LocalDate orderDate,LocalDate expectedDeliveryDate,
        String branchName,String currencyCode,String paymentTermName,String reference,String notes,BigDecimal subtotal,
        BigDecimal discount,BigDecimal tax,BigDecimal total,String issuedByDisplayName,OffsetDateTime issuedAt,
        List<PurchaseOrderReportLine> lines) {}
