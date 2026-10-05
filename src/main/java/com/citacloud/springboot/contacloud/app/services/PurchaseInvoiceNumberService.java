package com.citacloud.springboot.contacloud.app.services;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PurchaseInvoiceNumberService {
    private final EntityManager entityManager;

    public PurchaseInvoiceNumberService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public String next(UUID tenantId, UUID empresaId) {
        Object value = entityManager.createNativeQuery("""
            INSERT INTO purchase_invoice_sequence(tenant_id, empresa_id, last_number)
            VALUES (:tenant, :empresa, 1)
            ON CONFLICT (tenant_id, empresa_id)
            DO UPDATE SET last_number = purchase_invoice_sequence.last_number + 1
            RETURNING last_number
            """)
            .setParameter("tenant", tenantId)
            .setParameter("empresa", empresaId)
            .getSingleResult();
        return "FP-%06d".formatted(((Number) value).longValue());
    }
}
