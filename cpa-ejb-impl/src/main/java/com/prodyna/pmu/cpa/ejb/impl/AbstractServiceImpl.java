/*
 * $Id$
 * Copyright 2013 PRODYNA AG
 */
package com.prodyna.pmu.cpa.ejb.impl;

import javax.inject.Inject;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.MappedField;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.slf4j.Logger;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.prodyna.pmu.cpa.common.entity.BaseEntity;
import com.prodyna.pmu.cpa.ejb.EntityService;

/**
 * Abstract implementation of the {@link EntityService} interface.
 * <p>
 * This abstract class should reduce the concrete service implementation significantly.
 *
 * @author <a href="mailto:pmueller@prodyna.com">pmueller@prodyna.com</a>
 * @param <T> The entity interface which is serviced.
 * @param <I> The entity implementation to use.
 */
public abstract class AbstractServiceImpl<T extends BaseEntity, I extends T> implements EntityService<T> {

	/**
	 * Abstract implementation of the {@link com.prodyna.pmu.cpa.ejb.EntityService.Listable} interface.
	 *
   * @param <T> The entity interface which is serviced.
   * @param <I> The entity implementation to use.
	 */
	public static abstract class Listable<T extends BaseEntity, I extends T> extends AbstractServiceImpl<T, I>
			implements EntityService.Listable<T> {
		
		/** The default order. */
		private static final String DEFAULT_ORDER = "id";

		/** The default list limit. */
		private static final int DEFAULT_LIMIT = 100;
		
	  /**
		 * {@inheritDoc}
		 */
	  @Override
	  public Iterable<T> list() {
	  	Iterable<I> result = getDatastore().find(getEntityClass())
	  			.order(getDefaultOrder())
	  			.limit(getDefaultLimit())
	  			.fetch();
		  return Iterables.transform(result, new Function<I, T>() {
		  	@Override public T apply(I input) {
		  	  return input;
		  	}
		  });
	  }
	  
	  /**
	   * Returns the default order to apply if none is specified.
	   * <p>
	   * This method can be safely overridden by subclasses.
	   *
	   * @return the default order.
	   */
	  protected String getDefaultOrder() {
	  	return DEFAULT_ORDER;
	  }
	  
	  /**
	   * Returns the default limit for queries if none is specified.
	   * <p>
	   * This method can be safely overridden by subclasses.
	   *
	   * @return the default fetch limit.
	   */
	  protected int getDefaultLimit() {
	  	return DEFAULT_LIMIT;
	  }
	}
	
	/**
	 * This {@code UpdateOperationsPreparator} is able to prepare an {@link UpdateOperations} object using
	 * {@code Morphia}'s mapping to determine the persistent fields of the specified object.
	 */
	protected class UpdateOperationsPreparator {
		
		/** The {@code Morphia} instance to use. */
		@Inject
		private Morphia morphia;

		/**
		 * Prepares the specified {@link UpdateOperations} object, setting the updatable values taken from the specified
		 * {@code object}.
		 *
		 * @param updateOperations The update operations object to prepare.
		 * @param object The object used for updating.
		 */
		protected void prepare(UpdateOperations<I> updateOperations, T object) {
			MappedClass mc = morphia.getMapper().getMappedClass(object);
			if (mc != null) {
				for (MappedField mf : mc.getPersistenceFields()) {
					updateOperations.add(mf.getNameToStore(), mf.getFieldValue(object));
					if (logger.isDebugEnabled()) {
						logger.debug("Setting " + mf.getNameToStore() + " = '" + mf.getFieldValue(object) + "'");
					}
				}
			}
			else {
				logger.warn("No mapping found for " + object.getClass().getName());
			}
		}
	}
	
	/** The logger instance to use. */
	@Inject
	private Logger logger;
	
	/** The underlying datastore object. */
	@Inject
	private Datastore datastore;

	/**
	 * {@inheritDoc}
	 */
  @Override
  public T read(String objectId) {
  	return queryById(objectId).get();
  }

	/**
	 * {@inheritDoc}
	 */
  @Override
  public T create(T object) {
		datastore.save(object);
		return object;
  }

	/**
	 * {@inheritDoc}
	 */
  @Override
  public T update(String objectId, T object) {
  	if (object.getObjectId() != null && !object.getObjectId().equals(objectId))
  		throw new RuntimeException(); // TODO Proper exception implementation
  	// Prepare
  	UpdateOperations<I> updateOperations = datastore.createUpdateOperations(getEntityClass());
  	prepare(updateOperations, object);
  	// Execute
  	return datastore.findAndModify(
  			queryById(objectId),
  			updateOperations
  	);
  }

	/**
	 * {@inheritDoc}
	 */
  @Override
  public T delete(String objectId) {
	  return datastore.findAndDelete(queryById(objectId));
  }

	/**
	 * Returns the underlying datastore object.
	 *
	 * @return a datastore object.
	 */
	protected final Datastore getDatastore() {
		return datastore;
	}
	
	/**
	 * Prepares the specified {@link UpdateOperations} object, setting the updatable values taken from the specified
	 * {@code object}.
	 *
	 * @param updateOperations The update operations object to prepare.
	 * @param object The object used for updating.
	 */
	protected void prepare(UpdateOperations<I> updateOperations, T object) {
		new UpdateOperationsPreparator().prepare(updateOperations, object);
	}
	
	/**
	 * Returns the interface of the serviced entity.
	 *
	 * @return an interface class.
	 */
	protected abstract Class<T> getEntityInterface();
	
	/**
	 * Returns the implementation class of the serviced entity.
	 *
	 * @return an implementation class.
	 */
	protected abstract Class<I> getEntityClass();

	/**
	 * Returns the default query to execute when looking up an identifier.
	 *
	 * @param objectId The object identifier to query for.
	 * @return the default query.
	 */
	protected Query<I> queryById(String objectId) {
  	return datastore.createQuery(getEntityClass()).field("id").equal(new ObjectId(objectId));
  }
}