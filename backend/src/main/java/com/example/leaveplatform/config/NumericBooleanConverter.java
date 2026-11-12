package com.example.leaveplatform.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

/**
 * Stores Java booleans in Oracle {@code NUMBER(1)} columns.
 *
 * <p>Hibernate 7 expects a native {@code BOOLEAN} column, which Oracle only
 * gained in 23ai, while the migrations keep {@code NUMBER(1)} flags so the
 * schema also runs on 19c/21c Express Edition. Converting through
 * {@link BigDecimal} makes the expected JDBC type numeric and matches both the
 * column and the CHECK constraints.
 */
@Converter(autoApply = true)
public class NumericBooleanConverter implements AttributeConverter<Boolean, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    @Override
    public Boolean convertToEntityAttribute(BigDecimal value) {
        return value != null && value.signum() != 0;
    }
}
