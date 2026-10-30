package neko.fantasy.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonTimeConfig {

    static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Bean
    JacksonModule shanghaiInstantModule() {
        return new SimpleModule("shanghai-instant")
                .addSerializer(Instant.class, new ShanghaiInstantSerializer());
    }

    static final class ShanghaiInstantSerializer extends ValueSerializer<Instant> {

        @Override
        public void serialize(Instant value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeString(FORMATTER.format(value.atZone(ZONE_ID)));
        }
    }
}
