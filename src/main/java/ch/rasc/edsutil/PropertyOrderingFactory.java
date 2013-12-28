package ch.rasc.edsutil;

import java.util.Collection;

import org.springframework.expression.ParseException;

import ch.ralscha.extdirectspring.bean.GroupInfo;
import ch.ralscha.extdirectspring.bean.SortDirection;
import ch.ralscha.extdirectspring.bean.SortInfo;

import com.google.common.collect.Ordering;

public class PropertyOrderingFactory {

	private PropertyOrderingFactory() {
		// singleton
	}

	public static <T> Ordering<T> createOrdering(String propertyName) {
		try {
			return new PropertyOrdering<>(propertyName);
		} catch (ParseException e) {
			return null;
		}
	}

	public static <T> Ordering<T> createOrdering(String propertyName, SortDirection sortDirection) {
		try {
			Ordering<T> ordering = new PropertyOrdering<>(propertyName);

			if (sortDirection == SortDirection.DESCENDING) {
				ordering = ordering.reverse();
			}

			return ordering;
		} catch (ParseException e) {
			return null;
		}
	}

	public static <T> Ordering<T> createOrderingFromSorters(Collection<SortInfo> sortInfos) {
		Ordering<T> ordering = null;

		if (sortInfos != null) {
			for (SortInfo sorter : sortInfos) {
				Ordering<T> propertyOrdering = createOrdering(sorter.getProperty(), sorter.getDirection());
				if (ordering == null) {
					ordering = propertyOrdering;
				} else {
					ordering = ordering.compound(propertyOrdering);
				}
			}
		}

		return ordering;
	}

	public static <T> Ordering<T> createOrderingFromGroups(Collection<GroupInfo> groupInfos) {
		Ordering<T> ordering = null;

		if (groupInfos != null) {
			for (GroupInfo group : groupInfos) {
				Ordering<T> propertyOrdering = createOrdering(group.getProperty(), group.getDirection());
				if (ordering == null) {
					ordering = propertyOrdering;
				} else {
					ordering = ordering.compound(propertyOrdering);
				}
			}
		}

		return ordering;
	}

}
