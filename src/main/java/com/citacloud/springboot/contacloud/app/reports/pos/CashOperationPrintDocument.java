package com.citacloud.springboot.contacloud.app.reports.pos;
import java.util.*;
/** Stored values only. Neither the adapter nor renderer recalculates cash balances. */
public record CashOperationPrintDocument(String title,String company,String taxId,String number,String date,String branch,String cash,String user,List<Line> lines,String totalLabel,String total,List<String> notes){
 public record Line(String label,String value){}
 public CashOperationPrintDocument{Objects.requireNonNull(title);Objects.requireNonNull(number);Objects.requireNonNull(company);lines=List.copyOf(lines);notes=List.copyOf(notes);if(lines.size()>1000)throw new IllegalArgumentException("Demasiadas líneas.");}
}
