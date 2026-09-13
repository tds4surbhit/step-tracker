package org.example.steptracker.device;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PlatformConverter implements AttributeConverter<Platform, String> {

    @Override
    public String convertToDatabaseColumn(Platform attribute) {
        return attribute == null ? null : attribute.getWireValue();
    }

    @Override
    public Platform convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Platform.fromWireValue(dbData);
    }
}
