/**
 * 
 */
package io.mosip.kernel.uingenerator.service;

import io.mosip.kernel.uingenerator.dto.GetBulkUinsResponseDto;
import io.mosip.kernel.uingenerator.dto.UinResponseDto;
import io.mosip.kernel.uingenerator.dto.UinStatusUpdateReponseDto;
import io.mosip.kernel.uingenerator.dto.UpdateBulkUinsStatusResponseDto;
import io.mosip.kernel.uingenerator.entity.UinEntity;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Dharmesh Khandelwal
 * @author Megha Tanga
 * @since 1.0.0
 *
 */
public interface UinService {

	/**
	 * Gets a uin from database
	 * 
	 * @return UinResponseDto
	 */

	UinResponseDto getUin(RoutingContext routingContext);

	/**
	 * Update the status of the Uin from ISSUED to ASSIGNED
	 * 
	 * @param uin pass uin object as param
	 * 
	 * @return UinStatusUpdateReponseDto
	 */
	UinStatusUpdateReponseDto updateUinStatus(UinEntity uin, RoutingContext routingContext);

	/**
	 * Get multiple uins from database
	 *
	 * @return GetBulkUinsResponseDto
	 */
	GetBulkUinsResponseDto getUinsInBulk(RoutingContext routingContext, int count);

	/**
	 * Update the status of multiple Uins from ISSUED to ASSIGNED
	 *
	 * @param uin pass uin object as param
	 *
	 * @return ResponseWrapper<List<UinStatusUpdateReponseDto>>
	 */
	UpdateBulkUinsStatusResponseDto updateUinsStatusInBulk(UpdateBulkUinsStatusResponseDto uinsStatus, RoutingContext routingContext);

	void transferUin();

	boolean uinExist(String uin);
	
}