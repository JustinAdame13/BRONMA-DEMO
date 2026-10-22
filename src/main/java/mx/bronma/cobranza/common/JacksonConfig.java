package mx.bronma.cobranza.common;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;

@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer mj(){
        SimpleModule modulo=new SimpleModule().addSerializer(BigDecimal.class, new Serializador());

        return builder -> builder.addModule(modulo);
    }
}
