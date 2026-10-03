package com.citacloud.springboot.contacloud.app.services;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class PurchaseOrderNumberService {
    private final EntityManager entityManager;
    public PurchaseOrderNumberService(EntityManager entityManager){this.entityManager=entityManager;}
    public String next(UUID tenant,UUID empresa){Object value=entityManager.createNativeQuery("""
        INSERT INTO purchase_order_sequence(tenant_id,empresa_id,last_number)
        VALUES (:tenant,:empresa,1)
        ON CONFLICT (tenant_id,empresa_id)
        DO UPDATE SET last_number=purchase_order_sequence.last_number+1
        RETURNING last_number
        """).setParameter("tenant",tenant).setParameter("empresa",empresa).getSingleResult();
        return "OC-%06d".formatted(((Number)value).longValue());
    }
}
