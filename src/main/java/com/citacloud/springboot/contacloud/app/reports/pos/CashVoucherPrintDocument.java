package com.citacloud.springboot.contacloud.app.reports.pos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Historical voucher values. Movement direction is explicit and never inferred from an amount. */
public record CashVoucherPrintDocument(CompanySnapshot company,String voucherNumber,LocalDateTime createdDateTime,
        String branch,String cashRegister,String cashier,MovementType movementType,String concept,BigDecimal amount,
        String currency,String paymentMethod,String reference,String observation,String performedBy,
        RelatedParty relatedParty,boolean showSignature,String cancellationReason,List<String> footer) {
    public enum MovementType { CASH_IN, CASH_OUT }
    public enum PartyLabel { BENEFICIARIO, ENTREGADO_A, RECIBIDO_POR }
    public record RelatedParty(PartyLabel label,String name) {
        public RelatedParty {Objects.requireNonNull(label);required(name,300);}
    }
    public record CompanySnapshot(String name,String identification,String address,String phone,String countryCode) {
        public CompanySnapshot {required(name,200);optional(identification,60);optional(address,500);optional(phone,60);required(countryCode,2);}
    }
    public CashVoucherPrintDocument {
        Objects.requireNonNull(company);Objects.requireNonNull(createdDateTime);Objects.requireNonNull(movementType);
        required(voucherNumber,100);required(cashRegister,150);required(concept,1500);required(currency,12);
        optional(branch,150);optional(cashier,300);optional(performedBy,300);optional(paymentMethod,100);
        optional(reference,200);optional(observation,1500);optional(cancellationReason,1500);
        if(amount==null||amount.signum()<0||amount.precision()>19||Math.abs(amount.scale())>6)throw new IllegalArgumentException("Importe fuera de límites");
        footer=List.copyOf(footer);if(footer.size()>10)throw new IllegalArgumentException("Pie fuera de límites");footer.forEach(x->required(x,300));
    }
    public CashVoucherPrintDocument withCancellation(String reason) {
        return new CashVoucherPrintDocument(company,voucherNumber,createdDateTime,branch,cashRegister,cashier,movementType,concept,amount,currency,paymentMethod,reference,observation,performedBy,relatedParty,showSignature,reason,footer);
    }
    private static void required(String s,int max){optional(s,max);if(s==null||s.isBlank())throw new IllegalArgumentException("Dato requerido");}
    private static void optional(String s,int max){if(s!=null&&(s.length()>max||s.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n')))throw new IllegalArgumentException("Texto fuera de límites");}
}
