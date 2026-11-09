package com.citacloud.springboot.contacloud.app.reports.pos;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Confirmed immutable receipt; no financial reconstruction is performed here. */
public record PaymentReceiptPrintDocument(CompanySnapshot company,String receiptNumber,LocalDateTime receiptDateTime,
    CustomerSnapshot customer,String branch,String cashRegister,String operator,String concept,String currency,
    List<PaidDocument> documents,BigDecimal totalApplied,List<PaymentMethod> paymentMethods,BigDecimal totalCollected,
    BigDecimal previousBalance,BigDecimal amountApplied,BigDecimal remainingBalance,List<String> footer) {
    public record CompanySnapshot(String name,String identification,String address,String phone,String countryCode,String logoObjectKey) {
        public CompanySnapshot(String name,String identification,String address,String phone,String countryCode) {
            this(name,identification,address,phone,countryCode,null);
        }
        public CompanySnapshot {required(name,200);optional(identification,60);optional(address,500);optional(phone,60);required(countryCode,2);}
    }
    public record CustomerSnapshot(String name,String identification) {
        public CustomerSnapshot {required(name,200);optional(identification,60);}
    }
    public record PaidDocument(String documentNumber,BigDecimal amount,java.time.LocalDate date,BigDecimal originalTotal,
            String documentType,java.util.UUID documentId) {
        public PaidDocument(String documentNumber,BigDecimal amount) {this(documentNumber,amount,null,null,null,null);}
        public PaidDocument {required(documentNumber,150);money(amount);optionalMoney(originalTotal);}
    }
    /** Public references only; card details must already be masked. */
    public record PaymentMethod(String name,BigDecimal amount,String reference) {
        public PaymentMethod {required(name,200);money(amount);optional(reference,500);
            if(reference!=null&&(reference.matches("(?is).*(?:cvv|cvc|pin|token|password)\\s*[:=].*")||reference.matches("(?s).*(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d).*")))throw new IllegalArgumentException("Referencia no imprimible");}
    }
    public PaymentReceiptPrintDocument {
        Objects.requireNonNull(company);Objects.requireNonNull(customer);Objects.requireNonNull(receiptDateTime);
        required(receiptNumber,100);optional(branch,150);optional(cashRegister,150);optional(operator,200);optional(concept,1500);
        if(currency==null||!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException("Moneda inválida");
        documents=List.copyOf(documents);paymentMethods=List.copyOf(paymentMethods);footer=List.copyOf(footer);
        if(documents.size()>500||paymentMethods.isEmpty()||paymentMethods.size()>100||footer.size()>10)throw new IllegalArgumentException("Recibo fuera de límites");
        money(totalApplied);money(totalCollected);optionalMoney(previousBalance);optionalMoney(amountApplied);optionalMoney(remainingBalance);footer.forEach(x->required(x,300));
    }
    private static void optionalMoney(BigDecimal n){if(n!=null)money(n);}
    private static void money(BigDecimal n){if(n==null||n.signum()<0||n.precision()>19||Math.abs(n.scale())>6)throw new IllegalArgumentException("Importe fuera de límites");}
    private static void required(String s,int max){optional(s,max);if(s==null||s.isBlank())throw new IllegalArgumentException("Dato requerido");}
    private static void optional(String s,int max){if(s!=null&&(s.length()>max||s.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n')))throw new IllegalArgumentException("Texto fuera de límites");}
}
