package ca.uhn.fhir.jpa.dao.index;

/*-
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2018 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.model.entity.*;
import ca.uhn.fhir.jpa.searchparam.extractor.ResourceIndexedSearchParams;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class DaoSearchParamSynchronizer {
	@Autowired
	private DaoConfig myDaoConfig;

	@PersistenceContext(type = PersistenceContextType.TRANSACTION)
	protected EntityManager myEntityManager;

	public void synchronizeSearchParamsToDatabase(ResourceIndexedSearchParams theParams, ResourceTable theEntity, ResourceIndexedSearchParams existingParams) {

		synchronize(theParams, theEntity, theParams.stringParams, existingParams.stringParams);
		synchronize(theParams, theEntity, theParams.tokenParams, existingParams.tokenParams);
		synchronize(theParams, theEntity, theParams.numberParams, existingParams.numberParams);
		synchronize(theParams, theEntity, theParams.quantityParams, existingParams.quantityParams);
		synchronize(theParams, theEntity, theParams.dateParams, existingParams.dateParams);
		synchronize(theParams, theEntity, theParams.uriParams, existingParams.uriParams);
		synchronize(theParams, theEntity, theParams.coordsParams, existingParams.coordsParams);
		synchronize(theParams, theEntity, theParams.links, existingParams.links);

		// make sure links are indexed
		theEntity.setResourceLinks(theParams.links);
	}

	private <T extends BaseResourceIndex> void synchronize(ResourceIndexedSearchParams theParams, ResourceTable theEntity, Collection<T> theNewParms, Collection<T> theExistingParms) {
		theParams.calculateHashes(theNewParms);
		List<T> quantitiesToRemove = subtract(theExistingParms, theNewParms);
		List<T> quantitiesToAdd = subtract(theNewParms, theExistingParms);
		tryToReuseIndexEntities(quantitiesToRemove, quantitiesToAdd);
		for (T next : quantitiesToRemove) {
			myEntityManager.remove(next);
			theEntity.getParamsQuantity().remove(next);
		}
		for (T next : quantitiesToAdd) {
			myEntityManager.merge(next);
		}
	}

	/**
	 * The logic here is that often times when we update a resource we are dropping
	 * one index row and adding another. This method tries to reuse rows that would otherwise
	 * have been deleted by updating them with the contents of rows that would have
	 * otherwise been added. In other words, we're trying to replace
	 * "one delete + one insert" with "one update"
	 *
	 * @param theIndexesToRemove The rows that would be removed
	 * @param theIndexesToAdd The rows that would be added
	 */
	private <T extends BaseResourceIndex> void tryToReuseIndexEntities(List<T> theIndexesToRemove, List<T> theIndexesToAdd) {
		for (int addIndex = 0; addIndex < theIndexesToAdd.size(); addIndex++) {

			// If there are no more rows to remove, there's nothing we can reuse
			if (theIndexesToRemove.isEmpty()) {
				break;
			}

			T targetEntity = theIndexesToAdd.get(addIndex);
			if (targetEntity.getId() != null) {
				continue;
			}

			// Take a row we were going to remove, and repurpose its ID
			T entityToReuse = theIndexesToRemove.remove(theIndexesToRemove.size() - 1);
			targetEntity.setId(entityToReuse.getId());
		}
	}




	<T> List<T> subtract(Collection<T> theSubtractFrom, Collection<T> theToSubtract) {
		assert theSubtractFrom != theToSubtract;

		if (theSubtractFrom.isEmpty()) {
			return new ArrayList<>();
		}

		ArrayList<T> retVal = new ArrayList<>(theSubtractFrom);
		retVal.removeAll(theToSubtract);
		return retVal;
	}
}
