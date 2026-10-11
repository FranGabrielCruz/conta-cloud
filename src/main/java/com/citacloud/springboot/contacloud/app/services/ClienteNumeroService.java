package com.citacloud.springboot.contacloud.app.services;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ClienteNumeroService {
    private final EntityManager em;
    public ClienteNumeroService(EntityManager em){this.em=em;}
    public String siguiente(UUID tenant,UUID empresa){Object value=em.createNativeQuery("INSERT INTO customer_sequence(tenant_id,empresa_id,last_number) VALUES (:tenant,:empresa,1) ON CONFLICT (tenant_id,empresa_id) DO UPDATE SET last_number=customer_sequence.last_number+1 RETURNING last_number")
        .setParameter("tenant",tenant).setParameter("empresa",empresa).getSingleResult();return "CLI-%06d".formatted(((Number)value).longValue());}
}
