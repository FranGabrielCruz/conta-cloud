// Rendering core mirrored from contaCloudPrint; see docs/DEVOLUCION-POS-80MM.md.
package com.citacloud.springboot.contacloud.app.reports.pos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Confirmed return snapshot. Contains no sale reconstruction or financial calculations. */
public record SalesReturnPrintDocument(CompanySnapshot company, String returnNumber, String originalInvoiceNumber,
        LocalDateTime returnDateTime, CustomerSnapshot customer, String branch, String cashRegister, String operator,
        String reason, String currency, List<ReturnedItem> items, BigDecimal subtotalReturned, BigDecimal discountReturned,
        BigDecimal surchargeReturned, List<AmountLine> taxesReturned, BigDecimal totalReturned,
        List<AmountLine> refundMethods, BigDecimal totalRefunded, List<String> footer, boolean testDocument) {
    public record CompanySnapshot(String name, String identification, String address, String phone, String countryCode) {
        public CompanySnapshot { required(name,200);optional(identification,60);optional(address,500);optional(phone,60);required(countryCode,2); }
    }
    public record CustomerSnapshot(String name, String identification) {
        public CustomerSnapshot { required(name,200);optional(identification,60); }
    }
    public record ReturnedItem(BigDecimal returnedQuantity, String description, BigDecimal lineTotal,
            BigDecimal unitPrice, BigDecimal discount, BigDecimal tax) {
        public ReturnedItem(BigDecimal quantity,String description,BigDecimal total) { this(quantity,description,total,null,null,null); }
        public ReturnedItem {
            money(returnedQuantity);if(returnedQuantity.signum()<=0)throw new IllegalArgumentException("Cantidad devuelta inválida");
            required(description,1000);money(lineTotal);optionalMoney(unitPrice);optionalMoney(discount);optionalMoney(tax);
        }
    }
    /** Safe description and amount only: no card/account credentials are part of this model. */
    public record AmountLine(String label, BigDecimal amount) {
        public AmountLine { required(label,120);money(amount); }
    }
    public SalesReturnPrintDocument {
        Objects.requireNonNull(company);Objects.requireNonNull(customer);Objects.requireNonNull(returnDateTime);
        required(returnNumber,100);required(originalInvoiceNumber,100);optional(branch,150);optional(cashRegister,100);optional(operator,200);optional(reason,1500);
        if(currency==null||!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException("Moneda inválida");
        items=List.copyOf(items);taxesReturned=List.copyOf(taxesReturned);refundMethods=List.copyOf(refundMethods);footer=List.copyOf(footer);
        if(items.isEmpty()||items.size()>500||taxesReturned.size()>20||refundMethods.size()>20||footer.size()>10)throw new IllegalArgumentException("Devolución fuera de límites");
        money(subtotalReturned);money(totalReturned);optionalMoney(discountReturned);optionalMoney(surchargeReturned);optionalMoney(totalRefunded);
        footer.forEach(line->required(line,300));
    }
    private static void optionalMoney(BigDecimal value){if(value!=null)money(value);}
    private static void money(BigDecimal value){if(value==null||value.signum()<0||value.precision()>19||Math.abs(value.scale())>6)throw new IllegalArgumentException("Importe fuera de límites");}
    private static void required(String text,int max){optional(text,max);if(text==null||text.isBlank())throw new IllegalArgumentException("Dato requerido");}
    private static void optional(String text,int max){if(text!=null&&(text.length()>max||text.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n')))throw new IllegalArgumentException("Texto fuera de límites");}
}
