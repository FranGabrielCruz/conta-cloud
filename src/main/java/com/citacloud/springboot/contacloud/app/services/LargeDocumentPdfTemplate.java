package com.citacloud.springboot.contacloud.app.services;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** The purchase-order resource is the single source of large-document geometry,
 * corporate header, typography, table styles, continuation header and pagination.
 * Sales notes replace only domain-specific boxes/rows; the source OC is never edited. */
final class LargeDocumentPdfTemplate {
    private LargeDocumentPdfTemplate(){}
    static InputStream purchaseOrder(){
        var in=LargeDocumentPdfTemplate.class.getResourceAsStream("/reports/purchase-order/purchase-order.jrxml");
        if(in==null)throw new ReglaNegocioException("No se encontró la plantilla oficial de PDF grande.");return in;
    }
    static String creditNote(boolean logo,int corporateHeight,int boxHeight,int noteHeight,boolean voided)throws IOException{
        String base;try(var in=purchaseOrder()){base=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        String corporate=between(base,"<title>","<rectangle radius=");corporate=corporate.substring(corporate.indexOf('>')+1);corporate=corporate.substring(corporate.indexOf('>')+1);
        corporate=corporate.replace("ORDEN DE COMPRA","NOTA DE CRÉDITO").replace("\"Entrega: \" + $P{expectedDeliveryDate}","\"Moneda: \" + $P{currencyCode}");
        if(!logo)corporate=corporate.replace("x=\"90\" y=\"0\" width=\"430\"","x=\"0\" y=\"0\" width=\"520\"").replace("width=\"430\"","width=\"520\"");
        StringBuilder title=new StringBuilder("<title><band height=\"").append(corporateHeight+boxHeight+noteHeight+10).append("\" splitType=\"Stretch\">").append(corporate);
        if(voided)title.append(staticText("ANULADA",560,88,222,24,"Heading","Right").replace("style=\"Heading\"","style=\"Heading\" forecolor=\"#B91C1C\""));
        int boxY=corporateHeight;
        title.append("<rectangle radius=\"4\"><reportElement x=\"0\" y=\"").append(boxY).append("\" width=\"782\" height=\"").append(boxHeight).append("\" forecolor=\"#CBD5E1\"/></rectangle>");
        title.append("<line><reportElement x=\"391\" y=\"").append(boxY).append("\" width=\"1\" height=\"").append(boxHeight).append("\" forecolor=\"#CBD5E1\"/></line>");
        title.append(staticText("CLIENTE",10,boxY+6,365,13,"Section","Left"));title.append(staticText("DOCUMENTO AFECTADO",402,boxY+6,370,13,"Section","Left"));
        title.append(field("$P{clientInformation}",10,boxY+23,365,boxHeight-28,"Base","Left",true));title.append(field("$P{affectedInformation}",402,boxY+23,370,boxHeight-28,"Base","Left",true));
        int noteY=boxY+boxHeight+9;title.append(staticText("INFORMACIÓN DE LA NOTA",0,noteY,782,13,"Section","Left"));
        title.append(field("$P{noteInformation}",0,noteY+16,782,noteHeight-18,"Base","Left",true));title.append("</band></title>");
        base=replaceSection(base,"title",title.toString());
        base=base.replace("name=\"purchase_order\"","name=\"sales_credit_note\"");
        String extras="<parameter name=\"clientInformation\" class=\"java.lang.String\"/><parameter name=\"affectedInformation\" class=\"java.lang.String\"/><parameter name=\"noteInformation\" class=\"java.lang.String\"/><parameter name=\"emissionFooter\" class=\"java.lang.String\"/><field name=\"rowKind\" class=\"java.lang.String\"/><field name=\"rowLabel\" class=\"java.lang.String\"/><field name=\"rowAmount\" class=\"java.math.BigDecimal\"/>";
        // Parameters precede fields in the JRXML schema.
        int firstField=base.indexOf("<field ");String declarations=extras.substring(0,extras.indexOf("<field "));String fields=extras.substring(extras.indexOf("<field "));base=base.substring(0,firstField)+declarations+fields+base.substring(firstField);
        base=base.replace("ORDEN DE COMPRA","NOTA DE CRÉDITO").replace("Proveedor: ","Cliente: ");
        int[] widths={55,336,102,86,102,101};String[] labels={"CANT.","DESCRIPCIÓN","PRECIO","DESC.","IMPUESTO","IMPORTE"};String[] values={"cantidad","descripcion","precioUnitario","descuento","impuesto","total"};
        StringBuilder columns=new StringBuilder("<columnHeader><band height=\"24\">"+when("LINE"));StringBuilder detail=new StringBuilder("<detail><band height=\"22\" splitType=\"Prevent\">").append(when("LINE"));
        int x=0;for(int i=0;i<widths.length;i++){
            columns.append(staticText(labels[i],x,0,widths[i],24,"TableHeader",i>=2?"Right":"Left"));
            detail.append(field("$F{"+values[i]+"}",x+(i<2?3:0),2,widths[i]-(i<2?3:0),18,"Base",i>=2?"Right":"Left",i==1));x+=widths[i];
        }columns.append("</band></columnHeader>");
        detail.append("<line><reportElement positionType=\"Float\" x=\"0\" y=\"21\" width=\"782\" height=\"1\" forecolor=\"#E2E8F0\"/></line></band>");
        detail.append("<band height=\"17\" splitType=\"Stretch\">").append(when("MONEY")).append(field("$F{rowLabel}",520,0,120,16,"Base","Right",true)).append(field("$F{rowAmount}",650,0,132,16,"Base","Right",false)).append("</band>");
        detail.append("<band height=\"31\" splitType=\"Prevent\">").append(when("TOTAL")).append("<line><reportElement x=\"520\" y=\"3\" width=\"262\" height=\"1\" forecolor=\"#94A3B8\"/></line>").append(field("$F{rowLabel}",520,8,120,22,"Heading","Right",false)).append(field("$F{rowAmount}",650,8,132,22,"Heading","Right",false)).append("</band>");
        detail.append("<band height=\"22\" splitType=\"Prevent\">").append(when("SECTION")).append(field("$F{rowLabel}",0,7,782,14,"Section","Left",false)).append("</band>");
        detail.append("<band height=\"16\" splitType=\"Stretch\">").append(when("TEXT")).append(field("$F{rowLabel}",0,0,782,15,"Base","Left",true)).append("</band>");
        detail.append("<band height=\"28\" splitType=\"Prevent\">").append(when("OBSERVATION")).append(staticText("OBSERVACIONES",0,0,782,13,"Section","Left")).append(field("$F{rowLabel}",0,14,782,12,"Base","Left",true)).append("</band>");
        detail.append("<band height=\"17\" splitType=\"Stretch\">").append(when("FINANCE")).append(field("$F{rowLabel}",0,0,600,16,"Base","Left",true)).append(field("$F{rowAmount}",650,0,132,16,"Base","Right",false)).append("</band></detail>");
        base=replaceSection(base,"columnHeader",columns.toString());base=replaceSection(base,"detail",detail.toString());base=replaceSection(base,"summary","");
        String footer=section(base,"pageFooter");footer=footer.replace("<band height=\"18\">","<band height=\"18\">"+field("$P{emissionFooter}",0,2,580,14,"Base","Left",false));base=replaceSection(base,"pageFooter",footer);
        return base;
    }
    static String debitNote(boolean logo,int corporateHeight,int boxHeight,int noteHeight,boolean voided)throws IOException{
        return creditNote(logo,corporateHeight,boxHeight,noteHeight,voided).replace("NOTA DE CRÉDITO","NOTA DE DÉBITO").replace("sales_credit_note","sales_debit_note");
    }

    static String receipt(boolean logo,int corporateHeight,int boxHeight,boolean voided)throws IOException{
        int noteY=corporateHeight+boxHeight+9;
        String base=creditNote(logo,corporateHeight,boxHeight,22,voided)
            .replace("NOTA DE CRÉDITO","RECIBO DE COBRO").replace("sales_credit_note","customer_receipt_large")
            .replace("DOCUMENTO AFECTADO","DATOS DEL RECIBO").replace("ANULADA","ANULADO")
            .replace(staticText("INFORMACIÓN DE LA NOTA",0,noteY,782,13,"Section","Left"),"")
            .replace(field("$P{noteInformation}",0,noteY+16,782,4,"Base","Left",true),"")
            .replace("height=\""+(corporateHeight+boxHeight+32)+"\" splitType=\"Stretch\"","height=\""+(corporateHeight+boxHeight+8)+"\" splitType=\"Stretch\"");
        base=replaceSection(base,"columnHeader","");
        String detail=section(base,"detail");
        int firstBandEnd=detail.indexOf("</band>")+7;
        int[] widths={342,108,166,166};
        String[] labels={"DOCUMENTO","FECHA","TOTAL DOCUMENTO","APLICADO"};
        String[] expressions={"$F{descripcion}","$F{rowLabel}","$F{precioUnitario}","$F{total}"};
        var headings=new StringBuilder("<band height=\"24\" splitType=\"Prevent\">").append(when("DOC_HEADER"));
        var rows=new StringBuilder("<band height=\"21\" splitType=\"Prevent\">").append(when("LINE"));
        int x=0;
        for(int i=0;i<widths.length;i++){
            String align=i>=2?"Right":"Left";
            headings.append(staticText(labels[i],x,0,widths[i],24,"TableHeader",align));
            rows.append(field(expressions[i],x+3,3,widths[i]-6,16,"Base",align,false));
            x+=widths[i];
        }
        headings.append("</band>");rows.append("</band>");
        detail="<detail>"+headings+rows+detail.substring(firstBandEnd);
        detail=detail.replace(field("$F{rowLabel}",520,8,120,22,"Heading","Right",false),
            field("$F{rowLabel}",350,8,290,22,"Heading","Right",false));
        return replaceSection(base,"detail",detail);
    }

    static String cartera(boolean logo,int corporateHeight,int boxHeight,boolean antiguedad)throws IOException{
        String titulo=antiguedad?"ANTIGÜEDAD DE SALDOS":"CUENTAS POR COBRAR";
        String base=receipt(logo,corporateHeight,boxHeight,false)
            .replace("RECIBO DE COBRO",titulo).replace("customer_receipt_large",antiguedad?"receivable_aging":"receivable_portfolio")
            .replace("DATOS DEL RECIBO","FILTROS APLICADOS")
            .replace("\"Moneda: \" + $P{currencyCode}","\"Cartera por moneda\"");
        int[] anchos=antiguedad?new int[]{202,55,87,87,87,87,87,90}:new int[]{102,65,65,235,55,90,90,80};
        String[] etiquetas=antiguedad
            ?new String[]{"CLIENTE","MONEDA","CORRIENTE","1–30 DÍAS","31–60 DÍAS","61–90 DÍAS","+90 DÍAS","TOTAL"}
            :new String[]{"DOCUMENTO","FECHA","VENCE","CLIENTE","MONEDA","TOTAL","SALDO","ESTADO"};
        String campos=java.util.stream.IntStream.range(0,anchos.length)
            .mapToObj(i->"<field name=\"dato"+i+"\" class=\"java.lang.String\"/>").collect(java.util.stream.Collectors.joining());
        int primerCampo=base.indexOf("<field ");
        base=base.substring(0,primerCampo)+campos+base.substring(primerCampo);
        var columnas=new StringBuilder("<columnHeader><band height=\"24\">");
        var detalle=new StringBuilder("<detail><band height=\"20\" splitType=\"Prevent\">").append(when("LINE"));
        int x=0;
        for(int i=0;i<anchos.length;i++){
            boolean importe=antiguedad?i>=2:i==5||i==6;
            String alineacion=importe?"Right":"Left";
            columnas.append(staticText(etiquetas[i],x,0,anchos[i],24,"TableHeader",alineacion)
                .replace("<textElement","<box leftPadding=\"4\" rightPadding=\"4\"/><textElement"));
            detalle.append(field("$F{dato"+i+"}",x+3,3,anchos[i]-6,16,"Base",alineacion,i==(antiguedad?0:3)));
            x+=anchos[i];
        }
        columnas.append("</band></columnHeader>");
        detalle.append("</band>");
        String anterior=section(base,"detail");
        // Reutiliza las bandas de sección, observaciones y totales del documento grande.
        int bandasComunes=anterior.indexOf("</band>",anterior.indexOf("</band>")+7)+7;
        base=replaceSection(base,"detail",detalle+anterior.substring(bandasComunes));
        return base.replace("<detail>",columnas+"<detail>");
    }

    private static String when(String kind){return "<printWhenExpression><![CDATA[\""+kind+"\".equals($F{rowKind})]]></printWhenExpression>";}
    private static String staticText(String text,int x,int y,int w,int h,String style,String align){return "<staticText><reportElement style=\""+style+"\" x=\""+x+"\" y=\""+y+"\" width=\""+w+"\" height=\""+h+"\"/><textElement textAlignment=\""+align+"\" verticalAlignment=\"Middle\"/><text><![CDATA["+text+"]]></text></staticText>";}
    private static String field(String expression,int x,int y,int w,int h,String style,String align,boolean stretch){return "<textField pattern=\"#,##0.00\""+(stretch?" textAdjust=\"StretchHeight\"":"")+"><reportElement style=\""+style+"\" x=\""+x+"\" y=\""+y+"\" width=\""+w+"\" height=\""+h+"\"/><textElement textAlignment=\""+align+"\"/><textFieldExpression><![CDATA["+expression+"]]></textFieldExpression></textField>";}
    private static String between(String text,String start,String end){return text.substring(text.indexOf(start),text.indexOf(end,text.indexOf(start)));}
    private static String section(String text,String tag){return text.substring(text.indexOf("<"+tag+">"),text.indexOf("</"+tag+">")+tag.length()+3);}
    private static String replaceSection(String text,String tag,String value){return text.replace(section(text,tag),value);}
}
