package org.example.steptracker.device;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class HealthSourceConverter implements AttributeConverter<HealthSource, String> {

    @Override
    public String convertToDatabaseColumn(HealthSource attribute) {
        return attribute == null ? null : attribute.getWireValue();
    }

    @Override
    public HealthSource convertToEntityAttribute(String dbData) {
        return dbData == null ? null : HealthSource.fromWireValue(dbData);
    }
}
