package com.citacloud.springboot.contacloud.app.reports.pos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Confirmed cash-count snapshot. No totals, differences or states are reconstructed here. */
public record CashCountPrintDocument(CompanySnapshot company,String cashCountNumber,LocalDateTime createdDateTime,
        LocalDateTime openingDateTime,LocalDateTime countDateTime,String branch,String cashRegister,String cashier,
        String performedBy,String currency,List<Denomination> denominations,boolean includeZeroDenominations,
        BigDecimal expectedCash,BigDecimal countedCash,BigDecimal difference,Status status,String observation,List<String> footer) {
    public enum Status { CUADRADO, FALTANTE, SOBRANTE }
    public record CompanySnapshot(String name,String identification,String address,String phone,String countryCode) {
        public CompanySnapshot {required(name,200);optional(identification,60);optional(address,500);optional(phone,60);required(countryCode,2);}
    }
    public record Denomination(BigDecimal denomination,int quantity,BigDecimal amount,Integer displayOrder) {
        public Denomination {
            positive(denomination);if(denomination.signum()==0||quantity<0||quantity>1_000_000)throw new IllegalArgumentException("Denominación fuera de límites");
            positive(amount);if(displayOrder!=null&&displayOrder<0)throw new IllegalArgumentException("Orden fuera de límites");
        }
    }
    public CashCountPrintDocument {
        Objects.requireNonNull(company);Objects.requireNonNull(createdDateTime);Objects.requireNonNull(countDateTime);Objects.requireNonNull(status);
        required(cashCountNumber,100);required(cashRegister,150);optional(branch,150);optional(cashier,300);optional(performedBy,300);optional(observation,1500);required(currency,12);
        denominations=List.copyOf(denominations);footer=List.copyOf(footer);
        if(denominations.size()>500||footer.size()>10)throw new IllegalArgumentException("Recuento fuera de límites");
        signed(expectedCash);positive(countedCash);signed(difference);footer.forEach(x->required(x,300));
    }
    private static void signed(BigDecimal n){if(n==null||n.precision()>19||Math.abs(n.scale())>6)throw new IllegalArgumentException("Importe fuera de límites");}
    private static void positive(BigDecimal n){signed(n);if(n.signum()<0)throw new IllegalArgumentException("Importe negativo");}
    private static void required(String s,int max){optional(s,max);if(s==null||s.isBlank())throw new IllegalArgumentException("Dato requerido");}
    private static void optional(String s,int max){if(s!=null&&(s.length()>max||s.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n')))throw new IllegalArgumentException("Texto fuera de límites");}
}
