package com.expensia.backend.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.bson.types.ObjectId;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;

@JsonComponent
public class ObjectIdSerializer {

  public static class Serializer extends JsonSerializer<ObjectId>{
    @Override
    public void serialize(ObjectId value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      if (value == null) {
        gen.writeNull();
      } else {
        gen.writeString(value.toHexString());
      }
    }
  }

  public static class Deserializer extends com.fasterxml.jackson.databind.JsonDeserializer<ObjectId> {
    @Override
    public ObjectId deserialize(JsonParser p , DeserializationContext ctxt) throws IOException {
      String id = p.getValueAsString();
      return id==null ? null : new ObjectId(id);
    }
  }
}
