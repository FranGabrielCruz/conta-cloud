package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.multitenancy.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InfrastructureAdminServiceTest {
    @Mock DirectoryGateway directory;

    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void ocultaNombreFisicoSinPermisoDeEdicion(){
        authenticate(Set.of("bases_datos.ver"));DatabaseNode node=node();
        when(directory.searchNodes("db",null,null,0,10,false)).thenReturn(new DirectoryPage<>(List.of(node),1));

        var result=new InfrastructureAdminService(directory,"20").buscarBases("db",null,null,0,10);

        assertThat(result.content().getFirst().nombreBase()).isNull();
        verify(directory).searchNodes("db",null,null,0,10,false);
    }

    @Test void permiteBuscarNombreFisicoSoloAQuienPuedeEditar(){
        authenticate(Set.of("bases_datos.ver","bases_datos.editar"));DatabaseNode node=node();
        when(directory.searchNodes("conta",null,null,0,10,true)).thenReturn(new DirectoryPage<>(List.of(node),1));

        var result=new InfrastructureAdminService(directory,"20").buscarBases("conta",null,null,0,10);

        assertThat(result.content().getFirst().nombreBase()).isEqualTo("conta_01");
    }

    @Test void editarConfiguracionNoConcedePermisoParaCambiarEstado(){
        authenticate(Set.of("bases_datos.editar"));DatabaseNode node=node();
        when(directory.findNode(node.id())).thenReturn(java.util.Optional.of(node));
        var input=new com.citacloud.springboot.contacloud.app.dto.DatabaseNodeInputDto(node.code(),node.name(),node.type(),DatabaseNodeStatus.MANTENIMIENTO,"conservar",node.databaseName(),node.region(),node.maxTenants(),node.capacityThreshold(),node.schemaVersion(),node.healthy());

        assertThatThrownBy(()->new InfrastructureAdminService(directory,"20").actualizarBase(node.id(),input))
            .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cambiar el estado");
    }

    private static DatabaseNode node(){return new DatabaseNode(UUID.randomUUID(),"DB001","Principal",DatabaseNodeType.COMPARTIDA,DatabaseNodeStatus.ACTIVA,"host-ref","conta_01","local",100,10,BigDecimal.valueOf(90),"20","secret-ref",true);}
    private static void authenticate(Set<String> permissions){var principal=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"EMPRESA","Admin","admin","",true,permissions);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));}
}
