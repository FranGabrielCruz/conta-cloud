package com.citacloud.springboot.contacloud.app.reports.pos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Confirmed session snapshot. Amounts, denomination subtotals and status are supplied by the backend. */
public record CashClosingPrintDocument(CompanySnapshot company,String closingNumber,String cashRegister,String branch,
        String cashier,LocalDateTime openingDateTime,LocalDateTime closingDateTime,String currency,
        List<AmountLine> salesSummary,AmountLine netSales,List<PaymentMethod> paymentMethods,String paymentDescription,
        AmountLine paymentTotal,List<AmountLine> cashMovements,List<Denomination> denominations,boolean includeZeroDenominations,
        BigDecimal countedCash,BigDecimal expectedCash,BigDecimal difference,Status status,List<Operation> operations,
        String observation,String closedBy,boolean signatureRequired,List<String> footer,
        String shiftNumber,CashSummary cashSummary,List<PaymentMethod> collectionMethods,BigDecimal totalCollected) {
    public enum Status { CUADRADO, FALTANTE, SOBRANTE }
    public record CompanySnapshot(String name,String identification,String address,String phone,String countryCode) {
        public CompanySnapshot {required(name,200);optional(identification,60);optional(address,500);optional(phone,60);required(countryCode,2);}
    }
    public record AmountLine(String label,BigDecimal amount) {
        public AmountLine {required(label,100);money(amount);}
    }
    public record CashSummary(BigDecimal openingFund,BigDecimal cashEntries,BigDecimal cashExits) {
        public CashSummary {money(openingFund);money(cashEntries);money(cashExits);}
    }
    /** Original snapshot schema remains readable; absent collection metrics are not invented. */
    public CashClosingPrintDocument(CompanySnapshot company,String closingNumber,String cashRegister,String branch,
            String cashier,LocalDateTime openingDateTime,LocalDateTime closingDateTime,String currency,
            List<AmountLine> salesSummary,AmountLine netSales,List<PaymentMethod> paymentMethods,String paymentDescription,
            AmountLine paymentTotal,List<AmountLine> cashMovements,List<Denomination> denominations,boolean includeZeroDenominations,
            BigDecimal countedCash,BigDecimal expectedCash,BigDecimal difference,Status status,List<Operation> operations,
            String observation,String closedBy,boolean signatureRequired,List<String> footer) {
        this(company,closingNumber,cashRegister,branch,cashier,openingDateTime,closingDateTime,currency,salesSummary,netSales,paymentMethods,paymentDescription,paymentTotal,cashMovements,denominations,includeZeroDenominations,countedCash,expectedCash,difference,status,operations,observation,closedBy,signatureRequired,footer,null,null,null,null);
    }
    public CashClosingPrintDocument withReportContext(String shift,CashSummary cash,List<PaymentMethod> collections,BigDecimal total) {
        return new CashClosingPrintDocument(company,closingNumber,cashRegister,branch,cashier,openingDateTime,closingDateTime,currency,salesSummary,netSales,paymentMethods,paymentDescription,paymentTotal,cashMovements,denominations,includeZeroDenominations,countedCash,expectedCash,difference,status,operations,observation,closedBy,signatureRequired,footer,shift,cash,collections,total);
    }
    public record PaymentMethod(String name,BigDecimal amount,Integer displayOrder) {
        public PaymentMethod {required(name,150);money(amount);order(displayOrder);}
    }
    public record Denomination(BigDecimal denomination,int quantity,BigDecimal amount,Integer displayOrder) {
        public Denomination {positive(denomination);if(denomination.signum()==0||quantity<0||quantity>1_000_000)throw new IllegalArgumentException("Denominación fuera de límites");positive(amount);order(displayOrder);}
    }
    public record Operation(String name,long quantity) {
        public Operation {required(name,150);if(quantity<0)throw new IllegalArgumentException("Cantidad negativa");}
    }
    public CashClosingPrintDocument {
        Objects.requireNonNull(company);Objects.requireNonNull(openingDateTime);Objects.requireNonNull(closingDateTime);Objects.requireNonNull(status);
        required(closingNumber,100);required(cashRegister,150);required(currency,12);optional(branch,150);optional(cashier,300);
        optional(paymentDescription,300);optional(observation,1500);optional(closedBy,300);
        optional(shiftNumber,300);
        salesSummary=List.copyOf(salesSummary);paymentMethods=List.copyOf(paymentMethods);cashMovements=List.copyOf(cashMovements);denominations=List.copyOf(denominations);operations=List.copyOf(operations);footer=List.copyOf(footer);
        if(salesSummary.size()>50||paymentMethods.size()>100||cashMovements.size()>50||denominations.size()>500||operations.size()>50||footer.size()>10)throw new IllegalArgumentException("Cierre fuera de límites");
        positive(countedCash);money(expectedCash);money(difference);footer.forEach(x->required(x,300));
        if(collectionMethods!=null){collectionMethods=List.copyOf(collectionMethods);if(collectionMethods.size()>100)throw new IllegalArgumentException("Demasiadas formas de cobro");money(totalCollected);}
        else if(totalCollected!=null)throw new IllegalArgumentException("Total sin formas de cobro");
    }
    private static void money(BigDecimal n){if(n==null||n.precision()>19||Math.abs(n.scale())>6)throw new IllegalArgumentException("Importe fuera de límites");}
    private static void positive(BigDecimal n){money(n);if(n.signum()<0)throw new IllegalArgumentException("Importe negativo");}
    private static void order(Integer n){if(n!=null&&n<0)throw new IllegalArgumentException("Orden negativo");}
    private static void required(String s,int max){optional(s,max);if(s==null||s.isBlank())throw new IllegalArgumentException("Dato requerido");}
    private static void optional(String s,int max){if(s!=null&&(s.length()>max||s.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n')))throw new IllegalArgumentException("Texto fuera de límites");}
}
