package com.contacttx.contactservice.validation;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class StrictStringDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(
            JsonParser parser,
            DeserializationContext context) throws JacksonException {

        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            context.reportWrongTokenException(
                    this,
                    JsonToken.VALUE_STRING,
                    "contactPhone must be supplied as a JSON string"
            );
        }

        return parser.getString();
    }
}
