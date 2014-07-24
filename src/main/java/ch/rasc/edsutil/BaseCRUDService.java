/**
 * Copyright 2013-2014 Ralph Schaer <ralphschaer@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.rasc.edsutil;

import static ch.ralscha.extdirectspring.annotation.ExtDirectMethodType.STORE_MODIFY;
import static ch.ralscha.extdirectspring.annotation.ExtDirectMethodType.STORE_READ;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import ch.ralscha.extdirectspring.annotation.ExtDirectMethod;
import ch.ralscha.extdirectspring.bean.ExtDirectStoreReadRequest;
import ch.ralscha.extdirectspring.bean.ExtDirectStoreResult;
import ch.ralscha.extdirectspring.bean.SortDirection;
import ch.ralscha.extdirectspring.bean.SortInfo;
import ch.ralscha.extdirectspring.filter.Filter;
import ch.ralscha.extdirectspring.filter.NumericFilter;
import ch.ralscha.extdirectspring.filter.StringFilter;
import ch.rasc.edsutil.bean.ExtDirectStoreValidationResult;
import ch.rasc.edsutil.bean.ValidationError;
import ch.rasc.edsutil.entity.AbstractPersistable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysema.query.SearchResults;
import com.mysema.query.jpa.JPQLQuery;
import com.mysema.query.jpa.impl.JPAQuery;
import com.mysema.query.types.Order;
import com.mysema.query.types.OrderSpecifier;
import com.mysema.query.types.path.PathBuilder;

public abstract class BaseCRUDService<T extends AbstractPersistable> {

	@PersistenceContext
	protected EntityManager entityManager;

	@Autowired
	protected Validator validator;

	@Autowired
	private ObjectMapper objectMapper;

	@ExtDirectMethod(STORE_READ)
	@Transactional(readOnly = true)
	public ExtDirectStoreResult<T> read(ExtDirectStoreReadRequest request) {
		PathBuilder<T> pathBuilder = createPathBuilder();
		JPQLQuery query = new JPAQuery(entityManager).from(pathBuilder);
		addPagingAndSorting(request, query, pathBuilder);

		if (!request.getFilters().isEmpty()) {
			for (Filter filter : request.getFilters()) {
				if (filter instanceof NumericFilter) {
					NumericFilter nf = (NumericFilter) filter;
					int pointPos = nf.getField().indexOf('.');
					if (pointPos == -1) {
						query.where(pathBuilder.get(nf.getField(), Long.class).eq(
								nf.getValue().longValue()));
					}
					else {
						String property = nf.getField().substring(0, pointPos);
						String field = nf.getField().substring(pointPos + 1);
						query.where(pathBuilder.get(property).get(field, Long.class)
								.eq(nf.getValue().longValue()));

					}
				}
				else if (filter instanceof StringFilter && filter.getField().equals("id")) {
					String value = ((StringFilter) filter).getValue();
					try {
						List<Long> ids = objectMapper.readValue(value,
								new TypeReference<List<Long>>() {
									// nothing here
								});
						query.where(pathBuilder.get(filter.getField(), Long.class)
								.in(ids));
					}
					catch (IOException e) {
						// ignore this for now
						e.printStackTrace();
					}

				}
			}
		}

		SearchResults<T> result = query.listResults(pathBuilder);
		return new ExtDirectStoreResult<>(result.getTotal(), result.getResults());
	}

	@ExtDirectMethod(STORE_MODIFY)
	@Transactional
	public ExtDirectStoreResult<T> destroy(Long id) {
		T dbEntity = entityManager.find(getTypeClass(), id);
		entityManager.remove(dbEntity);

		ExtDirectStoreResult<T> result = new ExtDirectStoreResult<>();
		result.setSuccess(Boolean.TRUE);
		return result;
	}

	@ExtDirectMethod(STORE_MODIFY)
	@Transactional
	public ExtDirectStoreValidationResult<T> create(T newEntity) {
		preModify(newEntity);

		List<ValidationError> violations = validateEntity(newEntity);
		if (violations.isEmpty()) {
			entityManager.persist(newEntity);
			return new ExtDirectStoreValidationResult<>(newEntity);
		}

		ExtDirectStoreValidationResult<T> result = new ExtDirectStoreValidationResult<>(
				newEntity);
		result.setValidations(violations);
		return result;

	}

	@ExtDirectMethod(STORE_MODIFY)
	@Transactional
	public ExtDirectStoreValidationResult<T> update(T updatedEntity) {
		preModify(updatedEntity);

		List<ValidationError> violations = validateEntity(updatedEntity);
		if (violations.isEmpty()) {
			return new ExtDirectStoreValidationResult<>(
					entityManager.merge(updatedEntity));
		}

		ExtDirectStoreValidationResult<T> result = new ExtDirectStoreValidationResult<>(
				updatedEntity);
		result.setValidations(violations);
		return result;
	}

	protected List<ValidationError> validateEntity(T entity) {
		Set<ConstraintViolation<T>> constraintViolations = validator.validate(entity);
		List<ValidationError> validationErrors = new ArrayList<>();
		if (!constraintViolations.isEmpty()) {
			for (ConstraintViolation<T> constraintViolation : constraintViolations) {
				ValidationError error = new ValidationError();
				error.setField(constraintViolation.getPropertyPath().toString());
				error.setMessage(constraintViolation.getMessage());
				validationErrors.add(error);
			}
		}
		return validationErrors;
	}

	protected void preModify(@SuppressWarnings("unused") T entity) {
		// default implementation. do nothing.
	}

	@SuppressWarnings("unchecked")
	protected Class<T> getTypeClass() {
		return (Class<T>) GenericTypeResolver.resolveTypeArgument(getClass(),
				BaseCRUDService.class);
	}

	protected PathBuilder<T> createPathBuilder() {
		Class<T> typeClass = getTypeClass();
		return new PathBuilder<>(typeClass, StringUtils.uncapitalize(typeClass
				.getSimpleName()));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void addSorting(ExtDirectStoreReadRequest request, JPQLQuery query,
			PathBuilder<?> pathBuilder) {

		Class<?> clazz = getTypeClass();

		if (!request.getSorters().isEmpty()) {
			for (SortInfo sortInfo : request.getSorters()) {

				Order order;
				if (sortInfo.getDirection() == SortDirection.ASCENDING) {
					order = Order.ASC;
				}
				else {
					order = Order.DESC;
				}

				Field field = ReflectionUtils.findField(clazz, sortInfo.getProperty());
				SortProperty sortProperty = field.getAnnotation(SortProperty.class);

				if (sortProperty != null) {
					String[] splittedValue = sortProperty.value().split("\\.");
					field = ReflectionUtils.findField(clazz, splittedValue[0]);
					PathBuilder path = new PathBuilder(field.getType(), splittedValue[0]);
					query.leftJoin(pathBuilder.get(splittedValue[0]), path);
					query.orderBy(new OrderSpecifier(order, path.get(splittedValue[1])));
				}
				else {
					query.orderBy(new OrderSpecifier(order, pathBuilder.get(sortInfo
							.getProperty())));
				}

			}
		}
	}

	protected void addPagingAndSorting(ExtDirectStoreReadRequest request,
			JPQLQuery query, PathBuilder<?> pathBuilder) {

		if (request.getStart() != null && request.getLimit() > 0) {
			query.offset(request.getStart()).limit(request.getLimit());
		}

		addSorting(request, query, pathBuilder);
	}
}
