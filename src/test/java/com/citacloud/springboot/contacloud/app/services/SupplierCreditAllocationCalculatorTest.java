package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.FacturaAplicacionCreditoDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierCreditAllocationCalculatorTest {
    @Test void seleccionaElMenorEntreSaldoYCreditoRestante(){UUID first=UUID.randomUUID(),second=UUID.randomUUID();Map<UUID,BigDecimal> values=new LinkedHashMap<>();values.put(first,new BigDecimal("1755.84"));assertThat(SupplierCreditAllocationCalculator.amountOnSelection(new BigDecimal("2950.00"),values,second,new BigDecimal("14160.00"))).isEqualByComparingTo("1194.16");}
    @Test void deseleccionarLiberaElCredito(){UUID invoice=UUID.randomUUID();Map<UUID,BigDecimal> values=new LinkedHashMap<>();values.put(invoice,new BigDecimal("1755.84"));values.remove(invoice);assertThat(SupplierCreditAllocationCalculator.remaining(new BigDecimal("2950.00"),values)).isEqualByComparingTo("2950.00");}
    @Test void conservaUnaAplicacionParcial(){Map<UUID,BigDecimal> values=Map.of(UUID.randomUUID(),new BigDecimal("1000.00"));assertThat(SupplierCreditAllocationCalculator.total(values)).isEqualByComparingTo("1000.00");assertThat(SupplierCreditAllocationCalculator.remaining(new BigDecimal("2950.00"),values)).isEqualByComparingTo("1950.00");}
    @Test void distribuyePorVencimientoFechaYNumeroInterno(){var newer=invoice("FP-000003","14160.00",LocalDate.of(2026,10,10),LocalDate.of(2026,10,4));var oldest=invoice("FP-000001","11.80",LocalDate.of(2026,10,5),LocalDate.of(2026,10,4));var middle=invoice("FP-000002","1755.84",LocalDate.of(2026,10,6),LocalDate.of(2026,10,4));var result=SupplierCreditAllocationCalculator.distribute(new BigDecimal("2950.00"),List.of(newer,middle,oldest));assertThat(result.keySet()).containsExactly(oldest.facturaId(),middle.facturaId(),newer.facturaId());assertThat(result.values()).containsExactly(new BigDecimal("11.80"),new BigDecimal("1755.84"),new BigDecimal("1182.36"));}
    @Test void noSuperaElSaldoDeUnaFactura(){var invoice=invoice("FP-000001","11.80",LocalDate.now(),LocalDate.now());var result=SupplierCreditAllocationCalculator.distribute(new BigDecimal("2950.00"),List.of(invoice));assertThat(result.get(invoice.facturaId())).isEqualByComparingTo("11.80");}
    @Test void noSuperaElCreditoDisponible(){var invoice=invoice("FP-000001","14160.00",LocalDate.now(),LocalDate.now());var result=SupplierCreditAllocationCalculator.distribute(new BigDecimal("2950.00"),List.of(invoice));assertThat(result.get(invoice.facturaId())).isEqualByComparingTo("2950.00");}

    private static FacturaAplicacionCreditoDto invoice(String internal,String balance,LocalDate due,LocalDate date){return new FacturaAplicacionCreditoDto(UUID.randomUUID(),internal,"PROV-1",null,date,due,UUID.randomUUID(),"DOP",new BigDecimal(balance),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal(balance));}
}
