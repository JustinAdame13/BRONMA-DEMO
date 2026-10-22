package mx.bronma.cobranza.common;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Serializador extends StdSerializer<BigDecimal> {

    public Serializador() {
        super(BigDecimal.class);
    }

    public void serialize(BigDecimal valor, JsonGenerator gen, SerializationContext cntx){
        gen.writeString(valor.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }
}
