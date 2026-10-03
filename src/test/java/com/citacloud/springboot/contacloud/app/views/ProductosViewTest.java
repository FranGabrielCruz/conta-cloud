package com.citacloud.springboot.contacloud.app.views;

import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class ProductosViewTest {
    @Test void parsesFormattedDecimalValues(){TextField field=new TextField();field.setValue("38,000.00");assertThat(ProductosView.decimal(field)).isEqualByComparingTo(new BigDecimal("38000.00"));}
    @Test void rejectsInvalidDecimalValues(){TextField field=new TextField();field.setValue("abc");assertThatThrownBy(()->ProductosView.decimal(field)).isInstanceOf(com.citacloud.springboot.contacloud.app.services.ReglaNegocioException.class);}
}
