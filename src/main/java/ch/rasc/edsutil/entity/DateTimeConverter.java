package ch.rasc.edsutil.entity;

import java.sql.Timestamp;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

@Converter(autoApply = true)
public class DateTimeConverter implements AttributeConverter<DateTime, Timestamp> {

	private static final DateTimeFormatter DATETIME_FORMATTER = new DateTimeFormatterBuilder()
			.appendPattern("yyyy-MM-dd HH:mm:ss'.'").appendFractionOfSecond(0, 9).toFormatter();

	@Override
	public Timestamp convertToDatabaseColumn(DateTime value) {

		DateTime valueInUTC = value.withZone(DateTimeZone.UTC);

		String formattedTimestamp = DATETIME_FORMATTER.print(valueInUTC);
		if (formattedTimestamp.endsWith(".")) {
			formattedTimestamp = formattedTimestamp.substring(0, formattedTimestamp.length() - 1);
		}

		return Timestamp.valueOf(formattedTimestamp);
	}

	@Override
	public DateTime convertToEntityAttribute(Timestamp value) {
		if (value != null) {
			return DATETIME_FORMATTER.withZone(DateTimeZone.UTC).parseDateTime(value.toString());
		}
		return null;
	}

}
