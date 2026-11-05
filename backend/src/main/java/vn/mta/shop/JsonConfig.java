package vn.mta.shop;
import java.io.IOException;
import java.time.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;
@Configuration
public class JsonConfig {
    // DATETIME của database được quy ước UTC; thêm offset khi trả JSON để trình duyệt
    // không hiểu nhầm giờ server là giờ địa phương. Input LocalDateTime vẫn theo hợp đồng UTC.
    @Bean Jackson2ObjectMapperBuilderCustomizer utcDates(){return builder -> builder.serializerByType(LocalDateTime.class,new JsonSerializer<LocalDateTime>(){
        @Override public void serialize(LocalDateTime value,JsonGenerator gen,SerializerProvider provider)throws IOException{gen.writeString(value.atOffset(ZoneOffset.UTC).toString());}
    });}
}
