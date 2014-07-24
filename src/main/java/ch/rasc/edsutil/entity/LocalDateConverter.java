package ch.rasc.edsutil.entity;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter(autoApply = true)
public class LocalDateConverter implements AttributeConverter<LocalDate, Timestamp> {
	@Override
	public Timestamp convertToDatabaseColumn(LocalDate value) {
		if (value != null) {
			return Timestamp.valueOf(value.atTime(LocalTime.MIDNIGHT));
		}
		return null;
	}

	@Override
	public LocalDate convertToEntityAttribute(Timestamp value) {
		if (value != null) {
			return value.toLocalDateTime().toLocalDate();
		}
		return null;
	}
}