package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SupplierCreditNoteReportData(UUID tenantId,UUID companyId,UUID noteId,String noteNumber,
        String companyTradeName,String companyLegalName,String companyTaxIdentification,String companyAddress,
        String companyPhone,String companyEmail,byte[] companyLogo,String supplierName,String supplierTaxIdentification,
        LocalDate noteDate,String status,String supplierCreditNumber,String fiscalNumber,String currencyCode,
        String reason,String relatedInvoice,String notes,BigDecimal subtotal,BigDecimal discount,BigDecimal tax,
        BigDecimal total,BigDecimal applied,BigDecimal available,String generatedByDisplayName,OffsetDateTime generatedAt,
        List<SupplierCreditNoteReportLine> lines,List<SupplierCreditTaxReportLine> taxes,
        List<SupplierCreditApplicationReportLine> applications) {}
