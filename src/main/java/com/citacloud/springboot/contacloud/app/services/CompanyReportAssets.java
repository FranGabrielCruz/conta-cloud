package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.models.Empresa;
/** Same company-logo source and size limit used by official large documents. */
final class CompanyReportAssets {
    private CompanyReportAssets(){}
    static byte[] logo(Empresa company,LocalFileStorageService storage){
        return company.getLogoObjectKey()==null?null:storage.load(company.getLogoObjectKey(),5L*1024*1024).orElse(null);
    }
}
