package com.citacloud.springboot.contacloud.app.views.components;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BillingPrintActionButtonTest {
 @Test void acknowledgedActionGetsNewIntent(){
  var keys=new ArrayList<UUID>();var button=new BillingPrintActionButton(UUID.randomUUID(),ButtonSize.MAIN,"Imprimir",(id,key)->{keys.add(key);return true;});
  button.click();button.click();assertNotEquals(keys.get(0),keys.get(1));assertTrue(button.isDisableOnClick());assertTrue(button.isEnabled());
 }
 @Test void retryKeepsIntentUntilAcknowledged(){
  var keys=new ArrayList<UUID>();var button=new BillingPrintActionButton(UUID.randomUUID(),ButtonSize.MAIN,"Imprimir",(id,key)->{keys.add(key);return false;});
  button.click();button.click();assertEquals(keys.get(0),keys.get(1));assertTrue(button.isEnabled());
 }
}
