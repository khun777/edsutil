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
package ch.rasc.edsutil.entity;

import static com.mysema.query.types.PathMetadataFactory.forVariable;

import javax.annotation.Generated;

import com.mysema.query.types.Path;
import com.mysema.query.types.PathMetadata;
import com.mysema.query.types.path.EntityPathBase;
import com.mysema.query.types.path.NumberPath;

/**
 * QAbstractPersistable is a Querydsl query type for AbstractPersistable
 */
@Generated("com.mysema.query.codegen.SupertypeSerializer")
public class QAbstractPersistable extends EntityPathBase<AbstractPersistable> {

	private static final long serialVersionUID = 1928985407L;

	public static final QAbstractPersistable abstractPersistable = new QAbstractPersistable("abstractPersistable");

	public final NumberPath<Long> id = createNumber("id", Long.class);

	public QAbstractPersistable(String variable) {
		super(AbstractPersistable.class, forVariable(variable));
	}

	public QAbstractPersistable(Path<? extends AbstractPersistable> path) {
		super(path.getType(), path.getMetadata());
	}

	public QAbstractPersistable(PathMetadata<?> metadata) {
		super(AbstractPersistable.class, metadata);
	}

}
