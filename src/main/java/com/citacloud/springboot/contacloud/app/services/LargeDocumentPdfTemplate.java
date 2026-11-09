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
        return tablaReporte(base,anchos,etiquetas,i->(antiguedad?i>=2:i==5||i==6)?"Right":"Left",i->i==(antiguedad?0:3));
    }

    static String activosFijos(boolean logo,boolean conFiltros)throws IOException{
        String base=receipt(logo,90,36,false)
            .replace("RECIBO DE COBRO","REPORTE DE ACTIVOS FIJOS")
            .replace("customer_receipt_large","fixed_asset_register");
        int primerParametro=base.indexOf("<parameter ");
        base=base.substring(0,primerParametro)
            +"<style name=\"AssetMetric\" style=\"Heading\" fontSize=\"12\"/>"
            +base.substring(primerParametro);
        int primerCampo=base.indexOf("<field ");
        String parametros=java.util.stream.Stream.of("assetFilters","assetCount","assetCost","assetDepreciation","assetBook")
            .map(p->"<parameter name=\""+p+"\" class=\"java.lang.String\"/>")
            .collect(java.util.stream.Collectors.joining());
        base=base.substring(0,primerCampo)+parametros+base.substring(primerCampo);
        var titulo=section(base,"title");
        int banda=titulo.indexOf("<band ");
        String empresa=titulo.substring(titulo.indexOf('>',banda)+1,titulo.indexOf("<rectangle"))
            .replace("\"Moneda: \" + $P{currencyCode}","\"\"");

        int resumenY=conFiltros?106:89;
        var cabecera=new StringBuilder("<title><band height=\""+(resumenY+53)+"\" splitType=\"Stretch\">")
            .append(empresa);
        if(conFiltros)cabecera.append(field("$P{assetFilters}",0,84,782,14,"Base","Left",true));
        cabecera.append("<frame><reportElement positionType=\"Float\" x=\"0\" y=\"").append(resumenY)
            .append("\" width=\"782\" height=\"49\"/>")
            .append(lineaHorizontal(0,"#CBD5E1"))
            .append(staticText("RESUMEN",0,4,782,12,"Section","Left"))
            .append(metricaActivo("ACTIVOS INCLUIDOS","assetCount",0,156));
        String[] etiquetas={"COSTO DE ADQUISICIÓN","DEPRECIACIÓN ACUM.","VALOR EN LIBROS"};
        String[] metricas={"assetCost","assetDepreciation","assetBook"};
        cabecera.append("<frame><reportElement x=\"156\" y=\"0\" width=\"626\" height=\"49\">")
            .append("<printWhenExpression><![CDATA[$P{assetCost} != null]]></printWhenExpression></reportElement>");
        for(int i=0;i<metricas.length;i++)cabecera.append(metricaActivo(etiquetas[i],metricas[i],i*208,208));
        cabecera.append("</frame></frame></band></title>");
        base=replaceSection(base,"title",cabecera.toString());
        base=replaceSection(base,"pageHeader","<pageHeader><band height=\"26\" splitType=\"Prevent\">"
            +"<printWhenExpression><![CDATA[$V{PAGE_NUMBER} > 1]]></printWhenExpression>"
            +field("$P{companyTradeName}",0,2,410,20,"AssetMetric","Left",false)
            +staticText("REPORTE DE ACTIVOS FIJOS",449,0,333,12,"Section","Right")
            +field("\"Fecha: \" + $P{orderDate}",449,12,333,12,"Base","Right",false)
            +"</band></pageHeader>");

        int[] anchos={77,167,122,74,51,95,95,101};
        base=tablaReporte(base,anchos,
            new String[]{"CÓDIGO","ACTIVO","CATEGORÍA","ADQUISICIÓN","MONEDA","COSTO","DEP. ACUM.","VALOR EN LIBROS"},
            i->i>=5?"Right":i>=3?"Center":"Left",i->i==1||i==2);
        var totales=new StringBuilder("<band height=\"23\" splitType=\"Prevent\">").append(when("ASSET_TOTAL"))
            .append(lineaHorizontal(0,"#CBD5E1"))
            .append(field("$F{dato0}",3,5,anchos[0]+anchos[1]-6,15,"Section","Left",false));
        int x=java.util.Arrays.stream(anchos,0,5).sum();
        for(int i=5;i<anchos.length;i++){
            totales.append(field("$F{dato"+i+"}",x+3,5,anchos[i]-6,15,"Section","Right",false));
            x+=anchos[i];
        }
        return base.replace("</detail>",totales+"</band></detail>");
    }

    private static String metricaActivo(String etiqueta,String parametro,int x,int ancho){
        return staticText(etiqueta,x,17,ancho,12,"Section","Left")
            +field("$P{"+parametro+"}",x,30,ancho,17,"AssetMetric","Left",false);
    }

    private static String lineaHorizontal(int y,String color){
        return "<line><reportElement x=\"0\" y=\""+y+"\" width=\"782\" height=\"1\" forecolor=\""+color+"\"/></line>";
    }

    private static String tablaReporte(String base,int[] anchos,String[] etiquetas,
            java.util.function.IntFunction<String> alinear,java.util.function.IntPredicate expandir){
        String campos=java.util.stream.IntStream.range(0,anchos.length)
            .mapToObj(i->"<field name=\"dato"+i+"\" class=\"java.lang.String\"/>").collect(java.util.stream.Collectors.joining());
        int primerCampo=base.indexOf("<field ");
        base=base.substring(0,primerCampo)+campos+base.substring(primerCampo);
        var columnas=new StringBuilder("<columnHeader><band height=\"24\">");
        var detalle=new StringBuilder("<detail><band height=\"20\" splitType=\"Prevent\">").append(when("LINE"));
        int x=0;
        for(int i=0;i<anchos.length;i++){
            String alineacion=alinear.apply(i);
            columnas.append(staticText(etiquetas[i],x,0,anchos[i],24,"TableHeader",alineacion)
                .replace("<textElement","<box leftPadding=\"4\" rightPadding=\"4\"/><textElement"));
            detalle.append(field("$F{dato"+i+"}",x+3,3,anchos[i]-6,16,"Base",alineacion,expandir.test(i)));
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
